import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {

        // --- Read API keys from environment variables (never hardcode them) ---
        String weatherKey = System.getenv("OPENWEATHER_API_KEY");
        String groqKey = System.getenv("GROQ_API_KEY");

        if (weatherKey == null || groqKey == null) {
            System.err.println("Error: set OPENWEATHER_API_KEY and GROQ_API_KEY");
            System.exit(1);
        }

        // --- Wire up the services ---
        WeatherService weatherService = new WeatherService(weatherKey);
        GroqService groqService = new GroqService(groqKey);
        ChatHandler chatHandler = new ChatHandler(weatherService, groqService);

        // --- Start the HTTP server ---
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // POST /chat → ChatHandler
        server.createContext("/chat", chatHandler);

        // GET / → serve index.html from the frontend folder
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            try {
                Path htmlPath = Path.of("frontend/index.html");
                byte[] html = Files.readAllBytes(htmlPath);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, html.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(html);
                }
            } catch (IOException e) {
                String msg = "frontend/index.html not found";
                exchange.sendResponseHeaders(404, msg.length());
                exchange.getResponseBody().write(msg.getBytes());
                exchange.getResponseBody().close();
            }
        });

        server.setExecutor(null); // uses default executor
        server.start();

        System.out.println("Server running at http://localhost:" + PORT);
        System.out.println("Open that URL in your browser to start chatting.");
    }
}
