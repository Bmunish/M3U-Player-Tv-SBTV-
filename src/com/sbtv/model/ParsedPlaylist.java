package com.sbtv.model;

import java.util.*;

public class ParsedPlaylist {
    private List<Channel> channels = new ArrayList<>();
    private List<Movie> movies = new ArrayList<>();
    private List<SeriesGroup> series = new ArrayList<>();

    public ParsedPlaylist() {}

    public ParsedPlaylist(List<Channel> channels, List<Movie> movies, List<SeriesGroup> series) {
        this.channels = channels != null ? channels : new ArrayList<>();
        this.movies = movies != null ? movies : new ArrayList<>();
        this.series = series != null ? series : new ArrayList<>();
    }

    public List<Channel> getChannels() { return channels; }
    public void setChannels(List<Channel> channels) { this.channels = channels; }

    public List<Movie> getMovies() { return movies; }
    public void setMovies(List<Movie> movies) { this.movies = movies; }

    public List<SeriesGroup> getSeries() { return series; }
    public void setSeries(List<SeriesGroup> series) { this.series = series; }

    public void merge(ParsedPlaylist other) {
        if (other == null) return;

        Set<String> existingChannels = new HashSet<>();
        for (Channel c : this.channels) {
            existingChannels.add(c.getId());
        }
        for (Channel c : other.getChannels()) {
            if (existingChannels.add(c.getId())) {
                this.channels.add(c);
            }
        }

        Set<String> existingMovies = new HashSet<>();
        for (Movie m : this.movies) {
            existingMovies.add(m.getId());
        }
        for (Movie m : other.getMovies()) {
            if (existingMovies.add(m.getId())) {
                this.movies.add(m);
            }
        }

        Map<String, SeriesGroup> seriesMap = new LinkedHashMap<>();
        for (SeriesGroup sg : this.series) {
            seriesMap.put(sg.getName().toLowerCase(), sg);
        }
        for (SeriesGroup sg : other.getSeries()) {
            String key = sg.getName().toLowerCase();
            SeriesGroup existing = seriesMap.get(key);
            if (existing != null) {
                Set<String> epIds = new HashSet<>();
                for (Series ep : existing.getEpisodes()) {
                    epIds.add(ep.getId());
                }
                for (Series ep : sg.getEpisodes()) {
                    if (epIds.add(ep.getId())) {
                        existing.addEpisode(ep);
                    }
                }
            } else {
                seriesMap.put(key, sg);
                this.series.add(sg);
            }
        }
    }
}
