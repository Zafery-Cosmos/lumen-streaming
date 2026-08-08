#!/usr/bin/env python3
"""
Serveur de mises à jour Lumen.

- GET  /api/version              → le manifeste de la dernière version
- GET  /api/events               → SSE : notification INSTANTANÉE à la publication
- GET  /files/<nom>              → téléchargement du binaire (Range supporté)
- POST /api/publish              → publie une version et prévient tous les clients

Le push live évite d'attendre un redémarrage de l'app : dès qu'on publie,
toutes les instances connectées reçoivent l'événement et affichent le bandeau.
"""
import json
import os
import queue
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = "/opt/lumen-updates"
FILES = os.path.join(ROOT, "files")
MANIFEST = os.path.join(ROOT, "releases.json")
TOKEN = os.environ.get("LUMEN_PUBLISH_TOKEN", "lumen-publish")
PORT = int(os.environ.get("LUMEN_UPDATE_PORT", "8500"))

# Un abonné SSE = une file ; on diffuse à tout le monde à la publication.
subscribers = []
subscribers_lock = threading.Lock()


def read_manifest():
    if not os.path.exists(MANIFEST):
        return {"version": "0.0.0", "notes": [], "platforms": {}}
    with open(MANIFEST, encoding="utf-8") as f:
        return json.load(f)


def broadcast(payload):
    """Diffuse un événement à toutes les apps connectées, sans attendre."""
    with subscribers_lock:
        dead = []
        for q in subscribers:
            try:
                q.put_nowait(payload)
            except queue.Full:
                dead.append(q)
        for q in dead:
            subscribers.remove(q)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass  # silence : les logs utiles passent par le service

    def _json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]

        if path in ("/api/version", "/api/latest"):
            self._json(read_manifest())

        elif path == "/api/events":
            self._sse()

        elif path.startswith("/files/"):
            self._serve_file(os.path.basename(path[len("/files/"):]))

        elif path == "/health":
            self._json({"ok": True, "subscribers": len(subscribers)})

        else:
            self._json({"error": "not found"}, 404)

    def do_POST(self):
        if self.path.split("?")[0] != "/api/publish":
            return self._json({"error": "not found"}, 404)
        if self.headers.get("X-Publish-Token") != TOKEN:
            return self._json({"error": "unauthorized"}, 401)

        length = int(self.headers.get("Content-Length", 0))
        try:
            manifest = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            return self._json({"error": "invalid json"}, 400)

        manifest["published_at"] = int(time.time())
        with open(MANIFEST, "w", encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=2)

        broadcast(manifest)  # ← les apps ouvertes réagissent tout de suite
        self._json({"ok": True, "notified": len(subscribers)})

    def _sse(self):
        q = queue.Queue(maxsize=8)
        with subscribers_lock:
            subscribers.append(q)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        try:
            # État courant à la connexion, puis les publications en direct.
            self._sse_send("hello", read_manifest())
            while True:
                try:
                    payload = q.get(timeout=20)
                    self._sse_send("update", payload)
                except queue.Empty:
                    # Ping : garde la connexion vivante à travers les proxys.
                    self.wfile.write(b": ping\n\n")
                    self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            with subscribers_lock:
                if q in subscribers:
                    subscribers.remove(q)

    def _sse_send(self, event, data):
        payload = json.dumps(data)
        self.wfile.write(f"event: {event}\ndata: {payload}\n\n".encode())
        self.wfile.flush()

    def _serve_file(self, name):
        full = os.path.join(FILES, name)
        if not os.path.isfile(full):
            return self._json({"error": "not found"}, 404)

        size = os.path.getsize(full)
        start, end = 0, size - 1
        rng = self.headers.get("Range")
        partial = False
        if rng and rng.startswith("bytes="):
            # Reprise de téléchargement : indispensable sur connexion instable.
            spec = rng[len("bytes="):].split("-")
            if spec[0]:
                start = int(spec[0])
            if len(spec) > 1 and spec[1]:
                end = int(spec[1])
            partial = True

        length = end - start + 1
        self.send_response(206 if partial else 200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

        with open(full, "rb") as f:
            f.seek(start)
            remaining = length
            while remaining > 0:
                chunk = f.read(min(256 * 1024, remaining))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return
                remaining -= len(chunk)


if __name__ == "__main__":
    os.makedirs(FILES, exist_ok=True)
    ThreadingHTTPServer.daemon_threads = True
    print(f"Serveur de mises à jour Lumen sur :{PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
