/* ProjectMind Web UI */
(() => {
  'use strict';

  const API = '/api/v1';
  const STORAGE_KEY = 'projectmind.repoPath';
  const THEME_KEY = 'projectmind.theme';
  const MODEL_KEY = 'projectmind.ollamaModel';

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => document.querySelectorAll(sel);

  const state = {
    repoPath: localStorage.getItem(STORAGE_KEY) || '',
    metadata: null,
    chatHistory: [],
  };

  // ── API helpers ──────────────────────────────────────────────

  async function api(method, path, body) {
    const opts = {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
    };
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(API + path, opts);
    if (res.status === 204) return null;

    const text = await res.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = null; }
    }

    if (!res.ok) {
      const msg = data?.message || data?.error || text || res.statusText;
      const details = data?.details?.length ? '\n' + data.details.join('\n') : '';
      throw new Error(msg + details);
    }

    if (text && (data === null || typeof data !== 'object')) {
      throw new Error(
        'ProjectMind API returned an invalid response. ' +
        'Make sure the server is running (default: http://localhost:8081) and port 8080 is not used by Jenkins or another app.'
      );
    }

    return data;
  }

  const apiGet = (path) => api('GET', path);
  const apiPost = (path, body) => api('POST', path, body);
  const apiDelete = (path) => api('DELETE', path);

  // ── UI helpers ───────────────────────────────────────────────

  function showOverlay(msg) {
    $('#overlayMessage').textContent = msg || 'Working...';
    $('#overlay').hidden = false;
  }

  function hideOverlay() {
    $('#overlay').hidden = true;
  }

  function toast(message, type = 'info') {
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.textContent = message;
    $('#toastContainer').appendChild(el);
    setTimeout(() => el.remove(), 4500);
  }

  function repoPath() {
    const path = $('#repoPath').value.trim();
    if (!path) throw new Error('Enter a repository path first');
    state.repoPath = path;
    localStorage.setItem(STORAGE_KEY, path);
    return path;
  }

  function encodePath(path) {
    return encodeURIComponent(path);
  }

  function formatDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString();
  }

  function formatStatus(status) {
    if (!status) return 'Unknown';
    return status.replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase());
  }

  function showActionResult(data) {
    const card = $('#actionResult');
    const body = $('#actionResultBody');
    card.hidden = false;
    body.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  }

  async function withLoading(message, fn) {
    showOverlay(message);
    try {
      return await fn();
    } finally {
      hideOverlay();
    }
  }

  // ── Navigation ───────────────────────────────────────────────

  function switchTab(tab) {
    $$('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.tab === tab));
    $$('.panel').forEach(el => el.classList.toggle('active', el.id === `panel-${tab}`));

    if (tab === 'ask') loadOllamaModels();
    if (tab === 'history') loadHistory().catch(e => toast(e.message, 'error'));
    if (tab === 'graph') renderGraph().catch(e => toast(e.message, 'error'));
    if (tab === 'docs') loadDocsPreview().catch(e => toast(e.message, 'error'));
  }

  // ── Dashboard ────────────────────────────────────────────────

  function updateStatusPill(metadata) {
    const pill = $('#statusPill');
    const dot = pill.querySelector('.status-dot');
    const text = $('#statusText');

    if (!metadata) {
      dot.className = 'status-dot not-scanned';
      text.textContent = 'Not scanned';
      return;
    }

    const status = (metadata.status || 'NOT_SCANNED').toLowerCase().replace('_', '-');
    dot.className = `status-dot ${status.replace('_', '-')}`;
    text.textContent = formatStatus(metadata.status);
    state.metadata = metadata;
  }

  function syncRepoPath(canonicalPath) {
    if (!canonicalPath) return;
    $('#repoPath').value = canonicalPath;
    state.repoPath = canonicalPath;
    localStorage.setItem(STORAGE_KEY, canonicalPath);
  }

  function isIndexed(metadata) {
    if (!metadata || typeof metadata !== 'object') return false;
    const status = String(metadata.status || 'NOT_SCANNED').toUpperCase();
    return status !== 'NOT_SCANNED';
  }

  function isNotScanned(metadata) {
    return !isIndexed(metadata);
  }

  function applyDashboard(metadata, overview) {
    if (metadata) updateStatusPill(metadata);
    if (overview && !isNotScanned(overview.metadata)) {
      renderStats(overview);
    } else if (metadata && !isNotScanned(metadata)) {
      renderStats({
        metadata,
        graphNodeCount: 0,
        graphEdgeCount: 0,
        fileSummaryCount: 0,
        classSummaryCount: 0,
        packageSummaryCount: 0,
        apiSummaryCount: 0,
      });
    } else {
      renderStats(overview || null);
    }
    setNotScannedBanner(isNotScanned(metadata || overview?.metadata), $('#repoPath')?.value);
  }

  function setNotScannedBanner(visible, path) {
    const banner = $('#notScannedBanner');
    if (!banner) return;
    if (!path?.trim()) {
      banner.hidden = true;
      return;
    }
    banner.hidden = !visible;
    const hint = $('#notScannedPath');
    if (hint) {
      hint.textContent = visible ? path.trim() : '';
    }
  }

  function renderStats(overview) {
    const grid = $('#statsGrid');
    if (!overview || isNotScanned(overview.metadata)) {
      grid.innerHTML = `
        <div class="stat-card">
          <div class="stat-label">Status</div>
          <div class="stat-value">Not scanned</div>
          <div class="stat-sub">Click Scan to index this repository</div>
        </div>
      `;
      return;
    }

    const m = overview.metadata || {};
    grid.innerHTML = `
      <div class="stat-card">
        <div class="stat-label">Files indexed</div>
        <div class="stat-value">${m.indexedFiles ?? 0}</div>
        <div class="stat-sub">of ${m.totalFiles ?? 0} total</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Graph nodes</div>
        <div class="stat-value">${overview.graphNodeCount ?? 0}</div>
        <div class="stat-sub">${overview.graphEdgeCount ?? 0} edges</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Summaries</div>
        <div class="stat-value">${overview.fileSummaryCount ?? 0}</div>
        <div class="stat-sub">${overview.classSummaryCount ?? 0} classes</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Last updated</div>
        <div class="stat-value" style="font-size:1rem">${formatDate(m.lastUpdatedAt || m.lastScannedAt).split(',')[0]}</div>
        <div class="stat-sub">${m.ollamaModel || 'auto model'}</div>
      </div>
    `;
  }

  async function loadPlugins() {
    try {
      const data = await apiGet('/plugins');
      const list = $('#pluginList');
      if (!data?.plugins?.length) {
        list.innerHTML = '<span class="empty-state" style="padding:0">No plugins discovered</span>';
        return;
      }
      list.innerHTML = data.plugins.map(p =>
        `<span class="plugin-pill ${p.enabled ? 'enabled' : ''}">${p.name}${p.enabled ? ' ✓' : ''}</span>`
      ).join('');
    } catch (e) {
      $('#pluginList').innerHTML = `<span class="text-muted">${e.message}</span>`;
    }
  }

  async function refreshDashboard() {
    const path = $('#repoPath').value.trim();
    if (!path) {
      updateStatusPill(null);
      renderStats(null);
      setNotScannedBanner(false);
      toast('Enter a repository path first', 'error');
      return;
    }

    state.repoPath = path;
    localStorage.setItem(STORAGE_KEY, path);

    setStatsLoading(true);
    let status = null;
    let overview = null;

    try {
      status = await apiGet(`/status?path=${encodePath(path)}`);
      if (status?.repositoryPath) syncRepoPath(status.repositoryPath);
    } catch (e) {
      updateStatusPill(null);
      setNotScannedBanner(false);
      toast(`Could not load status: ${e.message}`, 'error');
    }

    try {
      overview = await apiGet(`/memory?path=${encodePath($('#repoPath').value.trim())}`);
    } catch (e) {
      if (!status) {
        toast(`Could not load memory overview: ${e.message}`, 'error');
      }
    } finally {
      setStatsLoading(false);
    }

    applyDashboard(status || overview?.metadata, overview);
  }

  function setStatsLoading(loading) {
    if (loading) {
      $('#statsGrid').classList.add('loading');
    } else {
      $('#statsGrid').classList.remove('loading');
    }
  }

  // ── Ask AI ───────────────────────────────────────────────────

  function formatModelLabel(model) {
    const caps = model.capabilities?.length
      ? model.capabilities.join(', ')
      : 'unknown';
    return `${model.name} (${caps})`;
  }

  function isCompletionCapable(model) {
    if (model.capabilities?.includes('completion')) return true;
    if (model.capabilities?.length) return false;
    return !model.name.toLowerCase().includes('embed');
  }

  async function loadOllamaModels() {
    const select = $('#modelSelect');
    const status = $('#modelStatus');
    const saved = localStorage.getItem(MODEL_KEY) || '';

    try {
      status.textContent = 'Loading models...';
      status.className = 'model-status';

      const data = await apiGet('/ollama/models');
      const models = data?.models || [];

      select.innerHTML = '<option value="">Auto — pick first available</option>';
      models.forEach(model => {
        const opt = document.createElement('option');
        opt.value = model.name;
        opt.textContent = formatModelLabel(model);
        if (!isCompletionCapable(model)) {
          opt.dataset.embedOnly = 'true';
        }
        select.appendChild(opt);
      });

      if (saved && models.some(m => m.name === saved)) {
        select.value = saved;
      } else if (saved) {
        localStorage.removeItem(MODEL_KEY);
      }

      status.textContent = models.length
        ? `${models.length} model(s) installed`
        : 'No models found — run ollama pull <model>';
      status.className = models.length ? 'model-status ok' : 'model-status error';
    } catch (e) {
      select.innerHTML = '<option value="">Auto — pick first available</option>';
      status.textContent = e.message;
      status.className = 'model-status error';
    }
  }

  function selectedModel() {
    const value = $('#modelSelect').value.trim();
    localStorage.setItem(MODEL_KEY, value);
    return value || null;
  }

  function appendMessage(role, content, meta) {
    const welcome = $('.chat-welcome');
    if (welcome) welcome.remove();

    const div = document.createElement('div');
    div.className = `message ${role}`;

    let sourcesHtml = '';
    if (meta?.sourceFiles?.length) {
      sourcesHtml = `<div class="source-chips">${meta.sourceFiles.map(f =>
        `<span class="source-chip">${f}</span>`
      ).join('')}</div>`;
    }

    div.innerHTML = `
      <div class="message-bubble">${escapeHtml(content)}</div>
      ${sourcesHtml}
      <div class="message-meta">${meta?.model ? `via ${meta.model}` : ''}</div>
    `;

    $('#chatMessages').appendChild(div);
    div.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }

  function escapeHtml(text) {
    const d = document.createElement('div');
    d.textContent = text;
    return d.innerHTML.replace(/\n/g, '<br>');
  }

  async function askQuestion(question) {
    const path = repoPath();
    const model = selectedModel();
    appendMessage('user', question);

    try {
      const body = { path, question };
      if (model) body.model = model;

      const response = await withLoading(
        model ? `Thinking with ${model}...` : 'Thinking with Ollama...',
        () => apiPost('/ask', body)
      );
      appendMessage('assistant', response.answer, {
        sourceFiles: response.sourceFiles,
        model: response.model,
      });
      state.chatHistory.push({ question, response });
    } catch (e) {
      appendMessage('assistant', `Error: ${e.message}`);
      toast(e.message, 'error');
    }
  }

  // ── Graph ────────────────────────────────────────────────────

  async function renderGraph() {
    const path = repoPath();
    const depth = $('#graphDepth').value;
    const container = $('#graphContainer');

    container.innerHTML = '<div class="spinner" style="margin:2rem auto"></div>';

    try {
      const mermaidText = await apiGet(
        `/graph/mermaid?path=${encodePath(path)}&depth=${depth}`
      );

      container.innerHTML = `<pre class="mermaid">${mermaidText}</pre>`;
      mermaid.initialize({
        startOnLoad: false,
        theme: document.documentElement.dataset.theme === 'light' ? 'default' : 'dark',
        securityLevel: 'loose',
      });
      await mermaid.run({ nodes: container.querySelectorAll('.mermaid') });
    } catch (e) {
      container.innerHTML = `<p class="empty-state">${e.message}</p>`;
    }
  }

  // ── History ──────────────────────────────────────────────────

  async function loadHistory() {
    const path = repoPath();
    const timeline = $('#historyTimeline');

    try {
      const items = await apiGet(`/history?path=${encodePath(path)}&limit=20`);
      if (!items?.length) {
        timeline.innerHTML = '<p class="empty-state">No history yet.</p>';
        return;
      }
      timeline.innerHTML = items.map(item => `
        <div class="history-item">
          <div class="history-date">${formatDate(item.timestamp)}</div>
          <div class="history-body">
            <h4>${item.operation || 'update'} — ${item.filesChanged ?? 0} file(s)</h4>
            <p>${item.description || ''}${item.affectedFiles?.length ? ': ' + item.affectedFiles.slice(0, 5).join(', ') : ''}</p>
          </div>
        </div>
      `).join('');
    } catch (e) {
      timeline.innerHTML = `<p class="empty-state">${e.message}</p>`;
    }
  }

  // ── Docs ─────────────────────────────────────────────────────

  function loadDocsPreview() {
    const path = $('#repoPath').value.trim();
    if (!path) {
      toast('Enter a repository path first', 'error');
      return Promise.resolve();
    }

    const frame = $('#docsFrame');
    const placeholder = $('#docsPlaceholder');

    frame.src = `${API}/docs/html?path=${encodePath(path)}`;
    frame.onload = () => {
      try {
        const doc = frame.contentDocument;
        if (doc && doc.body?.innerText?.trim()) {
          frame.classList.add('loaded');
          placeholder.classList.add('hidden');
        }
      } catch {
        frame.classList.add('loaded');
        placeholder.classList.add('hidden');
      }
    };
    frame.onerror = () => {
      frame.classList.remove('loaded');
      placeholder.classList.remove('hidden');
    };
  }

  // ── Actions ──────────────────────────────────────────────────

  async function runAction(action) {
    const path = repoPath();

    const handlers = {
      scan: async () => {
        const result = await withLoading('Scanning repository (this may take a while)...', () =>
          apiPost('/scan', { path })
        );
        showActionResult(result);
        toast('Scan complete!', 'success');
        syncRepoPath(result?.repositoryPath);
        applyDashboard(result, null);
        await refreshDashboard();
      },
      resume: async () => {
        const result = await withLoading('Resuming scan...', () =>
          apiPost('/resume', { path })
        );
        showActionResult(result);
        toast('Scan resumed and completed', 'success');
        await refreshDashboard();
      },
      update: async () => {
        const result = await withLoading('Running incremental update...', () =>
          apiPost('/update', { path })
        );
        showActionResult(result);
        toast('Update complete', 'success');
        await refreshDashboard();
      },
      docs: async () => {
        const result = await withLoading('Generating documentation...', () =>
          apiPost('/docs', { path })
        );
        showActionResult(result);
        toast('Documentation generated', 'success');
        switchTab('docs');
        loadDocsPreview();
      },
      'docs-view': () => {
        switchTab('docs');
        loadDocsPreview();
      },
      export: async () => {
        const outputDir = $('#exportDir').value.trim();
        const body = outputDir ? { path, outputDir } : { path };
        const result = await withLoading('Exporting memory...', () =>
          apiPost('/export', body)
        );
        showActionResult(result);
        toast('Export complete', 'success');
      },
      'clear-cache': async () => {
        await withLoading('Clearing cache...', () =>
          apiDelete(`/cache?path=${encodePath(path)}`)
        );
        toast('Cache cleared', 'success');
        await refreshDashboard();
      },
      clean: async () => {
        if (!confirm('Delete all .ai-memory for this repository? This cannot be undone.')) return;
        await withLoading('Deleting memory...', () =>
          apiDelete(`/memory?path=${encodePath(path)}`)
        );
        toast('Memory deleted', 'success');
        updateStatusPill(null);
        renderStats(null);
      },
    };

    const handler = handlers[action];
    if (!handler) return;

    try {
      await handler();
    } catch (e) {
      toast(e.message, 'error');
      showActionResult({ error: e.message });
    }
  }

  // ── Theme ────────────────────────────────────────────────────

  function initTheme() {
    const saved = localStorage.getItem(THEME_KEY) || 'dark';
    document.documentElement.dataset.theme = saved;
  }

  function toggleTheme() {
    const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    localStorage.setItem(THEME_KEY, next);
  }

  // ── Init ─────────────────────────────────────────────────────

  function bindEvents() {
    $$('.nav-item').forEach(btn => {
      btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });

    $('#refreshBtn').addEventListener('click', () => {
      refreshDashboard().catch(e => toast(e.message, 'error'));
    });

    $('#repoPath').addEventListener('change', () => {
      state.repoPath = $('#repoPath').value.trim();
      localStorage.setItem(STORAGE_KEY, state.repoPath);
    });

    $('#askForm').addEventListener('submit', (e) => {
      e.preventDefault();
      const input = $('#questionInput');
      const q = input.value.trim();
      if (!q) return;
      input.value = '';
      askQuestion(q);
    });

    $$('[data-action]').forEach(btn => {
      btn.addEventListener('click', () => {
        const action = btn.dataset.action;
        if (action === 'ask-tab') switchTab('ask');
        else if (action === 'docs-tab') { switchTab('docs'); loadDocsPreview(); }
        else runAction(action);
      });
    });

    $('#graphRefreshBtn').addEventListener('click', () => renderGraph());
    $('#graphDepth').addEventListener('input', (e) => {
      $('#graphDepthVal').textContent = e.target.value;
    });

    $('#refreshModelsBtn').addEventListener('click', () => loadOllamaModels());
    $('#modelSelect').addEventListener('change', () => selectedModel());

    $('#themeToggle').addEventListener('click', toggleTheme);
  }

  function init() {
    initTheme();
    bindEvents();
    hideOverlay();

    if (state.repoPath) {
      $('#repoPath').value = state.repoPath;
      refreshDashboard().catch(() => {});
    } else {
      updateStatusPill(null);
      renderStats(null);
    }

    loadPlugins();
    loadOllamaModels().catch(() => {});
    mermaid.initialize({ startOnLoad: false, theme: 'dark' });
  }

  document.addEventListener('DOMContentLoaded', init);
})();
