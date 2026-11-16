/**
 * SBTV M3U Parser for Node.js / Vercel
 * Matches com.sbtv.parser.M3UParser behavior exactly.
 */

const S_X_E_X = /(.+?)\s*[-–:]?\s*[Ss](\d{1,2})\s*[Ee](\d{1,3})/;
const SEASON_EP = /(.+?)\s*[-–:]?\s*Season\s*(\d{1,2})\s*Episode\s*(\d{1,3})/i;
const EP_ONLY = /(.+?)\s*[-–:]?\s*(?:Episode|Ep|EP)\.?\s*(\d{1,3})/i;

function cleanDisplayTitle(title) {
  if (!title) return 'Stream';
  return title
    .replace(/\s*\[Geo-blocked\]/gi, '')
    .replace(/\s*\[Not 24\/7\]/gi, '')
    .trim();
}

function extractAttribute(line, attrName) {
  const pattern = attrName + '="';
  const start = line.indexOf(pattern);
  if (start !== -1) {
    const valStart = start + pattern.length;
    const valEnd = line.indexOf('"', valStart);
    if (valEnd !== -1) {
      return line.substring(valStart, valEnd).trim();
    }
  }
  return null;
}

function extractTitleFromUrl(url) {
  const slash = url.lastIndexOf('/');
  if (slash !== -1 && slash + 1 < url.length) {
    let end = url.substring(slash + 1);
    const q = end.indexOf('?');
    if (q !== -1) end = end.substring(0, q);
    return end;
  }
  return url;
}

function isSeries(group, url, title) {
  const g = group.toLowerCase();
  const u = url.toLowerCase();

  if (S_X_E_X.test(title) || SEASON_EP.test(title) || EP_ONLY.test(title)) {
    return true;
  }
  if (u.includes('/series/')) {
    return true;
  }
  if (!g.includes(';')) {
    return g.includes('series') || g.includes('web series') || g.includes('tv show') || g.includes('episodes');
  }
  return false;
}

function isMovie(group, url, title, sourceName) {
  const g = group.toLowerCase();
  const u = url.toLowerCase();
  const t = title.toLowerCase();

  if (g.includes('movie') || g.includes('vod') || g.includes('cinema') || g.includes('film') || g.includes('bollywood')) {
    return true;
  }
  if (u.includes('/movie/') || /\.(mp4|mkv|avi|webm)(\?.*)?$/i.test(u)) {
    return true;
  }
  if (t.startsWith('[vod]') || t.includes('(movie)') || t.includes('full movie')) {
    return true;
  }

  if (
    t.includes('goldmines') || t.includes('b4u movies') || t.includes('sony max') ||
    t.includes('sony wah') || t.includes('zee cinema') || t.includes('zee action') ||
    t.includes('zee bollywood') || t.includes('zee classic') || t.includes('star gold') ||
    t.includes('&pictures') || t.includes('and pictures') || t.includes('colors cineplex') ||
    t.includes('utv movies') || t.includes('manoranjan') || t.includes('maha movie') ||
    t.includes('enterr10 movies') || t.includes('all time movies') || t.includes('gemini movies') ||
    t.includes('surya movies') || t.includes('udaya movies') || t.includes('raj digital') ||
    t.includes('ktv') || t.includes('j movie') || t.includes('movies')
  ) {
    return true;
  }
  return false;
}

function normalizeSeriesCategory(group, title) {
  const g = group.toLowerCase();
  const t = title.toLowerCase();

  if (g.includes('sci-fi') || g.includes('scifi') || t.includes('space') || t.includes('cyber') || t.includes('quantum')) {
    return 'Sci-Fi Series';
  }
  if (g.includes('crime') || g.includes('mystery') || g.includes('thriller')) {
    return 'Crime & Thriller Series';
  }
  if (g.includes('action') || g.includes('adventure')) {
    return 'Action Series';
  }
  if (g.includes('animation') || g.includes('anime') || g.includes('cartoon') || g.includes('kids')) {
    return 'Animation & Anime Series';
  }
  if (g.includes('comedy') || g.includes('sitcom')) {
    return 'Comedy Series';
  }
  if (g.includes('doc') || g.includes('nature') || g.includes('history')) {
    return 'Documentary Series';
  }
  if (g.includes('indian') || g.includes('hindi') || g.includes('desi')) {
    return 'Indian Web Series';
  }
  if (g.includes('drama')) {
    return 'Drama Series';
  }
  return group && !group.includes(';') ? group : 'Drama Series';
}

function normalizeMovieCategory(group, title, sourceName) {
  const g = group.toLowerCase();
  const t = title.toLowerCase();
  const s = (sourceName || '').toLowerCase();

  if (g.includes('bollywood') || g.includes('indian') || s.includes('indian') ||
      t.includes('goldmines') || t.includes('b4u') || t.includes('zee') || t.includes('sony') ||
      t.includes('bollywood') || t.includes('hindi')) {
    return 'Bollywood & Indian Movies';
  }
  if (g.includes('animation') || g.includes('family') || g.includes('kids') || g.includes('cartoon')) {
    return 'Animation & Family Movies';
  }
  if (g.includes('sci-fi') || g.includes('scifi') || g.includes('fantasy')) {
    return 'Sci-Fi & Fantasy Movies';
  }
  if (g.includes('action') || g.includes('adventure') || t.includes('action')) {
    return 'Action & Adventure Movies';
  }
  if (g.includes('comedy') || t.includes('comedy')) {
    return 'Comedy Movies';
  }
  if (g.includes('thriller') || g.includes('horror') || g.includes('mystery') || g.includes('crime')) {
    return 'Thriller & Horror Movies';
  }
  if (g.includes('doc') || t.includes('documentary')) {
    return 'Documentary Films';
  }
  if (g.includes('classic') || t.includes('classic') || t.includes('retro') || t.includes('70s') || t.includes('80s') || t.includes('90s')) {
    return 'Classic Cinema';
  }
  if (g.includes('drama') || g.includes('romance')) {
    return 'Drama & Romance Movies';
  }
  return 'Featured Movies';
}

function normalizeChannelGroup(group, title, sourceName) {
  const g = group.toLowerCase();
  const t = title.toLowerCase();
  const s = (sourceName || '').toLowerCase();

  // Kids & Cartoons
  if (g.includes('kids') || g.includes('cartoon') || g.includes('animation') ||
      t.includes('kids') || t.includes('cartoon') || t.includes('pogo') ||
      t.includes('disney') || t.includes('nickelodeon') || t.includes('nick') ||
      t.includes('hungama') || t.includes('sonic') || t.includes('sony yay') ||
      t.includes('gubbare') || t.includes('chutti') || t.includes('kochu') ||
      t.includes('cbeebies') || t.includes('cbbc') || t.includes('anime')) {
    return 'Kids & Cartoons';
  }

  // Sports
  if (g.includes('sport') || s.includes('sport') ||
      t.includes('sport') || t.includes('cricket') || t.includes('football') ||
      t.includes('espn') || t.includes('sony ten') || t.includes('star sports') ||
      t.includes('red bull') || t.includes('golf') || t.includes('racing') ||
      t.includes('wwe') || t.includes('fight') || t.includes('tennis')) {
    return 'Sports';
  }

  // News
  if (g.includes('news') || s.includes('news') ||
      t.includes('news') || t.includes('aaj tak') || t.includes('ndtv') ||
      t.includes('abp') || t.includes('republic') || t.includes('india tv') ||
      t.includes('times now') || t.includes('wion') || t.includes('cnbc') ||
      t.includes('bloomberg') || t.includes('bbc') || t.includes('cnn') ||
      t.includes('al jazeera') || t.includes('dw') || t.includes('france 24') ||
      t.includes('samachar') || t.includes('tv9') || t.includes('v6') ||
      t.includes('polimer') || t.includes('thanthi') || t.includes('puthiya') ||
      t.includes('mathrubhumi') || t.includes('manorama news') || t.includes('24 news') ||
      t.includes('sandesh') || t.includes('otv') || t.includes('kanak')) {
    return s.includes('indian') ? 'Indian News' : 'World News';
  }

  // Music
  if (g.includes('music') || s.includes('music') ||
      t.includes('music') || t.includes('9xm') || t.includes('9x jalwa') ||
      t.includes('9x tashan') || t.includes('9x jhakaas') || t.includes('mastiii') ||
      t.includes('mtv') || t.includes('vh1') || t.includes('zing') ||
      t.includes('zoom') || t.includes('sangeet') || t.includes('b4u music') ||
      t.includes('showbox') || t.includes('pitaara') || t.includes('balle balle') ||
      t.includes('isaiaruvi') || t.includes('melody') || t.includes('hits')) {
    return 'Music';
  }

  // Documentary & Science
  if (g.includes('doc') || g.includes('science') || g.includes('education') || s.includes('doc') ||
      t.includes('discovery') || t.includes('animal planet') || t.includes('nat geo') ||
      t.includes('national geographic') || t.includes('history') || t.includes('nasa') ||
      t.includes('epic') || t.includes('travelxp') || t.includes('tlc') || t.includes('earth')) {
    return 'Documentary';
  }

  // Devotional / Spiritual
  if (g.includes('relig') || g.includes('devot') ||
      t.includes('aastha') || t.includes('sanskar') || t.includes('satsang') ||
      t.includes('bhajan') || t.includes('bhakti') || t.includes('peace') ||
      t.includes('arihant') || t.includes('paras') || t.includes('sadhna') ||
      t.includes('ishwar') || t.includes('darshan') || t.includes('svbc') ||
      t.includes('angel tv') || t.includes('shalom') || t.includes('goodness') ||
      t.includes('simran') || t.includes('chardikla') || t.includes('qtv')) {
    return 'Devotional';
  }

  if (s.includes('indian')) {
    return 'Indian Entertainment';
  }

  if (g.includes('entertainment') || g.includes('general') || g.includes('comedy') || g.includes('lifestyle')) {
    return 'Entertainment';
  }

  if (group && !group.includes(';')) {
    return group;
  }
  return 'Entertainment';
}

function padZero(numStr) {
  const n = parseInt(numStr, 10);
  return n < 10 ? '0' + n : String(n);
}

function cleanSeriesName(raw) {
  if (!raw) return 'Series';
  let s = raw
    .replace(/\s*[-–:]?\s*[Ss]\d{1,2}\s*[Ee]\d{1,3}.*/i, '')
    .replace(/\s*[-–:]?\s*Season\s*\d{1,2}\s*Episode\s*\d{1,3}.*/i, '')
    .replace(/\s*[-–:]?\s*(?:Episode|Ep|EP)\.?\s*\d{1,3}.*/i, '')
    .trim();
  return s || raw.trim();
}

function parseSeriesInfo(title) {
  let m = S_X_E_X.exec(title);
  if (m) {
    return {
      seriesName: cleanSeriesName(m[1]),
      season: 'S' + padZero(m[2]),
      episodeNum: 'E' + padZero(m[3])
    };
  }

  m = SEASON_EP.exec(title);
  if (m) {
    return {
      seriesName: cleanSeriesName(m[1]),
      season: 'S' + padZero(m[2]),
      episodeNum: 'E' + padZero(m[3])
    };
  }

  m = EP_ONLY.exec(title);
  if (m) {
    return {
      seriesName: cleanSeriesName(m[1]),
      season: 'S01',
      episodeNum: 'E' + padZero(m[2])
    };
  }

  return {
    seriesName: cleanSeriesName(title),
    season: 'S01',
    episodeNum: 'E01'
  };
}

function javaHashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0; // Convert to 32bit integer
  }
  return Math.abs(hash);
}

function parseM3U(content, sourceName) {
  if (!content) {
    return { channels: [], movies: [], series: [] };
  }

  const channels = [];
  const movies = [];
  const seriesGroupMap = new Map();

  const lines = content.split(/\r?\n/);
  let name = '';
  let logo = null;
  let group = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) continue;

    if (line.startsWith('#EXTINF')) {
      const commaIdx = line.lastIndexOf(',');
      if (commaIdx !== -1 && commaIdx + 1 < line.length) {
        name = line.substring(commaIdx + 1).trim();
      }

      logo = extractAttribute(line, 'tvg-logo') || extractAttribute(line, 'logo');
      group = extractAttribute(line, 'group-title');

    } else if (!line.startsWith('#')) {
      const url = line;
      if (url.toLowerCase().endsWith('.mpd')) {
        name = '';
        logo = null;
        group = null;
        continue;
      }

      const rawTitle = name ? name : extractTitleFromUrl(url);
      const currentTitle = cleanDisplayTitle(rawTitle);
      const rawGroup = group ? group.trim() : '';
      const itemId = String(javaHashCode(currentTitle + '|' + url));

      if (isSeries(rawGroup, url, currentTitle)) {
        const info = parseSeriesInfo(currentTitle);
        const seriesCat = normalizeSeriesCategory(rawGroup, currentTitle);
        const episode = {
          id: itemId,
          name: currentTitle,
          url,
          logo,
          category: seriesCat,
          seriesName: info.seriesName,
          season: info.season,
          episodeNum: info.episodeNum,
          sourceName
        };

        const groupKey = info.seriesName.toLowerCase();
        let sg = seriesGroupMap.get(groupKey);
        if (!sg) {
          sg = {
            id: String(javaHashCode(groupKey)),
            name: info.seriesName,
            logo,
            category: seriesCat,
            sourceName,
            episodes: []
          };
          seriesGroupMap.set(groupKey, sg);
        }
        sg.episodes.push(episode);

      } else if (isMovie(rawGroup, url, currentTitle, sourceName)) {
        const movieCat = normalizeMovieCategory(rawGroup, currentTitle, sourceName);
        movies.push({
          id: itemId,
          name: currentTitle,
          url,
          logo,
          category: movieCat,
          sourceName
        });

        if (url.toLowerCase().includes('.m3u8') && !rawGroup.toLowerCase().includes('vod') &&
            ((sourceName || '').toLowerCase().includes('indian') || (sourceName || '').toLowerCase().includes('channel'))) {
          channels.push({
            id: 'ch_' + itemId,
            name: currentTitle,
            url,
            logo,
            group: movieCat,
            sourceName
          });
        }

      } else {
        const channelGroup = normalizeChannelGroup(rawGroup, currentTitle, sourceName);
        channels.push({
          id: itemId,
          name: currentTitle,
          url,
          logo,
          group: channelGroup,
          sourceName
        });
      }

      name = '';
      logo = null;
      group = null;
    }
  }

  return {
    channels,
    movies,
    series: Array.from(seriesGroupMap.values())
  };
}

module.exports = {
  parseM3U,
  cleanDisplayTitle,
  javaHashCode
};
