const https = require('https');
const http = require('http');
const { URL } = require('url');

const USER_AGENT = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const VLC_USER_AGENT = 'VLC/3.0.18 LibVLC/3.0.18';
const URI_ATTR_PATTERN = /URI="([^"]+)"/g;

function resolveUrl(base, relative) {
  try {
    const trimmed = relative.trim();
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return trimmed;
    }
    if (trimmed.startsWith('//')) {
      const proto = base.protocol || 'https:';
      return proto + trimmed;
    }
    return new URL(trimmed, base.href).href;
  } catch (e) {
    return relative;
  }
}

async function fetchUpstream(targetUrl, userAgent, includeReferer, rangeHeader) {
  const urlObj = new URL(targetUrl);
  const headers = {
    'User-Agent': userAgent,
    'Accept': '*/*',
    'Accept-Language': 'en-US,en;q=0.9'
  };

  if (includeReferer && urlObj.origin) {
    headers['Referer'] = urlObj.origin + '/';
    headers['Origin'] = urlObj.origin;
  }

  if (rangeHeader) {
    headers['Range'] = rangeHeader;
  }

  // Use native global fetch with redirect follow
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 15000);

  try {
    const res = await fetch(urlObj.href, {
      method: 'GET',
      headers,
      redirect: 'follow',
      signal: controller.signal
    });
    clearTimeout(timeoutId);
    return res;
  } catch (err) {
    clearTimeout(timeoutId);
    throw err;
  }
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Accept-Ranges');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const targetUrl = req.query.url;
  if (!targetUrl || !targetUrl.trim()) {
    return res.status(400).json({ error: 'Missing ?url= parameter' });
  }

  try {
    const rawTarget = targetUrl.trim();
    const rangeHeader = req.headers.range;

    let upstreamRes;
    try {
      upstreamRes = await fetchUpstream(rawTarget, USER_AGENT, true, rangeHeader);
      // Retry with VLC user agent if blocked
      if ([401, 403, 406].includes(upstreamRes.status)) {
        upstreamRes = await fetchUpstream(rawTarget, VLC_USER_AGENT, false, rangeHeader);
      }
    } catch (e) {
      // Fallback try with VLC
      try {
        upstreamRes = await fetchUpstream(rawTarget, VLC_USER_AGENT, false, rangeHeader);
      } catch (err2) {
        return res.status(502).json({ error: 'Failed to connect to stream source: ' + err2.message });
      }
    }

    if (!upstreamRes || upstreamRes.status >= 400) {
      const code = upstreamRes ? upstreamRes.status : 502;
      return res.status(code).json({ error: `Upstream stream returned HTTP ${code}` });
    }

    const effectiveUrl = upstreamRes.url || rawTarget;
    const baseUri = new URL(effectiveUrl);
    const contentType = (upstreamRes.headers.get('content-type') || '').toLowerCase();
    const effectiveUrlLower = effectiveUrl.toLowerCase();

    const isM3u8 =
      effectiveUrlLower.includes('.m3u8') ||
      rawTarget.toLowerCase().includes('.m3u8') ||
      contentType.includes('mpegurl') ||
      contentType.includes('application/x-mpegurl') ||
      contentType.includes('vnd.apple.mpegurl');

    if (isM3u8) {
      const manifestText = await upstreamRes.text();
      const lines = manifestText.split(/\r?\n/);
      const rewritten = [];

      for (let line of lines) {
        const trimmed = line.trim();
        if (!trimmed) {
          rewritten.push('');
          continue;
        }

        if (trimmed.startsWith('#')) {
          if (trimmed.includes('URI="')) {
            line = line.replace(URI_ATTR_PATTERN, (match, rawUri) => {
              const resolved = resolveUrl(baseUri, rawUri);
              return `URI="/api/stream?url=${encodeURIComponent(resolved)}"`;
            });
          }
          rewritten.push(line);
        } else {
          // Segment or child playlist URL
          const resolved = resolveUrl(baseUri, trimmed);
          rewritten.push(`/api/stream?url=${encodeURIComponent(resolved)}`);
        }
      }

      const bodyBuffer = Buffer.from(rewritten.join('\n'), 'utf8');
      res.setHeader('Content-Type', 'application/vnd.apple.mpegurl; charset=utf-8');
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');

      if (req.method === 'HEAD') {
        return res.status(upstreamRes.status).end();
      }

      return res.status(upstreamRes.status).send(bodyBuffer);
    }

    // Binary stream chunk (.ts, .mp4, etc.)
    const ct = upstreamRes.headers.get('content-type') || 'video/mp2t';
    res.setHeader('Content-Type', ct);

    const cr = upstreamRes.headers.get('content-range');
    if (cr) res.setHeader('Content-Range', cr);

    const ar = upstreamRes.headers.get('accept-ranges') || 'bytes';
    res.setHeader('Accept-Ranges', ar);

    const cl = upstreamRes.headers.get('content-length');
    if (cl) res.setHeader('Content-Length', cl);

    if (req.method === 'HEAD') {
      return res.status(upstreamRes.status).end();
    }

    const arrayBuffer = await upstreamRes.arrayBuffer();
    return res.status(upstreamRes.status).send(Buffer.from(arrayBuffer));

  } catch (err) {
    if (!res.headersSent) {
      return res.status(502).json({ error: 'Proxy error: ' + err.message });
    }
  }
};
