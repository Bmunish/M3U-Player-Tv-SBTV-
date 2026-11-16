package com.sbtv.handler;

import com.sbtv.service.StreamProxyService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ProxyHandler implements HttpHandler {
    private final StreamProxyService proxyService;

    public ProxyHandler(StreamProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String rawQuery = exchange.getRequestURI().getRawQuery();
        String targetUrl = null;
        if (rawQuery != null) {
            String[] parts = rawQuery.split("&");
            for (String p : parts) {
                if (p.startsWith("url=")) {
                    targetUrl = URLDecoder.decode(p.substring(4), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        if (targetUrl == null || targetUrl.isBlank()) {
            byte[] err = "{\"error\":\"Missing ?url= parameter\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(400, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
            return;
        }

        proxyService.proxyStream(exchange, targetUrl);
    }
}

