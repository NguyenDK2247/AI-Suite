import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {

        String weatherKey = System.getenv("OPENWEATHER_API_KEY");
        String groqKey = System.getenv("GROQ_API_KEY");

        if (weatherKey == null || groqKey == null) {
            System.err.println("Error: set OPENWEATHER_API_KEY and GROQ_API_KEY");
            System.exit(1);
        }

        WeatherService weatherService = new WeatherService(weatherKey);
        GroqService groqService = new GroqService(groqKey);
        ChatHandler chatHandler = new ChatHandler(weatherService, groqService);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // POST /chat → ChatHandler
        server.createContext("/chat", chatHandler);

        // GET / → serve index.html
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            serveFile(exchange, "frontend/index.html", "text/html; charset=utf-8");
        });

        // GET /styles.css → serve styles.css
        server.createContext("/styles.css", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            serveFile(exchange, "frontend/styles.css", "text/css; charset=utf-8");
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:" + PORT);
        System.out.println("Open that URL in your browser to start chatting.");
    }

    private static void serveFile(com.sun.net.httpserver.HttpExchange exchange,
            String filePath, String contentType) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filePath));
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            String msg = filePath + " not found";
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(404, msg.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(msg.getBytes());
            }
        }
    }
}
