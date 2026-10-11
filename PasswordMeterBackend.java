import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class PasswordMeterBackend {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/password_meter";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "MY_SQL_PASSWORD";

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", new HealthCheckHandler());
        server.createContext("/api/submit", new SubmitHandler());
        server.createContext("/api/export", new ExportHandler());

        server.setExecutor(null); // use the default single-threaded executor
        server.start();

        System.out.println("✅ Java backend running at http://localhost:" + PORT);
        System.out.println("   Health check: http://localhost:" + PORT + "/");
        System.out.println("   Submit endpoint: POST http://localhost:" + PORT + "/api/submit");
        System.out.println("   Export endpoint: GET  http://localhost:" + PORT + "/api/export");
    }

    // ============================================
    // DATABASE CONNECTION HELPER
    // ============================================
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Java backend is running.";
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    // SUBMIT HANDLER — receives participant data and saves it to MySQL
    static class SubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Allow the browser (running on a different address) to call this server.
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Browsers send a preflight OPTIONS request before the real POST — just approve it.
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, "{\"status\":\"error\",\"message\":\"Only POST is allowed\"}");
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> data = parseJson(body);

                try (Connection conn = getConnection()) {
                    String sql = "INSERT INTO responses "
                            + "(participant_id, group_name, password_hash, score, time_taken_ms, password_length, "
                            + "has_upper, has_lower, has_digit, has_special, motivation, engagement) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, data.getOrDefault("participantId", ""));
                    stmt.setString(2, data.getOrDefault("group", ""));
                    stmt.setString(3, data.getOrDefault("hash", ""));
                    stmt.setInt(4, parseIntSafe(data.get("score")));
                    stmt.setInt(5, parseIntSafe(data.get("timeTaken")));
                    stmt.setInt(6, parseIntSafe(data.get("passwordLength")));
                    stmt.setBoolean(7, parseBoolSafe(data.get("hasUpper")));
                    stmt.setBoolean(8, parseBoolSafe(data.get("hasLower")));
                    stmt.setBoolean(9, parseBoolSafe(data.get("hasDigit")));
                    stmt.setBoolean(10, parseBoolSafe(data.get("hasSpecial")));
                    stmt.setInt(11, parseIntSafe(data.get("motivation")));
                    stmt.setInt(12, parseIntSafe(data.get("engagement")));

                    stmt.executeUpdate();
                }

                sendJson(exchange, 200, "{\"status\":\"success\"}");
                System.out.println("📥 Saved: " + data.get("participantId"));

            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    // EXPORT HANDLER — lets you view all saved data as JSON in a browser
    static class ExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            StringBuilder json = new StringBuilder("[");
            try (Connection conn = getConnection()) {
                String sql = "SELECT * FROM responses ORDER BY created_at ASC";
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();

                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"participantId\":\"").append(rs.getString("participant_id")).append("\",")
                        .append("\"group\":\"").append(rs.getString("group_name")).append("\",")
                        .append("\"score\":").append(rs.getInt("score")).append(",")
                        .append("\"motivation\":").append(rs.getInt("motivation")).append(",")
                        .append("\"engagement\":").append(rs.getInt("engagement")).append(",")
                        .append("\"createdAt\":\"").append(rs.getTimestamp("created_at")).append("\"")
                        .append("}");
                }
            } catch (SQLException e) {
                sendJson(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
                return;
            }
            json.append("]");

            sendJson(exchange, 200, json.toString());
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            int idx = pair.indexOf(":");
            if (idx == -1) continue;
            String key = pair.substring(0, idx).trim().replaceAll("^\"|\"$", "");
            String value = pair.substring(idx + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    private static int parseIntSafe(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean parseBoolSafe(String s) {
        return "true".equalsIgnoreCase(s);
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
