package com.sbtv.parser;

import com.sbtv.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    private static final Pattern S_X_E_X = Pattern.compile("(.+?)\\s*[-–:]?\\s*[Ss](\\d{1,2})\\s*[Ee](\\d{1,3})");
    private static final Pattern SEASON_EP = Pattern.compile("(.+?)\\s*[-–:]?\\s*Season\\s*(\\d{1,2})\\s*Episode\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EP_ONLY = Pattern.compile("(.+?)\\s*[-–:]?\\s*(?:Episode|Ep|EP)\\.?\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE);

    public ParsedPlaylist parse(String content, String sourceName) {
        if (content == null || content.isBlank()) {
            return new ParsedPlaylist();
        }

        List<Channel> channels = new ArrayList<>();
        List<Movie> movies = new ArrayList<>();
        Map<String, SeriesGroup> seriesGroupMap = new LinkedHashMap<>();

        String[] lines = content.split("\\r?\\n");

        String name = "";
        String logo = null;
        String group = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF")) {
                int commaIdx = line.lastIndexOf(',');
                if (commaIdx != -1 && commaIdx + 1 < line.length()) {
                    name = line.substring(commaIdx + 1).trim();
                }

                logo = extractAttribute(line, "tvg-logo");
                if (logo == null || logo.isBlank()) {
                    logo = extractAttribute(line, "logo");
                }

                group = extractAttribute(line, "group-title");

            } else if (!line.startsWith("#")) {
                String url = line;
                // Skip non-HTML5-playable raw DASH manifests
                if (url.toLowerCase().endsWith(".mpd")) {
                    name = "";
                    logo = null;
                    group = null;
                    continue;
                }

                String rawTitle = !name.isBlank() ? name : extractTitleFromUrl(url);
                String currentTitle = cleanDisplayTitle(rawTitle);
                String rawGroup = (group != null && !group.isBlank()) ? group.trim() : "";

                String itemId = String.valueOf(Math.abs((currentTitle + "|" + url).hashCode()));

                // 1. Check Series FIRST before altering group
                if (isSeries(rawGroup, url, currentTitle)) {
                    SeriesInfo info = parseSeriesInfo(currentTitle);
                    String seriesCat = normalizeSeriesCategory(rawGroup, currentTitle);
                    Series episode = new Series(
                            itemId,
                            currentTitle,
                            url,
                            logo,
                            seriesCat,
                            info.seriesName,
                            info.season,
                            info.episodeNum,
                            sourceName
                    );

                    String groupKey = info.seriesName.toLowerCase();
                    final String sLogo = logo;
                    final String sCat = seriesCat;
                    SeriesGroup sg = seriesGroupMap.computeIfAbsent(groupKey, k -> new SeriesGroup(
                            String.valueOf(Math.abs(groupKey.hashCode())),
                            info.seriesName,
                            sLogo,
                            sCat,
                            sourceName
                    ));
                    sg.addEpisode(episode);

                // 2. Check Movie SECOND before altering group
                } else if (isMovie(rawGroup, url, currentTitle, sourceName)) {
                    String movieCat = normalizeMovieCategory(rawGroup, currentTitle, sourceName);
                    movies.add(new Movie(
                            itemId,
                            currentTitle,
                            url,
                            logo,
                            movieCat,
                            sourceName
                    ));

                    // If it's also a live movie channel from IPTV sources, include in channels too
                    if (url.toLowerCase().contains(".m3u8") && !rawGroup.toLowerCase().contains("vod") &&
                        (sourceName.toLowerCase().contains("indian") || sourceName.toLowerCase().contains("channel"))) {
                        channels.add(new Channel(
                                "ch_" + itemId,
                                currentTitle,
                                url,
                                logo,
                                movieCat,
                                sourceName
                        ));
                    }

                // 3. Otherwise classify as Live TV Channel
                } else {
                    String channelGroup = normalizeChannelGroup(rawGroup, currentTitle, sourceName);
                    channels.add(new Channel(
                            itemId,
                            currentTitle,
                            url,
                            logo,
                            channelGroup,
                            sourceName
                    ));
                }

                name = "";
                logo = null;
                group = null;
            }
        }

        return new ParsedPlaylist(channels, movies, new ArrayList<>(seriesGroupMap.values()));
    }

    private static String cleanDisplayTitle(String title) {
        if (title == null) return "Stream";
        return title
                .replaceAll("(?i)\\s*\\[Geo-blocked\\]", "")
                .replaceAll("(?i)\\s*\\[Not 24/7\\]", "")
                .trim();
    }

    private static String extractAttribute(String line, String attrName) {
        String pattern = attrName + "=\"";
        int start = line.indexOf(pattern);
        if (start != -1) {
            int valStart = start + pattern.length();
            int valEnd = line.indexOf('"', valStart);
            if (valEnd != -1) {
                return line.substring(valStart, valEnd).trim();
            }
        }
        return null;
    }

    private static String extractTitleFromUrl(String url) {
        int slash = url.lastIndexOf('/');
        if (slash != -1 && slash + 1 < url.length()) {
            String end = url.substring(slash + 1);
            int q = end.indexOf('?');
            if (q != -1) end = end.substring(0, q);
            return end;
        }
        return url;
    }

    private static boolean isSeries(String group, String url, String title) {
        String g = group.toLowerCase();
        String u = url.toLowerCase();

        // Explicit episode markers in title
        if (S_X_E_X.matcher(title).find() || SEASON_EP.matcher(title).find() || EP_ONLY.matcher(title).find()) {
            return true;
        }

        // URL path indicates series
        if (u.contains("/series/")) {
            return true;
        }

        // Dedicated series group (avoid semicolon-tagged live TV channels like "Movies;Series" or "Animation;Kids;Series")
        if (!g.contains(";")) {
            return g.contains("series") || g.contains("web series") || g.contains("tv show") || g.contains("episodes");
        }
        return false;
    }

    private static boolean isMovie(String group, String url, String title, String sourceName) {
        String g = group.toLowerCase();
        String u = url.toLowerCase();
        String t = title.toLowerCase();

        if (g.contains("movie") || g.contains("vod") || g.contains("cinema") || g.contains("film") || g.contains("bollywood")) {
            return true;
        }
        if (u.contains("/movie/") || u.matches(".*\\.(mp4|mkv|avi|webm)(\\?.*)?$")) {
            return true;
        }
        if (t.startsWith("[vod]") || t.contains("(movie)") || t.contains("full movie")) {
            return true;
        }
        // Detect Indian Movie channels even when group-title is missing in in.m3u
        if (t.contains("goldmines") || t.contains("b4u movies") || t.contains("sony max") ||
            t.contains("sony wah") || t.contains("zee cinema") || t.contains("zee action") ||
            t.contains("zee bollywood") || t.contains("zee classic") || t.contains("star gold") ||
            t.contains("&pictures") || t.contains("and pictures") || t.contains("colors cineplex") ||
            t.contains("utv movies") || t.contains("manoranjan") || t.contains("maha movie") ||
            t.contains("enterr10 movies") || t.contains("all time movies") || t.contains("gemini movies") ||
            t.contains("surya movies") || t.contains("udaya movies") || t.contains("raj digital") ||
            t.contains("ktv") || t.contains("j movie") || t.contains("movies")) {
            return true;
        }
        return false;
    }

    private static String normalizeSeriesCategory(String group, String title) {
        String g = group.toLowerCase();
        String t = title.toLowerCase();
        if (g.contains("sci-fi") || g.contains("scifi") || t.contains("space") || t.contains("cyber") || t.contains("quantum")) {
            return "Sci-Fi Series";
        }
        if (g.contains("crime") || g.contains("mystery") || g.contains("thriller")) {
            return "Crime & Thriller Series";
        }
        if (g.contains("action") || g.contains("adventure")) {
            return "Action Series";
        }
        if (g.contains("animation") || g.contains("anime") || g.contains("cartoon") || g.contains("kids")) {
            return "Animation & Anime Series";
        }
        if (g.contains("comedy") || g.contains("sitcom")) {
            return "Comedy Series";
        }
        if (g.contains("doc") || g.contains("nature") || g.contains("history")) {
            return "Documentary Series";
        }
        if (g.contains("indian") || g.contains("hindi") || g.contains("desi")) {
            return "Indian Web Series";
        }
        if (g.contains("drama")) {
            return "Drama Series";
        }
        return !group.isBlank() && !group.contains(";") ? group : "Drama Series";
    }

    private static String normalizeMovieCategory(String group, String title, String sourceName) {
        String g = group.toLowerCase();
        String t = title.toLowerCase();
        String s = sourceName != null ? sourceName.toLowerCase() : "";

        if (g.contains("bollywood") || g.contains("indian") || s.contains("indian") ||
            t.contains("goldmines") || t.contains("b4u") || t.contains("zee") || t.contains("sony") ||
            t.contains("bollywood") || t.contains("hindi")) {
            return "Bollywood & Indian Movies";
        }
        if (g.contains("animation") || g.contains("family") || g.contains("kids") || g.contains("cartoon")) {
            return "Animation & Family Movies";
        }
        if (g.contains("sci-fi") || g.contains("scifi") || g.contains("fantasy")) {
            return "Sci-Fi & Fantasy Movies";
        }
        if (g.contains("action") || g.contains("adventure") || t.contains("action")) {
            return "Action & Adventure Movies";
        }
        if (g.contains("comedy") || t.contains("comedy")) {
            return "Comedy Movies";
        }
        if (g.contains("thriller") || g.contains("horror") || g.contains("mystery") || g.contains("crime")) {
            return "Thriller & Horror Movies";
        }
        if (g.contains("doc") || t.contains("documentary")) {
            return "Documentary Films";
        }
        if (g.contains("classic") || t.contains("classic") || t.contains("retro") || t.contains("70s") || t.contains("80s") || t.contains("90s")) {
            return "Classic Cinema";
        }
        if (g.contains("drama") || g.contains("romance")) {
            return "Drama & Romance Movies";
        }
        return "Featured Movies";
    }

    private static String normalizeChannelGroup(String group, String title, String sourceName) {
        String g = group.toLowerCase();
        String t = title.toLowerCase();
        String s = sourceName != null ? sourceName.toLowerCase() : "";

        // Kids & Cartoons
        if (g.contains("kids") || g.contains("cartoon") || g.contains("animation") ||
            t.contains("kids") || t.contains("cartoon") || t.contains("pogo") ||
            t.contains("disney") || t.contains("nickelodeon") || t.contains("nick") ||
            t.contains("hungama") || t.contains("sonic") || t.contains("sony yay") ||
            t.contains("gubbare") || t.contains("chutti") || t.contains("kochu") ||
            t.contains("cbeebies") || t.contains("cbbc") || t.contains("anime")) {
            return "Kids & Cartoons";
        }

        // Sports
        if (g.contains("sport") || s.contains("sport") ||
            t.contains("sport") || t.contains("cricket") || t.contains("football") ||
            t.contains("espn") || t.contains("sony ten") || t.contains("star sports") ||
            t.contains("red bull") || t.contains("golf") || t.contains("racing") ||
            t.contains("wwe") || t.contains("fight") || t.contains("tennis")) {
            return "Sports";
        }

        // News
        if (g.contains("news") || s.contains("news") ||
            t.contains("news") || t.contains("aaj tak") || t.contains("ndtv") ||
            t.contains("abp") || t.contains("republic") || t.contains("india tv") ||
            t.contains("times now") || t.contains("wion") || t.contains("cnbc") ||
            t.contains("bloomberg") || t.contains("bbc") || t.contains("cnn") ||
            t.contains("al jazeera") || t.contains("dw") || t.contains("france 24") ||
            t.contains("samachar") || t.contains("tv9") || t.contains("v6") ||
            t.contains("polimer") || t.contains("thanthi") || t.contains("puthiya") ||
            t.contains("mathrubhumi") || t.contains("manorama news") || t.contains("24 news") ||
            t.contains("sandesh") || t.contains("otv") || t.contains("kanak")) {
            return s.contains("indian") ? "Indian News" : "World News";
        }

        // Music
        if (g.contains("music") || s.contains("music") ||
            t.contains("music") || t.contains("9xm") || t.contains("9x jalwa") ||
            t.contains("9x tashan") || t.contains("9x jhakaas") || t.contains("mastiii") ||
            t.contains("mtv") || t.contains("vh1") || t.contains("zing") ||
            t.contains("zoom") || t.contains("sangeet") || t.contains("b4u music") ||
            t.contains("showbox") || t.contains("pitaara") || t.contains("balle balle") ||
            t.contains("isaiaruvi") || t.contains("melody") || t.contains("hits")) {
            return "Music";
        }

        // Documentary & Science
        if (g.contains("doc") || g.contains("science") || g.contains("education") || s.contains("doc") ||
            t.contains("discovery") || t.contains("animal planet") || t.contains("nat geo") ||
            t.contains("national geographic") || t.contains("history") || t.contains("nasa") ||
            t.contains("epic") || t.contains("travelxp") || t.contains("tlc") || t.contains("earth")) {
            return "Documentary";
        }

        // Devotional / Spiritual
        if (g.contains("relig") || g.contains("devot") ||
            t.contains("aastha") || t.contains("sanskar") || t.contains("satsang") ||
            t.contains("bhajan") || t.contains("bhakti") || t.contains("peace") ||
            t.contains("arihant") || t.contains("paras") || t.contains("sadhna") ||
            t.contains("ishwar") || t.contains("darshan") || t.contains("svbc") ||
            t.contains("angel tv") || t.contains("shalom") || t.contains("goodness") ||
            t.contains("simran") || t.contains("chardikla") || t.contains("qtv")) {
            return "Devotional";
        }

        // Indian Entertainment
        if (s.contains("indian")) {
            return "Indian Entertainment";
        }

        if (g.contains("entertainment") || g.contains("general") || g.contains("comedy") || g.contains("lifestyle")) {
            return "Entertainment";
        }

        if (!group.isBlank() && !group.contains(";")) {
            return group;
        }
        return "Entertainment";
    }

    private static SeriesInfo parseSeriesInfo(String title) {
        Matcher m1 = S_X_E_X.matcher(title);
        if (m1.find()) {
            return new SeriesInfo(
                    cleanSeriesName(m1.group(1)),
                    "S" + padZero(m1.group(2)),
                    "E" + padZero(m1.group(3))
            );
        }

        Matcher m2 = SEASON_EP.matcher(title);
        if (m2.find()) {
            return new SeriesInfo(
                    cleanSeriesName(m2.group(1)),
                    "S" + padZero(m2.group(2)),
                    "E" + padZero(m2.group(3))
            );
        }

        Matcher m4 = EP_ONLY.matcher(title);
        if (m4.find()) {
            return new SeriesInfo(
                    cleanSeriesName(m4.group(1)),
                    "S01",
                    "E" + padZero(m4.group(2))
            );
        }

        return new SeriesInfo(cleanSeriesName(title), "S01", "E01");
    }

    private static String cleanSeriesName(String raw) {
        if (raw == null) return "Series";
        return raw.replaceAll("[-–:|]+$", "").trim();
    }

    private static String padZero(String num) {
        return num.length() == 1 ? "0" + num : num;
    }

    private static class SeriesInfo {
        final String seriesName;
        final String season;
        final String episodeNum;

        SeriesInfo(String seriesName, String season, String episodeNum) {
            this.seriesName = seriesName;
            this.season = season;
            this.episodeNum = episodeNum;
        }
    }
}
