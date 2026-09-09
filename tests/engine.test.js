import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  createState,
  evaluateContext,
  decide,
  record,
  stats,
  focusScore,
  streakDays,
  notesMarkdown,
  addUsage,
  setTarget,
  updateSettings,
  inWindow,
  dateKey,
  PolicyError,
} from '../src/engine.js';

// Local-time constructor so tests do not depend on the machine time zone.
const at = (y, m, d, hh, mm = 0) => new Date(y, m - 1, d, hh, mm, 0, 0).getTime();
const IG = 'com.instagram.android';
const YT = 'com.google.android.youtube';
const KAKAO = 'com.kakao.talk';
const MIN = 60 * 1000;

test('time windows wrap midnight correctly', () => {
  assert.equal(inWindow(23 * 60 + 30, '23:00', '07:00'), true);
  assert.equal(inWindow(2 * 60, '23:00', '07:00'), true);
  assert.equal(inWindow(12 * 60, '23:00', '07:00'), false);
  assert.equal(inWindow(9 * 60, '09:00', '18:00'), true);
  assert.equal(inWindow(18 * 60, '09:00', '18:00'), false);
  assert.equal(inWindow(10 * 60, '10:00', '10:00'), false);
});

test('context: sleep > meeting > work > free', () => {
  const s = createState();
  const tue = (hh, mm = 0) => at(2026, 9, 8, hh, mm); // 2026-09-08 is a Tuesday
  assert.deepEqual(evaluateContext(s.settings, tue(23, 30)), { mode: 'SLEEP', shield: 'HARDCORE' });
  assert.deepEqual(evaluateContext(s.settings, tue(23, 30), { calendarBusy: true }), { mode: 'SLEEP', shield: 'HARDCORE' });
  assert.deepEqual(evaluateContext(s.settings, tue(10), { calendarBusy: true }), { mode: 'MEETING', shield: 'HARDCORE' });
  assert.deepEqual(evaluateContext(s.settings, tue(10)), { mode: 'WORK', shield: 'NORMAL' });
  assert.deepEqual(evaluateContext(s.settings, tue(20)), { mode: 'FREE', shield: 'SOFT' });
  const sat = at(2026, 9, 12, 10);
  assert.deepEqual(evaluateContext(s.settings, sat), { mode: 'FREE', shield: 'SOFT' });
});

test('decide: allowlist and non-targets are never intercepted', () => {
  const s = createState();
  const now = at(2026, 9, 8, 10);
  assert.equal(decide(s, KAKAO, now).action, 'ALLOW');
  assert.equal(decide(s, 'com.unknown.app', now).action, 'ALLOW');
  assert.equal(decide(s, 'com.twitter.android', now).action, 'ALLOW'); // disabled target
});

test('decide: AUTO shield follows context, fixed shield overrides it', () => {
  const s = createState();
  const work = at(2026, 9, 8, 10);
  const d1 = decide(s, IG, work);
  assert.equal(d1.action, 'INTERCEPT');
  assert.equal(d1.shield, 'NORMAL');
  assert.equal(d1.holdSeconds, 5);
  assert.equal(d1.canBypass, true);
  setTarget(s, IG, { shield: 'SOFT' });
  assert.equal(decide(s, IG, work).shield, 'SOFT');
  setTarget(s, IG, { shield: 'HARDCORE' });
  const d3 = decide(s, IG, work);
  assert.equal(d3.shield, 'HARDCORE');
  assert.equal(d3.canBypass, false);
});

test('decide: bypass starts a cool-down, swap does not', () => {
  const s = createState();
  const t0 = at(2026, 9, 8, 20);
  record(s, { pkg: IG, action: 'BYPASSED', now: t0 });
  const d = decide(s, IG, t0 + 5 * MIN);
  assert.equal(d.action, 'ALLOW');
  assert.equal(d.reason, 'cooldown');
  assert.equal(d.cooldownRemainingSec, 5 * 60);
  assert.equal(decide(s, IG, t0 + 10 * MIN).action, 'INTERCEPT');
  record(s, { pkg: IG, action: 'SWAPPED', swapType: 'STRETCH', now: t0 + 11 * MIN });
  assert.equal(decide(s, IG, t0 + 11 * MIN + 1000).action, 'INTERCEPT');
  // cool-down is per app
  assert.equal(decide(s, YT, t0 + 1 * MIN).action, 'INTERCEPT');
});

test('decide: exhausted daily quota forces HARDCORE with no bypass', () => {
  const s = createState();
  const now = at(2026, 9, 8, 20);
  addUsage(s, IG, 30, now); // quota is 30
  const d = decide(s, IG, now);
  assert.equal(d.mode, 'QUOTA');
  assert.equal(d.shield, 'HARDCORE');
  assert.equal(d.canBypass, false);
  assert.equal(d.quotaLeftMinutes, 0);
  // Quota resets on a new day
  assert.equal(decide(s, IG, now + 24 * 60 * MIN).shield, 'SOFT');
});

test('decide: friction fatigue guard drops NORMAL to SOFT after many intercepts in an hour', () => {
  const s = createState();
  const t0 = at(2026, 9, 8, 10);
  for (let i = 0; i < 6; i++) record(s, { pkg: IG, action: 'DISMISSED_HOME', now: t0 + i * MIN });
  const d = decide(s, IG, t0 + 7 * MIN);
  assert.equal(d.shield, 'SOFT');
  // but never softens HARDCORE contexts
  const night = at(2026, 9, 8, 23, 30);
  for (let i = 0; i < 6; i++) record(s, { pkg: YT, action: 'DISMISSED_HOME', now: night + i * MIN });
  assert.equal(decide(s, YT, night + 7 * MIN).shield, 'HARDCORE');
});

test('record validates input and stores quick notes', () => {
  const s = createState();
  const now = at(2026, 9, 8, 12);
  assert.throws(() => record(s, { pkg: IG, action: 'NOPE', now }), PolicyError);
  assert.throws(() => record(s, { pkg: IG, action: 'SWAPPED', swapType: 'DANCE', now }), PolicyError);
  assert.throws(() => record(s, { pkg: IG, action: 'SWAPPED', swapType: 'QUICK_NOTE', note: '   ', now }), (e) => e.code === 'emptyNote');
  record(s, { pkg: IG, action: 'SWAPPED', swapType: 'QUICK_NOTE', note: 'Idea: swap, do not block', now });
  assert.equal(s.notes.length, 1);
  assert.equal(s.notes[0].content, 'Idea: swap, do not block');
  const md = notesMarkdown(s, now);
  assert.match(md, /# HabitBridge Notes 2026-09-08/);
  assert.match(md, /\[12:00\] Idea: swap, do not block _\(instead of Instagram\)_/);
});

test('stats: swap ratio, saved minutes, focus score, streak', () => {
  const s = createState();
  const day = (d, hh) => at(2026, 9, d, hh);
  // three days of mostly good behaviour
  for (const d of [6, 7, 8]) {
    record(s, { pkg: IG, action: 'SWAPPED', swapType: 'VOCAB', now: day(d, 9) });
    record(s, { pkg: YT, action: 'DISMISSED_HOME', now: day(d, 13) });
    if (d === 7) record(s, { pkg: IG, action: 'BYPASSED', now: day(d, 21) });
  }
  const now = day(8, 22);
  const st = stats(s, now);
  assert.equal(st.today.swaps, 1);
  assert.equal(st.today.exits, 1);
  assert.equal(st.today.savedMinutes, 10);
  assert.equal(st.week.intercepts, 7);
  assert.equal(st.week.avoided, 6);
  assert.ok(Math.abs(st.week.swapRatio - 6 / 7) < 1e-9);
  // focus = 70 * 6/7 + 30 * 1 (no usage recorded → within quota)
  assert.equal(focusScore(s, now), Math.round(70 * (6 / 7) + 30));
  assert.equal(streakDays(s, now), 3);
  // quota adherence drops the score when usage exceeds quotas
  addUsage(s, IG, 31, now);
  addUsage(s, YT, 46, now);
  addUsage(s, 'com.zhiliaoapp.musically', 21, now);
  assert.equal(st.quotaAdherence, 1);
  assert.equal(stats(s, now).quotaAdherence, 0);
  assert.equal(focusScore(s, now), Math.round(70 * (6 / 7)));
});

test('focus score is null with no data and streak breaks on bypass-majority days', () => {
  const s = createState();
  const now = at(2026, 9, 8, 22);
  assert.equal(focusScore(s, now), null);
  record(s, { pkg: IG, action: 'BYPASSED', now: at(2026, 9, 7, 9) });
  record(s, { pkg: IG, action: 'BYPASSED', now: at(2026, 9, 7, 10) });
  record(s, { pkg: IG, action: 'SWAPPED', swapType: 'EMOTION', emotion: 'BORED', now: at(2026, 9, 7, 11) });
  record(s, { pkg: IG, action: 'SWAPPED', swapType: 'EMOTION', emotion: 'BORED', now: at(2026, 9, 8, 11) });
  assert.equal(streakDays(s, now), 1);
});

test('settings validation', () => {
  const s = createState();
  assert.throws(() => updateSettings(s, { sleepStart: '25:00' }), PolicyError);
  assert.throws(() => updateSettings(s, { cooldownMinutes: -1 }), PolicyError);
  assert.throws(() => setTarget(s, IG, { shield: 'ULTRA' }), PolicyError);
  assert.throws(() => setTarget(s, IG, { quotaMinutes: 1.5 }), PolicyError);
  updateSettings(s, { cooldownMinutes: 0 });
  const t0 = at(2026, 9, 8, 20);
  record(s, { pkg: IG, action: 'BYPASSED', now: t0 });
  assert.equal(decide(s, IG, t0 + 1000).action, 'INTERCEPT', 'zero cooldown means immediate re-interception');
  assert.equal(dateKey(at(2026, 1, 5, 0)), '2026-01-05');
});

test('stats ignore logs stamped after now (simulated clock moved backwards)', () => {
  const s = createState();
  record(s, { pkg: IG, action: 'SWAPPED', swapType: 'EMOTION', emotion: 'BORED', now: at(2026, 9, 8, 10, 15) });
  const earlier = at(2026, 9, 8, 0, 40);
  const st = stats(s, earlier);
  assert.equal(st.today.swaps, 0);
  assert.equal(st.week.swaps, 0);
  assert.equal(focusScore(s, earlier), null);
  const later = at(2026, 9, 8, 22);
  assert.equal(stats(s, later).today.swaps, 1);
});
