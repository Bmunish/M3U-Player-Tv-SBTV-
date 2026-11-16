const { playlistService } = require('../lib/playlistService');

module.exports = function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const { category, source, search, limit, offset } = req.query;
  const result = playlistService.getMovies({
    category,
    source,
    search,
    limit: limit ? parseInt(limit, 10) : 60,
    offset: offset ? parseInt(offset, 10) : 0
  });

  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  return res.status(200).json(result);
};
