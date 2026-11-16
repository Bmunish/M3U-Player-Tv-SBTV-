package com.sbtv;

import com.sbtv.config.AppConfig;
import com.sbtv.handler.ApiHandler;
import com.sbtv.handler.ProxyHandler;
import com.sbtv.handler.StaticFileHandler;
import com.sbtv.service.PlaylistService;
import com.sbtv.service.StreamProxyService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        int port = 8080;

        // Check command line arguments
        boolean verifyOnly = false;
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if ("--verify".equals(args[i])) {
                    verifyOnly = true;
                } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                    try { port = Integer.parseInt(args[++i]); } catch (NumberFormatException ignored) {}
                } else {
                    try { port = Integer.parseInt(args[i]); } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Check system property or env variable
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }

        String propPort = System.getProperty("port");
        if (propPort != null && !propPort.isBlank()) {
            try {
                port = Integer.parseInt(propPort);
            } catch (NumberFormatException ignored) {}
        }

        AppConfig.setPort(port);

        try {
            // Initialize services
            PlaylistService playlistService = new PlaylistService();
            if (verifyOnly) {
                playlistService.reloadAll();
                var p = playlistService.getMergedPlaylist();
                System.out.println("VERIFY_OK: channels=" + p.getChannels().size()
                        + ", movies=" + p.getMovies().size()
                        + ", series=" + p.getSeries().size());
                return;
            }
            StreamProxyService streamProxyService = new StreamProxyService();

            // Create HTTP Server
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Register handlers
            server.createContext("/api/stream", new ProxyHandler(streamProxyService));
            server.createContext("/api/", new ApiHandler(playlistService));
            server.createContext("/", new StaticFileHandler());

            // Multi-threaded executor
            server.setExecutor(Executors.newFixedThreadPool(32));

            // Load playlists immediately so first request has full data
            playlistService.reloadAll();

            // Start server
            server.start();

            String hostAddress = "localhost";
            try {
                hostAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ignored) {}

            System.out.println("==================================================================");
            System.out.println("     🎬 SBTV / MovieFlix M3U Web Streaming Player (Java)");
            System.out.println("==================================================================");
            System.out.println("  ✓ Local URL:      http://localhost:" + port);
            System.out.println("  ✓ Network URL:    http://" + hostAddress + ":" + port);
            System.out.println("  ✓ Authentication: NO LOGIN REQUIRED (Direct Streaming)");
            System.out.println("  ✓ Content:        Live TV Channels, Movies (VOD), Series");
            System.out.println("  ✓ Status:         Ready and serving requests!");
            System.out.println("==================================================================");

            // Graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nStopping SBTV streaming server...");
                server.stop(1);
                System.out.println("Server stopped.");
            }));

        } catch (IOException e) {
            System.err.println("Fatal error starting server on port " + port + ": " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

