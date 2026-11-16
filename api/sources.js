const { playlistService } = require('../lib/playlistService');

module.exports = function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  res.setHeader('Content-Type', 'application/json; charset=utf-8');

  if (req.method === 'GET') {
    const sources = playlistService.getSources();
    return res.status(200).json(sources);
  }

  if (req.method === 'POST') {
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : (req.body || {});
    const { name, url, type } = body;
    if (!name || !url) {
      return res.status(400).json({ error: 'name and url are required' });
    }
    const added = playlistService.addSource(name, url, type || 'url');
    return res.status(201).json(added);
  }

  if (req.method === 'DELETE') {
    const { id } = req.query;
    if (!id) {
      return res.status(400).json({ error: 'id parameter is required' });
    }
    const ok = playlistService.removeSource(id);
    return res.status(ok ? 200 : 404).json({ success: ok });
  }

  if (req.method === 'PUT') {
    const { id, enabled } = req.query;
    if (!id || enabled === undefined) {
      return res.status(400).json({ error: 'id and enabled parameters are required' });
    }
    const isEnabled = enabled === 'true' || enabled === true;
    const ok = playlistService.toggleSource(id, isEnabled);
    return res.status(ok ? 200 : 404).json({ success: ok });
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
