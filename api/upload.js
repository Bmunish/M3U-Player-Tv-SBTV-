const { playlistService } = require('../lib/playlistService');
const { parseM3U } = require('../lib/m3uParser');

module.exports = function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method must be POST' });
  }

  const body = req.body;
  if (!body) {
    return res.status(400).json({ error: 'Empty playlist content' });
  }

  const content = typeof body === 'string' ? body : JSON.stringify(body);
  const name = (req.query && req.query.name) || `Uploaded Playlist (${Date.now()})`;

  // In serverless, parse in-memory and register source
  const parsed = parseM3U(content, name);
  const source = playlistService.addSource(name, `inline:${Date.now()}`, 'local');
  playlistService.cache.set(source.id, parsed);

  // Merge into current active merged playlist
  const merged = playlistService.merged;
  const channelIds = new Set(merged.channels.map(c => c.id));
  const movieIds = new Set(merged.movies.map(m => m.id));
  const seriesMap = new Map();
  for (const sg of merged.series) {
    seriesMap.set(sg.name.toLowerCase(), sg);
  }

  for (const c of parsed.channels) {
    if (!channelIds.has(c.id)) {
      channelIds.add(c.id);
      merged.channels.push(c);
    }
  }
  for (const m of parsed.movies) {
    if (!movieIds.has(m.id)) {
      movieIds.add(m.id);
      merged.movies.push(m);
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
      merged.series.push(sg);
    }
  }

  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  return res.status(201).json({ success: true, source });
};
