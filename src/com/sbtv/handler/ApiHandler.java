package com.sbtv.handler;

import com.sbtv.config.AppConfig;
import com.sbtv.model.*;
import com.sbtv.service.PlaylistService;
import com.sbtv.util.JsonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ApiHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ApiHandler.class.getName());

    private final PlaylistService playlistService;

    public ApiHandler(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            if (path.equals("/api/channels")) {
                handleChannels(exchange);
            } else if (path.equals("/api/movies")) {
                handleMovies(exchange);
            } else if (path.equals("/api/series")) {
                handleSeries(exchange);
            } else if (path.equals("/api/categories")) {
                handleCategories(exchange);
            } else if (path.equals("/api/sources")) {
                handleSources(exchange);
            } else if (path.equals("/api/upload")) {
                handleUpload(exchange);
            } else if (path.equals("/api/reload")) {
                handleReload(exchange);
            } else if (path.equals("/api/featured")) {
                handleFeatured(exchange);
            } else if (path.equals("/api/stats")) {
                handleStats(exchange);
            } else {
                sendJson(exchange, 404, "{\"error\":\"API route not found\"}");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "API handle error for " + path, e);
            sendJson(exchange, 500, "{\"error\":\"" + JsonUtils.escape(e.getMessage()) + "\"}");
        }
    }

    private void handleChannels(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String group = query.get("group");
        String source = query.get("source");
        String search = query.get("search");
        int limit = parseInt(query.get("limit"), 100);
        int offset = parseInt(query.get("offset"), 0);

        List<Channel> channels = playlistService.getMergedPlaylist().getChannels();

        if (group != null && !group.isBlank() && !"All".equalsIgnoreCase(group)) {
            String gLower = group.toLowerCase();
            channels = channels.stream()
                    .filter(c -> c.getGroup() != null &&
                            (c.getGroup().equalsIgnoreCase(group) || c.getGroup().toLowerCase().contains(gLower)))
                    .collect(Collectors.toList());
        }

        if (source != null && !source.isBlank() && !"All".equalsIgnoreCase(source)) {
            channels = channels.stream()
                    .filter(c -> source.equalsIgnoreCase(c.getSourceName()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            channels = channels.stream()
                    .filter(c -> c.getName().toLowerCase().contains(lower) ||
                                (c.getGroup() != null && c.getGroup().toLowerCase().contains(lower)))
                    .collect(Collectors.toList());
        }

        int total = channels.size();
        List<Channel> paged = channels.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        String json = "{\"total\":" + total + ",\"offset\":" + offset + ",\"limit\":" + limit
                + ",\"items\":" + JsonUtils.toJson(paged) + "}";
        sendJson(exchange, 200, json);
    }

    private void handleMovies(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String category = query.get("category");
        String source = query.get("source");
        String search = query.get("search");
        int limit = parseInt(query.get("limit"), 60);
        int offset = parseInt(query.get("offset"), 0);

        List<Movie> movies = playlistService.getMergedPlaylist().getMovies();

        if (category != null && !category.isBlank() && !"All".equalsIgnoreCase(category)) {
            String cLower = category.toLowerCase();
            movies = movies.stream()
                    .filter(m -> m.getCategory() != null &&
                            (m.getCategory().equalsIgnoreCase(category) || m.getCategory().toLowerCase().contains(cLower)))
                    .collect(Collectors.toList());
        }

        if (source != null && !source.isBlank() && !"All".equalsIgnoreCase(source)) {
            movies = movies.stream()
                    .filter(m -> source.equalsIgnoreCase(m.getSourceName()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            movies = movies.stream()
                    .filter(m -> m.getName().toLowerCase().contains(lower) ||
                                (m.getCategory() != null && m.getCategory().toLowerCase().contains(lower)))
                    .collect(Collectors.toList());
        }

        int total = movies.size();
        List<Movie> paged = movies.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        String json = "{\"total\":" + total + ",\"offset\":" + offset + ",\"limit\":" + limit
                + ",\"items\":" + JsonUtils.toJson(paged) + "}";
        sendJson(exchange, 200, json);
    }

    private void handleSeries(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String category = query.get("category");
        String source = query.get("source");
        String search = query.get("search");
        int limit = parseInt(query.get("limit"), 60);
        int offset = parseInt(query.get("offset"), 0);

        List<SeriesGroup> series = playlistService.getMergedPlaylist().getSeries();

        if (category != null && !category.isBlank() && !"All".equalsIgnoreCase(category)) {
            String cLower = category.toLowerCase();
            series = series.stream()
                    .filter(s -> s.getCategory() != null &&
                            (s.getCategory().equalsIgnoreCase(category) || s.getCategory().toLowerCase().contains(cLower)))
                    .collect(Collectors.toList());
        }

        if (source != null && !source.isBlank() && !"All".equalsIgnoreCase(source)) {
            series = series.stream()
                    .filter(s -> source.equalsIgnoreCase(s.getSourceName()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            series = series.stream()
                    .filter(s -> s.getName().toLowerCase().contains(lower) ||
                                (s.getCategory() != null && s.getCategory().toLowerCase().contains(lower)))
                    .collect(Collectors.toList());
        }

        int total = series.size();
        List<SeriesGroup> paged = series.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        String json = "{\"total\":" + total + ",\"offset\":" + offset + ",\"limit\":" + limit
                + ",\"items\":" + JsonUtils.toJson(paged) + "}";
        sendJson(exchange, 200, json);
    }

    private void handleCategories(HttpExchange exchange) throws IOException {
        ParsedPlaylist p = playlistService.getMergedPlaylist();

        Set<String> channelGroups = new TreeSet<>();
        for (Channel c : p.getChannels()) {
            if (c.getGroup() != null && !c.getGroup().isBlank()) channelGroups.add(c.getGroup());
        }

        Set<String> movieCategories = new TreeSet<>();
        for (Movie m : p.getMovies()) {
            if (m.getCategory() != null && !m.getCategory().isBlank()) movieCategories.add(m.getCategory());
        }

        Set<String> seriesCategories = new TreeSet<>();
        for (SeriesGroup s : p.getSeries()) {
            if (s.getCategory() != null && !s.getCategory().isBlank()) seriesCategories.add(s.getCategory());
        }

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"channelGroups\":").append(JsonUtils.toJson(new ArrayList<>(channelGroups))).append(",");
        sb.append("\"movieCategories\":").append(JsonUtils.toJson(new ArrayList<>(movieCategories))).append(",");
        sb.append("\"seriesCategories\":").append(JsonUtils.toJson(new ArrayList<>(seriesCategories)));
        sb.append("}");

        sendJson(exchange, 200, sb.toString());
    }

    private void handleSources(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            List<PlaylistSource> sources = playlistService.getSources();
            sendJson(exchange, 200, JsonUtils.toJson(sources));
        } else if ("POST".equalsIgnoreCase(method)) {
            String body = readRequestBody(exchange);
            Map<String, String> data = JsonUtils.parseSimpleObject(body);
            String name = data.get("name");
            String url = data.get("url");
            String type = data.getOrDefault("type", "url");

            if (name == null || name.isBlank() || url == null || url.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"name and url are required\"}");
                return;
            }

            PlaylistSource added = playlistService.addSource(name, url, type);
            sendJson(exchange, 201, JsonUtils.toJson(added));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String id = query.get("id");
            if (id == null || id.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"id parameter is required\"}");
                return;
            }
            boolean ok = playlistService.removeSource(id);
            sendJson(exchange, ok ? 200 : 404, "{\"success\":" + ok + "}");
        } else if ("PUT".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String id = query.get("id");
            String enabledStr = query.get("enabled");
            if (id == null || enabledStr == null) {
                sendJson(exchange, 400, "{\"error\":\"id and enabled parameters are required\"}");
                return;
            }
            boolean enabled = Boolean.parseBoolean(enabledStr);
            boolean ok = playlistService.toggleSource(id, enabled);
            sendJson(exchange, ok ? 200 : 404, "{\"success\":" + ok + "}");
        } else {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method must be POST\"}");
            return;
        }

        String body = readRequestBody(exchange);
        if (body == null || body.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Empty playlist content\"}");
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.getOrDefault("name", "Uploaded Playlist (" + System.currentTimeMillis() + ")");

        Path playlistFile = AppConfig.getPlaylistsDir().resolve("upload_" + System.currentTimeMillis() + ".m3u");
        Files.writeString(playlistFile, body, StandardCharsets.UTF_8);

        PlaylistSource source = playlistService.addSource(name, playlistFile.toAbsolutePath().toString(), "local");
        sendJson(exchange, 201, "{\"success\":true,\"source\":" + JsonUtils.toJson(source) + "}");
    }

    private void handleReload(HttpExchange exchange) throws IOException {
        new Thread(playlistService::reloadAll).start();
        sendJson(exchange, 200, "{\"message\":\"Reloading all playlists in background\"}");
    }

    private void handleFeatured(HttpExchange exchange) throws IOException {
        ParsedPlaylist p = playlistService.getMergedPlaylist();
        List<Object> featured = new ArrayList<>();

        // Add top movies first
        List<Movie> movies = p.getMovies();
        for (int i = 0; i < Math.min(3, movies.size()); i++) {
            featured.add(movies.get(i));
        }

        // Add top channels
        List<Channel> channels = p.getChannels();
        for (int i = 0; i < Math.min(3, channels.size()); i++) {
            featured.add(channels.get(i));
        }

        // Add top series
        List<SeriesGroup> series = p.getSeries();
        for (int i = 0; i < Math.min(2, series.size()); i++) {
            featured.add(series.get(i));
        }

        sendJson(exchange, 200, JsonUtils.toJson(featured));
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        ParsedPlaylist p = playlistService.getMergedPlaylist();
        String json = "{"
                + "\"channels\":" + p.getChannels().size() + ","
                + "\"movies\":" + p.getMovies().size() + ","
                + "\"series\":" + p.getSeries().size() + ","
                + "\"sources\":" + playlistService.getSources().size()
                + "}";
        sendJson(exchange, 200, json);
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return params;

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = idx + 1 < pair.length() ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                params.put(key, val);
            }
        }
        return params;
    }

    private int parseInt(String s, int defaultValue) {
        if (s == null) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

