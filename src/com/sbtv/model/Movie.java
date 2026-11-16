package com.sbtv.model;

public class Movie {
    private String id;
    private String name;
    private String streamUrl;
    private String poster;
    private String category;
    private String sourceName;

    public Movie() {}

    public Movie(String id, String name, String streamUrl, String poster, String category, String sourceName) {
        this.id = id;
        this.name = name;
        this.streamUrl = streamUrl;
        this.poster = poster;
        this.category = category != null && !category.isBlank() ? category : "Movies";
        this.sourceName = sourceName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public String getPoster() { return poster; }
    public void setPoster(String poster) { this.poster = poster; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
}

