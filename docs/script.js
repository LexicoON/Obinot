/* ============================================================
   Obinot landing page — interactions
   ============================================================ */

(function () {
  'use strict';

  // ---------- Theme toggle ----------
  const THEME_KEY = 'obinot-theme';
  const saved = localStorage.getItem(THEME_KEY);
  if (saved) {
    document.documentElement.setAttribute('data-theme', saved);
  }

  const themeBtn = document.getElementById('themeToggle');
  if (themeBtn) {
    themeBtn.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme') || 'dark';
      const next = current === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem(THEME_KEY, next);
    });
  }

  // ---------- Reveal on scroll ----------
  const revealTargets = document.querySelectorAll('[data-reveal]');
  if ('IntersectionObserver' in window && revealTargets.length) {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry, i) => {
          if (entry.isIntersecting) {
            // stagger if multiple in same row
            const siblings = Array.from(entry.target.parentElement?.children || [])
              .filter((el) => el.hasAttribute('data-reveal'));
            const idx = siblings.indexOf(entry.target);
            const delay = Math.max(0, idx) * 80;
            setTimeout(() => entry.target.classList.add('is-visible'), delay);
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -60px 0px' }
    );
    revealTargets.forEach((el) => io.observe(el));
  } else {
    // fallback: show everything
    revealTargets.forEach((el) => el.classList.add('is-visible'));
  }

  // ---------- Smooth scroll for hash links ----------
  document.querySelectorAll('a[href^="#"]').forEach((a) => {
    a.addEventListener('click', (e) => {
      const href = a.getAttribute('href');
      if (href && href.length > 1) {
        const target = document.querySelector(href);
        if (target) {
          e.preventDefault();
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }
    });
  });

  // ---------- Stagger feature cards ----------
  // We mark them with [data-lift] for the hover lift; we don't need JS for that.
  // But let's add a subtle parallax-on-mouse for the hero icon for extra polish.
  const heroIcon = document.querySelector('.hero-icon');
  if (heroIcon && window.matchMedia('(pointer: fine)').matches) {
    let raf = null;
    window.addEventListener('mousemove', (e) => {
      if (raf) return;
      raf = requestAnimationFrame(() => {
        const x = (e.clientX / window.innerWidth - 0.5) * 12;
        const y = (e.clientY / window.innerHeight - 0.5) * 12;
        heroIcon.style.transform = `translate(${x}px, ${y}px)`;
        raf = null;
      });
    });
  }

  // ---------- Console welcome ----------
  // (Pure easter egg for devs)
  /* eslint-disable no-console */
  console.log(
    '%cObinot',
    'background:linear-gradient(135deg,#B4A0FF,#00D9C0);color:#0F0F1A;padding:8px 16px;border-radius:8px;font-weight:800;font-size:18px;font-family:Inter,sans-serif;'
  );
  console.log(
    '%cAI voice notes for Android · github.com/LexicoON/Obinot',
    'color:#A6A6C2;font-family:Inter,sans-serif;'
  );
  /* eslint-enable no-console */
})();
