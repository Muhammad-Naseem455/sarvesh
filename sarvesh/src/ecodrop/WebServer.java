package ecodrop;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Built-in Zero-Dependency Web Server for EcoDrop using JDK's HttpServer.
 * Serves modern web frontend and provides REST APIs.
 */
public class WebServer {
    private static final EcoService svc = new EcoService();

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null) {
            try { port = Integer.parseInt(envPort); } catch (Exception ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // 1. Root route: Serves web/index.html
        server.createContext("/", ex -> {
            Path p = Paths.get("web/index.html");
            if (!Files.exists(p)) {
                p = Paths.get("C:/Users/Muhammad Naseem/.gemini/antigravity/scratch/EcoDrop/web/index.html");
            }
            if (!Files.exists(p)) {
                String err = "<h1>404: web/index.html not found</h1>";
                ex.sendResponseHeaders(404, err.length());
                ex.getResponseBody().write(err.getBytes());
                ex.close();
                return;
            }
            byte[] bytes = Files.readAllBytes(p);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });

        // 2. Authentication APIs
        server.createContext("/api/login", ex -> handle(ex, q -> {
            User u = svc.login(q.get("query"));
            return "{\"message\":\"Welcome back, " + User.esc(u.getName()) + "!\",\"user\":" + u.toJson() + "}";
        }));

        server.createContext("/api/register", ex -> handle(ex, q -> {
            String role = q.get("role");
            User u = "RECYCLER".equalsIgnoreCase(role)
                    ? new Recycler(0, q.get("name"), q.get("email"), q.get("phone"), q.get("city"))
                    : new Citizen(0, q.get("name"), q.get("email"), q.get("phone"), q.get("city"), 0);
            int id = svc.register(u);
            return "{\"message\":\"Account created successfully!\",\"user\":" + u.toJson() + "}";
        }));

        // 3. Citizen Operations
        server.createContext("/api/deposit", ex -> handle(ex, q -> {
            int citId = Integer.parseInt(q.get("citizenId"));
            String cat = q.get("category");
            String name = q.get("itemName");
            int qty = Integer.parseInt(q.get("qty"));
            HazardLevel hz = HazardLevel.fromString(q.get("hazard"));
            svc.depositItem(citId, cat, name, qty, hz);
            return msg("E-Waste item listed successfully in the hazard queue!");
        }));

        server.createContext("/api/my-items", ex -> handle(ex, q -> {
            int citId = Integer.parseInt(q.get("citizenId"));
            return "[" + svc.myItems(citId).stream().map(EWasteItem::toJson).collect(Collectors.joining(",")) + "]";
        }));

        // 4. Recycler Operations & Priority Queue
        server.createContext("/api/search", ex -> handle(ex, q ->
            "[" + svc.searchHazardQueue(q.get("q"), q.get("city")).stream().map(EWasteItem::toJson).collect(Collectors.joining(",")) + "]"
        ));

        server.createContext("/api/schedule", ex -> handle(ex, q -> {
            int recyclerId = Integer.parseInt(q.get("recyclerId"));
            int itemId = Integer.parseInt(q.get("itemId"));
            svc.schedulePickup(recyclerId, itemId);
            return msg("Pickup haul successfully scheduled! The item is locked for you.");
        }));

        server.createContext("/api/my-pickups", ex -> handle(ex, q -> {
            int recyclerId = Integer.parseInt(q.get("recyclerId"));
            return "[" + svc.myPickups(recyclerId).stream().map(EcoService.PickupDto::toJson).collect(Collectors.joining(",")) + "]";
        }));

        server.createContext("/api/complete", ex -> handle(ex, q -> {
            int pickupId = Integer.parseInt(q.get("pickupId"));
            svc.completeRecycling(pickupId);
            return msg("Recycling verified & completed! EcoCredits awarded to Citizen.");
        }));

        // 5. Environmental Stats
        server.createContext("/api/stats", ex -> handle(ex, q ->
            svc.getImpactStats().entrySet().stream()
               .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
               .collect(Collectors.joining(",", "{", "}"))
        ));

        server.start();
        System.out.println("==================================================");
        System.out.println("⚡ EcoDrop Web Server Started!");
        System.out.println("🌐 Accessible at: http://localhost:" + port);
        System.out.println("==================================================");
    }

    interface ApiHandler { String process(Map<String, String> params) throws Exception; }

    private static byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static void handle(HttpExchange ex, ApiHandler handler) throws IOException {
        int code = 200;
        String body;
        try {
            String raw = ex.getRequestMethod().equalsIgnoreCase("POST")
                    ? new String(readBytes(ex.getRequestBody()), StandardCharsets.UTF_8)
                    : ex.getRequestURI().getRawQuery();
            body = handler.process(parseQuery(raw));
        } catch (Exception e) {
            code = 400;
            body = "{\"error\":\"" + User.esc(e.getMessage()) + "\"}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String msg(String m) {
        return "{\"message\":\"" + User.esc(m) + "\"}";
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            try {
                String k = URLDecoder.decode(parts[0], "UTF-8");
                String v = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "";
                map.put(k, v);
            } catch (Exception ignored) {}
        }
        return map;
    }
}
