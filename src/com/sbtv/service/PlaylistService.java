package com.sbtv.service;

import com.sbtv.config.AppConfig;
import com.sbtv.model.*;
import com.sbtv.parser.M3UParser;
import com.sbtv.util.JsonUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistService {
    private static final Logger LOGGER = Logger.getLogger(PlaylistService.class.getName());

    private final M3UParser parser = new M3UParser();
    private final HttpClient httpClient;
    private final List<PlaylistSource> sources = new CopyOnWriteArrayList<>();
    private final Map<String, ParsedPlaylist> cache = new ConcurrentHashMap<>();

    private volatile ParsedPlaylist mergedPlaylist = new ParsedPlaylist();
    private final Path sourcesConfigFile;

    public PlaylistService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        this.sourcesConfigFile = AppConfig.getDataDir().resolve("sources.json");
        initDefaultSources();
    }

    private void initDefaultSources() {
        try {
            Files.createDirectories(AppConfig.getDataDir());
            Files.createDirectories(AppConfig.getPlaylistsDir());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create data directories", e);
        }

        if (Files.exists(sourcesConfigFile)) {
            loadSourcesFromFile();
        }

        Path sampleVodFile = AppConfig.getPlaylistsDir().resolve("sample_movies.m3u");

        if (sources.isEmpty()) {
            sources.add(new PlaylistSource("src_sample_vod", "MovieFlix On-Demand Movies & Series",
                    sampleVodFile.toString(), true, "local"));
            sources.add(new PlaylistSource("src_in", "Indian TV",
                    "https://raw.githubusercontent.com/iptv-org/iptv/refs/heads/master/streams/in.m3u", true, "url"));
            sources.add(new PlaylistSource("src_movies", "Movies Channel",
                    "https://iptv-org.github.io/iptv/categories/movies.m3u", true, "url"));
            sources.add(new PlaylistSource("src_sports", "Live Sports",
                    "https://iptv-org.github.io/iptv/categories/sports.m3u", true, "url"));
            sources.add(new PlaylistSource("src_kids", "Kids & Animation",
                    "https://iptv-org.github.io/iptv/categories/animation.m3u", true, "url"));
            sources.add(new PlaylistSource("src_news", "World News",
                    "https://iptv-org.github.io/iptv/categories/news.m3u", true, "url"));
            sources.add(new PlaylistSource("src_music", "Music Live",
                    "https://iptv-org.github.io/iptv/categories/music.m3u", true, "url"));
            sources.add(new PlaylistSource("src_doc", "Documentary",
                    "https://iptv-org.github.io/iptv/categories/documentary.m3u", true, "url"));

            saveSourcesToFile();
        } else {
            // Ensure src_sample_vod is present and prioritized first for instant VOD Movies & Series
            PlaylistSource vodSource = null;
            for (PlaylistSource s : sources) {
                if ("src_sample_vod".equals(s.getId())) {
                    vodSource = s;
                    break;
                }
            }
            if (vodSource == null) {
                vodSource = new PlaylistSource("src_sample_vod", "MovieFlix On-Demand Movies & Series",
                        sampleVodFile.toString(), true, "local");
                sources.add(0, vodSource);
                saveSourcesToFile();
            } else if (sources.indexOf(vodSource) > 0) {
                sources.remove(vodSource);
                sources.add(0, vodSource);
                saveSourcesToFile();
            }
        }
    }

    public synchronized void reloadAll() {
        LOGGER.info("Loading all playlist sources...");
        cache.clear();

        // Phase 1: Load local files & cached playlists immediately so UI is populated in <100ms
        for (PlaylistSource source : sources) {
            if (!source.isEnabled()) continue;
            try {
                Path localFile = resolveLocalCacheFile(source);
                if (localFile != null && Files.exists(localFile)) {
                    String content = Files.readString(localFile, StandardCharsets.UTF_8);
                    if (content != null && !content.isBlank()) {
                        ParsedPlaylist parsed = parser.parse(content, source.getName());
                        cache.put(source.getId(), parsed);
                        LOGGER.info("Loaded " + source.getName() + " from local storage: "
                                + parsed.getChannels().size() + " channels, "
                                + parsed.getMovies().size() + " movies, "
                                + parsed.getSeries().size() + " series");
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to read local cache for " + source.getName(), e);
            }
        }

        rebuildMergedPlaylist();

        // Phase 2: Refresh remote URL playlists asynchronously in background if network is available
        CompletableFuture.runAsync(() -> {
            boolean updated = false;
            for (PlaylistSource source : sources) {
                if (!source.isEnabled()) continue;
                if ("local".equalsIgnoreCase(source.getType()) || !source.getUrl().startsWith("http")) {
                    continue;
                }
                Path localBackup = resolveLocalCacheFile(source);
                // Only fetch over network if local backup doesn't exist yet
                if (localBackup != null && Files.exists(localBackup)) {
                    continue;
                }
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(source.getUrl()))
                            .header("User-Agent", AppConfig.USER_AGENT)
                            .timeout(Duration.ofSeconds(8))
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                        String content = response.body();
                        if (localBackup != null) {
                            Files.writeString(localBackup, content, StandardCharsets.UTF_8);
                        }
                        ParsedPlaylist parsed = parser.parse(content, source.getName());
                        cache.put(source.getId(), parsed);
                        updated = true;
                    }
                } catch (Exception ignored) {
                }
            }
            if (updated) {
                rebuildMergedPlaylist();
            }
        });
    }

    private Path resolveLocalCacheFile(PlaylistSource source) {
        if ("local".equalsIgnoreCase(source.getType()) || !source.getUrl().startsWith("http")) {
            Path p = Path.of(source.getUrl());
            if (Files.exists(p)) return p;
            Path resolved = AppConfig.getBaseDir().resolve(source.getUrl());
            if (Files.exists(resolved)) return resolved;
            return AppConfig.getPlaylistsDir().resolve("sample_movies.m3u");
        }
        String cacheName = source.getId().replace("src_", "") + ".m3u";
        return AppConfig.getPlaylistsDir().resolve(cacheName);
    }

    private synchronized void rebuildMergedPlaylist() {
        ParsedPlaylist merged = new ParsedPlaylist();
        for (PlaylistSource source : sources) {
            if (source.isEnabled()) {
                ParsedPlaylist p = cache.get(source.getId());
                if (p != null) {
                    merged.merge(p);
                }
            }
        }
        this.mergedPlaylist = merged;
        LOGGER.info("Merged playlist ready: " + merged.getChannels().size() + " channels, "
                + merged.getMovies().size() + " movies, " + merged.getSeries().size() + " series");
    }

    public ParsedPlaylist getMergedPlaylist() {
        return mergedPlaylist;
    }

    public List<PlaylistSource> getSources() {
        return new ArrayList<>(sources);
    }

    public synchronized PlaylistSource addSource(String name, String urlOrPath, String type) {
        String id = "src_" + System.currentTimeMillis();
        PlaylistSource source = new PlaylistSource(id, name, urlOrPath, true, type);
        sources.add(source);
        saveSourcesToFile();

        CompletableFuture.runAsync(() -> {
            try {
                String content;
                if ("local".equalsIgnoreCase(type) || !urlOrPath.startsWith("http")) {
                    content = Files.readString(Path.of(urlOrPath), StandardCharsets.UTF_8);
                } else {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(urlOrPath))
                            .header("User-Agent", AppConfig.USER_AGENT)
                            .timeout(Duration.ofSeconds(20))
                            .GET().build();
                    content = httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body();
                }
                ParsedPlaylist parsed = parser.parse(content, name);
                cache.put(id, parsed);
                rebuildMergedPlaylist();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load new source " + name, e);
            }
        });

        return source;
    }

    public synchronized boolean removeSource(String id) {
        boolean removed = sources.removeIf(s -> s.getId().equals(id));
        if (removed) {
            cache.remove(id);
            saveSourcesToFile();
            rebuildMergedPlaylist();
        }
        return removed;
    }

    public synchronized boolean toggleSource(String id, boolean enabled) {
        for (PlaylistSource s : sources) {
            if (s.getId().equals(id)) {
                s.setEnabled(enabled);
                saveSourcesToFile();
                rebuildMergedPlaylist();
                return true;
            }
        }
        return false;
    }

    private void saveSourcesToFile() {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < sources.size(); i++) {
                if (i > 0) sb.append(",\n");
                sb.append("  ").append(JsonUtils.toJson(sources.get(i)));
            }
            sb.append("\n]");
            Files.writeString(sourcesConfigFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save sources to " + sourcesConfigFile, e);
        }
    }

    private void loadSourcesFromFile() {
        try {
            String json = Files.readString(sourcesConfigFile, StandardCharsets.UTF_8).trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1).trim();
                int braceDepth = 0;
                int start = -1;
                for (int i = 0; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '{') {
                        if (braceDepth == 0) start = i;
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0 && start != -1) {
                            String itemJson = json.substring(start, i + 1);
                            Map<String, String> map = JsonUtils.parseSimpleObject(itemJson);
                            String id = map.getOrDefault("id", "src_" + System.currentTimeMillis());
                            String name = map.getOrDefault("name", "Playlist");
                            String url = map.getOrDefault("url", "");
                            boolean enabled = Boolean.parseBoolean(map.getOrDefault("enabled", "true"));
                            String type = map.getOrDefault("type", "url");
                            if (!url.isBlank()) {
                                sources.add(new PlaylistSource(id, name, url, enabled, type));
                            }
                            start = -1;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load sources from " + sourcesConfigFile, e);
        }
    }
}
