/* Service worker: offline shell + runtime caching (scope = this directory). */
const CACHE_VERSION = 'v1';
const CACHE_PREFIX = 'habitbridge-';
const CACHE_NAME = `${CACHE_PREFIX}${CACHE_VERSION}`;
const SHELL_URL = new URL('./index.html', self.location).href;
const PRECACHE_URLS = [
  './',
  './index.html',
  './styles.css',
  './src/app.js',
  './src/data.js',
  './src/engine.js',
  './src/i18n.js',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png'
];
// Cross-origin files the app cannot start without; cached on install and served cache-first.
const CROSS_ORIGIN_URLS = [];

function putInCache(request, response) {
  if (!response || !response.ok || (response.type !== 'basic' && response.type !== 'cors')) {
    return Promise.resolve();
  }
  return caches.open(CACHE_NAME)
    .then((cache) => cache.put(request, response))
    .catch(() => {});
}

// Scripts/styles the shell references directly. Their (hashed) names change per build, and the first
// page load fetches them before this worker exists, so read them from the cached shell instead.
async function shellAssetUrls(cache) {
  const shell = await cache.match(SHELL_URL);
  if (!shell) return [];
  const html = await shell.text();
  const scope = self.registration.scope;
  const urls = new Set();
  for (const match of html.matchAll(/<(script|link)\b[^>]*>/gi)) {
    const tag = match[0];
    if (match[1].toLowerCase() === 'link' && !/\brel=["']?(stylesheet|modulepreload)\b/i.test(tag)) continue;
    const attr = tag.match(/\b(?:src|href)=["']([^"']+)["']/i);
    if (!attr) continue;
    const url = new URL(attr[1], SHELL_URL);
    if (url.origin === self.location.origin && url.href.startsWith(scope)) urls.add(url.href);
  }
  return [...urls];
}

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME);
    await cache.addAll(PRECACHE_URLS);
    const assets = await shellAssetUrls(cache);
    await Promise.allSettled(assets.map((url) => cache.add(url)));
    await Promise.allSettled(CROSS_ORIGIN_URLS.map((url) =>
      fetch(url, { mode: 'cors' }).then((response) => putInCache(url, response))));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
          .map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET' || request.headers.has('range')) return;
  const url = new URL(request.url);

  if (url.origin !== self.location.origin) {
    if (CROSS_ORIGIN_URLS.includes(url.href)) {
      event.respondWith(caches.match(url.href).then((cached) => cached || fetch(request)));
    }
    return;
  }
  if (!url.href.startsWith(self.registration.scope)) return;

  if (request.mode === 'navigate') {
    // Network-first for pages; fall back to the cached shell when offline.
    event.respondWith(
      fetch(request)
        .then((response) => {
          event.waitUntil(putInCache(request, response.clone()));
          return response;
        })
        .catch(() => caches.match(request)
          .then((cached) => cached || caches.match(SHELL_URL))
          .then((cached) => cached || Response.error()))
    );
    return;
  }

  // Stale-while-revalidate for static assets; the revalidation and cache write outlive the response.
  const network = fetch(request).then((response) => {
    event.waitUntil(putInCache(request, response.clone()));
    return response;
  });
  event.waitUntil(network.then(() => undefined, () => undefined));
  event.respondWith(
    caches.match(request)
      .then((cached) => cached || network)
      .catch(() => Response.error())
  );
});
