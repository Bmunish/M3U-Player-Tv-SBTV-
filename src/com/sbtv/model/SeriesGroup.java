package com.sbtv.model;

import java.util.ArrayList;
import java.util.List;

public class SeriesGroup {
    private String id;
    private String name;
    private String poster;
    private String category;
    private String sourceName;
    private List<Series> episodes = new ArrayList<>();

    public SeriesGroup() {}

    public SeriesGroup(String id, String name, String poster, String category, String sourceName) {
        this.id = id;
        this.name = name;
        this.poster = poster;
        this.category = category != null && !category.isBlank() ? category : "Series";
        this.sourceName = sourceName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPoster() { return poster; }
    public void setPoster(String poster) { this.poster = poster; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public List<Series> getEpisodes() { return episodes; }
    public void setEpisodes(List<Series> episodes) { this.episodes = episodes; }

    public void addEpisode(Series episode) {
        if (this.poster == null || this.poster.isBlank()) {
            this.poster = episode.getPoster();
        }
        this.episodes.add(episode);
    }
}

