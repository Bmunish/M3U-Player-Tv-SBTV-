/**
 * SBTV Stream — Main Web Application Controller
 * Handles SPA navigation, data fetching, pagination, playlists management, filters, and favorites.
 */

class SBTVApp {
  constructor() {
    this.currentTab = 'home';
    this.currentSource = 'All';
    this.currentChannelGroup = 'All';
    this.currentMovieCategory = 'All';
    this.currentSeriesCategory = 'All';
    this.searchQuery = '';
    this.favorites = this.loadFavorites();

    // Pagination offsets
    this.channelsOffset = 0;
    this.moviesOffset = 0;
    this.seriesOffset = 0;
    this.pageSize = 200;

    this.player = new StreamPlayer();

    // Cache containers
    this.cachedChannels = [];
    this.cachedMovies = [];
    this.cachedSeries = [];
    this.featuredItems = [];
    this.featuredIndex = 0;

    this.initDOM();
    this.initEvents();
    this.loadInitialData();
  }

  initDOM() {
    this.navLinks = document.querySelectorAll('.nav-link');
    this.views = document.querySelectorAll('.content-view');
    this.sourceSelect = document.getElementById('sourceSelect');
    this.searchInput = document.getElementById('searchInput');
    this.clearSearchBtn = document.getElementById('clearSearchBtn');
    this.heroSection = document.getElementById('heroSection');
    this.playlistsModal = document.getElementById('playlistsModal');
    this.seriesModal = document.getElementById('seriesModal');
    this.toast = document.getElementById('toast');
  }

  initEvents() {
    this.navLinks.forEach((link) => {
      link.addEventListener('click', (e) => {
        const tab = e.currentTarget.getAttribute('data-tab');
        this.switchTab(tab);
      });
    });

    document.getElementById('logoLink').addEventListener('click', (e) => {
      e.preventDefault();
      this.switchTab('home');
    });

    document.querySelectorAll('.rail-more-btn').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const target = e.currentTarget.getAttribute('data-target-tab');
        this.switchTab(target);
      });
    });

    this.sourceSelect.addEventListener('change', (e) => {
      this.currentSource = e.target.value;
      this.resetOffsets();
      this.refreshCurrentView();
    });

    let searchTimer = null;
    this.searchInput.addEventListener('input', (e) => {
      const val = e.target.value.trim();
      this.clearSearchBtn.style.display = val ? 'block' : 'none';
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        this.searchQuery = val;
        this.resetOffsets();
        this.refreshCurrentView();
      }, 250);
    });

    this.clearSearchBtn.addEventListener('click', () => {
      this.searchInput.value = '';
      this.clearSearchBtn.style.display = 'none';
      this.searchQuery = '';
      this.resetOffsets();
      this.refreshCurrentView();
    });

    document.getElementById('refreshBtn').addEventListener('click', () => {
      this.showToast('Refreshing stream playlists...');
      fetch('/api/reload', { method: 'POST' })
        .then(() => setTimeout(() => this.loadInitialData(), 800))
        .catch(err => console.error(err));
    });

    document.getElementById('addPlaylistBtn').addEventListener('click', () => {
      this.loadSourcesList();
      this.playlistsModal.showModal();
    });

    document.querySelectorAll('.dialog-tab').forEach((tabBtn) => {
      tabBtn.addEventListener('click', () => {
        document.querySelectorAll('.dialog-tab').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.dialog-tab-pane').forEach(p => p.classList.remove('active'));
        tabBtn.classList.add('active');
        const dtab = tabBtn.getAttribute('data-dtab');
        const pane = document.getElementById('dtab' + dtab.charAt(0).toUpperCase() + dtab.slice(1));
        if (pane) pane.classList.add('active');
      });
    });

    document.getElementById('addSourceForm').addEventListener('submit', (e) => {
      e.preventDefault();
      const name = document.getElementById('newSourceName').value.trim();
      const url = document.getElementById('newSourceUrl').value.trim();
      if (!name || !url) return;

      fetch('/api/sources', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, url, type: 'url' })
      })
      .then(res => res.json())
      .then(() => {
        this.showToast(`Playlist "${name}" added successfully!`);
        document.getElementById('addSourceForm').reset();
        this.loadSourcesList();
        this.loadSourcesDropdown();
        setTimeout(() => this.loadInitialData(), 1000);
      })
      .catch(err => {
        this.showToast('Failed to add playlist: ' + err.message);
      });
    });

    const dropzone = document.getElementById('uploadDropzone');
    const fileInput = document.getElementById('fileUploadInput');

    dropzone.addEventListener('click', () => fileInput.click());
    dropzone.addEventListener('dragover', (e) => {
      e.preventDefault();
      dropzone.style.borderColor = '#FFB800';
    });
    dropzone.addEventListener('dragleave', () => {
      dropzone.style.borderColor = 'rgba(198, 167, 94, 0.4)';
    });
    dropzone.addEventListener('drop', (e) => {
      e.preventDefault();
      dropzone.style.borderColor = 'rgba(198, 167, 94, 0.4)';
      if (e.dataTransfer.files.length) {
        this.handleFileUpload(e.dataTransfer.files[0]);
      }
    });
    fileInput.addEventListener('change', (e) => {
      if (e.target.files.length) {
        this.handleFileUpload(e.target.files[0]);
      }
    });

    document.getElementById('clearFavsBtn')?.addEventListener('click', () => {
      if (confirm('Clear all items from your Watchlist?')) {
        this.favorites = [];
        this.saveFavorites();
        this.renderFavorites();
      }
    });

    document.getElementById('heroPlayBtn').addEventListener('click', () => {
      if (this.featuredItem) {
        if (this.featuredItem.episodes && this.featuredItem.episodes.length) {
          this.player.play(this.featuredItem.episodes[0], this.featuredItem.episodes);
        } else {
          this.player.play(this.featuredItem, this.cachedMovies.length ? this.cachedMovies : [this.featuredItem]);
        }
      }
    });

    document.getElementById('heroAddFavBtn').addEventListener('click', () => {
      if (this.featuredItem) {
        this.toggleFavorite(this.featuredItem);
      }
    });
  }

  resetOffsets() {
    this.channelsOffset = 0;
    this.moviesOffset = 0;
    this.seriesOffset = 0;
  }

  handleFileUpload(file) {
    const status = document.getElementById('uploadStatus');
    status.style.display = 'block';
    status.textContent = `Uploading ${file.name}...`;

    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target.result;
      const playlistName = file.name.replace(/\.[^/.]+$/, "");

      fetch(`/api/upload?name=${encodeURIComponent(playlistName)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain; charset=utf-8' },
        body: content
      })
      .then(res => res.json())
      .then(() => {
        status.textContent = `✓ Uploaded ${file.name} successfully!`;
        status.style.color = '#10B981';
        this.showToast(`Playlist "${playlistName}" imported!`);
        this.loadSourcesList();
        this.loadSourcesDropdown();
        setTimeout(() => this.loadInitialData(), 1000);
      })
      .catch(err => {
        status.textContent = 'Upload failed: ' + err.message;
        status.style.color = '#E50914';
      });
    };
    reader.readAsText(file);
  }

  switchTab(tabId) {
    this.currentTab = tabId;
    this.resetOffsets();

    this.navLinks.forEach((link) => {
      link.classList.toggle('active', link.getAttribute('data-tab') === tabId);
    });

    this.views.forEach((view) => {
      view.classList.toggle('active', view.id === 'view' + tabId.charAt(0).toUpperCase() + tabId.slice(1));
    });

    this.heroSection.style.display = tabId === 'home' ? 'flex' : 'none';

    window.scrollTo({ top: 0, behavior: 'smooth' });
    this.refreshCurrentView();
  }

  refreshCurrentView() {
    switch (this.currentTab) {
      case 'home':
        this.loadHomeData();
        break;
      case 'channels':
        this.loadChannelsGrid(false);
        break;
      case 'movies':
        this.loadMoviesGrid(false);
        break;
      case 'series':
        this.loadSeriesGrid(false);
        break;
      case 'sports':
        this.loadSportsView();
        break;
      case 'kids':
        this.loadKidsView();
        break;
      case 'favorites':
        this.renderFavorites();
        break;
    }
  }

  loadInitialData() {
    this.loadSourcesDropdown();
    this.loadStats();
    this.loadFeatured();
    this.loadHomeData();
  }

  loadStats() {
    fetch('/api/stats')
      .then(res => res.json())
      .then(stats => {
        const descEl = document.getElementById('heroDesc');
        if (descEl && stats) {
          descEl.textContent = `Stream ${stats.channels.toLocaleString()}+ Live TV Channels, ${stats.movies.toLocaleString()}+ On-Demand Movies, and ${stats.series.toLocaleString()}+ Multi-Episode Series directly in your browser. Zero login required.`;
        }
        // Update navigation tab badges with live counts
        const chBtn = document.querySelector('.nav-link[data-tab="channels"]');
        const mvBtn = document.querySelector('.nav-link[data-tab="movies"]');
        const srBtn = document.querySelector('.nav-link[data-tab="series"]');
        if (chBtn && stats.channels) chBtn.textContent = `Live TV (${stats.channels.toLocaleString()})`;
        if (mvBtn && stats.movies) mvBtn.textContent = `Movies (${stats.movies.toLocaleString()})`;
        if (srBtn && stats.series) srBtn.textContent = `Series (${stats.series.toLocaleString()})`;
      })
      .catch(err => console.error('Error loading stats:', err));
  }

  loadSourcesDropdown() {
    fetch('/api/sources')
      .then(res => res.json())
      .then(sources => {
        this.sourceSelect.innerHTML = '<option value="All">All Sources</option>';
        sources.forEach((s) => {
          if (s.enabled) {
            const opt = document.createElement('option');
            opt.value = s.name;
            opt.textContent = s.name;
            this.sourceSelect.appendChild(opt);
          }
        });
        this.sourceSelect.value = this.currentSource;
      })
      .catch(err => console.error('Error loading sources:', err));
  }

  loadFeatured() {
    fetch('/api/featured')
      .then(res => res.json())
      .then(items => {
        if (!items || !items.length) return;
        this.featuredItems = items;
        this.featuredIndex = 0;
        this.applyFeaturedItem(items[0]);

        clearInterval(this.heroRotateTimer);
        if (items.length > 1) {
          this.heroRotateTimer = setInterval(() => {
            if (this.currentTab !== 'home') return;
            this.featuredIndex = (this.featuredIndex + 1) % this.featuredItems.length;
            this.applyFeaturedItem(this.featuredItems[this.featuredIndex]);
          }, 8000);
        }
      })
      .catch(err => console.error('Error loading featured:', err));
  }

  applyFeaturedItem(item) {
    if (!item) return;
    this.featuredItem = item;

    document.getElementById('heroTitle').textContent = item.name;
    document.getElementById('heroCategory').textContent = `${item.category || item.group || 'Featured Stream'} · ${item.sourceName || 'SBTV'}`;
    document.getElementById('heroSource').textContent = item.sourceName || 'SBTV Stream';
    document.getElementById('heroBadge').textContent = item.episodes
      ? 'FEATURED SERIES'
      : (item.poster ? 'FEATURED MOVIE' : 'FEATURED LIVE TV');

    const img = item.poster || item.logo;
    if (img) {
      document.getElementById('heroBackdrop').style.backgroundImage = `url('${img}')`;
    }
  }

  loadHomeData() {
    // Live TV Rail
    fetch(`/api/channels?limit=35&source=${encodeURIComponent(this.currentSource)}&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        this.cachedChannels = data.items || [];
        this.renderRail('railChannelsList', this.cachedChannels, 'channel');
      });

    // Movies Rail
    fetch(`/api/movies?limit=35&source=${encodeURIComponent(this.currentSource)}&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        this.cachedMovies = data.items || [];
        this.renderRail('railMoviesList', this.cachedMovies, 'movie');
      });

    // Series Rail
    fetch(`/api/series?limit=30&source=${encodeURIComponent(this.currentSource)}&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        this.cachedSeries = data.items || [];
        this.renderRail('railSeriesList', this.cachedSeries, 'series');
      });

    // Sports Rail
    fetch(`/api/channels?group=Sports&limit=30&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        this.renderRail('railSportsList', data.items || [], 'channel');
      });
  }

  renderRail(containerId, items, type) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';

    if (!items.length) {
      container.innerHTML = '<p class="text-muted" style="padding: 20px;">No streams available for this filter.</p>';
      return;
    }

    items.forEach((item) => {
      const card = this.createMediaCard(item, type, items);
      container.appendChild(card);
    });
  }

  createMediaCard(item, type, contextPlaylist = []) {
    const card = document.createElement('div');

    if (type === 'channel') {
      card.className = 'channel-card';
      const logo = item.logo || 'assets/tv-icon.png';
      card.innerHTML = `
        <div class="channel-logo-wrap">
          <span class="live-pill">LIVE</span>
          <img src="${logo}" onerror="this.src='assets/tv-icon.png'" class="channel-logo" alt="${item.name}" loading="lazy">
        </div>
        <div class="channel-info">
          <h4 class="channel-name" title="${item.name}">${item.name}</h4>
          <div class="channel-meta">
            <span>${item.group || 'Live TV'}</span>
            <span>${item.sourceName || ''}</span>
          </div>
        </div>
      `;
      card.addEventListener('click', () => {
        this.player.play(item, contextPlaylist);
      });

    } else if (type === 'movie') {
      card.className = 'movie-card';
      const poster = item.poster || 'assets/no-image.png';
      const isVod = item.streamUrl && /\.(mp4|webm|mkv)(\?.*)?$/i.test(item.streamUrl);
      card.innerHTML = `
        <div class="movie-poster-wrap">
          <img src="${poster}" onerror="this.src='assets/no-image.png'" class="movie-poster" alt="${item.name}" loading="lazy">
          <div class="movie-play-overlay">
            <div class="play-circle">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                <polygon points="6 4 20 12 6 20 6 4"></polygon>
              </svg>
            </div>
          </div>
          <span class="movie-badge">${isVod ? 'VOD HD' : 'HD'}</span>
        </div>
        <div class="movie-info">
          <h4 class="movie-title" title="${item.name}">${item.name}</h4>
          <p class="movie-genre">${item.category || 'Movie'}</p>
        </div>
      `;
      card.addEventListener('click', () => {
        this.player.play(item, contextPlaylist);
      });

    } else if (type === 'series') {
      card.className = 'movie-card';
      const poster = item.poster || 'assets/no-image.png';
      const epCount = (item.episodes && item.episodes.length) || 1;
      card.innerHTML = `
        <div class="movie-poster-wrap">
          <img src="${poster}" onerror="this.src='assets/no-image.png'" class="movie-poster" alt="${item.name}" loading="lazy">
          <div class="movie-play-overlay">
            <div class="play-circle">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                <polygon points="6 4 20 12 6 20 6 4"></polygon>
              </svg>
            </div>
          </div>
          <span class="movie-badge">${epCount} EPISODES</span>
        </div>
        <div class="movie-info">
          <h4 class="movie-title" title="${item.name}">${item.name}</h4>
          <p class="movie-genre">${item.category || 'Series'}</p>
        </div>
      `;
      card.addEventListener('click', () => {
        this.openSeriesModal(item);
      });
    }

    return card;
  }

  loadChannelsGrid(append = false) {
    if (!append) {
      this.channelsOffset = 0;
      fetch('/api/categories')
        .then(res => res.json())
        .then(cats => {
          const pillsContainer = document.getElementById('channelsPills');
          pillsContainer.innerHTML = '';
          const groups = ['All', ...(cats.channelGroups || [])];
          groups.forEach((g) => {
            const btn = document.createElement('button');
            btn.className = `pill ${g === this.currentChannelGroup ? 'active' : ''}`;
            btn.textContent = g;
            btn.addEventListener('click', () => {
              this.currentChannelGroup = g;
              this.loadChannelsGrid(false);
            });
            pillsContainer.appendChild(btn);
          });
        });
    }

    fetch(`/api/channels?limit=${this.pageSize}&offset=${this.channelsOffset}&group=${encodeURIComponent(this.currentChannelGroup)}&source=${encodeURIComponent(this.currentSource)}&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        const grid = document.getElementById('channelsGrid');
        if (!append) grid.innerHTML = '';

        const total = data.total || 0;
        const items = data.items || [];
        document.getElementById('channelsCountText').textContent = `${total.toLocaleString()} live channels available`;

        if (!items.length && !append) {
          grid.innerHTML = '<p class="text-muted" style="grid-column: 1/-1; padding: 40px; text-align: center;">No channels found.</p>';
          document.getElementById('channelsPagination').innerHTML = '';
          return;
        }

        items.forEach((ch) => {
          grid.appendChild(this.createMediaCard(ch, 'channel', items));
        });

        this.channelsOffset += items.length;
        this.renderLoadMore('channelsPagination', this.channelsOffset, total, () => this.loadChannelsGrid(true));
      });
  }

  loadMoviesGrid(append = false) {
    if (!append) {
      this.moviesOffset = 0;
      fetch('/api/categories')
        .then(res => res.json())
        .then(cats => {
          const pillsContainer = document.getElementById('moviesPills');
          pillsContainer.innerHTML = '';
          const catsList = ['All', ...(cats.movieCategories || [])];
          catsList.forEach((c) => {
            const btn = document.createElement('button');
            btn.className = `pill ${c === this.currentMovieCategory ? 'active' : ''}`;
            btn.textContent = c;
            btn.addEventListener('click', () => {
              this.currentMovieCategory = c;
              this.loadMoviesGrid(false);
            });
            pillsContainer.appendChild(btn);
          });
        });
    }

    fetch(`/api/movies?limit=${this.pageSize}&offset=${this.moviesOffset}&category=${encodeURIComponent(this.currentMovieCategory)}&source=${encodeURIComponent(this.currentSource)}&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        const grid = document.getElementById('moviesGrid');
        if (!append) grid.innerHTML = '';

        const total = data.total || 0;
        const items = data.items || [];
        document.getElementById('moviesCountText').textContent = `${total.toLocaleString()} movies & cinema streams available`;

        if (!items.length && !append) {
          grid.innerHTML = '<p class="text-muted" style="grid-column: 1/-1; padding: 40px; text-align: center;">No movies found.</p>';
          document.getElementById('moviesPagination').innerHTML = '';
          return;
        }

        items.forEach((m) => {
          grid.appendChild(this.createMediaCard(m, 'movie', items));
        });

        this.moviesOffset += items.length;
        this.renderLoadMore('moviesPagination', this.moviesOffset, total, () => this.loadMoviesGrid(true));
      });
  }

  loadSeriesGrid(append = false) {
    if (!append) {
      this.seriesOffset = 0;
      fetch('/api/categories')
        .then(res => res.json())
        .then(cats => {
          const pillsContainer = document.getElementById('seriesPills');
          pillsContainer.innerHTML = '';
          const catsList = ['All', ...(cats.seriesCategories || [])];
          catsList.forEach((c) => {
            const btn = document.createElement('button');
            btn.className = `pill ${c === this.currentSeriesCategory ? 'active' : ''}`;
            btn.textContent = c;
            btn.addEventListener('click', () => {
              this.currentSeriesCategory = c;
              this.loadSeriesGrid(false);
            });
            pillsContainer.appendChild(btn);
          });
        });
    }

    fetch(`/api/series?limit=${this.pageSize}&offset=${this.seriesOffset}&category=${encodeURIComponent(this.currentSeriesCategory)}&source=${encodeURIComponent(this.currentSource)}&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        const grid = document.getElementById('seriesGrid');
        if (!append) grid.innerHTML = '';

        const total = data.total || 0;
        const items = data.items || [];
        const totalEpisodes = items.reduce((acc, s) => acc + ((s.episodes && s.episodes.length) || 0), 0);
        document.getElementById('seriesCountText').textContent = `${total.toLocaleString()} TV series (${totalEpisodes} episodes) available`;

        if (!items.length && !append) {
          grid.innerHTML = '<p class="text-muted" style="grid-column: 1/-1; padding: 40px; text-align: center;">No series found.</p>';
          document.getElementById('seriesPagination').innerHTML = '';
          return;
        }

        items.forEach((s) => {
          grid.appendChild(this.createMediaCard(s, 'series', items));
        });

        this.seriesOffset += items.length;
        this.renderLoadMore('seriesPagination', this.seriesOffset, total, () => this.loadSeriesGrid(true));
      });
  }

  renderLoadMore(containerId, currentCount, totalCount, onLoadMore) {
    const wrap = document.getElementById(containerId);
    if (!wrap) return;
    wrap.innerHTML = '';

    if (currentCount < totalCount) {
      const btn = document.createElement('button');
      btn.className = 'btn btn-primary btn-large';
      btn.style.margin = '24px auto';
      btn.style.display = 'flex';
      btn.textContent = `Load More (${currentCount.toLocaleString()} of ${totalCount.toLocaleString()} shown)`;
      btn.addEventListener('click', onLoadMore);
      wrap.appendChild(btn);
    }
  }

  loadSportsView() {
    fetch(`/api/channels?group=Sports&limit=200&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        const grid = document.getElementById('sportsGrid');
        grid.innerHTML = '';
        document.getElementById('sportsCountText').textContent = `${(data.total || 0).toLocaleString()} live sports channels`;
        (data.items || []).forEach(ch => grid.appendChild(this.createMediaCard(ch, 'channel', data.items)));
      });
  }

  loadKidsView() {
    fetch(`/api/channels?group=Kids&limit=200&search=${encodeURIComponent(this.searchQuery)}`)
      .then(res => res.json())
      .then(data => {
        const grid = document.getElementById('kidsGrid');
        grid.innerHTML = '';
        document.getElementById('kidsCountText').textContent = `${(data.total || 0).toLocaleString()} kids & cartoon channels`;
        (data.items || []).forEach(ch => grid.appendChild(this.createMediaCard(ch, 'channel', data.items)));
      });
  }

  openSeriesModal(seriesGroup) {
    document.getElementById('seriesModalTitle').textContent = seriesGroup.name;
    document.getElementById('seriesModalPoster').src = seriesGroup.poster || 'assets/no-image.png';
    const epTotal = (seriesGroup.episodes && seriesGroup.episodes.length) || 0;
    document.getElementById('seriesModalCategory').textContent = `${seriesGroup.category || 'Series'} · ${epTotal} Episodes`;

    const seasonTabs = document.getElementById('seasonTabs');
    const episodesList = document.getElementById('episodesList');
    seasonTabs.innerHTML = '';
    episodesList.innerHTML = '';

    const seasonsMap = {};
    (seriesGroup.episodes || []).forEach((ep) => {
      const s = ep.season || 'S01';
      if (!seasonsMap[s]) seasonsMap[s] = [];
      seasonsMap[s].push(ep);
    });

    const seasons = Object.keys(seasonsMap).sort();
    if (!seasons.length) {
      episodesList.innerHTML = '<p class="text-muted">No episodes listed.</p>';
      this.seriesModal.showModal();
      return;
    }

    let activeSeason = seasons[0];

    const renderEpisodes = (season) => {
      episodesList.innerHTML = '';
      const eps = seasonsMap[season] || [];
      eps.forEach((ep, idx) => {
        const itemEl = document.createElement('div');
        itemEl.className = 'episode-item';
        itemEl.innerHTML = `
          <div class="episode-info">
            <span class="episode-title">${idx + 1}. ${ep.name}</span>
            <span class="episode-meta">${ep.season || 'S01'} ${ep.episodeNum || 'E0' + (idx + 1)} · HD Ready</span>
          </div>
          <button class="btn btn-primary btn-small">▶ Play Episode</button>
        `;
        itemEl.addEventListener('click', () => {
          this.seriesModal.close();
          this.player.play(ep, seriesGroup.episodes);
        });
        episodesList.appendChild(itemEl);
      });
    };

    seasons.forEach((season) => {
      const tab = document.createElement('button');
      tab.className = `season-tab ${season === activeSeason ? 'active' : ''}`;
      tab.textContent = `${season} (${seasonsMap[season].length} Ep)`;
      tab.addEventListener('click', () => {
        document.querySelectorAll('.season-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        activeSeason = season;
        renderEpisodes(season);
      });
      seasonTabs.appendChild(tab);
    });

    renderEpisodes(activeSeason);
    this.seriesModal.showModal();
  }

  loadSourcesList() {
    const list = document.getElementById('sourcesList');
    list.innerHTML = '<p class="loading-text">Loading playlists...</p>';

    fetch('/api/sources')
      .then(res => res.json())
      .then(sources => {
        list.innerHTML = '';
        if (!sources.length) {
          list.innerHTML = '<p class="text-muted">No playlists configured.</p>';
          return;
        }

        sources.forEach((src) => {
          const item = document.createElement('div');
          item.className = 'source-item';
          item.innerHTML = `
            <div class="source-item-info">
              <span class="source-item-name">${src.name}</span>
              <span class="source-item-url" title="${src.url}">${src.url}</span>
            </div>
            <div class="source-item-actions">
              <label class="switch-label">
                <input type="checkbox" ${src.enabled ? 'checked' : ''} class="source-toggle">
                <span class="toggle-switch"></span>
              </label>
              <button class="btn btn-icon-only delete-source-btn" title="Delete Playlist" style="color: #E50914;">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"></polyline>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                </svg>
              </button>
            </div>
          `;

          const toggle = item.querySelector('.source-toggle');
          toggle.addEventListener('change', (e) => {
            fetch(`/api/sources?id=${src.id}&enabled=${e.target.checked}`, { method: 'PUT' })
              .then(() => {
                this.showToast(`Playlist "${src.name}" ${e.target.checked ? 'enabled' : 'disabled'}`);
                this.loadSourcesDropdown();
                this.loadInitialData();
              });
          });

          const delBtn = item.querySelector('.delete-source-btn');
          delBtn.addEventListener('click', () => {
            if (confirm(`Remove playlist "${src.name}"?`)) {
              fetch(`/api/sources?id=${src.id}`, { method: 'DELETE' })
                .then(() => {
                  this.showToast(`Playlist "${src.name}" removed`);
                  this.loadSourcesList();
                  this.loadSourcesDropdown();
                  this.loadInitialData();
                });
            }
          });

          list.appendChild(item);
        });
      });
  }

  loadFavorites() {
    try {
      return JSON.parse(localStorage.getItem('sbtv_favorites')) || [];
    } catch (e) {
      return [];
    }
  }

  saveFavorites() {
    localStorage.setItem('sbtv_favorites', JSON.stringify(this.favorites));
  }

  isFavorite(id) {
    return this.favorites.some(f => f.id === id);
  }

  toggleFavorite(item) {
    const idx = this.favorites.findIndex(f => f.id === item.id);
    if (idx >= 0) {
      this.favorites.splice(idx, 1);
      this.showToast(`Removed "${item.name}" from Watchlist`);
    } else {
      this.favorites.push(item);
      this.showToast(`Added "${item.name}" to Watchlist!`);
    }
    this.saveFavorites();
    if (this.currentTab === 'favorites') {
      this.renderFavorites();
    }
  }

  renderFavorites() {
    const grid = document.getElementById('favoritesGrid');
    grid.innerHTML = '';
    document.getElementById('favCountText').textContent = `${this.favorites.length} saved titles`;

    if (!this.favorites.length) {
      grid.innerHTML = '<p class="text-muted" style="grid-column: 1/-1; padding: 40px; text-align: center;">Your Watchlist is empty. Click the save button on any channel, movie, or series to add it here.</p>';
      return;
    }

    this.favorites.forEach((item) => {
      const type = item.episodes ? 'series' : (item.poster ? 'movie' : 'channel');
      grid.appendChild(this.createMediaCard(item, type, this.favorites));
    });
  }

  showToast(msg) {
    this.toast.textContent = msg;
    this.toast.classList.add('show');
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => {
      this.toast.classList.remove('show');
    }, 3500);
  }
}

window.scrollRail = function(containerId, distance) {
  const el = document.getElementById(containerId);
  if (el) {
    el.scrollBy({ left: distance, behavior: 'smooth' });
  }
};

document.addEventListener('DOMContentLoaded', () => {
  window.app = new SBTVApp();
});
