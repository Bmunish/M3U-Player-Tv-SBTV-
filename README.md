# 🎬 SBTV / MovieFlix M3U Web Streaming Player (Java Edition)

> **Pure Java Web Application for Live TV, Movies & Series Streaming**  
> Direct browser streaming on `localhost` or web server — **Zero Login Required**!

---

## 🌟 Key Features

- **No Login / Authentication Required**:
  - Open `http://localhost:8080` and start watching immediately, just like MovieFlix or free streaming platforms.
  - Favorites, watchlists, and settings are saved automatically in your browser's `localStorage`.

- **Pure Java Backend**:
  - Built with standard OpenJDK (`com.sun.net.httpserver` + `java.net.http`).
  - Zero heavy external dependencies required; compiles with `javac` and runs as a standalone executable JAR (`sbtv-player.jar`).

- **Cinematic MovieFlix / Netflix Style UI**:
  - Deep dark cinema theme (`#0B0F1A`) with glowing gold accents (`#C6A75E`).
  - Dynamic Hero Banner with featured movies, live stream badges, and instant playback.
  - Horizontal carousel rails for **Live TV**, **Trending Movies**, **Popular Series**, and **Sports**.
  - High-density responsive grid views with category pill filters.

- **Integrated Stream Proxy (Bypasses CORS & Header Blocking)**:
  - Many IPTV streams fail in web browsers due to CORS restrictions, referer checks, or Apple HLS relative segment paths.
  - The built-in Java Stream Proxy (`/api/stream?url=...`) automatically fetches stream chunks with proper `Access-Control-Allow-Origin: *`, spoofed VLC User-Agent, and rewrites M3U8 child segments.

- **Offline & Curated Sources**:
  - Includes **500+ Indian Live TV channels** and **Sports streams** pre-cached locally (from `iptv1` & IPTV-org).
  - Bundled with sample On-Demand Movies and Series (Big Buck Bunny, Tears of Steel, Sintel, etc.).
  - Bundled local `hls.min.js` so player works even without external CDN access.

- **Playlist Manager**:
  - Add any custom M3U / M3U8 URL from the UI.
  - Upload local `.m3u` / `.m3u8` files via drag-and-drop.
  - Enable/disable or delete playlists on the fly.

- **Series with Season & Episode Support**:
  - Automatically parses titles (e.g., `S01E02`, `Season 1 Episode 3`) and groups episodes into clean season tabs with modal player.

- **Channel Quick-Switcher (Zapper)**:
  - In-player sidebar drawer to switch channels without leaving the video player.
  - Keyboard shortcuts: `Space` (Play/Pause), `F` (Fullscreen), `M` (Mute), `Esc` (Exit).

---

## 🚀 Quick Start

### 1. Run with One Command
```bash
./run.sh
```
*The script will automatically detect your Java installation, compile the project if needed, start the web server on port 8080, and open `http://localhost:8080` in your browser.*

### 2. Custom Port
```bash
./run.sh 9090
```

### 3. Build Executable JAR Manually
```bash
./build.sh
java -jar sbtv-player.jar --port 8080
```

---

## 📁 Project Structure

```
project in jhava/
├── src/
│   └── com/
│       └── sbtv/
│           ├── Main.java                        # Server bootstrap & lifecycle
│           ├── config/
│           │   └── AppConfig.java               # Port, directories, user-agents
│           ├── model/
│           │   ├── Channel.java                 # Live TV channel model
│           │   ├── Movie.java                   # VOD Movie model
│           │   ├── Series.java                  # TV Episode model
│           │   ├── SeriesGroup.java             # Series container with seasons & episodes
│           │   ├── PlaylistSource.java          # M3U source configuration
│           │   └── ParsedPlaylist.java          # Merged content container
│           ├── parser/
│           │   └── M3UParser.java               # M3U/M3U8 parser with series & movie detection
│           ├── service/
│           │   ├── PlaylistService.java         # Source manager, fetcher, local caching
│           │   └── StreamProxyService.java      # HLS CORS stream proxy & M3U8 rewriter
│           ├── handler/
│           │   ├── ApiHandler.java              # REST endpoints (/api/channels, /api/movies, etc.)
│           │   ├── ProxyHandler.java            # Stream proxy router (/api/stream)
│           │   └── StaticFileHandler.java       # High-speed static web asset server
│           └── util/
│               └── JsonUtils.java               # Zero-dependency fast JSON serializer/parser
├── web/
│   ├── index.html                               # MovieFlix single-page streaming web app
│   ├── css/
│   │   └── style.css                            # Cinema dark & gold theme styling
│   ├── js/
│   │   ├── app.js                               # SPA controller, filters, playlists & favorites
│   │   ├── player.js                            # Hls.js player, proxy fallback & channel zapper
│   │   └── hls.min.js                           # Self-contained HLS streaming engine (offline ready)
│   └── assets/
│       ├── logo.svg                             # Glowing SBTV brand logo
│       ├── tv-icon.png                          # Channel icon fallback
│       └── no-image.png                         # Poster fallback
├── data/
│   ├── sources.json                             # Configured M3U sources
│   └── playlists/
│       ├── in.m3u                               # Cached Indian TV channels
│       ├── sports.m3u                           # Cached Sports streams
│       └── sample_movies.m3u                    # Sample VOD movies & series
├── build.sh                                     # Standalone build & JAR packaging script
├── run.sh                                       # One-click launch script
└── sbtv-player.jar                              # Compiled executable JAR
```

---

## 🌐 REST API Endpoints

| Endpoint | Method | Description | Parameters |
|---|---|---|---|
| `/` | `GET` | Serves the web streaming app | None |
| `/api/stats` | `GET` | Count of channels, movies, series, sources | None |
| `/api/featured` | `GET` | Top featured titles for hero banner | None |
| `/api/channels` | `GET` | Filtered list of Live TV channels | `?group=&source=&search=&limit=&offset=` |
| `/api/movies` | `GET` | Filtered list of VOD movies | `?category=&source=&search=&limit=&offset=` |
| `/api/series` | `GET` | Filtered list of TV series | `?category=&source=&search=&limit=&offset=` |
| `/api/categories` | `GET` | All available genres and categories | None |
| `/api/sources` | `GET` / `POST` / `DELETE` / `PUT` | Manage M3U playlist sources | JSON body or `?id=` query |
| `/api/upload` | `POST` | Upload a raw `.m3u` file | Request body = file content |
| `/api/reload` | `POST` | Refresh and re-fetch all active playlists | None |
| `/api/stream` | `GET` | Reverse proxy for HLS / MP4 streams | `?url=<target_stream_url>` |

---

## 📺 How to Add Your Own Playlists

1. Open the web app at `http://localhost:8080`.
2. Click **Playlists** (`+`) in the top navigation bar.
3. Choose either:
   - **Add M3U URL**: Enter a title and any public or private IPTV `.m3u` / `.m3u8` link.
   - **Upload M3U File**: Drag and drop your `.m3u` file directly from your computer.
4. The channels, movies, and series will be parsed instantly and added to your catalog!

