self.addEventListener("fetch", function (event) {
  if (event.request.method === "GET" && new URL(event.request.url).origin === self.location.origin) {
    event.respondWith(fetch(event.request));
  }
});
