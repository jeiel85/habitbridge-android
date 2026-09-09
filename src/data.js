// Static product data. These values are the single source of truth for the
// web prototype and are mirrored in docs/PRD-SDD.md (§4 rules, §5 numbers).

export const SHIELDS = Object.freeze({
  SOFT: { id: 'SOFT', holdSeconds: 3, canBypass: true },
  NORMAL: { id: 'NORMAL', holdSeconds: 5, canBypass: true },
  HARDCORE: { id: 'HARDCORE', holdSeconds: 5, canBypass: false },
});
export const SHIELD_IDS = ['SOFT', 'NORMAL', 'HARDCORE'];

// Context modes, in priority order (first match wins).
export const MODES = Object.freeze({
  QUOTA: { id: 'QUOTA', shield: 'HARDCORE' }, // daily quota exhausted
  SLEEP: { id: 'SLEEP', shield: 'HARDCORE' },
  MEETING: { id: 'MEETING', shield: 'HARDCORE' },
  WORK: { id: 'WORK', shield: 'NORMAL' },
  FREE: { id: 'FREE', shield: 'SOFT' },
});

export const SWAP_TYPES = ['STRETCH', 'VOCAB', 'QUICK_NOTE', 'EMOTION'];
export const ACTIONS = ['SWAPPED', 'BYPASSED', 'DISMISSED_HOME'];
export const EMOTIONS = ['BORED', 'ANXIOUS', 'LONELY', 'HABIT', 'TIRED'];

export const DEFAULT_SETTINGS = Object.freeze({
  sleepStart: '23:00',
  sleepEnd: '07:00',
  workStart: '09:00',
  workEnd: '18:00',
  workDays: [1, 2, 3, 4, 5], // Mon..Fri (JS getDay)
  cooldownMinutes: 10, // after a bypass, no interception for this app
  swapGraceMinutes: 0, // after a swap the next open is intercepted again
  savedMinutesPerAvoid: 5, // estimated minutes saved per swap / exit-to-home
  maxInterceptsPerHour: 6, // anti friction-fatigue: beyond this, drop to SOFT
  focusScoreWeights: { swapRatio: 0.7, quotaAdherence: 0.3 },
});

// Demo app catalogue. Real Android builds read installed packages.
export const APP_CATALOG = [
  { pkg: 'com.instagram.android', name: 'Instagram', color: '#d62976', glyph: '◎', category: 'social' },
  { pkg: 'com.google.android.youtube', name: 'YouTube', color: '#ff0033', glyph: '▶', category: 'video' },
  { pkg: 'com.zhiliaoapp.musically', name: 'TikTok', color: '#111', glyph: '♪', category: 'video' },
  { pkg: 'com.twitter.android', name: 'X', color: '#222', glyph: '𝕏', category: 'social' },
  { pkg: 'com.reddit.frontpage', name: 'Reddit', color: '#ff4500', glyph: '☺', category: 'social' },
  { pkg: 'com.kakao.talk', name: 'KakaoTalk', color: '#fae100', glyph: '✉', category: 'messaging' },
  { pkg: 'com.google.android.apps.maps', name: 'Maps', color: '#34a853', glyph: '⌖', category: 'utility' },
  { pkg: 'com.android.chrome', name: 'Chrome', color: '#4285f4', glyph: '◍', category: 'browser' },
];

// Default targets: the three most-cited autopilot apps. Messaging/utility are
// allow-listed so emergencies never meet a shield (PRD §4.6).
export const DEFAULT_TARGETS = [
  { pkg: 'com.instagram.android', enabled: true, shield: 'AUTO', quotaMinutes: 30 },
  { pkg: 'com.google.android.youtube', enabled: true, shield: 'AUTO', quotaMinutes: 45 },
  { pkg: 'com.zhiliaoapp.musically', enabled: true, shield: 'AUTO', quotaMinutes: 20 },
  { pkg: 'com.twitter.android', enabled: false, shield: 'AUTO', quotaMinutes: 0 },
  { pkg: 'com.reddit.frontpage', enabled: false, shield: 'AUTO', quotaMinutes: 0 },
];
export const DEFAULT_ALLOWLIST = ['com.kakao.talk', 'com.google.android.apps.maps'];

// Tiny built-in vocab deck (demo). Production ships a licensed/CC deck (PRD §6.2).
export const VOCAB = [
  { word: 'epiphany', meaning: '갑작스러운 깨달음', example: 'She had an epiphany about her career.' },
  { word: 'serendipity', meaning: '뜻밖의 행운', example: 'Meeting her was pure serendipity.' },
  { word: 'resilient', meaning: '회복력 있는', example: 'Children are remarkably resilient.' },
  { word: 'meticulous', meaning: '꼼꼼한', example: 'He kept meticulous records.' },
  { word: 'candid', meaning: '솔직한', example: 'Give me your candid opinion.' },
  { word: 'ubiquitous', meaning: '어디에나 있는', example: 'Smartphones are ubiquitous.' },
  { word: 'lucid', meaning: '명료한', example: 'Her explanation was lucid.' },
  { word: 'tenacious', meaning: '끈질긴', example: 'A tenacious negotiator.' },
  { word: 'ephemeral', meaning: '덧없는', example: 'Fame is ephemeral.' },
  { word: 'pragmatic', meaning: '실용적인', example: 'A pragmatic approach to the problem.' },
  { word: 'deliberate', meaning: '의도적인', example: 'A deliberate choice, not a reflex.' },
  { word: 'restraint', meaning: '자제', example: 'He showed great restraint.' },
];

// 60-second posture reset script: [seconds, key]
export const STRETCH_STEPS = [
  [10, 'breathe'],
  [10, 'neckLeft'],
  [10, 'neckRight'],
  [10, 'shoulders'],
  [10, 'chest'],
  [10, 'eyes'],
];
