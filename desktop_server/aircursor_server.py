#!/usr/bin/env python3
"""AirCursor desktop bridge. Run only on a trusted private Wi-Fi network."""
import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = 8765
TOKEN = os.environ.get("AIRCURSOR_TOKEN", "").strip()

if len(TOKEN) < 16:
    raise SystemExit("Set AIRCURSOR_TOKEN to a private random value of at least 16 characters.")

try:
    import pyautogui
except ImportError:
    raise SystemExit("Install the required desktop input library first: python -m pip install pyautogui")


class Handler(BaseHTTPRequestHandler):
    def reply(self, code, message):
        data = json.dumps({"ok": code == 200, "message": message}).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        supplied = self.headers.get("Authorization", "")
        expected = "Bearer " + TOKEN
        return hmac.compare_digest(supplied, expected)

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, "AirCursor desktop server ready")
        else:
            self.reply(404, "Not found")

    def do_POST(self):
        if not self.authorized():
            self.reply(401, "Pairing token is missing or incorrect")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 0 or length > 4096:
                self.reply(413, "Request too large")
                return
            data = json.loads(self.rfile.read(length) or b"{}")

            if self.path == "/pair":
                self.reply(200, "Pairing successful")
                return
            elif self.path == "/move":
                dx = max(-500, min(500, float(data.get("dx", 0))))
                dy = max(-500, min(500, float(data.get("dy", 0))))
                pyautogui.moveRel(dx, dy, duration=0)
            elif self.path == "/click":
                button = data.get("button")
                if button not in ("left", "right"):
                    self.reply(400, "Invalid button")
                    return
                pyautogui.click(button=button)
            elif self.path == "/scroll":
                amount = max(-10, min(10, int(data.get("amount", 0))))
                pyautogui.scroll(amount)
            elif self.path == "/type":
                value = data.get("text", "")
                if not isinstance(value, str) or len(value) > 500 or any(ord(c) < 32 or ord(c) > 126 for c in value):
                    self.reply(400, "Text must be printable ASCII and at most 500 characters")
                    return
                pyautogui.write(value, interval=0.01)
            elif self.path == "/volume":
                keys = {"up": "volumeup", "down": "volumedown", "mute": "volumemute"}
                action = data.get("action")
                if action not in keys:
                    self.reply(400, "Invalid volume action")
                    return
                pyautogui.press(keys[action])
            elif self.path == "/media":
                keys = {"play_pause": "playpause", "next": "nexttrack", "previous": "prevtrack"}
                action = data.get("action")
                if action not in keys:
                    self.reply(400, "Invalid media action")
                    return
                pyautogui.press(keys[action])
            elif self.path == "/presentation":
                keys = {"next": "right", "previous": "left", "start": "f5", "exit": "esc", "black": "b"}
                action = data.get("action")
                if action not in keys:
                    self.reply(400, "Invalid presentation action")
                    return
                pyautogui.press(keys[action])
            else:
                self.reply(404, "Not found")
                return
            self.reply(200, "Command completed")
        except (ValueError, TypeError, json.JSONDecodeError):
            self.reply(400, "Invalid request")
        except Exception:
            self.reply(500, "Desktop input action failed")

    def log_message(self, fmt, *args):
        print("[AirCursor]", fmt % args)


if __name__ == "__main__":
    print(f"AirCursor listening on port {PORT}. Keep this on a trusted private Wi-Fi network.")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
