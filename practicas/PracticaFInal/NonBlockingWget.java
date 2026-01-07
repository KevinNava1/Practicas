import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NonBlockingWget {
    private static final int BUFFER_SIZE = 8192;
    private static final Set<String> VISITED = new HashSet<>();
    private static final Pattern BODY_START = Pattern.compile("\r\n\r\n");
    private static final Pattern LINK_PATTERN = Pattern.compile("<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_PATTERN = Pattern.compile("<img\\s+[^>]*src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_PATTERN = Pattern.compile("<link\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    static class ParsedUrl {
        String host;
        int port;
        String path;

        static ParsedUrl parse(String url) {
            if (!url.startsWith("http://")) throw new IllegalArgumentException("Solo HTTP");
            int hostStart = 7;
            int hostEnd = url.indexOf('/', hostStart);
            if (hostEnd == -1) hostEnd = url.length();
            ParsedUrl pu = new ParsedUrl();
            pu.host = url.substring(hostStart, hostEnd);
            pu.port = 80;
            pu.path = hostEnd < url.length() ? url.substring(hostEnd) : "/";
            return pu;
        }
    }

    // ⭐ NUEVO: Detecta extensión del archivo
    private static String getFileExtension(String path, String contentType) {
        // Por extensión de URL
        if (path.endsWith(".html") || path.endsWith(".htm")) return "html";
        if (path.contains(".png")) return "png";
        if (path.contains(".jpg") || path.contains(".jpeg")) return "jpg";
        if (path.contains(".gif")) return "gif";
        if (path.contains(".css")) return "css";
        if (path.contains(".js")) return "js";
        if (path.contains(".pdf")) return "pdf";
        if (path.contains(".zip")) return "zip";
        
        // Por Content-Type
        if (contentType.contains("image/png")) return "png";
        if (contentType.contains("image/jpeg")) return "jpg";
        if (contentType.contains("text/css")) return "css";
        if (contentType.contains("application/pdf")) return "pdf";
        
        return "bin";  // Default binario
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java NonBlockingWget <url> [max-depth]");
            return;
        }
        String url = args[0];
        int maxDepth = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        download(url, 0, maxDepth);
    }

    private static void download(String url, int depth, int maxDepth) {
        if (depth > maxDepth || !VISITED.add(url)) return;

        ParsedUrl pu = ParsedUrl.parse(url);
        String contentType = "unknown";
        StringBuilder headers = new StringBuilder();
        
        // ⭐ NUEVO: Detecta Content-Type de headers
        String fileName = String.format("wget_d%d_%s_%s", depth, pu.host, pu.path.replaceAll("[^a-zA-Z0-9.-]", "_"));
        
        System.out.printf("Descargando %s (profundidad %d)%n", url, depth);

        try (Selector selector = Selector.open();
             SocketChannel sc = SocketChannel.open();
             FileOutputStream fos = new FileOutputStream(fileName)) {

            sc.configureBlocking(false);
            sc.connect(new InetSocketAddress(pu.host, pu.port));
            sc.register(selector, SelectionKey.OP_CONNECT);

            String request = "GET " + pu.path + " HTTP/1.1\r\nHost: " + pu.host + "\r\nConnection: close\r\n\r\n";
            ByteBuffer reqBuf = ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8));
            ByteBuffer readBuf = ByteBuffer.allocate(BUFFER_SIZE);
            boolean headersDone = false;

            while (selector.select() > 0) {
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (key.isConnectable()) {
                        if (sc.finishConnect()) {
                            sc.register(selector, SelectionKey.OP_WRITE);
                        }
                    } else if (key.isWritable()) {
                        sc.write(reqBuf);
                        if (!reqBuf.hasRemaining()) {
                            sc.register(selector, SelectionKey.OP_READ);
                        }
                    } else if (key.isReadable()) {
                        int bytesRead = sc.read(readBuf);
                        if (bytesRead > 0) {
                            readBuf.flip();
                            String chunk = StandardCharsets.UTF_8.decode(readBuf).toString();
                            
                            if (!headersDone) {
                                headers.append(chunk);
                                Matcher bodyMatcher = BODY_START.matcher(headers);
                                if (bodyMatcher.find()) {
                                    headersDone = true;
                                    contentType = extractContentType(headers.toString());
                                    
                                    // ⭐ NUEVO: Extensión dinámica
                                    String ext = getFileExtension(pu.path, contentType);
                                    String finalFileName = fileName + "." + ext;
                                    fileName = finalFileName;  // Cambia nombre
// Elimina la línea try-with-resources fos y usa manual close

                                    
                                    // Escribe body
                                    String bodyPart = chunk.substring(bodyMatcher.end());
                                    if (!bodyPart.isEmpty()) {
                                        fos.getChannel().write(ByteBuffer.wrap(bodyPart.getBytes(StandardCharsets.UTF_8)));
                                    }
                                }
                            } else {
                                fos.getChannel().write(readBuf);
                            }
                            readBuf.clear();
                        } else {
                            String finalFileName = fileName + "." + getFileExtension(pu.path, contentType);
                            System.out.println("Completado: " + finalFileName);
                            
                            // ⭐ RECURSIÓN INTELIGENTE: HTML/CSS/JS sí, binarios NO
                            if (depth < maxDepth && isCrawlable(contentType)) {
                                crawlAllResources(headers.toString(), pu.host, depth + 1, maxDepth);
                            }
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error en " + url + ": " + e.getMessage());
        }
    }

    // ⭐ NUEVO: Extrae Content-Type de headers
    private static String extractContentType(String headers) {
        String[] lines = headers.split("\r\n");
        for (String line : lines) {
            if (line.toLowerCase().startsWith("content-type:")) {
                return line.substring("content-type:".length()).trim().toLowerCase();
            }
        }
        return "unknown";
    }

    // ⭐ NUEVO: Decide qué crawlear
    private static boolean isCrawlable(String contentType) {
        return contentType.contains("text/html") || 
               contentType.contains("text/css") || 
               contentType.contains("application/javascript");
    }

    // ⭐ NUEVO: Extrae TODOS los recursos (a href, img src, link href)
    private static void crawlAllResources(String html, String baseHost, int depth, int maxDepth) {
        // Links <a href>
        Matcher linkMatcher = LINK_PATTERN.matcher(html);
        while (linkMatcher.find()) {
            String link = linkMatcher.group(1);
            processLink(link, baseHost, depth, maxDepth);
        }
        
        // Imágenes <img src>
        Matcher imgMatcher = IMG_PATTERN.matcher(html);
        while (imgMatcher.find()) {
            String img = imgMatcher.group(1);
            processLink(img, baseHost, depth, maxDepth);
        }
        
        // CSS <link href>
        Matcher cssMatcher = CSS_PATTERN.matcher(html);
        while (cssMatcher.find()) {
            String css = cssMatcher.group(1);
            processLink(css, baseHost, depth, maxDepth);
        }
    }

    private static void processLink(String link, String baseHost, int depth, int maxDepth) {
        if (link.startsWith("/")) {
            download("http://" + baseHost + link, depth, maxDepth);
        } else if (link.startsWith("http://" + baseHost)) {
            download(link, depth, maxDepth);
        }
    }
}
