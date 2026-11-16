#!/usr/bin/env node
/**
 * Lightweight local development server mimicking Vercel Serverless & Static hosting
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = process.env.PORT || 3000;
const PUBLIC_DIR = path.join(__dirname, '..', 'public');

// Route map
const apiRoutes = {
  '/api/channels': require('../api/channels'),
  '/api/movies': require('../api/movies'),
  '/api/series': require('../api/series'),
  '/api/categories': require('../api/categories'),
  '/api/sources': require('../api/sources'),
  '/api/featured': require('../api/featured'),
  '/api/stats': require('../api/stats'),
  '/api/reload': require('../api/reload'),
  '/api/upload': require('../api/upload'),
  '/api/stream': require('../api/stream')
};

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.m3u8': 'application/vnd.apple.mpegurl'
};

const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  // Enhance res with status and json helpers like Vercel
  res.status = function(code) {
    res.statusCode = code;
    return res;
  };
  res.json = function(data) {
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify(data));
  };
  res.send = function(data) {
    res.end(data);
  };

  req.query = parsedUrl.query || {};

  // Check API routes
  if (apiRoutes[pathname]) {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      req.body = body;
      try {
        if (req.headers['content-type'] && req.headers['content-type'].includes('application/json')) {
          req.body = JSON.parse(body);
        }
      } catch (e) {}
      apiRoutes[pathname](req, res);
    });
    return;
  }

  // Static files
  let safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
  if (safePath === '/' || safePath === '\\') safePath = '/index.html';
  let filePath = path.join(PUBLIC_DIR, safePath);

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(PUBLIC_DIR, 'index.html');
  }

  if (fs.existsSync(filePath) && !fs.statSync(filePath).isDirectory()) {
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('404 Not Found');
  }
});

server.listen(PORT, () => {
  console.log(`\n🎬 SBTV Server running at http://localhost:${PORT}`);
  console.log(`✓ Serving static files from ${PUBLIC_DIR}`);
  console.log(`✓ API endpoints ready at http://localhost:${PORT}/api/*\n`);
});
