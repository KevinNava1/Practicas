import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NonBlockingCrawler {

    static class URLTask {
        String url;
        URLTask(String url) { this.url = url; }
    }

    private final Selector selector;
    private final Map<SocketChannel, ConnectionState> connections = new HashMap<>();
    private final Set<String> visited = new HashSet<>();
    private final Queue<URLTask> pending = new ArrayDeque<>();

    public NonBlockingCrawler() throws IOException {
        this.selector = Selector.open();
    }

    // Estado de cada conexión
    static class ConnectionState {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        boolean headersDone = false;
        URI uri;
        SocketChannel channel;
        Path filePath;
        FileChannel fileChannel;
        StringBuilder headers = new StringBuilder();
    }

    public void enqueue(String url) {
        if (!visited.contains(url)) {
            visited.add(url);
            pending.add(new URLTask(url));
        }
    }

    public void start(String startURL) throws IOException {
        enqueue(startURL);

        while (!pending.isEmpty() || !connections.isEmpty()) {

            // Iniciar nuevas descargas
            while (!pending.isEmpty()) {
                URLTask task = pending.poll();
                startDownload(task.url);
            }

            // Multiplexar sockets
            selector.select(1000);

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) continue;

                if (key.isConnectable()) handleConnect(key);
                if (key.isWritable()) handleWrite(key);
                if (key.isReadable()) handleRead(key);
            }
        }
    }

    private void startDownload(String url) throws IOException {
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = (uri.getPort() == -1) ? 80 : uri.getPort();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";

        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port));

        ConnectionState state = new ConnectionState();
        state.uri = uri;
        state.channel = channel;

        // --- Manejo de subdirectorios y index.html ---
        if (path.endsWith("/") || !path.contains(".")) {
            path = path + (path.endsWith("/") ? "index.html" : "/index.html");
        }

        state.filePath = Paths.get("downloads", path);
        Files.createDirectories(state.filePath.getParent());
        state.fileChannel = FileChannel.open(state.filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        connections.put(channel, state);
        channel.register(selector, SelectionKey.OP_CONNECT);
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        ConnectionState state = connections.get(ch);
        if (ch.finishConnect()) {
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        ConnectionState state = connections.get(ch);

        String request = "GET " + state.uri.getRawPath() + " HTTP/1.1\r\n" +
                "Host: " + state.uri.getHost() + "\r\n" +
                "Connection: close\r\n\r\n";

        ch.write(ByteBuffer.wrap(request.getBytes()));
        key.interestOps(SelectionKey.OP_READ);
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        ConnectionState state = connections.get(ch);

        state.buffer.clear();
        int read = ch.read(state.buffer);

        if (read == -1) {
            ch.close();
            connections.remove(ch);
            state.fileChannel.close();
            processHTML(state);
            return;
        }

        state.buffer.flip();

        if (!state.headersDone) {
            // Copiar bytes a un array para analizar headers
            byte[] data = new byte[state.buffer.remaining()];
            state.buffer.get(data);

            String temp = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
            int idx = temp.indexOf("\r\n\r\n");

            if (idx != -1) {
                state.headersDone = true;

                // Escribir los bytes reales del body directamente
                byte[] bodyBytes = Arrays.copyOfRange(data, idx + 4, data.length);
                state.fileChannel.write(ByteBuffer.wrap(bodyBytes));

                // Revisar si hay redirección Location
                String headersStr = temp.substring(0, idx);
                String[] lines = headersStr.split("\r\n");
                for (String line : lines) {
                    if (line.toLowerCase().startsWith("location:")) {
                        String newURL = line.substring(9).trim();
                        enqueue(newURL);
                        ch.close();
                        connections.remove(ch);
                        state.fileChannel.close();
                        return;
                    }
                }
            }
        } else {
            // Escritura normal de cualquier archivo (binario o texto)
            state.fileChannel.write(state.buffer);
        }
    }

    private void processHTML(ConnectionState state) throws IOException {
        byte[] contentBytes = Files.readAllBytes(state.filePath);
        String content = new String(contentBytes, java.nio.charset.StandardCharsets.UTF_8);

        if (content.toLowerCase().contains("<html")) {
            extractLinks(content, state.uri);
        }
    }

    private void extractLinks(String html, URI baseURI) {
        Pattern p = Pattern.compile("(?i)(href|src)=[\"'](.*?)[\"']");
        Matcher m = p.matcher(html);

        while (m.find()) {
            String link = m.group(2);
            String normalized = normalize(baseURI, link);
            enqueue(normalized);
        }
    }

    private String normalize(URI base, String link) {
        if (link.startsWith("http://") || link.startsWith("https://")) return link;
        if (link.startsWith("/")) return base.getScheme() + "://" + base.getHost() + link;

        String path = base.getPath();
        if (!path.endsWith("/")) path = path.substring(0, path.lastIndexOf('/') + 1);
        return base.getScheme() + "://" + base.getHost() + path + link;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Uso: java NonBlockingCrawler <URL>");
            return;
        }

        NonBlockingCrawler crawler = new NonBlockingCrawler();
        crawler.start(args[0]);
        System.out.println("Descarga completada.");
    }
}
