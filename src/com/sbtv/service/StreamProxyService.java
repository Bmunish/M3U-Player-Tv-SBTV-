package com.sbtv.service;

import com.sbtv.config.AppConfig;
import com.sun.net.httpserver.HttpExchange;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamProxyService {
    private static final Logger LOGGER = Logger.getLogger(StreamProxyService.class.getName());
    private static final Pattern URI_ATTR_PATTERN = Pattern.compile("URI=\"([^\"]+)\"");

    private final HttpClient httpClient;

    public StreamProxyService() {
        HttpClient client;
        try {
            // Configure trust-all SSL to allow IPTV streams with unverified or self-signed certs
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            SSLParameters sslParams = new SSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("");

            client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .sslParameters(sslParams)
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falling back to default HttpClient SSL", e);
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
        }
        this.httpClient = client;
    }

    public void proxyStream(HttpExchange exchange, String targetUrl) throws IOException {
        if (targetUrl == null || targetUrl.isBlank()) {
            sendError(exchange, 400, "Missing url parameter");
            return;
        }

        boolean headersSent = false;
        try {
            URI targetUri = parseSafeUri(targetUrl.trim());
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");

            HttpResponse<InputStream> response = fetchUpstream(targetUri, AppConfig.USER_AGENT, true, rangeHeader);
            int statusCode = response.statusCode();

            // Retry with VLC user-agent and no referer if blocked (401/403/406)
            if (statusCode == 401 || statusCode == 403 || statusCode == 406) {
                try {
                    response.body().close();
                } catch (Exception ignored) {}
                response = fetchUpstream(targetUri, AppConfig.VLC_USER_AGENT, false, rangeHeader);
                statusCode = response.statusCode();
            }

            if (statusCode >= 400) {
                try {
                    response.body().close();
                } catch (Exception ignored) {}
                sendError(exchange, statusCode, "Upstream stream returned HTTP " + statusCode);
                return;
            }

            // Use the final post-redirect URI so relative M3U8 paths resolve against the actual CDN host/path
            URI effectiveUri = response.uri() != null ? response.uri() : targetUri;
            String effectiveUrlStr = effectiveUri.toString().toLowerCase();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String lowerContentType = contentType.toLowerCase();

            boolean isM3u8 = effectiveUrlStr.contains(".m3u8") ||
                    targetUrl.toLowerCase().contains(".m3u8") ||
                    lowerContentType.contains("mpegurl") ||
                    lowerContentType.contains("application/x-mpegurl") ||
                    lowerContentType.contains("vnd.apple.mpegurl");

            // Add CORS & streaming headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges");

            headersSent = true;
            if (isM3u8) {
                handleM3u8Manifest(exchange, effectiveUri, response.body(), statusCode);
            } else {
                handleBinaryStream(exchange, response, statusCode);
            }

        } catch (Exception e) {
            if (!headersSent) {
                LOGGER.log(Level.WARNING, "Proxy error for URL: " + targetUrl + " -> " + e.getMessage());
                sendError(exchange, 502, "Error proxying stream: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
    }

    private HttpResponse<InputStream> fetchUpstream(URI targetUri, String userAgent, boolean includeReferer, String rangeHeader)
            throws IOException, InterruptedException {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9");

        if (includeReferer && targetUri.getScheme() != null && targetUri.getHost() != null) {
            String originRef = targetUri.getScheme() + "://" + targetUri.getHost() + "/";
            reqBuilder.header("Referer", originRef);
            reqBuilder.header("Origin", targetUri.getScheme() + "://" + targetUri.getHost());
        }

        if (rangeHeader != null && !rangeHeader.isBlank()) {
            reqBuilder.header("Range", rangeHeader);
        }

        return httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private void handleM3u8Manifest(HttpExchange exchange, URI baseUri, InputStream bodyStream, int statusCode) throws IOException {
        String manifestContent;
        try (bodyStream) {
            manifestContent = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        StringBuilder rewritten = new StringBuilder();
        String[] lines = manifestContent.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                rewritten.append("\n");
                continue;
            }

            if (trimmed.startsWith("#")) {
                // Rewrite all URI="..." attributes inside HLS tags (#EXT-X-KEY, #EXT-X-MAP, #EXT-X-MEDIA, etc.)
                if (trimmed.contains("URI=\"")) {
                    Matcher matcher = URI_ATTR_PATTERN.matcher(trimmed);
                    StringBuilder tagSb = new StringBuilder();
                    while (matcher.find()) {
                        String rawUri = matcher.group(1);
                        String resolved = resolveUri(baseUri, rawUri);
                        String proxied = "/api/stream?url=" + URLEncoder.encode(resolved, StandardCharsets.UTF_8);
                        matcher.appendReplacement(tagSb, Matcher.quoteReplacement("URI=\"" + proxied + "\""));
                    }
                    matcher.appendTail(tagSb);
                    line = tagSb.toString();
                }
                rewritten.append(line).append("\n");
            } else {
                // Segment or child playlist URL
                String resolved = resolveUri(baseUri, trimmed);
                String proxied = "/api/stream?url=" + URLEncoder.encode(resolved, StandardCharsets.UTF_8);
                rewritten.append(proxied).append("\n");
            }
        }

        byte[] bytes = rewritten.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
            return;
        }

        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleBinaryStream(HttpExchange exchange, HttpResponse<InputStream> response, int statusCode) throws IOException {
        String contentType = response.headers().firstValue("Content-Type").orElse("video/mp2t");
        exchange.getResponseHeaders().set("Content-Type", contentType);

        response.headers().firstValue("Content-Range").ifPresent(range -> {
            exchange.getResponseHeaders().set("Content-Range", range);
        });
        response.headers().firstValue("Accept-Ranges").ifPresentOrElse(
                accept -> exchange.getResponseHeaders().set("Accept-Ranges", accept),
                () -> exchange.getResponseHeaders().set("Accept-Ranges", "bytes")
        );

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
            try {
                response.body().close();
            } catch (Exception ignored) {}
            return;
        }

        // In com.sun.net.httpserver, >0 sets fixed Content-Length; 0 uses chunked transfer encoding
        exchange.sendResponseHeaders(statusCode, contentLength > 0 ? contentLength : 0);

        try (InputStream in = response.body(); OutputStream out = exchange.getResponseBody()) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException clientDisconnect) {
            // Normal when browser seeks or closes video stream
        }
    }

    private URI parseSafeUri(String rawUrl) {
        try {
            return URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            String sanitized = rawUrl
                    .replace(" ", "%20")
                    .replace("|", "%7C")
                    .replace("[", "%5B")
                    .replace("]", "%5D")
                    .replace("{", "%7B")
                    .replace("}", "%7D");
            return URI.create(sanitized);
        }
    }

    private String resolveUri(URI baseUri, String relativeOrAbsolute) {
        try {
            String cleaned = relativeOrAbsolute.trim();
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                return cleaned;
            }
            if (cleaned.startsWith("//")) {
                String scheme = baseUri.getScheme() != null ? baseUri.getScheme() : "https";
                return scheme + ":" + cleaned;
            }
            URI relUri = parseSafeUri(cleaned);
            return baseUri.resolve(relUri).toString();
        } catch (Exception e) {
            return relativeOrAbsolute;
        }
    }

    private void sendError(HttpExchange exchange, int code, String msg) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(code, -1);
            return;
        }
        String safeMsg = msg != null ? msg.replace("\"", "\\\"") : "Stream error";
        byte[] bytes = ("{\"error\":\"" + safeMsg + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
