package com.sbtv.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConfig {
    private static int port = 8080;
    private static Path baseDir = Paths.get(".").toAbsolutePath().normalize();
    private static Path dataDir = baseDir.resolve("data");
    private static Path webDir = java.nio.file.Files.exists(baseDir.resolve("public")) ? baseDir.resolve("public") : baseDir.resolve("web");
    private static Path playlistsDir = dataDir.resolve("playlists");

    public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String VLC_USER_AGENT = "VLC/3.0.18 LibVLC/3.0.18";

    public static int getPort() { return port; }
    public static void setPort(int p) { port = p; }

    public static Path getBaseDir() { return baseDir; }
    public static Path getDataDir() { return dataDir; }
    public static Path getWebDir() { return webDir; }
    public static Path getPlaylistsDir() { return playlistsDir; }
}

