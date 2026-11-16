#!/usr/bin/env node
/**
 * Prebuild script for SBTV on Vercel
 * 1. Generates data/preparsed.json for instant serverless startup
 * 2. Mirrors web/ to public/ for zero-config Vercel static hosting
 */

const fs = require('fs');
const path = require('path');
const { parseM3U } = require('../lib/m3uParser');

const BASE_DIR = path.resolve(__dirname, '..');
const DATA_DIR = path.join(BASE_DIR, 'data');
const PLAYLISTS_DIR = path.join(DATA_DIR, 'playlists');
const SOURCES_FILE = path.join(DATA_DIR, 'sources.json');
const PREPARSED_FILE = path.join(DATA_DIR, 'preparsed.json');
const WEB_DIR = path.join(BASE_DIR, 'web');
const PUBLIC_DIR = path.join(BASE_DIR, 'public');

console.log('==> Running SBTV Prebuild for Vercel...');

// Step 1: Copy web/ to public/
function copyRecursive(src, dest) {
  if (!fs.existsSync(dest)) {
    fs.mkdirSync(dest, { recursive: true });
  }
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyRecursive(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

if (fs.existsSync(WEB_DIR)) {
  console.log('Syncing static assets: web/ -> public/...');
  copyRecursive(WEB_DIR, PUBLIC_DIR);
}

// Step 2: Generate preparsed.json
if (fs.existsSync(SOURCES_FILE)) {
  console.log('Generating pre-parsed playlist cache for serverless runtime...');
  const sources = JSON.parse(fs.readFileSync(SOURCES_FILE, 'utf8'));

  const channels = [];
  const movies = [];
  const seriesMap = new Map();
  const channelIds = new Set();
  const movieIds = new Set();

  for (const s of sources) {
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
        console.log(`  ✓ Cached ${s.name}: ${parsed.channels.length} channels, ${parsed.movies.length} movies, ${parsed.series.length} series`);
      } catch (err) {
        console.warn(`  ! Warning parsing ${s.name}:`, err.message);
      }
    }
  }

  const output = {
    channels,
    movies,
    series: Array.from(seriesMap.values())
  };

  fs.writeFileSync(PREPARSED_FILE, JSON.stringify(output), 'utf8');
  console.log(`✓ Preparsed database written to data/preparsed.json (${(fs.statSync(PREPARSED_FILE).size / 1024).toFixed(1)} KB)`);
  console.log(`✓ Total ready: ${channels.length} channels, ${movies.length} movies, ${seriesMap.size} series`);
}

console.log('==> SBTV Prebuild finished successfully!');
