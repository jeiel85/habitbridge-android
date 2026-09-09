// HabitBridge policy engine (pure). No DOM, no Date.now(): every function takes
// `now` (ms epoch) so the same code drives the browser demo, the tests and, in
// the Android port, the Kotlin domain layer 1:1.

import { SHIELDS, MODES, DEFAULT_SETTINGS, DEFAULT_TARGETS, DEFAULT_ALLOWLIST, APP_CATALOG, SWAP_TYPES, ACTIONS } from './data.js';

export class PolicyError extends Error {
  constructor(code, detail) {
    super(code);
    this.code = code;
    this.detail = detail;
  }
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

export function createState(overrides = {}) {
  return {
    version: 1,
    settings: { ...DEFAULT_SETTINGS, ...(overrides.settings || {}) },
    targets: (overrides.targets || DEFAULT_TARGETS).map((t) => ({ ...t })),
    allowlist: [...(overrides.allowlist || DEFAULT_ALLOWLIST)],
    logs: [...(overrides.logs || [])], // { id, pkg, at, action, swapType?, note?, shield, mode }
    notes: [...(overrides.notes || [])], // { id, at, content, pkg, exported }
    usage: { ...(overrides.usage || {}) }, // usage[dateKey][pkg] = minutes in app today
    nextId: overrides.nextId || 1,
  };
}

export function appInfo(pkg) {
  return APP_CATALOG.find((a) => a.pkg === pkg) || { pkg, name: pkg, color: '#666', glyph: '?', category: 'other' };
}

export function targetFor(state, pkg) {
  return state.targets.find((t) => t.pkg === pkg) || null;
}

// ---------------------------------------------------------------------------
// Time helpers (local-time semantics via the Date object passed in)
// ---------------------------------------------------------------------------

export function dateKey(now) {
  const d = new Date(now);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

function minutesOfDay(now) {
  const d = new Date(now);
  return d.getHours() * 60 + d.getMinutes();
}

export function parseHHMM(s) {
  const [h, m] = String(s).split(':').map(Number);
  if (!Number.isInteger(h) || !Number.isInteger(m) || h < 0 || h > 23 || m < 0 || m > 59) {
    throw new PolicyError('badTime', s);
  }
  return h * 60 + m;
}

// Is `minute` inside [start, end) where the window may wrap midnight?
export function inWindow(minute, startHHMM, endHHMM) {
  const s = parseHHMM(startHHMM);
  const e = parseHHMM(endHHMM);
  if (s === e) return false;
  return s < e ? minute >= s && minute < e : minute >= s || minute < e;
}

// ---------------------------------------------------------------------------
// Context-aware smart shield
// ---------------------------------------------------------------------------

/**
 * Resolve the context mode for a moment in time.
 * @param {object} settings
 * @param {number} now epoch ms
 * @param {{calendarBusy?: boolean}} signals external signals (calendar, focus mode…)
 */
export function evaluateContext(settings, now, signals = {}) {
  const minute = minutesOfDay(now);
  const day = new Date(now).getDay();
  if (inWindow(minute, settings.sleepStart, settings.sleepEnd)) return { mode: 'SLEEP', shield: MODES.SLEEP.shield };
  if (signals.calendarBusy) return { mode: 'MEETING', shield: MODES.MEETING.shield };
  if (settings.workDays.includes(day) && inWindow(minute, settings.workStart, settings.workEnd)) {
    return { mode: 'WORK', shield: MODES.WORK.shield };
  }
  return { mode: 'FREE', shield: MODES.FREE.shield };
}

export function usageToday(state, pkg, now) {
  return (state.usage[dateKey(now)] || {})[pkg] || 0;
}

export function addUsage(state, pkg, minutes, now) {
  const key = dateKey(now);
  state.usage[key] = state.usage[key] || {};
  state.usage[key][pkg] = (state.usage[key][pkg] || 0) + minutes;
  return state.usage[key][pkg];
}

function lastLog(state, pkg, action) {
  for (let i = state.logs.length - 1; i >= 0; i--) {
    const l = state.logs[i];
    if (l.pkg === pkg && (!action || l.action === action)) return l;
  }
  return null;
}

function interceptsInLastHour(state, pkg, now) {
  const since = now - 60 * 60 * 1000;
  return state.logs.filter((l) => l.pkg === pkg && l.at > since && l.at <= now).length;
}

/**
 * Decide what happens when `pkg` comes to the foreground.
 * Returns ALLOW (no overlay) or INTERCEPT with the shield to apply.
 */
export function decide(state, pkg, now, signals = {}) {
  const s = state.settings;
  if (state.allowlist.includes(pkg)) return { action: 'ALLOW', reason: 'allowlist' };
  const target = targetFor(state, pkg);
  if (!target || !target.enabled) return { action: 'ALLOW', reason: 'notTarget' };

  const bypass = lastLog(state, pkg, 'BYPASSED');
  if (bypass) {
    const remaining = bypass.at + s.cooldownMinutes * 60 * 1000 - now;
    if (remaining > 0) return { action: 'ALLOW', reason: 'cooldown', cooldownRemainingSec: Math.ceil(remaining / 1000) };
  }
  const swap = lastLog(state, pkg, 'SWAPPED');
  if (swap && s.swapGraceMinutes > 0) {
    const remaining = swap.at + s.swapGraceMinutes * 60 * 1000 - now;
    if (remaining > 0) return { action: 'ALLOW', reason: 'swapGrace', cooldownRemainingSec: Math.ceil(remaining / 1000) };
  }

  const ctx = evaluateContext(s, now, signals);
  let mode = ctx.mode;
  let shieldId = target.shield === 'AUTO' ? ctx.shield : target.shield;

  const quotaLeft = target.quotaMinutes > 0 ? target.quotaMinutes - usageToday(state, pkg, now) : Infinity;
  if (quotaLeft <= 0) {
    mode = 'QUOTA';
    shieldId = MODES.QUOTA.shield;
  } else if (shieldId !== 'HARDCORE' && interceptsInLastHour(state, pkg, now) >= s.maxInterceptsPerHour) {
    // Friction fatigue guard: many opens in an hour means the shield is not
    // working as a pause any more; soften instead of nagging harder.
    shieldId = 'SOFT';
  }

  const shield = SHIELDS[shieldId];
  return {
    action: 'INTERCEPT',
    reason: mode === 'QUOTA' ? 'quota' : 'target',
    mode,
    shield: shield.id,
    holdSeconds: shield.holdSeconds,
    canBypass: shield.canBypass,
    quotaLeftMinutes: Number.isFinite(quotaLeft) ? quotaLeft : null,
  };
}

// ---------------------------------------------------------------------------
// Recording outcomes
// ---------------------------------------------------------------------------

export function record(state, { pkg, action, swapType = null, note = null, emotion = null, shield = null, mode = null, now }) {
  if (!ACTIONS.includes(action)) throw new PolicyError('badAction', action);
  if (action === 'SWAPPED' && !SWAP_TYPES.includes(swapType)) throw new PolicyError('badSwapType', swapType);
  if (typeof now !== 'number') throw new PolicyError('nowRequired');
  const entry = { id: state.nextId++, pkg, at: now, action, swapType, emotion, shield, mode };
  state.logs.push(entry);
  if (action === 'SWAPPED' && swapType === 'QUICK_NOTE') {
    const content = String(note || '').trim();
    if (!content) throw new PolicyError('emptyNote');
    state.notes.push({ id: state.nextId++, at: now, content, pkg, exported: false });
  }
  return entry;
}

// ---------------------------------------------------------------------------
// Statistics & Focus Score
// ---------------------------------------------------------------------------

function sameDay(a, b) {
  return dateKey(a) === dateKey(b);
}

export function summarize(state, logs) {
  const out = { swaps: 0, bypasses: 0, exits: 0, intercepts: logs.length, savedMinutes: 0, bySwapType: {}, byEmotion: {} };
  for (const l of logs) {
    if (l.action === 'SWAPPED') {
      out.swaps++;
      out.bySwapType[l.swapType] = (out.bySwapType[l.swapType] || 0) + 1;
      if (l.emotion) out.byEmotion[l.emotion] = (out.byEmotion[l.emotion] || 0) + 1;
    } else if (l.action === 'BYPASSED') out.bypasses++;
    else if (l.action === 'DISMISSED_HOME') out.exits++;
  }
  out.avoided = out.swaps + out.exits;
  out.savedMinutes = out.avoided * state.settings.savedMinutesPerAvoid;
  out.swapRatio = out.intercepts ? out.avoided / out.intercepts : null;
  return out;
}

export function quotaAdherence(state, now) {
  const quotas = state.targets.filter((t) => t.enabled && t.quotaMinutes > 0);
  if (!quotas.length) return 1;
  const ok = quotas.filter((t) => usageToday(state, t.pkg, now) <= t.quotaMinutes).length;
  return ok / quotas.length;
}

/**
 * Focus Score (0-100) = 70% swap ratio over the last 7 days + 30% quota adherence today.
 * Null when there is nothing to score yet.
 */
export function focusScore(state, now) {
  const week = state.logs.filter((l) => l.at > now - 7 * 24 * 3600 * 1000 && l.at <= now);
  const s = summarize(state, week);
  if (s.intercepts === 0) return null;
  const w = state.settings.focusScoreWeights;
  const score = 100 * (w.swapRatio * s.swapRatio + w.quotaAdherence * quotaAdherence(state, now));
  return Math.max(0, Math.min(100, Math.round(score)));
}

export function streakDays(state, now) {
  // Consecutive days (ending today or yesterday) with at least one avoided session and no bypass-majority.
  const byDay = new Map();
  for (const l of state.logs) {
    const k = dateKey(l.at);
    const d = byDay.get(k) || { avoided: 0, bypassed: 0 };
    if (l.action === 'BYPASSED') d.bypassed++;
    else d.avoided++;
    byDay.set(k, d);
  }
  let streak = 0;
  let cursor = now;
  const todayGood = (() => {
    const d = byDay.get(dateKey(now));
    return d && d.avoided > 0 && d.avoided >= d.bypassed;
  })();
  if (!todayGood) cursor -= 24 * 3600 * 1000; // today not (yet) counted; look back from yesterday
  for (let i = 0; i < 365; i++) {
    const d = byDay.get(dateKey(cursor));
    if (!d || d.avoided === 0 || d.avoided < d.bypassed) break;
    streak++;
    cursor -= 24 * 3600 * 1000;
  }
  return streak;
}

export function stats(state, now) {
  // Logs stamped after `now` are "not yet happened" (the demo clock can move backwards).
  const today = summarize(state, state.logs.filter((l) => sameDay(l.at, now) && l.at <= now));
  const week = summarize(state, state.logs.filter((l) => l.at > now - 7 * 24 * 3600 * 1000 && l.at <= now));
  return { today, week, focusScore: focusScore(state, now), streak: streakDays(state, now), quotaAdherence: quotaAdherence(state, now) };
}

// ---------------------------------------------------------------------------
// Markdown export (Quick Inspiration Log)
// ---------------------------------------------------------------------------

export function notesMarkdown(state, now, { dayOnly = true } = {}) {
  const key = dateKey(now);
  const notes = state.notes.filter((n) => !dayOnly || dateKey(n.at) === key);
  const lines = [`# HabitBridge Notes ${key}`, ''];
  for (const n of notes) {
    const d = new Date(n.at);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    lines.push(`- [${hh}:${mm}] ${n.content} _(instead of ${appInfo(n.pkg).name})_`);
  }
  if (notes.length === 0) lines.push('_No notes yet._');
  return lines.join('\n') + '\n';
}

export function markNotesExported(state, now) {
  const key = dateKey(now);
  let n = 0;
  for (const note of state.notes) {
    if (dateKey(note.at) === key && !note.exported) {
      note.exported = true;
      n++;
    }
  }
  return n;
}

// ---------------------------------------------------------------------------
// Settings mutation helpers (validated)
// ---------------------------------------------------------------------------

export function setTarget(state, pkg, patch) {
  let t = targetFor(state, pkg);
  if (!t) {
    t = { pkg, enabled: true, shield: 'AUTO', quotaMinutes: 0 };
    state.targets.push(t);
  }
  if (patch.shield !== undefined && !['AUTO', ...Object.keys(SHIELDS)].includes(patch.shield)) throw new PolicyError('badShield', patch.shield);
  if (patch.quotaMinutes !== undefined && !(Number.isInteger(patch.quotaMinutes) && patch.quotaMinutes >= 0 && patch.quotaMinutes <= 24 * 60)) {
    throw new PolicyError('badQuota', patch.quotaMinutes);
  }
  Object.assign(t, patch);
  return t;
}

export function updateSettings(state, patch) {
  for (const k of ['sleepStart', 'sleepEnd', 'workStart', 'workEnd']) if (patch[k] !== undefined) parseHHMM(patch[k]);
  if (patch.cooldownMinutes !== undefined && !(Number.isInteger(patch.cooldownMinutes) && patch.cooldownMinutes >= 0 && patch.cooldownMinutes <= 240)) {
    throw new PolicyError('badCooldown', patch.cooldownMinutes);
  }
  Object.assign(state.settings, patch);
  return state.settings;
}
