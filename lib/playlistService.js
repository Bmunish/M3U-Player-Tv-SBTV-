/**
 * SBTV Playlist Service for Node.js / Vercel Serverless
 */

const fs = require('fs');
const path = require('path');
const { parseM3U } = require('./m3uParser');

const BASE_DIR = process.cwd();
const DATA_DIR = path.join(BASE_DIR, 'data');
const PLAYLISTS_DIR = path.join(DATA_DIR, 'playlists');
const SOURCES_FILE = path.join(DATA_DIR, 'sources.json');
const PREPARSED_FILE = path.join(DATA_DIR, 'preparsed.json');

const USER_AGENT = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

class PlaylistService {
  constructor() {
    this.sources = [];
    this.merged = { channels: [], movies: [], series: [] };
    this.cache = new Map();
    this.initialized = false;
  }

  ensureInitialized() {
    if (this.initialized) return;
    this.loadSources();
    this.loadData();
    this.initialized = true;
  }

  loadSources() {
    try {
      if (fs.existsSync(SOURCES_FILE)) {
        this.sources = JSON.parse(fs.readFileSync(SOURCES_FILE, 'utf8'));
      }
    } catch (e) {
      console.warn('Error reading sources.json:', e.message);
    }

    if (!this.sources || this.sources.length === 0) {
      this.sources = [
        { id: 'src_sample_vod', name: 'MovieFlix On-Demand Movies & Series', url: 'data/playlists/sample_movies.m3u', enabled: true, type: 'local' },
        { id: 'src_in', name: 'Indian TV', url: 'https://raw.githubusercontent.com/iptv-org/iptv/refs/heads/master/streams/in.m3u', enabled: true, type: 'url' },
        { id: 'src_movies', name: 'Movies Channel', url: 'https://iptv-org.github.io/iptv/categories/movies.m3u', enabled: true, type: 'url' },
        { id: 'src_sports', name: 'Live Sports', url: 'https://iptv-org.github.io/iptv/categories/sports.m3u', enabled: true, type: 'url' },
        { id: 'src_kids', name: 'Kids & Animation', url: 'https://iptv-org.github.io/iptv/categories/animation.m3u', enabled: true, type: 'url' },
        { id: 'src_news', name: 'World News', url: 'https://iptv-org.github.io/iptv/categories/news.m3u', enabled: true, type: 'url' },
        { id: 'src_music', name: 'Music Live', url: 'https://iptv-org.github.io/iptv/categories/music.m3u', enabled: true, type: 'url' },
        { id: 'src_doc', name: 'Documentary', url: 'https://iptv-org.github.io/iptv/categories/documentary.m3u', enabled: true, type: 'url' }
      ];
    }
  }

  saveSources() {
    try {
      if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
      fs.writeFileSync(SOURCES_FILE, JSON.stringify(this.sources, null, 2), 'utf8');
    } catch (e) {
      // In serverless read-only filesystem, saving to disk may fail; memory keeps it
      console.warn('Could not persist sources.json to disk (read-only environment):', e.message);
    }
  }

  loadData() {
    // 1. Fast path: If preparsed.json exists, load it directly
    if (fs.existsSync(PREPARSED_FILE)) {
      try {
        const raw = fs.readFileSync(PREPARSED_FILE, 'utf8');
        this.merged = JSON.parse(raw);
        return;
      } catch (e) {
        console.warn('Error reading preparsed.json, falling back to m3u parsing:', e.message);
      }
    }

    // 2. Parse from local playlists
    this.rebuildFromFiles();
  }

  rebuildFromFiles() {
    const channels = [];
    const movies = [];
    const seriesMap = new Map();
    const channelIds = new Set();
    const movieIds = new Set();

    for (const s of this.sources) {
      if (!s.enabled) continue;
      let filePath = null;

      if (s.type === 'local' || !s.url.startsWith('http')) {
        filePath = path.isAbsolute(s.url) ? s.url : path.join(BASE_DIR, s.url);
      } else {
        const cacheName = s.id.replace('src_', '') + '.m3u';
        filePath = path.join(PLAYLISTS_DIR, cacheName);
      }

      if (filePath && fs.existsSync(filePath)) {
        try {
          const content = fs.readFileSync(filePath, 'utf8');
          const parsed = parseM3U(content, s.name);
          this.cache.set(s.id, parsed);

          for (const c of parsed.channels) {
            if (!channelIds.has(c.id)) {
              channelIds.add(c.id);
              channels.push(c);
            }
          }
          for (const m of parsed.movies) {
            if (!movieIds.has(m.id)) {
              movieIds.add(m.id);
              movies.push(m);
            }
          }
          for (const sg of parsed.series) {
            const key = sg.name.toLowerCase();
            if (seriesMap.has(key)) {
              const existing = seriesMap.get(key);
              const epIds = new Set(existing.episodes.map(e => e.id));
              for (const ep of sg.episodes) {
                if (!epIds.has(ep.id)) {
                  epIds.add(ep.id);
                  existing.episodes.push(ep);
                }
              }
            } else {
              seriesMap.set(key, sg);
            }
          }
        } catch (err) {
          console.warn(`Failed to parse playlist ${s.name}:`, err.message);
        }
      }
    }

    this.merged = {
      channels,
      movies,
      series: Array.from(seriesMap.values())
    };
  }

  getChannels({ group, source, search, limit = 100, offset = 0 } = {}) {
    this.ensureInitialized();
    let list = this.merged.channels;

    if (group && group.toLowerCase() !== 'all') {
      const gLower = group.toLowerCase();
      list = list.filter(c => c.group && (c.group.toLowerCase() === gLower || c.group.toLowerCase().includes(gLower)));
    }

    if (source && source.toLowerCase() !== 'all') {
      list = list.filter(c => c.sourceName && c.sourceName.toLowerCase() === source.toLowerCase());
    }

    if (search && search.trim()) {
      const sLower = search.toLowerCase().trim();
      list = list.filter(c =>
        (c.name && c.name.toLowerCase().includes(sLower)) ||
        (c.group && c.group.toLowerCase().includes(sLower))
      );
    }

    const total = list.length;
    const items = list.slice(offset, offset + limit);
    return { total, offset, limit, items };
  }

  getMovies({ category, source, search, limit = 60, offset = 0 } = {}) {
    this.ensureInitialized();
    let list = this.merged.movies;

    if (category && category.toLowerCase() !== 'all') {
      const cLower = category.toLowerCase();
      list = list.filter(m => m.category && (m.category.toLowerCase() === cLower || m.category.toLowerCase().includes(cLower)));
    }

    if (source && source.toLowerCase() !== 'all') {
      list = list.filter(m => m.sourceName && m.sourceName.toLowerCase() === source.toLowerCase());
    }

    if (search && search.trim()) {
      const sLower = search.toLowerCase().trim();
      list = list.filter(m =>
        (m.name && m.name.toLowerCase().includes(sLower)) ||
        (m.category && m.category.toLowerCase().includes(sLower))
      );
    }

    const total = list.length;
    const items = list.slice(offset, offset + limit);
    return { total, offset, limit, items };
  }

  getSeries({ category, source, search, limit = 60, offset = 0 } = {}) {
    this.ensureInitialized();
    let list = this.merged.series;

    if (category && category.toLowerCase() !== 'all') {
      const cLower = category.toLowerCase();
      list = list.filter(s => s.category && (s.category.toLowerCase() === cLower || s.category.toLowerCase().includes(cLower)));
    }

    if (source && source.toLowerCase() !== 'all') {
      list = list.filter(s => s.sourceName && s.sourceName.toLowerCase() === source.toLowerCase());
    }

    if (search && search.trim()) {
      const sLower = search.toLowerCase().trim();
      list = list.filter(s =>
        (s.name && s.name.toLowerCase().includes(sLower)) ||
        (s.category && s.category.toLowerCase().includes(sLower))
      );
    }

    const total = list.length;
    const items = list.slice(offset, offset + limit);
    return { total, offset, limit, items };
  }

  getCategories() {
    this.ensureInitialized();
    const channelGroups = new Set();
    const movieCategories = new Set();
    const seriesCategories = new Set();

    for (const c of this.merged.channels) {
      if (c.group && c.group.trim()) channelGroups.add(c.group.trim());
    }
    for (const m of this.merged.movies) {
      if (m.category && m.category.trim()) movieCategories.add(m.category.trim());
    }
    for (const s of this.merged.series) {
      if (s.category && s.category.trim()) seriesCategories.add(s.category.trim());
    }

    return {
      channelGroups: Array.from(channelGroups).sort(),
      movieCategories: Array.from(movieCategories).sort(),
      seriesCategories: Array.from(seriesCategories).sort()
    };
  }

  getSources() {
    this.ensureInitialized();
    return this.sources;
  }

  addSource(name, url, type = 'url') {
    this.ensureInitialized();
    const id = 'src_' + Date.now();
    const source = { id, name, url, enabled: true, type };
    this.sources.push(source);
    this.saveSources();
    return source;
  }

  removeSource(id) {
    this.ensureInitialized();
    const idx = this.sources.findIndex(s => s.id === id);
    if (idx !== -1) {
      this.sources.splice(idx, 1);
      this.saveSources();
      return true;
    }
    return false;
  }

  toggleSource(id, enabled) {
    this.ensureInitialized();
    const source = this.sources.find(s => s.id === id);
    if (source) {
      source.enabled = enabled;
      this.saveSources();
      return true;
    }
    return false;
  }

  getFeatured() {
    this.ensureInitialized();
    const featured = [];
    const movies = this.merged.movies;
    for (let i = 0; i < Math.min(3, movies.length); i++) {
      featured.push(movies[i]);
    }
    const channels = this.merged.channels;
    for (let i = 0; i < Math.min(3, channels.length); i++) {
      featured.push(channels[i]);
    }
    const series = this.merged.series;
    for (let i = 0; i < Math.min(2, series.length); i++) {
      featured.push(series[i]);
    }
    return featured;
  }

  getStats() {
    this.ensureInitialized();
    return {
      channels: this.merged.channels.length,
      movies: this.merged.movies.length,
      series: this.merged.series.length,
      sources: this.sources.length
    };
  }

  reloadAll() {
    this.cache.clear();
    this.rebuildFromFiles();
  }
}

// Global singleton instance
const playlistService = new PlaylistService();

module.exports = {
  playlistService,
  PlaylistService,
  USER_AGENT
};
