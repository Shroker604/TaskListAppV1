// Service Worker - Enables PWA "Install" and Offline support
const CACHE_NAME = 'tasklist-v2-install-fix'; // Bumped version
const ASSETS = [
    './',
    './index.html',
    './manifest.json',
    './css/styles.css',
    './css/components.css',
    './css/animations.css',
    './js/app.js',
    './js/store.js',
    './js/render.js',
    './js/ai.js',
    './assets/icon-192.png',
    './assets/icon-512.png'
];

self.addEventListener('install', (e) => {
    self.skipWaiting(); // active immediately
    e.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS))
    );
});

self.addEventListener('activate', (e) => {
    e.waitUntil(
        caches.keys().then((keyList) => {
            return Promise.all(keyList.map((key) => {
                if (key !== CACHE_NAME) {
                    return caches.delete(key);
                }
            }));
        })
    );
    self.clients.claim();
});

self.addEventListener('fetch', (e) => {
    e.respondWith(
        caches.match(e.request).then((response) => response || fetch(e.request))
    );
});
