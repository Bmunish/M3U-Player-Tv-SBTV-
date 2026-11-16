const { playlistService } = require('../lib/playlistService');

module.exports = function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const result = playlistService.getStats();
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  return res.status(200).json(result);
};
