package com.sbtv.handler;

import com.sbtv.config.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry(".html", "text/html; charset=utf-8"),
            Map.entry(".htm", "text/html; charset=utf-8"),
            Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".js", "application/javascript; charset=utf-8"),
            Map.entry(".json", "application/json; charset=utf-8"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".m3u8", "application/vnd.apple.mpegurl"),
            Map.entry(".ts", "video/mp2t"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"),
            Map.entry(".ttf", "font/ttf")
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String pathStr = exchange.getRequestURI().getPath();
        if (pathStr.equals("/") || pathStr.isEmpty()) {
            pathStr = "/index.html";
        }

        pathStr = URLDecoder.decode(pathStr, StandardCharsets.UTF_8);

        // Security check: prevent directory traversal
        if (pathStr.contains("..")) {
            sendText(exchange, 403, "Access denied");
            return;
        }

        Path filePath = AppConfig.getWebDir().resolve(pathStr.substring(1)).normalize();
        if (!filePath.startsWith(AppConfig.getWebDir()) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
            // Fallback: If requesting HTML route, serve index.html (SPA support)
            Path indexPath = AppConfig.getWebDir().resolve("index.html");
            if (Files.exists(indexPath) && !pathStr.startsWith("/api/")) {
                serveFile(exchange, indexPath);
                return;
            }
            sendText(exchange, 404, "404 Not Found: " + pathStr);
            return;
        }

        serveFile(exchange, filePath);
    }

    private void serveFile(HttpExchange exchange, Path file) throws IOException {
        String fileName = file.getFileName().toString().toLowerCase();
        String contentType = "application/octet-stream";
        for (Map.Entry<String, String> entry : MIME_TYPES.entrySet()) {
            if (fileName.endsWith(entry.getKey())) {
                contentType = entry.getValue();
                break;
            }
        }

        long fileLength = Files.size(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        if (fileName.endsWith(".html") || fileName.endsWith(".js") || fileName.endsWith(".css") || fileName.endsWith(".json")) {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
        } else {
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        }

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        exchange.sendResponseHeaders(200, fileLength);

        try (FileInputStream fis = new FileInputStream(file.toFile());
             OutputStream os = exchange.getResponseBody()) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
    }

    private void sendText(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
