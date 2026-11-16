package com.sbtv.util;

import com.sbtv.model.*;

import java.util.*;

public class JsonUtils {

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s) return "\"" + escape(s) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();

        if (obj instanceof Channel c) {
            return "{\"id\":\"" + escape(c.getId()) + "\","
                    + "\"name\":\"" + escape(c.getName()) + "\","
                    + "\"streamUrl\":\"" + escape(c.getStreamUrl()) + "\","
                    + "\"logo\":" + (c.getLogo() != null ? "\"" + escape(c.getLogo()) + "\"" : "null") + ","
                    + "\"group\":\"" + escape(c.getGroup()) + "\","
                    + "\"sourceName\":\"" + escape(c.getSourceName()) + "\"}";
        }

        if (obj instanceof Movie m) {
            return "{\"id\":\"" + escape(m.getId()) + "\","
                    + "\"name\":\"" + escape(m.getName()) + "\","
                    + "\"streamUrl\":\"" + escape(m.getStreamUrl()) + "\","
                    + "\"poster\":" + (m.getPoster() != null ? "\"" + escape(m.getPoster()) + "\"" : "null") + ","
                    + "\"category\":\"" + escape(m.getCategory()) + "\","
                    + "\"sourceName\":\"" + escape(m.getSourceName()) + "\"}";
        }

        if (obj instanceof Series s) {
            return "{\"id\":\"" + escape(s.getId()) + "\","
                    + "\"name\":\"" + escape(s.getName()) + "\","
                    + "\"streamUrl\":\"" + escape(s.getStreamUrl()) + "\","
                    + "\"poster\":" + (s.getPoster() != null ? "\"" + escape(s.getPoster()) + "\"" : "null") + ","
                    + "\"category\":\"" + escape(s.getCategory()) + "\","
                    + "\"seriesName\":\"" + escape(s.getSeriesName()) + "\","
                    + "\"season\":" + (s.getSeason() != null ? "\"" + escape(s.getSeason()) + "\"" : "null") + ","
                    + "\"episodeNum\":" + (s.getEpisodeNum() != null ? "\"" + escape(s.getEpisodeNum()) + "\"" : "null") + ","
                    + "\"sourceName\":\"" + escape(s.getSourceName()) + "\"}";
        }

        if (obj instanceof SeriesGroup g) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"id\":\"").append(escape(g.getId())).append("\",")
              .append("\"name\":\"").append(escape(g.getName())).append("\",")
              .append("\"poster\":").append(g.getPoster() != null ? "\"" + escape(g.getPoster()) + "\"" : "null").append(",")
              .append("\"category\":\"").append(escape(g.getCategory())).append("\",")
              .append("\"sourceName\":\"").append(escape(g.getSourceName())).append("\",")
              .append("\"episodes\":[");
            List<Series> eps = g.getEpisodes();
            for (int i = 0; i < eps.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(eps.get(i)));
            }
            sb.append("]}");
            return sb.toString();
        }

        if (obj instanceof PlaylistSource ps) {
            return "{\"id\":\"" + escape(ps.getId()) + "\","
                    + "\"name\":\"" + escape(ps.getName()) + "\","
                    + "\"url\":\"" + escape(ps.getUrl()) + "\","
                    + "\"enabled\":" + ps.isEnabled() + ","
                    + "\"type\":\"" + escape(ps.getType()) + "\"}";
        }

        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }

        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\":")
                  .append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }

        return "\"" + escape(obj.toString()) + "\"";
    }

    /**
     * Simple JSON map parser for string-value and boolean-value JSON objects
     * e.g. {"name": "My Channel", "url": "https://...", "enabled": true}
     */
    public static Map<String, String> parseSimpleObject(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;

        String trimmed = json.trim();
        if (trimmed.startsWith("{")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("}")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        // Parse key-value pairs
        int pos = 0;
        int len = trimmed.length();
        while (pos < len) {
            // Find key start
            int keyStart = trimmed.indexOf('"', pos);
            if (keyStart == -1) break;
            int keyEnd = trimmed.indexOf('"', keyStart + 1);
            if (keyEnd == -1) break;
            String key = trimmed.substring(keyStart + 1, keyEnd);

            // Find colon
            int colon = trimmed.indexOf(':', keyEnd);
            if (colon == -1) break;

            // Find value
            pos = colon + 1;
            while (pos < len && Character.isWhitespace(trimmed.charAt(pos))) {
                pos++;
            }
            if (pos >= len) break;

            String val = "";
            if (trimmed.charAt(pos) == '"') {
                int valStart = pos + 1;
                StringBuilder sb = new StringBuilder();
                boolean escaped = false;
                pos = valStart;
                while (pos < len) {
                    char c = trimmed.charAt(pos);
                    if (escaped) {
                        sb.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break;
                    } else {
                        sb.append(c);
                    }
                    pos++;
                }
                val = sb.toString();
                pos++; // skip closing quote
            } else {
                // Number or boolean
                int valStart = pos;
                while (pos < len && trimmed.charAt(pos) != ',' && trimmed.charAt(pos) != '}') {
                    pos++;
                }
                val = trimmed.substring(valStart, pos).trim();
            }

            map.put(key, val);

            // Skip comma
            while (pos < len && (Character.isWhitespace(trimmed.charAt(pos)) || trimmed.charAt(pos) == ',')) {
                pos++;
            }
        }

        return map;
    }
}

