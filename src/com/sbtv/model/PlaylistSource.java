package com.sbtv.model;

public class PlaylistSource {
    private String id;
    private String name;
    private String url;
    private boolean enabled;
    private String type; // "url" or "local"

    public PlaylistSource() {}

    public PlaylistSource(String id, String name, String url, boolean enabled, String type) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.enabled = enabled;
        this.type = type != null ? type : "url";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

