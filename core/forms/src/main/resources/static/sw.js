// Intentionally a no-op service worker.
//
// A previous revision installed an offline-cache SW that intercepted every
// navigation through a network-first/offline-fallback path. The interception
// added a measurable round-trip on top of every page load and surfaced
// stuck "pending" entries in DevTools' Network tab. We don't actually need
// offline support for an internal admin tool, so the worker now just
// installs, activates, claims clients, and never intercepts.
//
// Existing installations from the old version: the new install() below will
// take over thanks to skipWaiting + clients.claim, and the previous cache
// is purged during activate().

var STALE_CACHE = "offline-v1";

self.addEventListener("install", function () {
    self.skipWaiting();
});

self.addEventListener("activate", function (event) {
    event.waitUntil(
        caches.keys()
            .then(function (names) {
                return Promise.all(names.map(function (n) { return caches.delete(n); }));
            })
            .then(function () { return self.clients.claim(); })
    );
});

// No fetch handler - the browser uses its normal cache/network path,
// which is what we want.
