/* FinWise Premium UI — offline, no dependencies. Progressive enhancement only. */
(function () {
  'use strict';
  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------- Theme: light / dark — single source of truth ----------
     Preference (light|dark) is stored in localStorage under
     'finwise_theme'. The <html> attribute data-theme is ALWAYS the
     effective value (light|dark) so CSS stays simple. There is NO Auto
     mode: legacy 'auto'/unknown values migrate to 'light'.
     Server preference (user.theme) is exposed as window.__FW_SERVER_THEME
     or data-server-theme and only wins when no local preference exists. */
  var THEME_KEY = 'finwise_theme';
  function normalizePref(v) {
    if (v === 'light' || v === 'dark') return v;
    return null;
  }
  function migrateLegacyPref(v) {
    // 'auto' (legacy) and anything unknown become 'light'.
    if (v === 'auto') return 'light';
    return normalizePref(v);
  }
  function resolveEffective(pref) {
    return normalizePref(pref) || 'light';
  }
  function readStoredPref() {
    try {
      var raw = localStorage.getItem(THEME_KEY);
      var migrated = raw == null ? null : migrateLegacyPref(String(raw).trim().toLowerCase());
      if (migrated && migrated !== raw) {
        try { localStorage.setItem(THEME_KEY, migrated); } catch (e) {}
      }
      if (migrated) return migrated;
    } catch (e) {}
    return null;
  }
  function readServerPref() {
    if (typeof window.__FW_SERVER_THEME === 'string') {
      var s = migrateLegacyPref(window.__FW_SERVER_THEME.trim().toLowerCase());
      if (s) return s;
    }
    try {
      var attr = document.documentElement.getAttribute('data-server-theme');
      var n = attr == null ? null : migrateLegacyPref(String(attr).trim().toLowerCase());
      if (n) return n;
      // Back-compat: older pages render effective theme into data-theme.
      // Only trust it on first load when nothing else is stored.
      var dt = document.documentElement.getAttribute('data-theme');
      if (dt === 'light' || dt === 'dark') return dt;
    } catch (e) {}
    return null;
  }
  function currentPref() {
    // Effective pref = stored choice, else server choice, else Light.
    return readStoredPref() || readServerPref() || 'light';
  }
  function currentEffective() {
    var dt = null;
    try { dt = document.documentElement.getAttribute('data-theme'); } catch (e) {}
    if (dt === 'light' || dt === 'dark') return dt;
    return resolveEffective(currentPref());
  }
  /* Keep the attribute API ([data-theme]) and the class API (.dark / .light)
     in sync at all times so both selector systems theme the whole UI. */
  function syncThemeClasses(effective) {
    try {
      var root = document.documentElement;
      root.setAttribute('data-theme', effective);
      if (root.classList) {
        root.classList.toggle('dark', effective === 'dark');
        root.classList.toggle('light', effective === 'light');
      }
      try { root.style.colorScheme = effective; } catch (e) {}
    } catch (e) {}
  }
  // Parse-time sync (this file loads at end of <body>, before first paint in
  // practice): guarantees .dark/.light are present even before
  // DOMContentLoaded, complementing the <head> pre-paint bootstrap.
  try { syncThemeClasses(resolveEffective(currentPref())); } catch (e) {}
  /* Icon-only theme toggle art (Lucide-style stroke icons, no emoji/text).
     Spec: LIGHT mode shows the Moon, DARK mode shows the Sun. */
  var FW_MOON_SVG = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>';
  var FW_SUN_SVG = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>';
  window.__fwThemeIcon = function (effective) {
    return effective === 'dark' ? FW_SUN_SVG : FW_MOON_SVG;
  };
  function paintThemeButtons(effective, pref) {
    var btns = document.querySelectorAll('[data-fw-theme-btn]');
    var toLight = effective === 'dark';
    var label = trTheme(toLight ? 'switchToLight' : 'switchToDark',
      toLight ? 'Switch to light mode' : 'Switch to dark mode');
    btns.forEach(function (b) {
      var icon = b.querySelector('[data-fw-theme-icon]');
      var textLabel = b.querySelector('[data-fw-theme-label]');
      // Icon-only toggle: Moon in LIGHT, Sun in DARK. Never any text label.
      if (icon) { try { icon.innerHTML = window.__fwThemeIcon(effective); } catch (e) {} }
      else if (!textLabel) { b.textContent = effective === 'dark' ? '☀' : '☾'; }
      if (textLabel) { textLabel.remove(); }
      b.setAttribute('aria-label', label);
      b.setAttribute('title', label);
      b.setAttribute('data-fw-theme-state', effective);
      try { b.setAttribute('aria-pressed', effective === 'dark' ? 'true' : 'false'); } catch (e) {}
    });
    // Keep settings segmented control + radios in sync when present.
    try {
      document.querySelectorAll('[data-fw-theme-seg]').forEach(function (seg) {
        seg.querySelectorAll('[data-fw-theme-opt]').forEach(function (opt) {
          var on = opt.getAttribute('data-fw-theme-opt') === pref;
          opt.classList.toggle('on', on);
          opt.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
      });
      document.querySelectorAll('input[name="theme"][value]').forEach(function (r) {
        if (r.value === pref) { r.checked = true; }
      });
      var sel = document.querySelector('select[name="theme"]');
      if (sel && (pref === 'light' || pref === 'dark')) { sel.value = pref; }
    } catch (e) {}
  }
  function applyTheme(pref) {
    var p = normalizePref(pref) || currentPref();
    var effective = resolveEffective(p);
    syncThemeClasses(effective);
    try { localStorage.setItem(THEME_KEY, p); } catch (e) {}
    paintThemeButtons(effective, p);
    if (window.__fwChartsTheme) { try { window.__fwChartsTheme(effective); } catch (e) {} }
    try {
      document.dispatchEvent(new CustomEvent('finwise:theme', { detail: { pref: p, effective: effective } }));
    } catch (e) {}
    return { pref: p, effective: effective };
  }
  function persistThemeServer(pref) {
    // Persist best-effort (offline-safe). Server accepts light|dark.
    try {
      fetch('/account/update-theme', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ theme: pref }) }).catch(function () {});
    } catch (e) {}
  }
  // Early paint may already have run from the <head> bootstrap; re-apply on
  // DOMContentLoaded so buttons/charts/pref storage converge.
  document.addEventListener('DOMContentLoaded', function () { applyTheme(currentPref()); });
  window.fwGetTheme = function () { return { pref: currentPref(), effective: currentEffective() }; };
  window.fwSetTheme = function (pref) {
    var p = normalizePref(pref);
    if (!p) return null;
    var out = applyTheme(p);
    persistThemeServer(p);
    return out;
  };
  window.fwToggleTheme = function () {
    var cur = currentEffective();
    var next = cur === 'dark' ? 'light' : 'dark';
    var out = applyTheme(next);
    persistThemeServer(next);
    return out;
  };

  /* ---------- Sidebar: hover rail + drag resize (desktop), drawer (mobile) ---------- */
  var SB_WIDTH_KEY = 'finwise_sidebar_width';
  var SB_MIN_W = 180, SB_MAX_W = 460, SB_DEFAULT_W = 264;
  function isMobile() { return window.matchMedia('(max-width: 860px)').matches; }
  function sbRead(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function sbWrite(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  function cssWidth() {
    var v = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--fw-sidebar-w'), 10);
    return isFinite(v) && v > 0 ? v : SB_DEFAULT_W;
  }
  function applySidebarWidth(px) {
    if (px) document.documentElement.style.setProperty('--fw-sidebar-w', px + 'px');
    else document.documentElement.style.removeProperty('--fw-sidebar-w');
  }
  /* 'hover' = icon rail that opens while the pointer is on it and folds back
     when the pointer leaves. This is the default on every page load.
     'open'  = temporarily pinned open until the next navigation. */
  function syncSidebarButtons() {
    var hover = document.body.classList.contains('fw-sb-hover');
    var label = hover ? 'Collapse sidebar' : 'Pin sidebar open';
    document.querySelectorAll('.fw-sb-collapse, .fw-top .fw-iconbtn[onclick*="fwToggleSidebar"]').forEach(function (el) {
      if (el.classList.contains('fw-burger')) return;
      el.setAttribute('aria-label', label);
      el.title = label;
    });
  }
  window.fwSetSidebarMode = function (mode) {
    if (mode !== 'hover' && mode !== 'open') return;
    var b = document.body;
    b.classList.remove('fw-collapsed');
    b.classList.toggle('fw-sb-hover', mode === 'hover');
    syncSidebarButtons();
  };
  window.fwGetSidebarMode = function () {
    return document.body.classList.contains('fw-sb-hover') ? 'hover' : 'open';
  };
  window.fwToggleSidebar = function () {
    if (isMobile()) { document.body.classList.toggle('fw-sb-open'); return; }
    window.fwSetSidebarMode(window.fwGetSidebarMode() === 'hover' ? 'open' : 'hover');
  };
  window.fwCloseSidebar = function () { document.body.classList.remove('fw-sb-open'); };

  function initSidebarDrag() {
    var grip = document.getElementById('fwSbGrip');
    if (!grip) return;
    var dragging = false, startX = 0, startW = 0;

    function commit(w) {
      if (w < SB_MIN_W) {
        // Dragged down to rail size: go back to the hover rail.
        applySidebarWidth(0);
        sbWrite(SB_WIDTH_KEY, '');
        if (window.fwGetSidebarMode() !== 'hover') window.fwSetSidebarMode('hover');
        return;
      }
      applySidebarWidth(w);
      sbWrite(SB_WIDTH_KEY, String(w));
    }
    function endDrag() {
      if (!dragging) return;
      dragging = false;
      document.body.classList.remove('fw-sb-resizing');
      var raw = parseInt(document.documentElement.style.getPropertyValue('--fw-sidebar-w'), 10);
      if (isFinite(raw)) commit(raw);
    }

    grip.addEventListener('pointerdown', function (ev) {
      if (ev.button !== undefined && ev.button !== 0) return;
      if (isMobile()) return;
      dragging = true;
      startX = ev.clientX;
      startW = cssWidth();
      document.body.classList.add('fw-sb-resizing');
      try { grip.setPointerCapture(ev.pointerId); } catch (e) {}
      ev.preventDefault();
    });
    grip.addEventListener('pointermove', function (ev) {
      if (!dragging) return;
      var w = startW + (ev.clientX - startX);
      applySidebarWidth(Math.min(SB_MAX_W, Math.max(0, w)));
    });
    grip.addEventListener('pointerup', endDrag);
    grip.addEventListener('pointercancel', endDrag);
    grip.addEventListener('lostpointercapture', endDrag);
    grip.addEventListener('dblclick', function () {
      applySidebarWidth(0);
      sbWrite(SB_WIDTH_KEY, '');
    });
    grip.addEventListener('keydown', function (ev) {
      var step = ev.shiftKey ? 48 : 16;
      if (ev.key === 'ArrowRight') { commit(Math.min(SB_MAX_W, cssWidth() + step)); ev.preventDefault(); }
      else if (ev.key === 'ArrowLeft') { commit(cssWidth() - step); ev.preventDefault(); }
      else if (ev.key === 'Home' || ev.key === 'End') {
        applySidebarWidth(0);
        sbWrite(SB_WIDTH_KEY, '');
        ev.preventDefault();
      } else if (ev.key === 'Enter' || ev.key === ' ') {
        window.fwToggleSidebar();
        ev.preventDefault();
      }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (isMobile()) {
      // Mobile uses the overlay drawer; the desktop preference is left untouched.
      document.body.classList.remove('fw-collapsed', 'fw-sb-hover');
    } else {
      var savedW = parseInt(sbRead(SB_WIDTH_KEY), 10);
      if (isFinite(savedW) && savedW >= SB_MIN_W && savedW <= SB_MAX_W) applySidebarWidth(savedW);
      // Always start as the hover rail: it folds back when the mouse leaves.
      window.fwSetSidebarMode('hover');
    }
    initSidebarDrag();
    document.addEventListener('keydown', function (ev) { if (ev.key === 'Escape') window.fwCloseSidebar(); });
  });

  /* ---------- Greeting (follows the global language) ---------- */
  function trTheme(key, fallback) {
    try {
      if (window.FinwiseLanguage && typeof window.FinwiseLanguage.t === 'function') {
        var v = window.FinwiseLanguage.t(key);
        if (v && v !== key) return v;
      }
    } catch (e) {}
    return fallback;
  }
  window.fwGreeting = function () {
    var h = new Date().getHours();
    if (h < 12) return trTheme('goodMorning', 'Good morning');
    if (h < 17) return trTheme('goodAfternoon', 'Good afternoon');
    return trTheme('goodEvening', 'Good evening');
  };
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-fw-greeting]').forEach(function (el) { el.textContent = window.fwGreeting(); });
  });

  /* ---------- Toasts ---------- */
  function toasts() {
    var c = document.getElementById('fw-toasts');
    if (!c) { c = document.createElement('div'); c.id = 'fw-toasts'; c.setAttribute('aria-live', 'polite'); document.body.appendChild(c); }
    return c;
  }
  window.fwToast = function (title, msg, kind) {
    var c = toasts();
    var t = document.createElement('div');
    t.className = 'fw-toast ' + (kind || '');
    t.setAttribute('role', 'status');
    var icon = kind === 'ok' ? '✓' : kind === 'bad' ? '✕' : kind === 'warn' ? '⚠' : 'ℹ';
    t.innerHTML = '<div style="font-weight:800">' + icon + '</div><div><b></b><span></span></div><button aria-label="Dismiss">×</button>';
    t.querySelector('b').textContent = title || '';
    t.querySelector('span').textContent = msg || '';
    t.querySelector('button').addEventListener('click', function () { t.classList.add('out'); setTimeout(function () { t.remove(); }, 250); });
    c.appendChild(t);
    setTimeout(function () { t.classList.add('out'); setTimeout(function () { t.remove(); }, 300); }, 4200);
  };
  // Surface backend flash / param messages as toasts (keep inline alerts too)
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-fw-flash]').forEach(function (el) {
      var kind = el.getAttribute('data-fw-kind') || '';
      var title = kind === 'ok' ? 'Success' : kind === 'bad' ? 'Something went wrong' : 'Notice';
      if (el.textContent.trim()) window.fwToast(title, el.textContent.trim(), kind);
    });
  });

  /* ---------- Count-up numbers ---------- */
  document.addEventListener('DOMContentLoaded', function () {
    if (reduceMotion) return;
    document.querySelectorAll('[data-fw-count]').forEach(function (el) {
      var target = parseFloat(el.getAttribute('data-fw-count'));
      if (isNaN(target)) return;
      var prefix = el.getAttribute('data-fw-prefix') || '';
      var decimals = parseInt(el.getAttribute('data-fw-decimals') || '0', 10);
      var t0 = null, dur = 700;
      function fmt(v) { return prefix + v.toLocaleString('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }); }
      function step(ts) {
        if (!t0) t0 = ts;
        var p = Math.min(1, (ts - t0) / dur);
        var eased = 1 - Math.pow(1 - p, 3);
        el.textContent = fmt(target * eased);
        if (p < 1) requestAnimationFrame(step); else el.textContent = fmt(target);
      }
      // Only animate when visible
      if ('IntersectionObserver' in window) {
        var io = new IntersectionObserver(function (entries) {
          entries.forEach(function (en) { if (en.isIntersecting) { requestAnimationFrame(step); io.disconnect(); } });
        });
        io.observe(el);
      } else { requestAnimationFrame(step); }
    });
  });

  /* ---------- Animate progress bars + rings on view ---------- */
  document.addEventListener('DOMContentLoaded', function () {
    function animateProg(el) {
      var w = el.getAttribute('data-fw-w');
      if (w == null) return;
      if (reduceMotion) { el.style.width = w + '%'; return; }
      el.style.width = '0%';
      requestAnimationFrame(function () { requestAnimationFrame(function () { el.style.width = w + '%'; }); });
    }
    var progs = document.querySelectorAll('.fw-prog > i[data-fw-w]');
    if ('IntersectionObserver' in window) {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) { if (en.isIntersecting) { animateProg(en.target); io.unobserve(en.target); } });
      });
      progs.forEach(function (p) { io.observe(p); });
    } else { progs.forEach(animateProg); }

    document.querySelectorAll('[data-fw-ring]').forEach(function (ring) {
      var pct = Math.max(0, Math.min(100, parseFloat(ring.getAttribute('data-fw-ring')) || 0));
      var circ = 2 * Math.PI * 54;
      var fg = ring.querySelector('.fw-ring-fg');
      if (!fg) return;
      fg.style.strokeDasharray = circ + '';
      if (reduceMotion) { fg.style.strokeDashoffset = circ * (1 - pct / 100) + ''; return; }
      fg.style.strokeDashoffset = circ + '';
      var apply = function () { fg.style.transition = 'stroke-dashoffset .9s cubic-bezier(.2,.7,.3,1)'; fg.style.strokeDashoffset = circ * (1 - pct / 100) + ''; };
      if ('IntersectionObserver' in window) {
        var io2 = new IntersectionObserver(function (es) { es.forEach(function (en) { if (en.isIntersecting) { apply(); io2.disconnect(); } }); });
        io2.observe(ring);
      } else { apply(); }
    });
  });

  /* ---------- Client-side table search (progressive enhancement) ---------- */
  window.fwTableSearch = function (inputId, tableId) {
    var inp = document.getElementById(inputId), tbl = document.getElementById(tableId);
    if (!inp || !tbl) return;
    inp.addEventListener('input', function () {
      var q = inp.value.trim().toLowerCase();
      tbl.querySelectorAll('tbody tr').forEach(function (tr) {
        tr.style.display = !q || tr.textContent.toLowerCase().indexOf(q) !== -1 ? '' : 'none';
      });
    });
  };

  /* ---------- Chart.js premium defaults (theme-aware) ----------
     Single helper for every chart so ticks, grids, legends, titles and
     tooltips stay readable in BOTH themes and refresh live on toggle. */
  function chartColors(effective) {
    var dark = effective ? effective === 'dark'
      : (document.documentElement.getAttribute('data-theme') === 'dark'
        || (document.documentElement.classList && document.documentElement.classList.contains('dark')));
    return {
      dark: dark,
      tick: dark ? '#8ea0bb' : '#52627A',
      grid: dark ? 'rgba(148,163,184,.14)' : 'rgba(15,23,42,.10)',
      legend: dark ? '#CBD5E1' : '#52627A',
      title: dark ? '#F8FAFC' : '#172033',
      tooltipBg: dark ? '#151D2D' : '#FFFFFF',
      tooltipTitle: dark ? '#F8FAFC' : '#172033',
      tooltipBody: dark ? '#CBD5E1' : '#52627A',
      tooltipBorder: dark ? '#263247' : '#E2E8F0'
    };
  }
  window.fwChartThemeColors = chartColors;
  function refreshLiveCharts(c) {
    try {
      var instances = [];
      if (window.Chart && window.Chart.instances) {
        var inst = window.Chart.instances;
        instances = typeof inst.values === 'function' ? Array.from(inst.values())
          : Object.keys(inst).map(function (k) { return inst[k]; });
      }
      instances.forEach(function (ch) {
        try {
          if (!ch || !ch.options) return;
          applyChartThemeToOptions(ch.options, c);
          if (typeof ch.update === 'function') ch.update();
        } catch (e) {}
      });
    } catch (e) {}
  }
  function applyChartThemeToOptions(options, c) {
    options = options || {};
    if (!options.plugins) options.plugins = {};
    if (options.plugins.legend) {
      if (!options.plugins.legend.labels) options.plugins.legend.labels = {};
      if (options.plugins.legend.labels.color == null) options.plugins.legend.labels.color = c.legend;
    }
    if (options.plugins.title && options.plugins.title.display) {
      if (options.plugins.title.color == null) options.plugins.title.color = c.title;
    }
    if (!options.plugins.tooltip) options.plugins.tooltip = {};
    options.plugins.tooltip.backgroundColor = c.tooltipBg;
    options.plugins.tooltip.titleColor = c.tooltipTitle;
    options.plugins.tooltip.bodyColor = c.tooltipBody;
    options.plugins.tooltip.borderColor = c.tooltipBorder;
    options.plugins.tooltip.borderWidth = 1;
    if (!options.scales) options.scales = {};
    ['x', 'y'].forEach(function (ax) {
      if (options.scales[ax]) {
        if (!options.scales[ax].ticks) options.scales[ax].ticks = {};
        if (options.scales[ax].ticks.color == null) options.scales[ax].ticks.color = c.tick;
        if (!options.scales[ax].grid) options.scales[ax].grid = {};
        if (options.scales[ax].grid.color == null) options.scales[ax].grid.color = c.grid;
      }
    });
    return options;
  }
  window.__fwChartsTheme = function (effective) {
    if (!window.Chart) return;
    var c = chartColors(effective);
    Chart.defaults.color = c.tick;
    Chart.defaults.borderColor = c.grid;
    Chart.defaults.font.family = "'Inter', system-ui, sans-serif";
    try {
      Chart.defaults.plugins = Chart.defaults.plugins || {};
      Chart.defaults.plugins.legend = Chart.defaults.plugins.legend || {};
      Chart.defaults.plugins.legend.labels = Chart.defaults.plugins.legend.labels || {};
      Chart.defaults.plugins.legend.labels.color = c.legend;
      Chart.defaults.plugins.tooltip = Chart.defaults.plugins.tooltip || {};
      Chart.defaults.plugins.tooltip.backgroundColor = c.tooltipBg;
      Chart.defaults.plugins.tooltip.titleColor = c.tooltipTitle;
      Chart.defaults.plugins.tooltip.bodyColor = c.tooltipBody;
      Chart.defaults.plugins.tooltip.borderColor = c.tooltipBorder;
      Chart.defaults.plugins.tooltip.borderWidth = 1;
    } catch (e) {}
    refreshLiveCharts(c);
  };
  // Set defaults ASAP (page inline scripts create charts at parse time,
  // before DOMContentLoaded) and again once the DOM is ready.
  try { window.__fwChartsTheme(); } catch (e) {}
  document.addEventListener('DOMContentLoaded', function () { window.__fwChartsTheme(); });
  window.fwChartPalette = ['#4f46e5', '#059669', '#0284c7', '#d97706', '#dc2626', '#7c3aed', '#0d9488', '#e11d48', '#65a30d', '#9333ea'];
})();
