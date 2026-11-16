package com.sbtv.model;

public class Channel {
    private String id;
    private String name;
    private String streamUrl;
    private String logo;
    private String group;
    private String sourceName;

    public Channel() {}

    public Channel(String id, String name, String streamUrl, String logo, String group, String sourceName) {
        this.id = id;
        this.name = name;
        this.streamUrl = streamUrl;
        this.logo = logo;
        this.group = group != null && !group.isBlank() ? group : "General";
        this.sourceName = sourceName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
}

