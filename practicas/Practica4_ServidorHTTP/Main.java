// Main.java
// Servidor HTTP con:
// - GET, POST, PUT, DELETE
// - 4+ MIME: text/html, text/plain, application/json, image/png, text/css
// - Pool de conexiones configurable (semáforo)
// - Cuando el uso del pool supera 50% en el primario: arranca servidor secundario y redirige con 307
//


import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    // ==========================
    // Estado compartido
    // ==========================
    static class ServerState {
        final int poolSize;
        final int primaryPort;
        final int secondaryPort;
        final Semaphore poolSem;
        final Object lock = new Object();
        int inUse = 0;
        boolean secondaryStarted = false;
        HttpServer secondaryServer = null;
        ExecutorService secondaryExec = null;

        ServerState(int poolSize, int primaryPort, int secondaryPort) {
            this.poolSize = poolSize;
            this.primaryPort = primaryPort;
            this.secondaryPort = secondaryPort;
            this.poolSem = new Semaphore(poolSize);
        }

        int halfThresholdInt() {
            // superar la mitad => in_use > poolSize/2
            // Ej.: 4 -> umbral 2, 5 -> umbral 2.5 (comparamos como double)
            return poolSize / 2; // solo referencial
        }

        void acquireSlot() throws InterruptedException {
            poolSem.acquire();
            synchronized (lock) { inUse++; }
        }
        void releaseSlot() {
            synchronized (lock) { inUse = Math.max(0, inUse - 1); }
            poolSem.release();
        }

        boolean isOverHalf() {
            synchronized (lock) {
                return inUse > (poolSize / 2.0);
            }
        }

        void maybeStartSecondary() {
            synchronized (lock) {
                if (!secondaryStarted && isOverHalf()) {
                    System.out.printf("[INFO] Umbral superado: in_use=%d > %.1f. Iniciando servidor secundario en :%d...%n",
                            inUse, poolSize / 2.0, secondaryPort);
                    try {
                        secondaryServer = HttpServer.create(new InetSocketAddress(secondaryPort), 0);
                    } catch (IOException e) {
                        System.err.println("[FATAL] No se pudo iniciar servidor secundario: " + e);
                        return;
                    }
                    // En el secundario NO redirigimos
                    HttpHandler handler = new AppHandler(this, true);
                    secondaryServer.createContext("/", handler);
                    secondaryExec = Executors.newCachedThreadPool();
                    secondaryServer.setExecutor(secondaryExec);
                    secondaryServer.start();
                    secondaryStarted = true;
                    System.out.printf("[INFO] Servidor SECUNDARIO escuchando en http://0.0.0.0:%d (pool=%d)\n", secondaryPort, poolSize);
                }
            }
        }
    }

    // ==========================
    // Manejador principal
    // ==========================
    static class AppHandler implements HttpHandler {
        private final ServerState state;
        private final boolean isSecondary;
        private final Base64.Decoder b64 = Base64.getDecoder();
        // PNG 1x1 transparente
        private final byte[] PNG_1x1 = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/er0mWAAAAAASUVORK5CYII=");

        AppHandler(ServerState state, boolean isSecondary) {
            this.state = state;
            this.isSecondary = isSecondary;
        }

        @Override public void handle(HttpExchange ex) throws IOException {
            long start = System.nanoTime();
            boolean acquired = false;
            try {
                // Gestionar pool
                try {
                    state.acquireSlot();
                    acquired = true;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    sendText(ex, 503, "Servidor ocupado\n");
                    return;
                }

                // Si es primario, evaluar arranque/redirección
                if (!isSecondary) {
                    state.maybeStartSecondary();
                    if (state.secondaryStarted && state.isOverHalf()) {
                        // Redirección 307 al secundario
                        String host = ex.getRequestHeaders().getFirst("Host");
                        String hostnameOnly = (host == null) ? "127.0.0.1" : host.split(":")[0];
                        URI uri = ex.getRequestURI();
                        String location = String.format("http://%s:%d%s", hostnameOnly, state.secondaryPort, uri.toString());
                        System.out.printf("[INFO] Redirigiendo %s %s -> %s (in_use=%d/%d)\n",
                                ex.getRequestMethod(), uri, location, state.inUse, state.poolSize);
                        Headers h = ex.getResponseHeaders();
                        h.set("Location", location);
                        h.set("Connection", "close");
                        byte[] body = "Redirecting to secondary server...".getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(307, body.length);
                        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                        return;
                    }
                }

                // Procesar localmente
                route(ex);
            } catch (Exception e) {
                System.err.println("[ERROR] " + e);
                safe500(ex);
            } finally {
                if (acquired) state.releaseSlot();
                long durMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                logAccess(ex, durMs);
            }
        }

        private void route(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            URI uri = ex.getRequestURI();
            String path = uri.getPath();

            if ("GET".equals(method)) {
                if ("/".equals(path) || "/html".equals(path)) {
                    String html = """
                    <!doctype html>
                    <html lang=\"es\">
                      <head>
                        <meta charset=\"utf-8\">
                        <title>Servidor %s</title>
                        <link rel=\"stylesheet\" href=\"/style.css\">
                      </head>
                      <body>
                        <h1>Hola desde el servidor %s</h1>
                        <p>Pool en uso: %d/%d</p>
                        <p>Rutas: /html, /text, /json, /image.png, /style.css, /sleep?ms=NNN</p>
                      </body>
                    </html>
                    """.formatted(isSecondary?"secondary":"primary", isSecondary?"secondary":"primary", state.inUse, state.poolSize);
                    sendBytes(ex, 200, html.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8");
                    return;
                }
                if ("/text".equals(path)) {
                    String txt = "Texto plano desde el servidor " + (isSecondary?"secondary":"primary") + "\n";
                    sendText(ex, 200, txt);
                    return;
                }
                if ("/json".equals(path)) {
                    String json = "{" +
                            "\"ok\":true," +
                            "\"server\":\"" + (isSecondary?"secondary":"primary") + "\"," +
                            "\"pool\":{\"in_use\":" + state.inUse + ",\"size\":" + state.poolSize + "}}";
                    sendBytes(ex, 200, json.getBytes(StandardCharsets.UTF_8), "application/json");
                    return;
                }
                if ("/image.png".equals(path)) {
                    sendBytes(ex, 200, PNG_1x1, "image/png");
                    return;
                }
                if ("/style.css".equals(path)) {
                    String css = "body{font-family:system-ui,Segoe UI,Arial,sans-serif;margin:2rem;}h1{color:#333;}p{color:#555;}";
                    sendBytes(ex, 200, css.getBytes(StandardCharsets.UTF_8), "text/css; charset=utf-8");
                    return;
                }
                if ("/sleep".equals(path)) {
                    Map<String,String> q = parseQuery(uri.getQuery());
                    long ms = parseLongOr(q.getOrDefault("ms", "1000"), 1000);
                    try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    String json = "{\"slept_ms\":" + ms + ",\"server\":\"" + (isSecondary?"secondary":"primary") + "\"}";
                    sendBytes(ex, 200, json.getBytes(StandardCharsets.UTF_8), "application/json");
                    return;
                }
                notFound(ex); return;
            }

            if ("POST".equals(method)) {
                if ("/echo".equals(path)) {
                    String ctype = header(ex, "Content-Type", "application/octet-stream");
                    byte[] body = readBody(ex);
                    // Si es JSON, devolver tal cual; si no, devolver como octetos con su tipo
                    if (ctype.toLowerCase().startsWith("application/json")) {
                        sendBytes(ex, 200, body, "application/json");
                    } else {
                        sendBytes(ex, 200, body, ctype);
                    }
                    return;
                }
                notFound(ex); return;
            }

            if ("PUT".equals(method)) {
                if ("/resource".equals(path)) {
                    String ctype = header(ex, "Content-Type", "application/octet-stream");
                    byte[] body = readBody(ex);
                    Files.createDirectories(Path.of("data"));
                    String ext = ctype.toLowerCase().startsWith("application/json") ? ".json" : ".txt";
                    Path p = Path.of("data", "resource" + ext);
                    Files.write(p, body);
                    String json = "{\"ok\":true,\"saved_as\":\"" + p.toString().replace("\\", "/") + "\",\"content_type\":\"" + ctype + "\",\"server\":\"" + (isSecondary?"secondary":"primary") + "\"}";
                    sendBytes(ex, 201, json.getBytes(StandardCharsets.UTF_8), "application/json");
                    return;
                }
                notFound(ex); return;
            }

            if ("DELETE".equals(method)) {
                if ("/resource".equals(path)) {
                    boolean removed = false;
                    for (String name : List.of("data/resource.json", "data/resource.txt")) {
                        Path p = Path.of(name);
                        try { removed |= Files.deleteIfExists(p); } catch (IOException ignore) {}
                    }
                    String json = "{\"ok\":true,\"deleted\":" + removed + ",\"server\":\"" + (isSecondary?"secondary":"primary") + "\"}";
                    sendBytes(ex, 200, json.getBytes(StandardCharsets.UTF_8), "application/json");
                    return;
                }
                notFound(ex); return;
            }

            // Método no soportado
            sendText(ex, 405, "Method Not Allowed\n");
        }

        // ==========================
        // Utilidades HTTP
        // ==========================
        private static byte[] readBody(HttpExchange ex) throws IOException {
            try (InputStream is = ex.getRequestBody(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int r; while ((r = is.read(buf)) != -1) bos.write(buf, 0, r); return bos.toByteArray();
            }
        }
        private static String header(HttpExchange ex, String name, String def) {
            String v = ex.getRequestHeaders().getFirst(name);
            return v == null ? def : v;
        }
        private static void sendBytes(HttpExchange ex, int code, byte[] bytes, String contentType) throws IOException {
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", contentType);
            h.set("Content-Length", String.valueOf(bytes.length));
            h.set("Connection", "close");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
        private static void sendText(HttpExchange ex, int code, String text) throws IOException {
            sendBytes(ex, code, text.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
        }
        private static void notFound(HttpExchange ex) throws IOException { sendText(ex, 404, "Not Found\n"); }
        private static void safe500(HttpExchange ex) {
            try { sendText(ex, 500, "Internal Server Error\n"); } catch (IOException ignore) {}
        }
        private static void logAccess(HttpExchange ex, long durMs) {
            String addr = ex.getRemoteAddress() == null ? "-" : ex.getRemoteAddress().getAddress().getHostAddress();
            System.out.printf("[ACCESS] %s - - [%s] \"%s %s\" %dms in_use=%d/%d secondary=%s%n",
                    addr,
                    Date.from(Instant.now()),
                    ex.getRequestMethod(), ex.getRequestURI(),
                    durMs, 
                    ex == null ? -1 : ((ServerState)((AppHandler)ex.getHttpContext().getHandler()).state).inUse,
                    ((AppHandler)ex.getHttpContext().getHandler()).state.poolSize,
                    ((AppHandler)ex.getHttpContext().getHandler()).isSecondary);
        }
        private static Map<String,String> parseQuery(String q) {
            Map<String,String> map = new HashMap<>();
            if (q == null || q.isEmpty()) return map;
            for (String part : q.split("&")) {
                int i = part.indexOf('=');
                if (i > 0) {
                    map.put(urlDecode(part.substring(0,i)), urlDecode(part.substring(i+1)));
                } else {
                    map.put(urlDecode(part), "");
                }
            }
            return map;
        }
        private static String urlDecode(String s) {
            try { return URLDecoder.decode(s, StandardCharsets.UTF_8); } catch (Exception e) { return s; }
        }
        private static long parseLongOr(String s, long def) {
            try { return Long.parseLong(s); } catch (Exception e) { return def; }
        }
    }

    // ==========================
    // Bootstrap
    // ==========================
    public static void main(String[] args) throws Exception {
        int port = 8080;
        int redirectPort = 8081;
        int pool = 8;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": port = Integer.parseInt(args[++i]); break;
                case "--redirect-port": redirectPort = Integer.parseInt(args[++i]); break;
                case "--pool": pool = Integer.parseInt(args[++i]); break;
                default: System.err.println("[WARN] Argumento ignorado: " + args[i]);
            }
        }
        if (pool <= 0) { System.err.println("[FATAL] --pool debe ser > 0"); System.exit(2); }

        ServerState state = new ServerState(pool, port, redirectPort);

        HttpServer primary = HttpServer.create(new InetSocketAddress(port), 0);
        primary.createContext("/", new AppHandler(state, false));

        // Usamos un pool elástico; el límite de concurrencia real lo impone el semáforo
        ExecutorService exec = Executors.newCachedThreadPool();
        primary.setExecutor(exec);

        System.out.printf("[INFO] Servidor PRIMARIO escuchando en http://0.0.0.0:%d (pool=%d)\n", port, pool);
        primary.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[INFO] Apagando...");
            try { primary.stop(0); } catch (Exception ignore) {}
            exec.shutdownNow();
            synchronized (state.lock) {
                if (state.secondaryServer != null) {
                    try { state.secondaryServer.stop(0); } catch (Exception ignore) {}
                    if (state.secondaryExec != null) state.secondaryExec.shutdownNow();
                }
            }
        }));
    }
}
