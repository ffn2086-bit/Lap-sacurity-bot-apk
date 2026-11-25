# Sentinel Laptop Guard

Personal anti-theft system for **your own** Windows laptop.

* `app/` – Android app (Kotlin + Jetpack Compose). Dark silver / neon-red HUD, full-screen red
  warning popup over the lock screen, remote control dashboard.
* `laptop_agent/` – Windows agent (Python 3) that watches the laptop and talks to the phone.
* `.github/workflows/build-apk.yml` – builds the APK on every push.

Both sides talk through a free **Firebase Realtime Database** using plain REST calls
(no Firebase SDK, no `google-services.json`, no server of your own).

## 1. Create the free Firebase database (5 minutes)

1. https://console.firebase.google.com -> **Add project** (turn Google Analytics off).
2. **Build -> Realtime Database -> Create Database** -> any region -> **Locked mode**.
   Leave the rules locked: the database secret below bypasses them, and nobody else can read or write.
3. Copy the database URL shown at the top, e.g. `https://my-project-default-rtdb.firebaseio.com`.
4. **Project settings (gear) -> Service accounts -> Database secrets** -> show and copy the secret.

Keep the secret private: it allows full access to this database (including the lock/shutdown commands).

## 2. Build the APK on GitHub

```
git init
git add .
git commit -m "Sentinel Laptop Guard"
git branch -M main
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin main
```

Open the repo -> **Actions -> Build Android APK**. When it finishes (about 5-8 minutes), open the
run and download the `SentinelLaptopGuard-debug-apk` artifact. Unzip it and install
`app-debug.apk` on your phone (allow "install unknown apps").

(Uploading through the GitHub web page works too, but make sure the hidden `.github` folder is
included, otherwise no build starts. Git / GitHub Desktop is more reliable.)

## 3. Set up the phone

1. Open the app -> **gear** -> enter the Database URL and the secret -> **Save**.
2. Tap each permission button (Display over other apps, Full-screen notifications, Do Not Disturb
   access, Ignore battery optimization). These let the red popup wake the phone and show on the lock screen.
3. **Send test alert** – the red popup should appear within a few seconds.

## 4. Install the Windows agent

1. Install Python 3 (tick *Add python.exe to PATH*).
2. Copy the `laptop_agent` folder onto the laptop.
3. Right-click `install_startup.bat` -> **Run as administrator**. It installs the packages, asks for the
   same URL + secret (and optional Telegram details), registers a hidden task that starts at every
   logon, and starts the agent.
4. Test: `python laptop_agent.py --test-alert` (phone should light up red).

Logs: `laptop_agent/agent.log`. Remove: `schtasks /delete /tn SentinelLaptopGuard /f`.

## What triggers an alert (when ARMED)

| Event | How it is detected |
|---|---|
| Lid opened | Windows lid-switch power notification |
| Woke from sleep | agent loop freeze detection |
| Screen unlocked / logged on | Windows session notifications |
| Failed login | Security event 4625 (needs the Administrator task) |
| Charger unplugged | battery state change |
| Laptop powered on | agent starts at logon within 15 min of boot |

Use the **ARMED** switch in the app: switch it off before you use the laptop yourself, on when you leave.

## Remote commands

Lock (`rundll32.exe user32.dll,LockWorkStation`), siren at max volume, webcam photo, location and network
info (Wi-Fi SSID, public IP, IP-based approximate location), shutdown / sleep (with confirmation).
Commands older than 90 seconds are discarded, so a queued shutdown never fires after the laptop boots.

## Notes

* Location is IP-based (city level), not GPS.
* Sleep uses `SetSuspendState`; if hibernation is enabled Windows may hibernate instead.
* Telegram is an optional extra: the laptop also sends a message + photo to your Telegram chat.
  Telegram bots cannot receive commands for a custom app, so the app itself uses Firebase.
