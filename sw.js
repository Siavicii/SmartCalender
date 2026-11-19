const CACHE_NAME = "smartcal-pwa-v25";
const ASSETS = ["./", "./index.html"];

self.addEventListener("install", e => {
  self.skipWaiting();
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(ASSETS).catch(() => {})));
});

self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  const url = new URL(e.request.url);
  if (url.origin === location.origin) {
    e.respondWith(
      fetch(e.request, { cache: "no-store" }).then(res => {
        if (res && res.status === 200) {
          const clone = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
        }
        return res;
      }).catch(() => caches.match(e.request))
    );
  }
});

self.addEventListener("notificationclick", e => {
  const n = e.notification;
  const isStatusNotif = n && n.tag === "smartcal-daily-status";
  const title = n.title;
  const opts = {
    body: n.body,
    icon: n.icon,
    badge: n.badge,
    tag: n.tag,
    renotify: false,
    silent: true,
    requireInteraction: true
  };
  n.close();
  e.waitUntil(
    Promise.all([
      isStatusNotif ? self.registration.showNotification(title, opts).catch(() => {}) : Promise.resolve(),
      clients.matchAll({ type: "window", includeUncontrolled: true }).then(list => {
        for (const client of list) {
          if ("focus" in client) return client.focus();
        }
        if (clients.openWindow) return clients.openWindow("./");
      })
    ])
  );
});
