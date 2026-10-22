/* Obinot. — site behaviour: theme, language, nav, scroll animation, GitHub releases */
(function () {
  'use strict';
  var REPO = 'LexicoON/Obinot';
  var root = document.documentElement;
  var reduce = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

  function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } }

  /* ---------------- Theme ---------------- */
  var themeBtn = document.getElementById('themeToggle');
  if (themeBtn) themeBtn.addEventListener('click', function () {
    var next = root.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
    root.setAttribute('data-theme', next); store('obinot-theme', next);
    var m = document.querySelector('meta[name="theme-color"]'); if (m) m.content = next === 'light' ? '#fef7ff' : '#141218';
  });

  /* ---------------- Language (EN / ES) ---------------- */
  var ES = window.OBINOT_ES || {};
  var lang = 'en';
  function detect() {
    var saved = store('obinot-lang');
    if (saved === 'en' || saved === 'es') return saved;
    var l = (navigator.languages && navigator.languages[0]) || navigator.language || 'en';
    return /^es/i.test(l) ? 'es' : 'en';
  }
  function applyLang(l, persist) {
    lang = l;
    root.setAttribute('lang', l);
    document.querySelectorAll('[data-i18n]').forEach(function (el) {
      var html = el.hasAttribute('data-i18n-html');
      if (el.__en === undefined) el.__en = html ? el.innerHTML : el.textContent;
      var v = l === 'es' ? ES[el.getAttribute('data-i18n')] : el.__en;
      if (v === undefined) v = el.__en;
      if (html) el.innerHTML = v; else el.textContent = v;
    });
    document.querySelectorAll('[data-i18n-attrs]').forEach(function (el) {
      el.__enAttrs = el.__enAttrs || {};
      el.getAttribute('data-i18n-attrs').split(';').forEach(function (pair) {
        var i = pair.indexOf(':'), a = pair.slice(0, i), k = pair.slice(i + 1);
        if (el.__enAttrs[a] === undefined) el.__enAttrs[a] = el.getAttribute(a);
        el.setAttribute(a, l === 'es' && ES[k] !== undefined ? ES[k] : el.__enAttrs[a]);
      });
    });
    document.querySelectorAll('.seg [data-lang]').forEach(function (b) { b.setAttribute('aria-pressed', String(b.getAttribute('data-lang') === l)); });
    if (persist) store('obinot-lang', l);
    document.dispatchEvent(new CustomEvent('obinot:lang', { detail: l }));
  }
  document.querySelectorAll('.seg [data-lang]').forEach(function (b) {
    b.addEventListener('click', function () { applyLang(b.getAttribute('data-lang'), true); });
  });
  applyLang(detect(), false);

  /* ---------------- Nav ---------------- */
  var nav = document.getElementById('nav');
  var burger = document.getElementById('navBurger');
  var links = document.getElementById('navLinks');
  if (burger && links) {
    burger.addEventListener('click', function () {
      var open = !links.classList.contains('open');
      links.classList.toggle('open', open); burger.setAttribute('aria-expanded', String(open));
    });
    links.addEventListener('click', function (e) { if (e.target.closest('a')) { links.classList.remove('open'); burger.setAttribute('aria-expanded', 'false'); } });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') { links.classList.remove('open'); burger.setAttribute('aria-expanded', 'false'); } });
  }

  /* ---------------- Scroll: progress, nav shadow, parallax ---------------- */
  var bar = document.querySelector('.scroll-progress');
  var para = Array.prototype.slice.call(document.querySelectorAll('[data-parallax]'));
  var ticking = false;
  function onScroll() {
    var y = window.scrollY || 0;
    var max = document.documentElement.scrollHeight - window.innerHeight;
    if (bar) bar.style.setProperty('--p', max > 0 ? Math.min(1, y / max).toFixed(4) : 0);
    if (nav) nav.classList.toggle('scrolled', y > 10);
    if (!reduce && window.innerWidth > 900) {
      var vh = window.innerHeight;
      para.forEach(function (el) {
        var r = el.getBoundingClientRect();
        if (r.bottom < -200 || r.top > vh + 200) return;
        var center = r.top + r.height / 2 - vh / 2;
        var f = parseFloat(el.getAttribute('data-parallax')) || 0;
        el.style.setProperty('--py', (-center * f).toFixed(1));
      });
    }
    ticking = false;
  }
  window.addEventListener('scroll', function () { if (!ticking) { ticking = true; requestAnimationFrame(onScroll); } }, { passive: true });
  window.addEventListener('resize', function () { if (window.innerWidth <= 900) para.forEach(function (el) { el.style.removeProperty('--py'); }); onScroll(); });
  onScroll();

  /* ---------------- Reveal on scroll ---------------- */
  var targets = document.querySelectorAll('.reveal, .reveal-left, .reveal-right, .reveal-up');
  if ('IntersectionObserver' in window && !reduce) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
    }, { threshold: 0.12, rootMargin: '0px 0px -8% 0px' });
    targets.forEach(function (t) { io.observe(t); });
  } else targets.forEach(function (t) { t.classList.add('in'); });

  /* ---------------- Sticky "how it works" ---------------- */
  var steps = document.querySelectorAll('.step');
  var screens = document.querySelectorAll('.screen');
  var dots = document.querySelectorAll('.how-dots i');
  if (steps.length && 'IntersectionObserver' in window) {
    var so = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (!en.isIntersecting) return;
        var n = +en.target.getAttribute('data-step');
        steps.forEach(function (s, i) { s.classList.toggle('active', i === n); });
        screens.forEach(function (s, i) { s.classList.toggle('active', i === n); });
        dots.forEach(function (d, i) { d.classList.toggle('on', i === n); });
      });
    }, { rootMargin: '-45% 0px -45% 0px', threshold: 0 });
    steps.forEach(function (s) { so.observe(s); });
  }

  /* ---------------- GitHub releases ---------------- */
  var CACHE_KEY = 'obinot-releases-v1', TTL = 10 * 60 * 1000;
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

  function fetchReleases() {
    var cached = null;
    try { cached = JSON.parse(sessionStorage.getItem(CACHE_KEY) || 'null'); } catch (e) {}
    if (cached && Date.now() - cached.t < TTL) return Promise.resolve(cached.d);
    return fetch('https://api.github.com/repos/' + REPO + '/releases?per_page=30', { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function (d) {
        d = d.filter(function (r) {
          if (r.draft) return false;
          var major = parseInt(String(r.tag_name).replace(/^v/i, ''), 10);
          return !(/^binot/i.test(r.name || '')) && !(major < 2);   // Obinot releases only (2.x+)
        });
        try { sessionStorage.setItem(CACHE_KEY, JSON.stringify({ t: Date.now(), d: d })); } catch (e) {}
        return d;
      });
  }
  function apkOf(rel) {
    var a = (rel.assets || []).filter(function (x) { return /\.apk$/i.test(x.name); });
    if (!a.length) return null;
    return a.find(function (x) { return /universal|release/i.test(x.name); }) || a[0];
  }
  function fmtDate(iso) {
    try { return new Date(iso).toLocaleDateString(lang === 'es' ? 'es' : 'en', { year: 'numeric', month: 'long', day: 'numeric' }); } catch (e) { return iso.slice(0, 10); }
  }
  var T = {
    latest: { en: 'Latest', es: 'Última' },
    released: { en: 'Released', es: 'Publicada' },
    dl: { en: 'Download APK', es: 'Descargar APK' },
    gh: { en: 'View on GitHub', es: 'Ver en GitHub' },
    nonotes: { en: 'No release notes were written for this version.', es: 'Esta versión no tiene notas.' }
  };
  function t(k) { return T[k][lang]; }

  /* mini markdown renderer for release notes (escapes HTML first) */
  function inline(s) {
    s = esc(s);
    s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
    s = s.replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>').replace(/__([^_]+)__/g, '<b>$1</b>');
    s = s.replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<i>$2</i>');
    s = s.replace(/!\[[^\]]*\]\([^)]*\)/g, '');
    s = s.replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2" rel="noopener">$1</a>');
    s = s.replace(/(^|[\s(])(https?:\/\/[^\s<)]+)/g, '$1<a href="$2" rel="noopener">$2</a>');
    return s;
  }
  function md(src) {
    var lines = String(src || '').replace(/\r/g, '').split('\n'), out = [], i = 0, list = null;
    function closeList() { if (list) { out.push('</' + list + '>'); list = null; } }
    while (i < lines.length) {
      var ln = lines[i], m;
      if (/^```/.test(ln)) { closeList(); var buf = []; i++; while (i < lines.length && !/^```/.test(lines[i])) buf.push(lines[i++]); i++; out.push('<pre><code>' + esc(buf.join('\n')) + '</code></pre>'); continue; }
      if (/^\s*\|.*\|\s*$/.test(ln) && /^\s*\|[\s:|-]+\|\s*$/.test(lines[i + 1] || '')) {
        closeList();
        var head = ln.trim().replace(/^\||\|$/g, '').split('|'); i += 2; var rows = [];
        while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) { rows.push(lines[i].trim().replace(/^\||\|$/g, '').split('|')); i++; }
        out.push('<table><thead><tr>' + head.map(function (c) { return '<th>' + inline(c.trim()) + '</th>'; }).join('') + '</tr></thead><tbody>' +
          rows.map(function (r) { return '<tr>' + r.map(function (c) { return '<td>' + inline(c.trim()) + '</td>'; }).join('') + '</tr>'; }).join('') + '</tbody></table>');
        continue;
      }
      if ((m = /^(#{1,6})\s+(.*)$/.exec(ln))) { closeList(); var lvl = Math.min(5, Math.max(4, m[1].length + 2)); out.push('<h' + lvl + '>' + inline(m[2]) + '</h' + lvl + '>'); i++; continue; }
      if ((m = /^\s*[-*+]\s+(.*)$/.exec(ln))) { if (list !== 'ul') { closeList(); out.push('<ul>'); list = 'ul'; } out.push('<li>' + inline(m[1]) + '</li>'); i++; continue; }
      if ((m = /^\s*\d+[.)]\s+(.*)$/.exec(ln))) { if (list !== 'ol') { closeList(); out.push('<ol>'); list = 'ol'; } out.push('<li>' + inline(m[1]) + '</li>'); i++; continue; }
      if (/^\s*---+\s*$/.test(ln)) { closeList(); out.push('<hr>'); i++; continue; }
      if ((m = /^>\s?(.*)$/.exec(ln))) { closeList(); out.push('<blockquote>' + inline(m[1]) + '</blockquote>'); i++; continue; }
      if (!ln.trim()) { closeList(); i++; continue; }
      closeList(); out.push('<p>' + inline(ln) + '</p>'); i++;
    }
    closeList();
    return out.join('\n');
  }

  var state = { releases: null };
  function fillCommon(rels) {
    var latest = rels[0]; if (!latest) return;
    var apk = apkOf(latest);
    document.querySelectorAll('[data-download]').forEach(function (a) { a.href = apk ? apk.browser_download_url : latest.html_url; });
    document.querySelectorAll('[data-version]').forEach(function (n) { n.textContent = latest.tag_name; });
    document.querySelectorAll('[data-size]').forEach(function (n) { n.textContent = apk ? ' · ' + (apk.size / 1048576).toFixed(1) + ' MB' : ''; });
  }
  function highlights(body) {
    var items = [];
    String(body || '').split('\n').forEach(function (l) {
      var m = /^\s*[-*+]\s+(.*)$/.exec(l);
      if (m && items.length < 5) {
        var txt = m[1].replace(/\*\*|__|`/g, '').replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').trim();
        if (txt.length > 6) items.push(txt.length > 150 ? txt.slice(0, 147).replace(/\s+\S*$/, '') + '…' : txt);
      }
    });
    return items;
  }
  function renderHome(rels) {
    var card = document.getElementById('releaseCard'); if (!card || !rels[0]) return;
    var r = rels[0], notes = document.getElementById('relNotes'), fb = document.getElementById('relFallback');
    var badge = document.getElementById('relBadge'), d = document.getElementById('relDate');
    if (badge) { badge.hidden = false; badge.textContent = t('latest'); }
    if (d) d.textContent = t('released') + ' ' + fmtDate(r.published_at || r.created_at) + (r.name ? ' · ' + r.name : '');
    var hs = highlights(r.body);
    notes.innerHTML = hs.map(function (h) { return '<li>' + esc(h) + '</li>'; }).join('');
    if (fb) fb.hidden = hs.length > 0;
    if (fb && !hs.length) fb.textContent = t('nonotes');
  }
  function renderChangelog(rels) {
    var list = document.getElementById('clList'); if (!list) return;
    var html = rels.map(function (r, idx) {
      var apk = apkOf(r), body = (r.body || '').trim();
      return '<details class="cl-item"' + (idx === 0 ? ' open' : '') + '><summary>' +
        '<span class="cl-ver">' + esc(r.tag_name) + '</span>' +
        (idx === 0 ? '<span class="badge">' + t('latest') + '</span>' : '') +
        '<span class="cl-title">' + esc(r.name && r.name !== r.tag_name ? r.name : '') + '</span>' +
        '<span class="cl-date">' + esc(fmtDate(r.published_at || r.created_at)) + '</span></summary>' +
        '<div class="cl-body">' + (body ? md(body) : '<p>' + t('nonotes') + '</p>') +
        '<div class="cl-foot">' +
        (apk ? '<a class="btn btn-filled btn-sm" href="' + esc(apk.browser_download_url) + '">' + t('dl') + ' <span class="muted">' + (apk.size / 1048576).toFixed(1) + ' MB</span></a>' : '') +
        '<a class="btn btn-tonal btn-sm" href="' + esc(r.html_url) + '" rel="noopener">' + t('gh') + '</a></div></div></details>';
    }).join('');
    list.innerHTML = html;
    var note = document.getElementById('clLangNote'); if (note) note.hidden = false;
  }
  function renderAll() { if (!state.releases) return; fillCommon(state.releases); renderHome(state.releases); renderChangelog(state.releases); }

  var wantsReleases = document.querySelector('[data-download], [data-version], #releaseCard, #clList');
  if (wantsReleases) {
    fetchReleases().then(function (rels) { state.releases = rels; renderAll(); })
      .catch(function () {
        var e = document.getElementById('clError'); if (e) e.hidden = false;
        var l = document.getElementById('clList'); if (l) l.innerHTML = '';
        var fb = document.getElementById('relFallback'); if (fb) fb.hidden = true;
        var c = document.getElementById('releaseCard'); if (c) { var n = document.getElementById('relNotes'); if (n) n.innerHTML = ''; }
      });
    document.addEventListener('obinot:lang', renderAll);
  }
})();
