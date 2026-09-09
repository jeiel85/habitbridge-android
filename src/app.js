// Controller + rendering for the HabitBridge phone simulator.
// The policy engine (src/engine.js) never touches the DOM; this file owns the
// simulated clock, the phone screens, the side panels and persistence.

import { t, setLang, getLang } from './i18n.js';
import { APP_CATALOG, SHIELD_IDS, SWAP_TYPES, EMOTIONS, VOCAB, STRETCH_STEPS, DEFAULT_SETTINGS } from './data.js';
import {
  createState,
  appInfo,
  targetFor,
  evaluateContext,
  decide,
  record,
  stats,
  addUsage,
  usageToday,
  notesMarkdown,
  markNotesExported,
  setTarget,
  updateSettings,
  dateKey,
} from './engine.js';

const $ = (s) => document.querySelector(s);
const STORAGE = 'habitbridge-demo-v1';
const BASE_WEEKDAY = new Date(2026, 8, 8, 0, 0, 0, 0); // Tue 2026-09-08 (local)
const BASE_WEEKEND = new Date(2026, 8, 12, 0, 0, 0, 0); // Sat 2026-09-12

let state;
let sim; // { minute, weekend, meeting, speed, lang }
let screen = { kind: 'home' }; // home | app | interceptor | swap
let holdTimer = null;
let stretchTimer = null;
let toastTimer = null;

// ---------------------------------------------------------------------------
// Persistence
// ---------------------------------------------------------------------------

function load() {
  try {
    const raw = localStorage.getItem(STORAGE);
    if (raw) {
      const saved = JSON.parse(raw);
      state = createState(saved.state || {});
      sim = { minute: 615, weekend: false, meeting: false, speed: false, lang: navigator.language?.startsWith('ko') ? 'ko' : 'en', ...(saved.sim || {}) };
      return;
    }
  } catch {
    /* corrupt or unavailable storage: start fresh */
  }
  state = createState();
  sim = { minute: 615, weekend: false, meeting: false, speed: false, lang: navigator.language?.startsWith('ko') ? 'ko' : 'en' };
}

function save() {
  try {
    const { settings, targets, allowlist, logs, notes, usage, nextId } = state;
    localStorage.setItem(STORAGE, JSON.stringify({ state: { settings, targets, allowlist, logs, notes, usage, nextId }, sim }));
  } catch {
    /* storage unavailable: demo still works for the session */
  }
}

// ---------------------------------------------------------------------------
// Simulated clock
// ---------------------------------------------------------------------------

function now() {
  const base = sim.weekend ? BASE_WEEKEND : BASE_WEEKDAY;
  return base.getTime() + sim.minute * 60 * 1000;
}
function hhmm(minute) {
  return `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;
}
function advance(minutes) {
  sim.minute = (sim.minute + minutes) % 1440;
  $('#in-time').value = sim.minute;
  save();
  renderAll();
}
function speedFactor() {
  return sim.speed ? 5 : 1;
}

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------

function boot() {
  load();
  setLang(sim.lang);
  document.documentElement.lang = getLang();
  bind();
  renderStatic();
  renderAll();
}

function bind() {
  $('#btn-lang').addEventListener('click', () => {
    sim.lang = getLang() === 'ko' ? 'en' : 'ko';
    setLang(sim.lang);
    document.documentElement.lang = getLang();
    save();
    renderStatic();
    renderAll();
  });
  $('#in-time').addEventListener('input', (e) => {
    sim.minute = Number(e.target.value);
    save();
    renderAll();
  });
  $('#in-weekday').addEventListener('change', () => {
    sim.weekend = false;
    save();
    renderAll();
  });
  $('#in-weekend').addEventListener('change', () => {
    sim.weekend = true;
    save();
    renderAll();
  });
  $('#in-meeting').addEventListener('change', (e) => {
    sim.meeting = e.target.checked;
    save();
    renderAll();
  });
  $('#in-speed').addEventListener('change', (e) => {
    sim.speed = e.target.checked;
    save();
  });
  $('#presets').addEventListener('click', (e) => {
    const b = e.target.closest('[data-preset]');
    if (!b) return;
    const p = b.dataset.preset;
    const map = { commute: [490, false, false], work: [870, false, true], evening: [1215, false, false], night: [40, false, false], advance: null };
    if (p === 'advance') return advance(10);
    const [minute, weekend, meeting] = map[p];
    sim.minute = minute;
    sim.weekend = weekend;
    sim.meeting = meeting;
    $('#in-time').value = minute;
    $('#in-weekday').checked = !weekend;
    $('#in-weekend').checked = weekend;
    $('#in-meeting').checked = meeting;
    save();
    renderAll();
  });
  $('#apps').addEventListener('change', (e) => {
    const el = e.target;
    const pkg = el.dataset.pkg;
    if (!pkg) return;
    try {
      if (el.dataset.field === 'enabled') setTarget(state, pkg, { enabled: el.checked });
      if (el.dataset.field === 'shield') setTarget(state, pkg, { shield: el.value });
      if (el.dataset.field === 'quota') setTarget(state, pkg, { quotaMinutes: Math.max(0, Math.min(1440, Math.round(Number(el.value) || 0))) });
    } catch (err) {
      console.warn(err);
    }
    save();
    renderAll();
  });
  $('#apps').addEventListener('click', (e) => {
    const b = e.target.closest('[data-usage]');
    if (!b) return;
    addUsage(state, b.dataset.usage, 10, now());
    save();
    renderAll();
  });
  for (const [id, key] of [
    ['#in-sleep-start', 'sleepStart'],
    ['#in-sleep-end', 'sleepEnd'],
    ['#in-work-start', 'workStart'],
    ['#in-work-end', 'workEnd'],
  ]) {
    $(id).addEventListener('change', (e) => {
      try {
        updateSettings(state, { [key]: e.target.value });
      } catch {
        e.target.value = state.settings[key];
      }
      save();
      renderAll();
    });
  }
  $('#in-cooldown').addEventListener('change', (e) => {
    try {
      updateSettings(state, { cooldownMinutes: Math.round(Number(e.target.value)) });
    } catch {
      e.target.value = state.settings.cooldownMinutes;
    }
    save();
    renderAll();
  });
  $('#btn-card-png').addEventListener('click', downloadCard);
  $('#btn-card-copy').addEventListener('click', copyCardText);
  $('#btn-notes-md').addEventListener('click', downloadNotes);
  $('#btn-reset').addEventListener('click', () => {
    if (!confirm(t('panel.resetConfirm'))) return;
    const lang = sim.lang;
    try {
      localStorage.removeItem(STORAGE);
    } catch {
      /* ignore */
    }
    state = createState();
    sim = { minute: 615, weekend: false, meeting: false, speed: false, lang };
    screen = { kind: 'home' };
    $('#in-time').value = sim.minute;
    $('#in-weekday').checked = true;
    $('#in-meeting').checked = false;
    $('#in-speed').checked = false;
    save();
    renderAll();
  });
  $('#screen').addEventListener('click', onScreenClick);
  $('#screen').addEventListener('submit', (e) => {
    e.preventDefault();
    if (e.target.id === 'note-form') saveNote();
  });
}

function renderStatic() {
  $('#btn-lang').textContent = t('lang');
  $('#brand-sub').textContent = t('app.sub');
  $('#brand-tag').textContent = t('app.tagline');
  $('#howto').textContent = t('app.howto');
  $('#prototype-note').textContent = t('app.prototype');
  $('#link-docs').textContent = t('footer.docs');
  $('#link-repo').textContent = t('footer.repo');
  $('#h-context').textContent = t('panel.context');
  $('#l-time').textContent = t('panel.time');
  $('#l-weekday').textContent = t('panel.weekday');
  $('#l-weekend').textContent = t('panel.weekend');
  $('#l-meeting').textContent = t('panel.meeting');
  $('#l-speed').textContent = t('panel.speed');
  $('#l-nowmode').textContent = t('panel.nowMode');
  $('#l-nowshield').textContent = t('panel.nowShield');
  $('#h-dashboard').textContent = t('panel.dashboard');
  $('#h-apps').textContent = t('panel.apps');
  $('#l-allowlist').textContent = t('panel.allowlist');
  $('#h-settings').textContent = t('panel.settings');
  $('#l-sleep').textContent = t('set.sleep');
  $('#l-work').textContent = t('set.work');
  $('#l-cooldown').textContent = t('set.cooldown');
  $('#h-card').textContent = t('panel.card');
  $('#btn-card-png').textContent = t('card.download');
  $('#btn-card-copy').textContent = t('card.copy');
  $('#h-notes').textContent = t('panel.notes');
  $('#btn-reset').textContent = t('panel.reset');
  $('#presets').innerHTML = ['commute', 'work', 'evening', 'night']
    .map((p) => `<button type="button" class="chip-btn" data-preset="${p}">${esc(t('preset.' + p))}</button>`)
    .join('') + `<button type="button" class="chip-btn" data-preset="advance">${esc(t('panel.advance'))}</button>`;
  $('#in-time').value = sim.minute;
  $('#in-weekday').checked = !sim.weekend;
  $('#in-weekend').checked = sim.weekend;
  $('#in-meeting').checked = sim.meeting;
  $('#in-speed').checked = sim.speed;
}

// ---------------------------------------------------------------------------
// Rendering: panels
// ---------------------------------------------------------------------------

function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

function renderAll() {
  const n = now();
  $('#out-time').textContent = hhmm(sim.minute);
  $('#sb-time').textContent = hhmm(sim.minute);
  const ctx = evaluateContext(state.settings, n, { calendarBusy: sim.meeting });
  $('#v-nowmode').textContent = t('mode.' + ctx.mode);
  $('#v-nowshield').textContent = t('shield.' + ctx.shield);
  $('#v-modedesc').textContent = t(`mode.${ctx.mode}.desc`);
  $('#sb-mode').textContent = t('mode.' + ctx.mode);
  renderApps();
  renderSettings();
  renderDashboard();
  renderNotes();
  renderCard();
  renderScreen();
}

function renderApps() {
  const n = now();
  const rows = APP_CATALOG.filter((a) => !state.allowlist.includes(a.pkg))
    .map((a) => {
      const tgt = targetFor(state, a.pkg) || { enabled: false, shield: 'AUTO', quotaMinutes: 0 };
      const used = usageToday(state, a.pkg, n);
      return `<div class="app-row ${tgt.enabled ? '' : 'off'}">
        <label class="app-id"><input type="checkbox" data-pkg="${a.pkg}" data-field="enabled" ${tgt.enabled ? 'checked' : ''}><span class="dot" style="background:${a.color}">${a.glyph}</span><span>${esc(a.name)}</span></label>
        <select data-pkg="${a.pkg}" data-field="shield" aria-label="${esc(t('panel.appShield'))}">${['AUTO', ...SHIELD_IDS].map((s) => `<option value="${s}" ${tgt.shield === s ? 'selected' : ''}>${esc(t('shield.' + s))}</option>`).join('')}</select>
        <label class="quota"><span>${esc(t('panel.appQuota'))}</span><input type="number" min="0" max="1440" step="5" value="${tgt.quotaMinutes}" data-pkg="${a.pkg}" data-field="quota"></label>
        <span class="usage">${esc(t('panel.appUsage'))} <strong class="${tgt.quotaMinutes && used >= tgt.quotaMinutes ? 'over' : ''}">${used}</strong>${tgt.quotaMinutes ? `/${tgt.quotaMinutes}` : ''} <button type="button" class="mini" data-usage="${a.pkg}" title="+10 min">+10</button></span>
      </div>`;
    })
    .join('');
  $('#apps').innerHTML = rows;
  $('#v-allowlist').textContent = state.allowlist.map((p) => appInfo(p).name).join(', ');
}

function renderSettings() {
  const s = state.settings;
  $('#in-sleep-start').value = s.sleepStart;
  $('#in-sleep-end').value = s.sleepEnd;
  $('#in-work-start').value = s.workStart;
  $('#in-work-end').value = s.workEnd;
  $('#in-cooldown').value = s.cooldownMinutes;
  $('#v-fatigue').textContent = t('set.fatigue', { n: s.maxInterceptsPerHour });
}

function renderDashboard() {
  const n = now();
  const st = stats(state, n);
  const score = st.focusScore;
  const ratio = st.week.swapRatio;
  const recent = state.logs.filter((l) => l.at <= n).slice(-8).reverse();
  const swapTypeBars = SWAP_TYPES.map((k) => `<span class="tag">${esc(t(`swap.${k}.name`))} ${st.week.bySwapType[k] || 0}</span>`).join('');
  $('#dashboard').innerHTML = `
    <div class="focus">
      <div class="focus-score ${score === null ? 'none' : score >= 70 ? 'good' : score >= 40 ? 'mid' : 'low'}"><span class="num">${score === null ? '–' : score}</span><span class="lbl">${esc(t('dash.focus'))}</span></div>
      <div class="focus-side">
        <div class="ratio-bar" title="${esc(t('dash.ratio'))}"><div style="width:${ratio === null ? 0 : Math.round(ratio * 100)}%"></div></div>
        <div class="desc">${esc(t('dash.ratio'))} ${ratio === null ? '–' : Math.round(ratio * 100) + '%'} · ${esc(t('dash.streak', { n: st.streak }))}</div>
        <div class="desc small">${esc(t('dash.focusFormula'))}</div>
      </div>
    </div>
    <div class="kpis">
      ${kpi(t('dash.today'), st.today)}
      ${kpi(t('dash.week'), st.week)}
    </div>
    <div class="tags">${swapTypeBars}</div>
    <h3>${esc(t('dash.recent'))}</h3>
    ${recent.length === 0 ? `<p class="desc">${esc(t('dash.empty'))}</p>` : `<ul class="recent">${recent
      .map((l) => {
        const d = new Date(l.at);
        const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
        const what = l.action === 'SWAPPED' ? `${t('dash.action.SWAPPED')} · ${t(`swap.${l.swapType}.name`)}${l.emotion ? ' · ' + t('emotion.' + l.emotion) : ''}` : t('dash.action.' + l.action);
        return `<li class="act-${l.action}"><span class="when">${dateKey(l.at).slice(5)} ${time}</span><span class="who" style="color:${appInfo(l.pkg).color}">${esc(appInfo(l.pkg).name)}</span><span>${esc(what)}</span><span class="shield">${l.shield ? esc(l.shield) : ''}</span></li>`;
      })
      .join('')}</ul>`}`;
}

function kpi(label, s) {
  return `<div class="kpi"><div class="kpi-label">${esc(label)}</div>
    <div class="kpi-row"><span>${esc(t('dash.swaps'))}</span><strong>${s.swaps}</strong></div>
    <div class="kpi-row"><span>${esc(t('dash.exits'))}</span><strong>${s.exits}</strong></div>
    <div class="kpi-row"><span>${esc(t('dash.bypasses'))}</span><strong class="${s.bypasses ? 'warn' : ''}">${s.bypasses}</strong></div>
    <div class="kpi-row"><span>${esc(t('dash.saved'))}</span><strong>${esc(t('dash.min', { n: s.savedMinutes }))}</strong></div>
  </div>`;
}

function renderNotes() {
  const n = now();
  const todays = state.notes.filter((x) => dateKey(x.at) === dateKey(n));
  $('#notes').innerHTML = todays.length
    ? `<ul class="notes">${todays
        .map((x) => {
          const d = new Date(x.at);
          return `<li><span class="when">${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}</span>${esc(x.content)}<span class="desc"> · ${esc(appInfo(x.pkg).name)}</span></li>`;
        })
        .join('')}</ul>`
    : `<p class="desc">${esc(t('notes.empty'))}</p>`;
  $('#btn-notes-md').textContent = t('notes.download', { date: dateKey(n).replace(/-/g, '') });
  $('#btn-notes-md').disabled = todays.length === 0;
}

// ---------------------------------------------------------------------------
// Share card (canvas 1080x1920)
// ---------------------------------------------------------------------------

function renderCard() {
  const c = $('#card');
  const ctx = c.getContext('2d');
  const n = now();
  const st = stats(state, n);
  const W = 1080;
  const H = 1920;
  ctx.fillStyle = '#0b0f0e';
  ctx.fillRect(0, 0, W, H);
  // subtle grid
  ctx.strokeStyle = 'rgba(52,211,153,0.08)';
  ctx.lineWidth = 2;
  for (let y = 0; y < H; y += 80) {
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(W, y);
    ctx.stroke();
  }
  const mono = '"JetBrains Mono", "Cascadia Code", Consolas, "Courier New", monospace';
  ctx.fillStyle = '#34d399';
  ctx.font = `600 40px ${mono}`;
  ctx.fillText('~/habitbridge/report', 80, 140);
  ctx.fillStyle = '#e6f4ef';
  ctx.font = `700 64px ${mono}`;
  ctx.fillText(t('card.title'), 80, 250);
  ctx.fillStyle = '#7c9a8e';
  ctx.font = `400 36px ${mono}`;
  ctx.fillText(dateKey(n), 80, 310);

  // focus score ring
  const cx = W / 2;
  const cy = 700;
  const r = 260;
  ctx.lineWidth = 34;
  ctx.strokeStyle = '#16302a';
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.stroke();
  const score = st.focusScore;
  if (score !== null) {
    ctx.strokeStyle = score >= 70 ? '#34d399' : score >= 40 ? '#fbbf24' : '#f87171';
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + (Math.PI * 2 * score) / 100);
    ctx.stroke();
  }
  ctx.fillStyle = '#e6f4ef';
  ctx.textAlign = 'center';
  ctx.font = `700 200px ${mono}`;
  ctx.fillText(score === null ? '–' : String(score), cx, cy + 70);
  ctx.fillStyle = '#7c9a8e';
  ctx.font = `500 40px ${mono}`;
  ctx.fillText('FOCUS SCORE', cx, cy + 150);

  const rows = [
    [t('card.saved'), `${st.week.savedMinutes}m`],
    [t('card.ratio'), st.week.swapRatio === null ? '–' : `${Math.round(st.week.swapRatio * 100)}%`],
    [t('card.streak'), String(st.streak)],
  ];
  ctx.textAlign = 'left';
  let y = 1130;
  for (const [k, v] of rows) {
    ctx.fillStyle = '#7c9a8e';
    ctx.font = `500 36px ${mono}`;
    ctx.fillText(k, 120, y);
    ctx.fillStyle = '#e6f4ef';
    ctx.font = `700 96px ${mono}`;
    ctx.fillText(v, 120, y + 100);
    y += 200;
  }
  // swap type bars
  const total = Math.max(1, ...SWAP_TYPES.map((k) => st.week.bySwapType[k] || 0));
  y = 1740;
  SWAP_TYPES.forEach((k, i) => {
    const v = st.week.bySwapType[k] || 0;
    const x = 120 + i * 220;
    ctx.fillStyle = '#16302a';
    ctx.fillRect(x, y - 120, 160, 120);
    ctx.fillStyle = '#34d399';
    const h = Math.round((v / total) * 120);
    ctx.fillRect(x, y - h, 160, h);
    ctx.fillStyle = '#7c9a8e';
    ctx.font = `500 26px ${mono}`;
    ctx.fillText(k.replace('_', ' '), x, y + 40);
  });
  ctx.fillStyle = '#34d399';
  ctx.font = `500 32px ${mono}`;
  ctx.fillText(t('card.footer'), 120, 1860);
}

function downloadCard() {
  const c = $('#card');
  c.toBlob((blob) => {
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `habitbridge-report-${dateKey(now())}.png`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }, 'image/png');
}

async function copyCardText() {
  const st = stats(state, now());
  const text = `${t('card.title')} ${dateKey(now())}\nFocus Score ${st.focusScore ?? '–'} · ${t('card.saved')} ${st.week.savedMinutes}m · ${t('card.ratio')} ${st.week.swapRatio === null ? '–' : Math.round(st.week.swapRatio * 100) + '%'} · ${t('card.streak')} ${st.streak}\n#habitbridge`;
  try {
    await navigator.clipboard.writeText(text);
    toast(t('card.copied'));
  } catch {
    prompt(t('card.copy'), text);
  }
}

function downloadNotes() {
  const n = now();
  const md = notesMarkdown(state, n);
  markNotesExported(state, n);
  save();
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `Notes_${dateKey(n).replace(/-/g, '')}.md`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

// ---------------------------------------------------------------------------
// Phone screens
// ---------------------------------------------------------------------------

function renderScreen() {
  const el = $('#screen');
  el.className = 'screen ' + screen.kind;
  if (screen.kind === 'home') el.innerHTML = homeHtml();
  else if (screen.kind === 'app') el.innerHTML = appHtml();
  else if (screen.kind === 'interceptor') el.innerHTML = interceptorHtml();
  else if (screen.kind === 'swap') el.innerHTML = swapHtml();
}

function homeHtml() {
  const n = now();
  const icons = APP_CATALOG.map((a) => {
    const tgt = targetFor(state, a.pkg);
    const isTarget = tgt && tgt.enabled;
    const allowed = state.allowlist.includes(a.pkg);
    return `<button type="button" class="icon" data-open="${a.pkg}" aria-label="${esc(a.name)}">
      <span class="icon-img" style="background:${a.color}">${a.glyph}</span>
      <span class="icon-name">${esc(a.name)}</span>
      ${isTarget ? `<span class="badge">${esc(t('phone.target'))}</span>` : allowed ? `<span class="badge ok">${esc(t('phone.allow'))}</span>` : ''}
    </button>`;
  }).join('');
  const st = stats(state, n);
  return `<div class="home">
    <div class="clock">${hhmm(sim.minute)}<small>${dateKey(n)}</small></div>
    <div class="widget"><span class="w-k">HabitBridge</span><span class="w-v">${st.focusScore === null ? '–' : st.focusScore}</span><span class="w-k">Focus</span><span class="w-v">${st.today.savedMinutes}m</span><span class="w-k">saved</span></div>
    <div class="grid">${icons}</div>
  </div>`;
}

function appHtml() {
  const a = appInfo(screen.pkg);
  const tgt = targetFor(state, screen.pkg);
  const used = usageToday(state, screen.pkg, now());
  const reason = screen.reason
    ? screen.reason === 'cooldown'
      ? t('phone.allowReason.cooldown', { m: Math.floor(screen.cooldownSec / 60), s: String(screen.cooldownSec % 60).padStart(2, '0') })
      : t('phone.allowReason.' + screen.reason)
    : '';
  return `<div class="fakeapp" style="--c:${a.color}">
    <div class="fakeapp-bar"><span class="dot" style="background:${a.color}">${a.glyph}</span>${esc(a.name)}</div>
    <p class="fakeapp-title">${esc(t('phone.inApp', { app: a.name }))}</p>
    ${reason ? `<p class="reason">${esc(reason)}</p>` : ''}
    <div class="feed">${[1, 2, 3, 4].map(() => '<div class="post"><div class="ph"></div><div class="ln"></div><div class="ln short"></div></div>').join('')}</div>
    <p class="desc">${esc(t('phone.inAppHint'))}</p>
    <p class="usage-line">${esc(t('phone.usageToday', { n: used }))} ${tgt && tgt.quotaMinutes ? esc(t('phone.quota', { q: tgt.quotaMinutes })) : ''}</p>
    <div class="fakeapp-actions"><button type="button" data-scroll="5">${esc(t('phone.scroll5'))}</button><button type="button" class="primary" data-home="1">${esc(t('phone.backHome'))}</button></div>
  </div>`;
}

function interceptorHtml() {
  const d = screen.decision;
  const a = appInfo(screen.pkg);
  const left = screen.holdLeft;
  const done = left <= 0;
  const swaps = SWAP_TYPES.map(
    (k, i) => `<button type="button" class="swap-btn" data-swap="${k}" aria-label="${esc(t(`swap.${k}.name`))}"><span class="n">[${i + 1}]</span><span class="ic ic-${k}"></span><span class="tx"><strong>${esc(t(`swap.${k}.name`))}</strong><small>${esc(t(`swap.${k}.desc`))}</small></span></button>`,
  ).join('');
  let cont;
  if (!d.canBypass) cont = `<button type="button" class="term-btn locked" disabled>${esc(t('ic.continueLocked', { mode: t('mode.' + d.mode) }))}</button>`;
  else if (!done) cont = `<button type="button" class="term-btn" disabled>${esc(t('ic.continueWait', { s: left }))}</button>`;
  else cont = `<button type="button" class="term-btn" data-bypass="1">${esc(t('ic.continue'))}</button>`;
  const quota = d.quotaLeftMinutes === null ? '' : d.quotaLeftMinutes <= 0 ? t('ic.quotaOut') : t('ic.quotaLeft', { n: d.quotaLeftMinutes });
  return `<div class="term">
    <div class="term-head">${esc(t('ic.header'))}</div>
    <div class="term-body">
      <p class="term-title">${esc(t('ic.title'))}</p>
      <p class="kv"><span>${esc(t('ic.target'))}:</span> ${esc(a.pkg)}</p>
      <p class="kv"><span>${esc(t('ic.mode'))}  :</span> ${esc(t('mode.' + d.mode))} (${esc(d.shield)} Shield)</p>
      <p class="quote">&gt; ${esc(t('ic.quote'))}</p>
      <div class="hold ${done ? 'done' : ''}"><div class="hold-bar" style="width:${Math.round(((d.holdSeconds - Math.max(0, left)) / d.holdSeconds) * 100)}%"></div><span>[ ${done ? esc(t('ic.holdDone')) : `00:0${Math.max(0, left)}s ` + esc(t('ic.hold'))} ]</span></div>
      <p class="choose">${esc(t('ic.choose'))}</p>
      <div class="swaps">${swaps}</div>
      ${quota ? `<p class="quota-line ${d.quotaLeftMinutes <= 0 ? 'out' : ''}">${esc(quota)}</p>` : ''}
      ${d.canBypass ? `<p class="desc">${esc(t('ic.cooldownNote', { n: state.settings.cooldownMinutes }))}</p>` : ''}
    </div>
    <div class="term-foot"><button type="button" class="term-btn" data-exit="1">${esc(t('ic.exit'))}</button>${cont}</div>
  </div>`;
}

function swapHtml() {
  const k = screen.swap;
  let body = '';
  if (k === 'STRETCH') {
    const step = STRETCH_STEPS[screen.stepIndex];
    const total = STRETCH_STEPS.reduce((a, s) => a + s[0], 0);
    const elapsed = STRETCH_STEPS.slice(0, screen.stepIndex).reduce((a, s) => a + s[0], 0) + (step[0] - screen.stepLeft);
    body = screen.finished
      ? `<p class="big">✓</p><p class="lead">${esc(t('stretch.done'))}</p><button type="button" class="primary" data-swap-done="STRETCH">${esc(t('stretch.finish'))}</button>`
      : `<div class="ring" style="--p:${elapsed / total}"><span>${screen.stepLeft}</span></div>
         <p class="lead">${esc(t('stretch.' + step[1]))}</p>
         <p class="desc">${screen.stepIndex + 1}/${STRETCH_STEPS.length}</p>
         <button type="button" class="ghost" data-swap-cancel="1">${esc(t('stretch.skip'))}</button>`;
  } else if (k === 'VOCAB') {
    const v = VOCAB[screen.vocabIndex];
    body = `<div class="flash ${screen.flipped ? 'flipped' : ''}" data-flip="1" role="button" tabindex="0" aria-label="${esc(t('vocab.flip'))}"><div class="front"><strong>${esc(v.word)}</strong><small>${esc(t('vocab.flip'))}</small></div><div class="back"><strong>${esc(v.meaning)}</strong><em>${esc(v.example)}</em></div></div>
      <div class="row center"><button type="button" class="ghost" data-vocab-next="1">${esc(t('vocab.next'))}</button><button type="button" class="primary" data-swap-done="VOCAB" ${screen.flipped ? '' : 'disabled'}>${esc(t('vocab.got'))}</button></div>`;
  } else if (k === 'QUICK_NOTE') {
    body = `<form id="note-form" class="note-form"><textarea id="note-text" rows="4" maxlength="280" placeholder="${esc(t('note.placeholder'))}" required></textarea><p class="desc">${esc(t('note.hint'))}</p><button type="submit" class="primary">${esc(t('note.save'))}</button></form>`;
  } else if (k === 'EMOTION') {
    body = `<p class="lead">${esc(t('emotion.q'))}</p><div class="emotions">${EMOTIONS.map((e) => `<button type="button" data-emotion="${e}">${esc(t('emotion.' + e))}</button>`).join('')}</div>`;
  }
  return `<div class="swap-screen swap-${k}">
    <div class="swap-head"><button type="button" class="ghost" data-swap-back="1">${esc(t('ic.back'))}</button><span>${esc(t(`swap.${k}.name`))}</span></div>
    <div class="swap-body">${body}</div>
  </div>`;
}

// ---------------------------------------------------------------------------
// Phone interactions
// ---------------------------------------------------------------------------

function onScreenClick(e) {
  const el = e.target.closest('[data-open],[data-scroll],[data-home],[data-swap],[data-exit],[data-bypass],[data-swap-back],[data-swap-done],[data-swap-cancel],[data-flip],[data-vocab-next],[data-emotion]');
  if (!el) return;
  const ds = el.dataset;
  if (ds.open) return openApp(ds.open);
  if (ds.scroll) {
    addUsage(state, screen.pkg, Number(ds.scroll), now());
    advance(Number(ds.scroll));
    return;
  }
  if (ds.home) return goHome();
  if (ds.exit) return finishIntercept('DISMISSED_HOME');
  if (ds.bypass) return finishIntercept('BYPASSED');
  if (ds.swap) return startSwap(ds.swap);
  if (ds.swapBack) {
    stopStretch();
    screen = { kind: 'interceptor', pkg: screen.pkg, decision: screen.decision, holdLeft: 0 };
    return renderScreen();
  }
  if (ds.swapCancel) {
    stopStretch();
    screen = { kind: 'interceptor', pkg: screen.pkg, decision: screen.decision, holdLeft: 0 };
    return renderScreen();
  }
  if (ds.swapDone) return completeSwap(ds.swapDone, {});
  if (ds.flip) {
    screen.flipped = true;
    return renderScreen();
  }
  if (ds.vocabNext) {
    screen.vocabIndex = (screen.vocabIndex + 1) % VOCAB.length;
    screen.flipped = false;
    return renderScreen();
  }
  if (ds.emotion) return completeSwap('EMOTION', { emotion: ds.emotion });
}

function openApp(pkg) {
  const n = now();
  const d = decide(state, pkg, n, { calendarBusy: sim.meeting });
  if (d.action === 'ALLOW') {
    screen = { kind: 'app', pkg, reason: d.reason, cooldownSec: d.cooldownRemainingSec || 0 };
    renderScreen();
    return;
  }
  clearInterval(holdTimer);
  screen = { kind: 'interceptor', pkg, decision: d, holdLeft: d.holdSeconds };
  renderScreen();
  const tick = Math.round(1000 / speedFactor());
  holdTimer = setInterval(() => {
    if (screen.kind !== 'interceptor') return clearInterval(holdTimer);
    screen.holdLeft -= 1;
    if (screen.holdLeft <= 0) clearInterval(holdTimer);
    renderScreen();
  }, tick);
}

function goHome() {
  clearInterval(holdTimer);
  stopStretch();
  screen = { kind: 'home' };
  renderAll();
}

function finishIntercept(action) {
  clearInterval(holdTimer);
  const d = screen.decision;
  const pkg = screen.pkg;
  record(state, { pkg, action, shield: d.shield, mode: d.mode, now: now() });
  save();
  if (action === 'BYPASSED') {
    toast(t('phone.toast.bypassed', { n: state.settings.cooldownMinutes }));
    screen = { kind: 'app', pkg, reason: null };
    renderAll();
  } else {
    toast(t('phone.toast.exit', { n: state.settings.savedMinutesPerAvoid }));
    goHome();
  }
}

function startSwap(kind) {
  clearInterval(holdTimer);
  const base = { kind: 'swap', swap: kind, pkg: screen.pkg, decision: screen.decision };
  if (kind === 'STRETCH') {
    screen = { ...base, stepIndex: 0, stepLeft: STRETCH_STEPS[0][0], finished: false };
    renderScreen();
    stretchTimer = setInterval(() => {
      if (screen.kind !== 'swap' || screen.swap !== 'STRETCH') return stopStretch();
      screen.stepLeft -= 1;
      if (screen.stepLeft <= 0) {
        screen.stepIndex += 1;
        if (screen.stepIndex >= STRETCH_STEPS.length) {
          screen.finished = true;
          stopStretch();
        } else screen.stepLeft = STRETCH_STEPS[screen.stepIndex][0];
      }
      renderScreen();
    }, Math.round(1000 / speedFactor()));
    return;
  }
  if (kind === 'VOCAB') screen = { ...base, vocabIndex: Math.floor(Math.random() * VOCAB.length), flipped: false };
  else screen = base;
  renderScreen();
  if (kind === 'QUICK_NOTE') setTimeout(() => $('#note-text')?.focus(), 50);
}

function stopStretch() {
  clearInterval(stretchTimer);
  stretchTimer = null;
}

function saveNote() {
  const text = $('#note-text').value.trim();
  if (!text) return;
  completeSwap('QUICK_NOTE', { note: text });
}

function completeSwap(kind, extra) {
  stopStretch();
  const d = screen.decision;
  try {
    record(state, { pkg: screen.pkg, action: 'SWAPPED', swapType: kind, shield: d.shield, mode: d.mode, now: now(), ...extra });
  } catch (err) {
    console.warn(err);
    return;
  }
  save();
  toast(t('phone.toast.swapped', { n: state.settings.savedMinutesPerAvoid }));
  goHome();
}

function toast(text) {
  const el = $('#toast');
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.hidden = true), 2600);
}

// Keep unused imports referenced for clarity of the public surface.
void DEFAULT_SETTINGS;

boot();
