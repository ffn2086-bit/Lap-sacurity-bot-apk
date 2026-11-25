"""
Sentinel Laptop Guard - Windows agent
=====================================
Watches YOUR OWN Windows laptop and talks to the Sentinel Android app through a
Firebase Realtime Database (plain REST, no server of your own).

Alerts sent to the phone when armed:
  * Lid opened / woke from sleep   * Screen unlocked / user logged on
  * Failed login attempt (needs Administrator, reads the Security event log)
  * Charger unplugged              * Laptop booted / agent started at logon

Commands accepted from the phone:
  lock, siren, siren_stop, photo, location, shutdown, sleep, ping

Usage:
  python laptop_agent.py --setup        interactive configuration
  python laptop_agent.py --test-alert   send a test alert now, then exit
  python laptop_agent.py                run the agent (normally started hidden at logon)
"""
import base64
import ctypes
import json
import logging
import os
import re
import socket
import subprocess
import sys
import threading
import time
import uuid
from ctypes import wintypes
from logging.handlers import RotatingFileHandler
from urllib.parse import quote

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "config.json")
LOG_PATH = os.path.join(BASE_DIR, "agent.log")
CREATE_NO_WINDOW = 0x08000000

DEFAULT_CONFIG = {
    "db_url": "",
    "db_secret": "",
    "laptop_name": "",
    "auto_photo": True,
    "camera_index": 0,
    "siren_seconds": 120,
    "telegram_token": "",
    "telegram_chat_id": "",
}

log = logging.getLogger("sentinel")


def setup_logging():
    log.setLevel(logging.INFO)
    handler = RotatingFileHandler(LOG_PATH, maxBytes=500_000, backupCount=2, encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


# --------------------------------------------------------------------------- config
def load_config():
    cfg = dict(DEFAULT_CONFIG)
    if os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg.update(json.load(f))
    if not cfg.get("laptop_name"):
        cfg["laptop_name"] = socket.gethostname()
    return cfg


def run_setup():
    cfg = dict(DEFAULT_CONFIG)
    if os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg.update(json.load(f))

    print("=== Sentinel Laptop Guard - setup ===")
    print("Use the SAME Database URL and secret that you enter in the Android app.\n")

    def ask(label, key, secret=False):
        current = cfg.get(key, "")
        shown = "(keep current)" if (secret and current) else current
        val = input(f"{label} [{shown}]: ").strip()
        if val:
            cfg[key] = val

    ask("Firebase Realtime Database URL (https://xxxx-default-rtdb.firebaseio.com)", "db_url")
    cfg["db_url"] = cfg["db_url"].rstrip("/")
    ask("Database secret", "db_secret", secret=True)
    ask("Laptop name shown on the phone (blank = computer name)", "laptop_name")
    print("\nOptional: ALSO get a Telegram message + photo on your phone (leave blank to skip).")
    ask("Telegram bot token", "telegram_token", secret=True)
    ask("Telegram chat id", "telegram_chat_id")

    if not cfg["db_url"].startswith("https://"):
        print("ERROR: the Database URL must start with https://")
        sys.exit(1)

    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)
    print(f"\nSaved {CONFIG_PATH}")

    try:
        import requests
        url = cfg["db_url"] + "/.json?shallow=true"
        if cfg.get("db_secret"):
            url += "&auth=" + quote(cfg["db_secret"])
        r = requests.get(url, timeout=10)
        if r.ok:
            print("Connection to the database: OK")
        else:
            print(f"Database answered HTTP {r.status_code} - check the URL / secret.")
    except Exception as exc:  # noqa: BLE001
        print(f"Could not reach the database: {exc}")


# --------------------------------------------------------------------------- database
class Db:
    """Tiny Firebase Realtime Database REST wrapper."""

    def __init__(self, base_url, secret):
        import requests
        self.requests = requests
        self.base = base_url.rstrip("/")
        self.secret = secret or ""
        self.session = requests.Session()

    def _url(self, path):
        url = f"{self.base}{path}.json"
        if self.secret:
            url += "?auth=" + quote(self.secret)
        return url

    def get(self, path):
        r = self.session.get(self._url(path), timeout=10)
        r.raise_for_status()
        return r.json()

    def put(self, path, data):
        r = self.session.put(self._url(path), data=json.dumps(data), timeout=15)
        r.raise_for_status()
        return r.json()

    def patch(self, path, data):
        r = self.session.patch(self._url(path), data=json.dumps(data), timeout=10)
        r.raise_for_status()
        return r.json()

    def post(self, path, data):
        r = self.session.post(self._url(path), data=json.dumps(data), timeout=10)
        r.raise_for_status()
        return r.json()

    def delete(self, path):
        r = self.session.delete(self._url(path), timeout=10)
        r.raise_for_status()

    def shallow_keys(self, path):
        url = self._url(path)
        url += ("&" if "?" in url else "?") + "shallow=true"
        r = self.session.get(url, timeout=10)
        r.raise_for_status()
        data = r.json()
        return sorted(data.keys()) if isinstance(data, dict) else []


# --------------------------------------------------------------------------- helpers
def now_ms():
    return int(time.time() * 1000)


def set_max_volume():
    """Unmute and set the master volume to 100 %. Falls back to volume-up key presses."""
    try:
        import comtypes
        comtypes.CoInitialize()
    except Exception:  # noqa: BLE001
        pass
    try:
        from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
        from comtypes import CLSCTX_ALL

        speakers = AudioUtilities.GetSpeakers()
        if hasattr(speakers, "EndpointVolume"):
            vol = speakers.EndpointVolume
        else:
            iface = speakers.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
            vol = ctypes.cast(iface, ctypes.POINTER(IAudioEndpointVolume))
        vol.SetMute(0, None)
        vol.SetMasterVolumeLevelScalar(1.0, None)
        return
    except Exception as exc:  # noqa: BLE001
        log.info("pycaw volume control failed (%s); using key presses", exc)
    try:
        user32 = ctypes.windll.user32
        for _ in range(50):
            user32.keybd_event(0xAF, 0, 0, 0)  # VK_VOLUME_UP down
            user32.keybd_event(0xAF, 0, 2, 0)  # VK_VOLUME_UP up
    except Exception:  # noqa: BLE001
        pass


def run_hidden(args, **kwargs):
    return subprocess.run(
        args,
        capture_output=True,
        text=True,
        errors="replace",
        creationflags=CREATE_NO_WINDOW,
        **kwargs,
    )


# --------------------------------------------------------------------------- agent
class Agent:
    WAKE_KEY = "wake"

    def __init__(self, cfg):
        import psutil
        self.psutil = psutil
        self.cfg = cfg
        self.db = Db(cfg["db_url"], cfg.get("db_secret", ""))
        self.name = cfg["laptop_name"]
        self.armed = True

        self.cooldowns = {}
        self.cd_lock = threading.Lock()
        self.cam_lock = threading.Lock()
        self.pending = []
        self.pending_lock = threading.Lock()

        self.siren_stop = threading.Event()
        self.siren_thread = None

        self.last_plugged = None
        self.last_failed_rid = None
        self.can_read_security = True

        self._pub_ip = ("", 0.0)
        self._ssid = ("", 0.0)
        self._next_hb = 0.0
        self._next_cfg = 0.0
        self._next_sec = 0.0
        self._next_ip = 0.0

    # ---- network info -----------------------------------------------------
    def local_ip(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
        except Exception:  # noqa: BLE001
            return "-"
        finally:
            s.close()

    def public_ip(self, refresh=False):
        value, stamp = self._pub_ip
        if refresh and time.time() - stamp > 120:
            try:
                value = self.db.requests.get("https://api.ipify.org", timeout=6).text.strip()
                self._pub_ip = (value, time.time())
            except Exception:  # noqa: BLE001
                pass
        return value or "-"

    def ssid(self):
        value, stamp = self._ssid
        if time.time() - stamp < 30:
            return value
        out = "-"
        try:
            r = run_hidden(["netsh", "wlan", "show", "interfaces"], timeout=8)
            m = re.search(r"^\s*SSID\s*:\s*(.+?)\s*$", r.stdout, re.MULTILINE)
            if m:
                out = m.group(1)
        except Exception:  # noqa: BLE001
            pass
        self._ssid = (out, time.time())
        return out

    # ---- heartbeat / config ----------------------------------------------
    def heartbeat(self):
        batt = self.psutil.sensors_battery()
        if time.time() >= self._next_ip:
            self._next_ip = time.time() + 120
            self.public_ip(refresh=True)
        self.db.patch("/status", {
            "online": True,
            "ts": {".sv": "timestamp"},
            "name": self.name,
            "battery": int(batt.percent) if batt else -1,
            "plugged": bool(batt.power_plugged) if batt else True,
            "localIp": self.local_ip(),
            "ssid": self.ssid(),
        })

    def poll_config(self):
        value = self.db.get("/config/armed")
        self.armed = False if value is False else True

    # ---- alerts -----------------------------------------------------------
    def trigger(self, reason, key=None, cooldown=8.0):
        key = key or reason
        now = time.time()
        with self.cd_lock:
            if now - self.cooldowns.get(key, 0.0) < cooldown:
                return
            self.cooldowns[key] = now
        if not self.armed:
            log.info("Event ignored (disarmed): %s", reason)
            return
        log.info("ALERT: %s", reason)
        threading.Thread(target=self._send_alert, args=(reason, now), daemon=True).start()

    def _send_alert(self, reason, when):
        alert = {
            "title": "SECURITY BREACH: LAPTOP ACCESSED",
            "time": int(when * 1000),
            "laptop": self.name,
            "ip_local": self.local_ip(),
            "ip_public": self.public_ip(),
            "reason": reason,
        }
        if not self._push_alert(alert):
            with self.pending_lock:
                self.pending.append(alert)
        jpg = None
        if self.cfg.get("auto_photo", True):
            try:
                jpg = self.capture_photo()
                self.upload_photo(jpg, reason)
            except Exception as exc:  # noqa: BLE001
                log.info("Auto photo failed: %s", exc)
        self._telegram(alert, jpg)

    def _push_alert(self, alert):
        try:
            self.db.post("/alerts", alert)
        except Exception as exc:  # noqa: BLE001
            log.info("Alert push failed (will retry): %s", exc)
            return False
        try:
            keys = self.db.shallow_keys("/alerts")
            for old in keys[:-20]:
                self.db.delete(f"/alerts/{old}")
        except Exception:  # noqa: BLE001
            pass
        return True

    def flush_pending(self):
        with self.pending_lock:
            queue = list(self.pending)
            self.pending.clear()
        for i, alert in enumerate(queue):
            if not self._push_alert(alert):
                with self.pending_lock:
                    self.pending.extend(queue[i:])
                return

    def _telegram(self, alert, jpg):
        token = self.cfg.get("telegram_token", "")
        chat = self.cfg.get("telegram_chat_id", "")
        if not token or not chat:
            return
        text = (
            "SECURITY BREACH: LAPTOP ACCESSED\n"
            f"Laptop: {alert['laptop']}\nReason: {alert['reason']}\n"
            f"Local IP: {alert['ip_local']}\nPublic IP: {alert['ip_public']}"
        )
        try:
            api = f"https://api.telegram.org/bot{token}"
            self.db.requests.post(f"{api}/sendMessage", data={"chat_id": chat, "text": text}, timeout=15)
            if jpg:
                self.db.requests.post(
                    f"{api}/sendPhoto",
                    data={"chat_id": chat},
                    files={"photo": ("intruder.jpg", jpg, "image/jpeg")},
                    timeout=30,
                )
        except Exception as exc:  # noqa: BLE001
            log.info("Telegram failed: %s", exc)

    # ---- event detectors --------------------------------------------------
    def check_charger(self):
        batt = self.psutil.sensors_battery()
        if batt is None:
            return
        plugged = bool(batt.power_plugged)
        if self.last_plugged is True and plugged is False:
            self.trigger("Charger Unplugged")
        self.last_plugged = plugged

    def check_failed_logins(self):
        r = run_hidden(
            ["wevtutil", "qe", "Security", "/q:*[System[(EventID=4625)]]",
             "/c:1", "/rd:true", "/f:xml"],
            timeout=15,
        )
        if r.returncode != 0:
            self.can_read_security = False
            log.info("Cannot read Security log (run as Administrator for failed-login alerts).")
            return
        m = re.search(r"<EventRecordID>(\d+)</EventRecordID>", r.stdout)
        rid = int(m.group(1)) if m else 0
        if self.last_failed_rid is None:
            self.last_failed_rid = rid
            return
        if rid > self.last_failed_rid:
            self.last_failed_rid = rid
            self.trigger("Failed Login Attempt")

    # ---- commands ---------------------------------------------------------
    def purge_commands(self):
        try:
            self.db.delete("/commands")
        except Exception:  # noqa: BLE001
            pass

    def poll_commands(self):
        data = self.db.get("/commands")
        if not isinstance(data, dict):
            return
        for key in sorted(data.keys()):
            cmd = data[key]
            try:
                self.db.delete(f"/commands/{key}")
            except Exception:  # noqa: BLE001
                continue  # could not consume it: never risk running it twice
            if not isinstance(cmd, dict):
                continue
            ts = cmd.get("ts")
            if isinstance(ts, (int, float)) and now_ms() - ts > 90_000:
                log.info("Dropped stale command: %s", cmd.get("cmd"))
                continue
            threading.Thread(
                target=self.handle_command,
                args=(str(cmd.get("cmd", "")), str(cmd.get("arg", ""))),
                daemon=True,
            ).start()

    def respond(self, cmd, ok, msg):
        try:
            self.db.put("/responses/latest", {"cmd": cmd, "ok": ok, "msg": msg, "time": now_ms()})
        except Exception as exc:  # noqa: BLE001
            log.info("Could not write response: %s", exc)

    def handle_command(self, cmd, arg):
        log.info("Command: %s", cmd)
        try:
            if cmd == "lock":
                msg = self.cmd_lock()
            elif cmd == "siren":
                msg = self.cmd_siren()
            elif cmd == "siren_stop":
                msg = self.cmd_siren_stop()
            elif cmd == "photo":
                msg = self.cmd_photo()
            elif cmd == "location":
                msg = self.cmd_location()
            elif cmd == "shutdown":
                msg = self.cmd_shutdown()
            elif cmd == "sleep":
                msg = self.cmd_sleep()
            elif cmd == "ping":
                self.heartbeat()
                msg = "pong"
            else:
                self.respond(cmd, False, "Unknown command")
                return
            self.respond(cmd, True, msg)
        except Exception as exc:  # noqa: BLE001
            log.exception("Command %s failed", cmd)
            self.respond(cmd, False, str(exc))

    def cmd_lock(self):
        subprocess.Popen(["rundll32.exe", "user32.dll,LockWorkStation"], creationflags=CREATE_NO_WINDOW)
        return "Workstation locked"

    def cmd_siren(self):
        self.cmd_siren_stop()
        self.siren_stop.clear()
        self.siren_thread = threading.Thread(target=self._siren_loop, daemon=True)
        self.siren_thread.start()
        return "Siren started (max volume)"

    def cmd_siren_stop(self):
        self.siren_stop.set()
        return "Siren stopped"

    def _siren_loop(self):
        import winsound
        set_max_volume()
        end = time.time() + int(self.cfg.get("siren_seconds", 120))
        last_vol = time.time()
        sweep = list(range(700, 1500, 100)) + list(range(1500, 700, -100))
        while not self.siren_stop.is_set() and time.time() < end:
            for freq in sweep:
                if self.siren_stop.is_set():
                    break
                winsound.Beep(freq, 60)
            if time.time() - last_vol > 8:
                set_max_volume()
                last_vol = time.time()

    def capture_photo(self):
        import cv2
        with self.cam_lock:
            index = int(self.cfg.get("camera_index", 0))
            cap = cv2.VideoCapture(index, cv2.CAP_DSHOW)
            if not cap.isOpened():
                cap.release()
                cap = cv2.VideoCapture(index)
            if not cap.isOpened():
                cap.release()
                raise RuntimeError("Webcam not available")
            try:
                frame = None
                for _ in range(15):  # let auto-exposure settle
                    ok, img = cap.read()
                    if ok:
                        frame = img
                    time.sleep(0.08)
                if frame is None:
                    raise RuntimeError("Webcam returned no frame")
            finally:
                cap.release()
            height, width = frame.shape[:2]
            if width > 800:
                scale = 800.0 / width
                frame = cv2.resize(frame, (800, int(height * scale)))
            ok, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 70])
            if not ok:
                raise RuntimeError("JPEG encoding failed")
            return buf.tobytes()

    def upload_photo(self, jpg, reason):
        self.db.put("/photos/latest", {
            "b64": base64.b64encode(jpg).decode("ascii"),
            "time": now_ms(),
            "reason": reason,
        })

    def cmd_photo(self):
        jpg = self.capture_photo()
        self.upload_photo(jpg, "Requested from phone")
        return f"Photo captured ({len(jpg) // 1024} KB)"

    def cmd_location(self):
        info = {"ssid": self.ssid(), "publicIp": self.public_ip(refresh=True)}
        try:
            g = self.db.requests.get("https://ipwho.is/", timeout=10).json()
            if g.get("success", True):
                info["publicIp"] = g.get("ip", info["publicIp"])
                info["city"] = g.get("city", "-")
                info["region"] = g.get("region", "-")
                info["country"] = g.get("country", "-")
                conn = g.get("connection") or {}
                info["isp"] = conn.get("isp", "-")
                if g.get("latitude") is not None and g.get("longitude") is not None:
                    info["lat"] = g["latitude"]
                    info["lon"] = g["longitude"]
        except Exception as exc:  # noqa: BLE001
            log.info("Geo lookup failed: %s", exc)
        info["time"] = now_ms()
        self.db.put("/location/latest", info)
        return f"{info.get('city', '-')}, {info.get('country', '-')} / SSID {info['ssid']}"

    def cmd_shutdown(self):
        subprocess.Popen(["shutdown", "/s", "/f", "/t", "5"], creationflags=CREATE_NO_WINDOW)
        return "Shutting down in 5 seconds"

    def cmd_sleep(self):
        subprocess.Popen("rundll32.exe powrprof.dll,SetSuspendState 0,1,0", creationflags=CREATE_NO_WINDOW)
        return "Going to sleep"

    # ---- main loop --------------------------------------------------------
    def safe(self, fn):
        try:
            fn()
        except Exception as exc:  # noqa: BLE001
            log.debug("%s failed: %s", getattr(fn, "__name__", "task"), exc)

    def tick(self):
        now = time.time()
        if now >= self._next_hb:
            self._next_hb = now + 5
            self.safe(self.heartbeat)
        if now >= self._next_cfg:
            self._next_cfg = now + 3
            self.safe(self.poll_config)
        self.safe(self.poll_commands)
        if self.can_read_security and now >= self._next_sec:
            self._next_sec = now + 6
            self.safe(self.check_failed_logins)
        self.safe(self.check_charger)
        self.safe(self.flush_pending)

    def run(self):
        log.info("Agent started for '%s'", self.name)
        self.safe(self.purge_commands)
        self.safe(self.poll_config)
        self.safe(self.heartbeat)

        uptime = time.time() - self.psutil.boot_time()
        if uptime < 900:
            self.trigger("Laptop Powered On / User Logged In", key=self.WAKE_KEY, cooldown=15)

        watcher = SessionWatcher(self)
        watcher.start()

        while True:
            self.tick()
            before_sleep = time.time()
            time.sleep(2)
            if time.time() - before_sleep > 15:
                # The loop was frozen: the machine was suspended and has just resumed.
                self.trigger("Woke From Sleep / Lid Opened", key=self.WAKE_KEY, cooldown=15)


# --------------------------------------------------------------------------- session / lid watcher
class POWERBROADCAST_SETTING(ctypes.Structure):
    _fields_ = [
        ("PowerSetting", ctypes.c_ubyte * 16),
        ("DataLength", wintypes.DWORD),
        ("Data", ctypes.c_ubyte * 4),
    ]


class SessionWatcher(threading.Thread):
    """Hidden window that receives lock/unlock/logon and lid-switch notifications."""

    WM_WTSSESSION_CHANGE = 0x02B1
    WM_POWERBROADCAST = 0x0218
    PBT_POWERSETTINGCHANGE = 0x8013
    WTS_CONSOLE_CONNECT = 0x1
    WTS_SESSION_LOGON = 0x5
    WTS_SESSION_UNLOCK = 0x8
    LID_GUID = uuid.UUID("BA3E0F4D-B817-4094-A86D-D8F9DB6D8EE6")

    def __init__(self, agent):
        super().__init__(daemon=True)
        self.agent = agent

    def run(self):
        try:
            import win32api
            import win32gui
            import win32ts
        except Exception as exc:  # noqa: BLE001
            log.info("pywin32 missing, unlock/lid detection disabled: %s", exc)
            return
        try:
            wc = win32gui.WNDCLASS()
            wc.lpszClassName = "SentinelWatcherWindow"
            wc.hInstance = win32api.GetModuleHandle(None)
            wc.lpfnWndProc = self._wndproc
            atom = win32gui.RegisterClass(wc)
            hwnd = win32gui.CreateWindow(
                atom, "SentinelWatcher", 0, 0, 0, 0, 0, 0, 0, wc.hInstance, None
            )
            win32ts.WTSRegisterSessionNotification(hwnd, win32ts.NOTIFY_FOR_THIS_SESSION)

            user32 = ctypes.windll.user32
            user32.RegisterPowerSettingNotification.argtypes = [
                wintypes.HANDLE, ctypes.c_void_p, wintypes.DWORD]
            user32.RegisterPowerSettingNotification.restype = wintypes.HANDLE
            guid = (ctypes.c_ubyte * 16).from_buffer_copy(self.LID_GUID.bytes_le)
            handle = user32.RegisterPowerSettingNotification(hwnd, ctypes.byref(guid), 0)
            if not handle:
                log.info("Lid-switch notification not available on this machine.")
            win32gui.PumpMessages()
        except Exception:  # noqa: BLE001
            log.exception("SessionWatcher crashed")

    def _wndproc(self, hwnd, msg, wparam, lparam):
        import win32gui
        try:
            if msg == self.WM_WTSSESSION_CHANGE:
                if wparam == self.WTS_SESSION_UNLOCK:
                    self.agent.trigger("Screen Unlocked")
                elif wparam == self.WTS_SESSION_LOGON:
                    self.agent.trigger("User Logged On")
                elif wparam == self.WTS_CONSOLE_CONNECT:
                    self.agent.trigger("Console Session Connected")
                return 0
            if msg == self.WM_POWERBROADCAST and wparam == self.PBT_POWERSETTINGCHANGE:
                pbs = POWERBROADCAST_SETTING.from_address(lparam)
                if bytes(pbs.PowerSetting) == self.LID_GUID.bytes_le and pbs.Data[0] == 1:
                    self.agent.trigger("Lid Opened", key=Agent.WAKE_KEY, cooldown=15)
                return 1
        except Exception:  # noqa: BLE001
            log.exception("wndproc error")
        return win32gui.DefWindowProc(hwnd, msg, wparam, lparam)


# --------------------------------------------------------------------------- entry point
_mutex_handle = None


def already_running():
    global _mutex_handle
    kernel32 = ctypes.windll.kernel32
    _mutex_handle = kernel32.CreateMutexW(None, False, "Local\\SentinelLaptopGuardAgent")
    return kernel32.GetLastError() == 183  # ERROR_ALREADY_EXISTS


def main():
    if sys.platform != "win32":
        print("This agent only runs on Windows.")
        return 1

    if "--setup" in sys.argv:
        run_setup()
        return 0

    if not os.path.exists(CONFIG_PATH):
        if sys.stdin is None or not sys.stdin.isatty():
            return 1  # hidden run without configuration: nothing sensible to do
        run_setup()

    setup_logging()
    cfg = load_config()
    if not cfg.get("db_url"):
        log.error("db_url missing in config.json - run: python laptop_agent.py --setup")
        return 1

    if "--test-alert" in sys.argv:
        agent = Agent(cfg)
        agent.armed = True
        agent._send_alert("Test alert from laptop agent", time.time())
        print("Test alert sent. Check your phone.")
        return 0

    if already_running():
        return 0

    Agent(cfg).run()
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(0)
    except Exception:  # noqa: BLE001
        setup_logging()
        log.exception("Fatal error")
        sys.exit(1)
