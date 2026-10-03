/* Base do protótipo B+C: utilidades, ícones, gráficos e arte gerada (capas, cenas, avatares).
   Scripts clássicos: as declarações de topo são compartilhadas entre os arquivos. */
'use strict';
/* ============ utilidades ============ */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const norm = s => String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const nf = n => Number(n).toLocaleString('pt-BR');
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const store = {
  get(k, d) { try { const v = localStorage.getItem('xdr-' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('xdr-' + k, JSON.stringify(v)); } catch (e) { /* sem armazenamento: segue sem lembrar */ } },
};
function hash(str) { let h = 2166136261; for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
function rng(seed) { let s = seed >>> 0 || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
function gauss(r) { const u = Math.max(1e-9, r()), v = r(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v); }
function hexA(hex, a) { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`; }
function lum(hex) { const n = parseInt(hex.slice(1), 16); const f = c => { c /= 255; return c <= .03928 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }; return .2126 * f(n >> 16 & 255) + .7152 * f(n >> 8 & 255) + .0722 * f(n & 255); }
function mixHex(a, b, t) { const x = parseInt(a.slice(1), 16), y = parseInt(b.slice(1), 16); const m = (s) => Math.round(((x >> s) & 255) * (1 - t) + ((y >> s) & 255) * t); return '#' + [16, 8, 0].map(s => m(s).toString(16).padStart(2, '0')).join(''); }
function fmtMin(m) { const h = Math.floor(m / 60), r = m % 60; return h ? `${h} h${r ? ' ' + r + ' min' : ''}` : `${r} min`; }

/* ============ ícones (traço 24px) ============ */
const P = {
  play: '<path d="M8 5.5v13l10.5-6.5z" fill="currentColor" stroke="none"/>',
  star: '<path d="M12 3.6l2.55 5.2 5.7.83-4.12 4.02.97 5.68L12 16.64l-5.1 2.69.97-5.68L3.75 9.63l5.7-.83z"/>',
  starF: '<path d="M12 3.6l2.55 5.2 5.7.83-4.12 4.02.97 5.68L12 16.64l-5.1 2.69.97-5.68L3.75 9.63l5.7-.83z" fill="currentColor"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.3-4.3"/>',
  gear: '<circle cx="12" cy="12" r="3.2"/><path d="M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.5 5.5l1.85 1.85M16.65 16.65l1.85 1.85M5.5 18.5l1.85-1.85M16.65 7.35l1.85-1.85"/>',
  more: '<circle cx="12" cy="5.5" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="18.5" r="1.5" fill="currentColor" stroke="none"/>',
  back: '<path d="M14.5 5.5L8 12l6.5 6.5"/>',
  chevL: '<path d="M14.5 6L8.5 12l6 6"/>',
  chevR: '<path d="M9.5 6l6 6-6 6"/>',
  chevD: '<path d="M6 9.5l6 6 6-6"/>',
  grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.5"/>',
  home: '<path d="M4 11l8-6.5 8 6.5v8.5a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  layers: '<path d="M12 4l8.5 4.2L12 12.4 3.5 8.2z"/><path d="M3.5 12.2L12 16.4l8.5-4.2"/><path d="M3.5 16.2L12 20.4l8.5-4.2"/>',
  user: '<circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c1.2-3.7 4-5.6 7-5.6s5.8 1.9 7 5.6"/>',
  chip: '<rect x="6.5" y="6.5" width="11" height="11" rx="2"/><path d="M9.5 3v3.5M14.5 3v3.5M9.5 17.5V21M14.5 17.5V21M3 9.5h3.5M3 14.5h3.5M17.5 9.5H21M17.5 14.5H21"/>',
  bolt: '<path d="M13.2 3L5.5 13.4h5.6L10.4 21l8.1-10.6h-5.7z" fill="currentColor" stroke="none"/>',
  restart: '<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3"/><path d="M4.5 4.5v4h4"/>',
  reset: '<path d="M9 14.5L4 9.5l5-5"/><path d="M4 9.5h10a6 6 0 0 1 0 12h-3"/>',
  pin: '<path d="M9.5 3.5h5l-.8 5.2 3.3 3.3v2H7v-2l3.3-3.3z"/><path d="M12 14v6.5"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.2M12 7.7v.2"/>',
  warn: '<path d="M12 4.2l8.8 15.3H3.2z"/><path d="M12 10v4.2M12 17v.2"/>',
  chart: '<path d="M4 4v16h16"/><path d="M8.5 16v-4.5M12.5 16V8.5M16.5 16v-6"/>',
  patch: '<rect x="2.6" y="8.6" width="18.8" height="6.8" rx="3.4" transform="rotate(-45 12 12)"/><path d="M10.6 10.6h.01M13.4 13.4h.01M10.6 13.4h.01M13.4 10.6h.01"/>',
  box: '<path d="M4 7.8L12 4l8 3.8v8.4L12 20l-8-3.8z"/><path d="M4 7.8l8 3.9 8-3.9M12 11.7V20"/>',
  save: '<path d="M5 4.5h10.8L19.5 8v11a.5.5 0 0 1-.5.5H5a.5.5 0 0 1-.5-.5V5a.5.5 0 0 1 .5-.5z"/><path d="M8 4.5v4.5h7V4.5M8 19.5v-6h8v6"/>',
  trash: '<path d="M4.5 7h15M10 7V4.5h4V7M6.5 7l.9 12.5h9.2L17.5 7"/>',
  code: '<path d="M9 7.5L4.5 12 9 16.5M15 7.5l4.5 4.5-4.5 4.5"/>',
  wand: '<path d="M5 19L15.5 8.5"/><path d="M14 3.5v3M19.5 9h-3M18 5l-2 2M17.5 13v2.5M20.8 14.2h-2.5"/>',
  image: '<rect x="4" y="4" width="16" height="16" rx="2.2"/><circle cx="9.2" cy="9.4" r="1.6"/><path d="M20 15.5l-4.8-4.8L5 20"/>',
  link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  share: '<circle cx="6.5" cy="12" r="2.5"/><circle cx="17.5" cy="6" r="2.5"/><circle cx="17.5" cy="18" r="2.5"/><path d="M8.7 10.8l6.6-3.6M8.7 13.2l6.6 3.6"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  x: '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  disc: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.3"/>',
  zip: '<path d="M7 3.5h7l4 4V20a.5.5 0 0 1-.5.5h-11A.5.5 0 0 1 6 20V4a.5.5 0 0 1 .5-.5z"/><path d="M11 6h2M11 9h2M11 12h2M11 15v2.5h2V15z"/>',
  sliders: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
  folder: '<path d="M3.5 7a1.5 1.5 0 0 1 1.5-1.5h4.2l2 2H19a1.5 1.5 0 0 1 1.5 1.5v8.5A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5z"/>',
  gamepad: '<path d="M7.5 7.5h9a4.5 4.5 0 0 1 4.4 5.4l-.6 3a2.6 2.6 0 0 1-4.6 1.1L14.2 15H9.8l-1.5 2a2.6 2.6 0 0 1-4.6-1.1l-.6-3A4.5 4.5 0 0 1 7.5 7.5z"/><path d="M8 10.3v3M6.5 11.8h3"/><circle cx="15.3" cy="11" r=".9" fill="currentColor" stroke="none"/><circle cx="17.3" cy="13" r=".9" fill="currentColor" stroke="none"/>',
  speaker: '<path d="M4 9.5h3.5L12 5.8v12.4l-4.5-3.7H4z"/><path d="M15.5 9.2a4 4 0 0 1 0 5.6M18 6.7a7.5 7.5 0 0 1 0 10.6"/>',
  monitor: '<rect x="3" y="4.5" width="18" height="12" rx="2"/><path d="M8.5 20h7M12 16.5V20"/>',
  cpu: '<rect x="7" y="7" width="10" height="10" rx="1.5"/><path d="M10 3.5V7M14 3.5V7M10 17v3.5M14 17v3.5M3.5 10H7M3.5 14H7M17 10h3.5M17 14h3.5"/>',
  timeline: '<path d="M5 4v16"/><circle cx="5" cy="7" r="1.6" fill="currentColor" stroke="none"/><circle cx="5" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="5" cy="17" r="1.6" fill="currentColor" stroke="none"/><path d="M9 7h10M9 12h7M9 17h9"/>',
  download: '<path d="M12 4v11M7 10.5l5 5 5-5M5 20h14"/>',
  flask: '<path d="M9.5 3.5h5M10.5 3.5v6l-5 8.6A1.6 1.6 0 0 0 6.9 20.5h10.2a1.6 1.6 0 0 0 1.4-2.4l-5-8.6v-6"/><path d="M7.8 15h8.4"/>',
  shield: '<path d="M12 3.5l7 2.8v5.4c0 4.4-3 7.6-7 8.8-4-1.2-7-4.4-7-8.8V6.3z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
};
const ic = (n, s = 20) => `<svg class="ic" width="${s}" height="${s}" viewBox="0 0 24 24" aria-hidden="true">${P[n] || ''}</svg>`;


Object.assign(P, {
  wifi: '<path d="M3.5 9.5a12.5 12.5 0 0 1 17 0"/><path d="M6.5 12.8a8 8 0 0 1 11 0"/><path d="M9.5 16a3.6 3.6 0 0 1 5 0"/><circle cx="12" cy="19" r="1.1" fill="currentColor" stroke="none"/>',
  phone: '<rect x="7" y="3" width="10" height="18" rx="2.2"/><path d="M11 17.5h2"/>',
  battery: '<rect x="3.5" y="7.5" width="15" height="9" rx="2"/><path d="M21 10.5v3"/><path d="M6.5 10.5v3M9.5 10.5v3M12.5 10.5v3"/>',
  thermo: '<path d="M10 4.5a2 2 0 0 1 4 0v9.3a4 4 0 1 1-4 0z"/><path d="M12 9v6.5"/>',
  tv: '<rect x="3" y="5" width="18" height="12" rx="2"/><path d="M8 20.5h8"/><path d="M9 2.5l3 2.5 3-2.5"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/>',
  eyeOff: '<path d="M4 4l16 16"/><path d="M9.9 5.8A9.7 9.7 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.9 3.7M6.4 7.3A17 17 0 0 0 2.5 12S6 18.5 12 18.5a9.5 9.5 0 0 0 4.3-1"/>',
  move: '<path d="M12 3v18M3 12h18"/><path d="M9 5.5L12 3l3 2.5M9 18.5L12 21l3-2.5M5.5 9L3 12l2.5 3M18.5 9L21 12l-2.5 3"/>',
  magnet: '<path d="M6 4.5v7a6 6 0 0 0 12 0v-7"/><path d="M6 8h3.5M14.5 8H18"/><path d="M9.5 4.5v7a2.5 2.5 0 0 0 5 0v-7"/>',
  refresh: '<path d="M19.5 12a7.5 7.5 0 0 1-12.9 5.2"/><path d="M4.5 12a7.5 7.5 0 0 1 12.9-5.2"/><path d="M17.5 3v4h-4M6.5 21v-4h4"/>',
  cloud: '<path d="M7 18.5a4.5 4.5 0 0 1-.6-9 6 6 0 0 1 11.5 1.6A3.8 3.8 0 0 1 17.5 18.5z"/>',
  upload: '<path d="M12 20V9M7 13.5l5-5 5 5M5 4h14"/>',
  key: '<circle cx="8" cy="15" r="4"/><path d="M11 12l8.5-8.5M16 7l2.5 2.5M18.5 4.5l2 2"/>',
  bug: '<rect x="7" y="7.5" width="10" height="12" rx="5"/><path d="M12 7.5V19.5M3.5 13H7M17 13h3.5M5 8.5l2.5 1.5M19 8.5l-2.5 1.5M5 18l2.5-1.5M19 18l-2.5-1.5M9.5 7.5l-1-3M14.5 7.5l1-3"/>',
  ab: '<rect x="3" y="5" width="8" height="14" rx="2"/><rect x="13" y="5" width="8" height="14" rx="2"/><path d="M5.5 15l1.5-6 1.5 6M6 13h2M15.5 9v6h1.8a1.5 1.5 0 0 0 0-3H15.5h1.5a1.5 1.5 0 0 0 0-3z"/>',
  lock: '<rect x="5" y="10.5" width="14" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>',
  palette: '<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.4-1.3-1.8-1.3-3s1-2 2.2-2H18a2.5 2.5 0 0 0 2.5-2.5C20.5 7 16.7 3.5 12 3.5z"/><circle cx="7.5" cy="11" r="1.1" fill="currentColor" stroke="none"/><circle cx="10" cy="7.3" r="1.1" fill="currentColor" stroke="none"/><circle cx="14.5" cy="7.3" r="1.1" fill="currentColor" stroke="none"/>',
  spark: '<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M18.5 15.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>',
  flag: '<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>',
  pause: '<rect x="6.5" y="5" width="3.5" height="14" rx="1"/><rect x="14" y="5" width="3.5" height="14" rx="1"/>',
  exit: '<path d="M10 4.5H5.5a1 1 0 0 0-1 1v13a1 1 0 0 0 1 1H10"/><path d="M14.5 8l4 4-4 4M18.5 12H9"/>',
  mute: '<path d="M4 9.5h3.5L12 5.8v12.4l-4.5-3.7H4z"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>',
  vibrate: '<rect x="7.5" y="4" width="9" height="16" rx="2"/><path d="M4 9v6M2 10.5v3M20 9v6M22 10.5v3"/>',
  rotate: '<path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 4v4.5h-4.5"/>',
  hand: '<path d="M8.5 13V5.8a1.5 1.5 0 0 1 3 0V11M11.5 10.5V4.3a1.5 1.5 0 0 1 3 0V11M14.5 11V5.8a1.5 1.5 0 0 1 3 0v7.7a7 7 0 0 1-7 7h-.6a6 6 0 0 1-4.6-2.2L3.6 15.4a1.5 1.5 0 0 1 2.2-2l2.7 2.6"/>',
  split: '<rect x="4" y="3.5" width="16" height="17" rx="2"/><path d="M4 12h16"/>',
  globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.7 2.6 2.7 14.4 0 17M12 3.5c-2.7 2.6-2.7 14.4 0 17"/>',
  text: '<path d="M4 7V5h11v2M9.5 5v14M7.5 19h4"/><path d="M14 12v-1.5h7V12M17.5 10.5V19M16 19h3"/>',
  checkC: '<circle cx="12" cy="12" r="8.5"/><path d="M8 12.3l2.7 2.7L16 9.6"/>',
  xC: '<circle cx="12" cy="12" r="8.5"/><path d="M9 9l6 6M15 9l-6 6"/>',
  alert: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5v5.5M12 16.3v.2"/>',
  hud: '<rect x="3" y="4.5" width="18" height="15" rx="2"/><path d="M6.5 8.5h4M6.5 11.5h2.5"/><path d="M13 15.5l2-3 2 2 2-4"/>',
  scene: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 5v14M3 9h4M3 13h4M3 17h4"/>',
  sd: '<path d="M7 3.5h7.5L18 7v12.5a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1z"/><path d="M10 3.5v3.5M12.5 3.5v3.5M15 4v3"/>',
  inbox: '<path d="M3.5 13.5l2.6-7.2A1.5 1.5 0 0 1 7.5 5.3h9a1.5 1.5 0 0 1 1.4 1l2.6 7.2v4.7a1.5 1.5 0 0 1-1.5 1.5h-14A1.5 1.5 0 0 1 3.5 18.2z"/><path d="M3.5 13.5h4.5l1.5 2.5h5l1.5-2.5h4.5"/>',
  keyboard: '<rect x="2.5" y="6" width="19" height="12" rx="2"/><path d="M6 9.5h.01M9 9.5h.01M12 9.5h.01M15 9.5h.01M18 9.5h.01M6 12.5h.01M9 12.5h.01M12 12.5h.01M15 12.5h.01M18 12.5h.01M8 15.5h8"/>',
  message: '<path d="M4 5.5h16a1 1 0 0 1 1 1v9.5a1 1 0 0 1-1 1H9.5L5 20.5V17H4a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z"/><path d="M7.5 10h9M7.5 13h6"/>',
  star2: '<path d="M12 3.6l2.55 5.2 5.7.83-4.12 4.02.97 5.68L12 16.64l-5.1 2.69.97-5.68L3.75 9.63l5.7-.83z"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  minus: '<path d="M5 12h14"/>',
  dpad: '<path d="M9.5 3.5h5v6h6v5h-6v6h-5v-6h-6v-5h6z"/>',
});
/* ============ desempenho (histogramas como os do RunPerformance) ============ */
const PERF = {};
function perfOf(g) {
  if (!g.perf) return null; if (PERF[g.id]) return PERF[g.id];
  const p = g.perf, r = rng(hash(g.id + ':perf')), hist = new Array(61).fill(0), ft = new Array(51).fill(0);
  for (let i = 0; i < p.sec; i++) {
    let v = r() < p.lowP ? p.low + gauss(r) * Math.max(1.6, p.sd * 1.7) : p.t + gauss(r) * p.sd;
    v = Math.round(clamp(v, 1, p.cap)); hist[Math.min(60, v)]++;
    const base = 1000 / v;
    for (let j = 0; j < v; j++) { let f = base + gauss(r) * base * .06 + (r() < .003 ? base * 1.6 : 0); ft[clamp(Math.floor(f), 0, 50)]++; }
  }
  const sec = hist.reduce((a, b) => a + b, 0), frames = ft.reduce((a, b) => a + b, 0);
  const fpsP = f => { const w = Math.max(1, Math.ceil(f * sec)); let s = 0; for (let i = 0; i < hist.length; i++) { s += hist[i]; if (s >= w) return i; } return 60; };
  const ftU = f => { const w = Math.max(1, Math.ceil(f * frames)); let s = 0; for (let i = 0; i < ft.length; i++) { s += ft[i]; if (s >= w) return i >= 50 ? 50 : i + 1; } return 50; };
  return (PERF[g.id] = { hist, ft, sec, frames, p50: fpsP(.5), p5: fpsP(.05), ft50: ftU(.5), ft99: ftU(.99) });
}
/* Barras num SVG esticado (uma coluna por balde); rótulos e linhas em HTML, para o texto não escalar com o gráfico. */
function histChart(counts, hi, marks, ticks, label) {
  const n = counts.length, max = Math.max(...counts, 1);
  const bars = counts.map((c, i) => c ? `<rect x="${i + .12}" y="${(100 - 100 * c / max).toFixed(2)}" width=".76" height="${Math.max(1.2, 100 * c / max).toFixed(2)}"${i === hi ? ' class="hi"' : ''}/>` : '').join('');
  const pos = v => (v / n * 100).toFixed(2) + '%';
  const mk = marks.map(m => `<span class="mk${m.ref ? ' ref' : ''}" style="left:${pos(m.at)}"><i></i><b class="${m.side || ''}">${m.text}</b></span>`).join('');
  const ax = ticks.map((t, i) => `<span class="${i === 0 ? 'first' : i === ticks.length - 1 ? 'last' : ''}" style="left:${pos(t[0])}">${t[1]}</span>`).join('');
  return `<div class="hc" role="img" aria-label="${esc(label)}"><div class="hc-plot"><svg viewBox="0 0 ${n} 100" preserveAspectRatio="none" aria-hidden="true">${bars}</svg>${mk}</div><div class="hc-ax">${ax}</div></div>`;
}
function fpsSVG(st) {
  const near = Math.abs(st.p50 - st.p5) < 9, marks = [{ at: st.p50 + .5, text: 'mediana ' + st.p50, side: st.p50 > 50 ? 'r' : near ? 'l' : '' }];
  if (st.p5 !== st.p50) marks.push({ at: st.p5 + .5, text: '5%: ' + st.p5, ref: 1, side: near || st.p5 > 50 ? 'r' : '' });
  return histChart(st.hist, st.p50, marks, [[.5, '0'], [15.5, '15'], [30.5, '30'], [45.5, '45'], [60.5, '60 FPS']], `Segundos por FPS: mediana ${st.p50}; em 5% dos segundos, ${st.p5} ou menos`);
}
function ftSVG(st) {
  return histChart(st.ft, -1, [{ at: 16.7, text: '60 FPS', ref: 1 }, { at: 33.3, text: '30 FPS', ref: 1 }], [[0, '0'], [10, '10'], [20, '20'], [30, '30'], [40, '40'], [51, '50+ ms']], `Quadros por tempo de quadro: metade abaixo de ${st.ft50} ms, 99% abaixo de ${st.ft99} ms`);
}

/* ============ capas geradas (só para o protótipo) ============ */
const MOTIF = {
  rings(x, W, H, r, [a, b, h]) { const cx = W * .62, cy = H * .33; for (let i = 0; i < 10; i++) { x.beginPath(); x.arc(cx, cy, 20 + i * 21, 0, Math.PI * 2); x.strokeStyle = hexA(h, Math.max(.05, .6 - i * .055)); x.lineWidth = i % 3 ? 1.6 : 5; x.stroke(); } const g = x.createRadialGradient(cx, cy, 0, cx, cy, 90); g.addColorStop(0, hexA(h, .95)); g.addColorStop(1, hexA(h, 0)); x.fillStyle = g; x.fillRect(0, 0, W, H); },
  sun(x, W, H, r, [a, b, h]) { const cx = W * .5, cy = H * .4, R = 108; const g = x.createLinearGradient(0, cy - R, 0, cy + R); g.addColorStop(0, h); g.addColorStop(1, b); x.fillStyle = g; x.beginPath(); x.arc(cx, cy, R, 0, Math.PI * 2); x.fill(); x.fillStyle = a; for (let i = 0; i < 7; i++) x.fillRect(cx - R - 2, cy + 10 + i * 13, 2 * R + 4, 2 + i * 1.5); x.fillStyle = a; x.fillRect(0, cy + R * .92, W, H); x.strokeStyle = hexA(h, .55); x.lineWidth = 2; for (let i = -7; i <= 7; i++) { x.beginPath(); x.moveTo(cx, cy + R * .92); x.lineTo(cx + i * 64, H); x.stroke(); } for (let j = 1; j < 9; j++) { const y = cy + R * .92 + (H - cy - R * .92) * (j * j / 64); x.beginPath(); x.moveTo(0, y); x.lineTo(W, y); x.strokeStyle = hexA(h, .25); x.stroke(); } },
  mountains(x, W, H, r, [a, b, h]) { const g = x.createRadialGradient(W * .3, H * .34, 0, W * .3, H * .34, 100); g.addColorStop(0, hexA(h, 1)); g.addColorStop(.32, hexA(h, .7)); g.addColorStop(1, hexA(h, 0)); x.fillStyle = g; x.fillRect(0, 0, W, H); for (let l = 0; l < 4; l++) { x.beginPath(); const base = H * (.46 + l * .1); x.moveTo(0, H); x.lineTo(0, base); let px = 0; while (px < W) { px += 18 + r() * 42; x.lineTo(px, base - r() * (74 - l * 13)); } x.lineTo(W, H); x.closePath(); x.fillStyle = hexA(mixHex(a, '#000000', .1 * l), .5 + .14 * l); x.fill(); } },
  shards(x, W, H, r, [a, b, h]) { for (let i = 0; i < 26; i++) { x.beginPath(); const cx = r() * W, cy = r() * H * .8, s = 20 + r() * 95; x.moveTo(cx, cy); x.lineTo(cx + (r() - .5) * s * 2, cy + (r() - .2) * s * 2); x.lineTo(cx + (r() - .5) * s * 2, cy + (r() - .5) * s * 2); x.closePath(); x.fillStyle = hexA(i % 4 ? h : '#ffffff', .04 + r() * .2); x.fill(); } x.save(); x.translate(W * .5, H * .38); x.rotate(-.35); x.fillStyle = hexA(b, .9); x.fillRect(-W, -4, W * 2, 8); x.restore(); },
  grid(x, W, H, r, [a, b, h]) { const vx = W * .5, vy = H * .4; x.strokeStyle = hexA(h, .32); x.lineWidth = 1.2; for (let i = -10; i <= 10; i++) { x.beginPath(); x.moveTo(vx, vy); x.lineTo(vx + i * 60, H); x.stroke(); } for (let j = 1; j < 14; j++) { const y = vy + (H - vy) * (j * j / 196); x.beginPath(); x.moveTo(0, y); x.lineTo(W, y); x.stroke(); } let px = 0; while (px < W) { const w = 14 + r() * 32, hh = 40 + r() * 140; x.fillStyle = hexA('#000000', .55); x.fillRect(px, vy - hh, w, hh); for (let k = 0; k < 8; k++) { if (r() < .5) { x.fillStyle = hexA(h, .5); x.fillRect(px + 3 + r() * (w - 6), vy - hh + 6 + r() * (hh - 10), 2, 3); } } px += w + 2; } },
  bokeh(x, W, H, r, [a, b, h]) { for (let i = 0; i < 36; i++) { const cx = r() * W, cy = r() * H * .85, R = 6 + r() * 40; const g = x.createRadialGradient(cx, cy, 0, cx, cy, R); const c = i % 3 ? h : '#ffffff'; g.addColorStop(0, hexA(c, .1 + r() * .32)); g.addColorStop(1, hexA(c, 0)); x.fillStyle = g; x.beginPath(); x.arc(cx, cy, R, 0, 7); x.fill(); } },
  slash(x, W, H, r, [a, b, h]) { const g = x.createRadialGradient(W * .66, H * .3, 0, W * .66, H * .3, 92); g.addColorStop(0, hexA(b, 1)); g.addColorStop(1, hexA(b, 0)); x.fillStyle = g; x.fillRect(0, 0, W, H); x.save(); x.translate(W / 2, H * .42); x.rotate(-.52); for (let i = 0; i < 5; i++) { x.fillStyle = hexA(i === 2 ? h : '#ffffff', i === 2 ? .9 : .06 + i * .03); x.fillRect(-W, -5 + (i - 2) * 30, W * 2, i === 2 ? 9 : 2.5); } x.restore(); },
  bands(x, W, H, r, [a, b, h]) { for (let i = 0; i < 13; i++) { x.beginPath(); const y0 = H * .12 + i * 24; x.moveTo(0, y0); for (let px = 0; px <= W; px += 8) x.lineTo(px, y0 + Math.sin(px / 38 + i * .7) * 13); x.strokeStyle = hexA(h, .1 + i * .035); x.lineWidth = 3; x.stroke(); } },
};
function grain(x, W, H, r, a) { const img = x.getImageData(0, 0, W, H), d = img.data; for (let i = 0; i < d.length; i += 4) { const n = (r() - .5) * 255 * a; d[i] += n; d[i + 1] += n; d[i + 2] += n; } x.putImageData(img, 0, 0); }
function wrapLines(x, words, maxW) { const lines = []; let cur = ''; for (const w of words) { const t = cur ? cur + ' ' + w : w; if (x.measureText(t).width <= maxW || !cur) cur = t; else { lines.push(cur); cur = w; } } if (cur) lines.push(cur); return lines; }
function drawCover(g) {
  const W = 360, H = 480, c = document.createElement('canvas'); c.width = W; c.height = H; const x = c.getContext('2d'); const r = rng(hash(g.id)); const [a, b, h] = g.pal;
  const bg = x.createLinearGradient(0, 0, W * .35, H); bg.addColorStop(0, b); bg.addColorStop(1, a); x.fillStyle = bg; x.fillRect(0, 0, W, H);
  MOTIF[g.motif](x, W, H, r, g.pal);
  const lg = x.createRadialGradient(W * .8, H * .1, 10, W * .8, H * .1, W); lg.addColorStop(0, 'rgba(255,255,255,.14)'); lg.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = lg; x.fillRect(0, 0, W, H);
  const bf = x.createLinearGradient(0, H * .46, 0, H); bf.addColorStop(0, 'rgba(0,0,0,0)'); bf.addColorStop(1, 'rgba(0,0,0,.78)'); x.fillStyle = bf; x.fillRect(0, 0, W, H);
  grain(x, W, H, r, .07);
  const words = g.name.toUpperCase().split(' '); let size = 70, lines;
  for (; size >= 28; size -= 2) { x.font = `700 ${size}px "Barlow Semi Condensed","Arial Narrow",sans-serif`; lines = wrapLines(x, words, W - 52); if (lines.length <= 3 && lines.every(l => x.measureText(l).width <= W - 52)) break; }
  const lh = size * .94; let y = H - 40 - (lines.length - 1) * lh - (g.sub ? 18 : 0);
  x.fillStyle = '#ffffff'; x.shadowColor = 'rgba(0,0,0,.55)'; x.shadowBlur = 16; x.textBaseline = 'alphabetic';
  for (const l of lines) { x.fillText(l, 26, y); y += lh; }
  x.shadowBlur = 0;
  if (g.sub) { x.font = '600 15px "Barlow",sans-serif'; if ('letterSpacing' in x) x.letterSpacing = '4px'; x.fillStyle = h; x.fillText(g.sub.toUpperCase(), 27, H - 34); }
  x.fillStyle = hexA(h, .95); x.fillRect(26, 26, 34, 4);
  return c;
}
function drawIcon(g) {
  const N = 64, c = document.createElement('canvas'); c.width = N; c.height = N; const x = c.getContext('2d'); const [a, b, h] = g.pal;
  const gr = x.createLinearGradient(0, 0, N, N); gr.addColorStop(0, b); gr.addColorStop(1, a); x.fillStyle = gr; x.fillRect(0, 0, N, N);
  if (g.iconStyle === 'neon') { x.shadowColor = h; x.shadowBlur = 7; x.lineWidth = 3; x.strokeStyle = '#ff4fd8'; x.beginPath(); x.arc(32, 32, 22, 0, Math.PI * 2); x.stroke(); x.strokeStyle = h; x.beginPath(); x.moveTo(32, 13); x.lineTo(48, 45); x.lineTo(32, 37); x.lineTo(16, 45); x.closePath(); x.stroke(); }
  else if (g.iconStyle === 'pinata') { const cs = ['#ff5a8a', '#ffd84a', '#4ad6ff', '#7dff6a', '#ff8a3d']; for (let i = 0; i < 5; i++) { x.fillStyle = cs[i]; x.fillRect(14, 14 + i * 7, 34, 7); } x.fillStyle = cs[1]; x.fillRect(40, 7, 9, 10); x.fillStyle = '#2a0a2d'; x.fillRect(44, 10, 2, 2); x.fillStyle = cs[0]; x.fillRect(16, 49, 5, 8); x.fillRect(40, 49, 5, 8); }
  else { x.fillStyle = h; x.beginPath(); for (let i = 0; i < 10; i++) { const ang = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? 11 : 25; x.lineTo(32 + Math.cos(ang) * rr, 31 + Math.sin(ang) * rr); } x.closePath(); x.fill(); x.fillStyle = a; x.font = '800 13px "Barlow",sans-serif'; x.textAlign = 'center'; x.fillText('3', 32, 37); }
  return c;
}
const toURL = (c, type, q) => new Promise(res => c.toBlob(b => res(b ? URL.createObjectURL(b) : c.toDataURL()), type, q));

/* Cena 16:9 do jogo (sem título) para as telas em jogo; e avatares de perfil. */
function drawScene(g) {
  const W = 960, H = 540, c = document.createElement('canvas'); c.width = W; c.height = H; const x = c.getContext('2d'); const r = rng(hash(g.id + ':scene')); const [a, b, h] = g.pal;
  const bg = x.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, b); bg.addColorStop(1, a); x.fillStyle = bg; x.fillRect(0, 0, W, H);
  const m = g.motif || 'bokeh';
  x.save(); x.translate(W * .18, -H * .05); x.scale(1.7, 1.25); MOTIF[m](x, 360, 480, r, g.pal); x.restore();
  const v = x.createRadialGradient(W / 2, H / 2, H * .2, W / 2, H / 2, W * .7); v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(0,0,0,.6)'); x.fillStyle = v; x.fillRect(0, 0, W, H);
  grain(x, W, H, r, .05);
  x.strokeStyle = 'rgba(255,255,255,.55)'; x.lineWidth = 2; x.beginPath(); x.arc(W / 2, H / 2, 9, 0, 7); x.stroke(); x.beginPath(); x.moveTo(W / 2 - 18, H / 2); x.lineTo(W / 2 - 12, H / 2); x.moveTo(W / 2 + 12, H / 2); x.lineTo(W / 2 + 18, H / 2); x.stroke();
  return c;
}
function drawAvatar(p) {
  const N = 128, c = document.createElement('canvas'); c.width = N; c.height = N; const x = c.getContext('2d'); const r = rng(hash(p.tag));
  const g = x.createLinearGradient(0, 0, N, N); g.addColorStop(0, p.c[0]); g.addColorStop(1, p.c[1]); x.fillStyle = g; x.fillRect(0, 0, N, N);
  for (let i = 0; i < 5; i++) { x.fillStyle = 'rgba(255,255,255,' + (.06 + r() * .1) + ')'; x.beginPath(); x.arc(r() * N, r() * N, 14 + r() * 40, 0, 7); x.fill(); }
  x.fillStyle = 'rgba(255,255,255,.92)'; x.font = '700 54px "Barlow Semi Condensed","Arial Narrow",sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(p.tag.slice(0, 2).toUpperCase(), N / 2, N / 2 + 3);
  return c;
}
async function makeArt() {
  try { await Promise.race([Promise.all(['700 40px "Barlow Semi Condensed"', '600 15px "Barlow"'].map(f => document.fonts.load(f))), new Promise(r => setTimeout(r, 1800))]); } catch (e) { /* sem fonte: usa a reserva */ }
  await Promise.all(GAMES.map(async g => {
    if (g.iconStyle) g.icon = await toURL(drawIcon(g), 'image/png'); else g.cover = await toURL(drawCover(g), 'image/jpeg', .88);
    if (!g.iconStyle) g.scene = await toURL(drawScene(g), 'image/jpeg', .84); else g.scene = await toURL(drawScene(Object.assign({}, g, { motif: 'bokeh' })), 'image/jpeg', .84);
  }));
  await Promise.all(PROFILES.concat(PROFILE_TRASH).map(async p => { p.avatar = await toURL(drawAvatar(p), 'image/png'); }));
}
