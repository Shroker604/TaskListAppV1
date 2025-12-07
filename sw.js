// Service Worker - Enables PWA "Install" and Offline support
const CACHE_NAME = 'tasklist-v1';
const ASSETS = [
    './',
    './index.html',
    './manifest.json',
    './css/styles.css',
    './css/components.css',
    './css/animations.css',
    './js/app.js',
    './js/store.js',
    './js/render.js'
];

self.addEventListener('install', (e) => {
    e.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS))
    );
});

self.addEventListener('fetch', (e) => {
    e.respondWith(
        caches.match(e.request).then((response) => response || fetch(e.request))
    );
});
