# MAINTAIN AI Android

Native Android companion app for **MAINTAIN AI**.

## Purpose

This is a separate mobile client for monitoring the MAINTAIN AI maintenance system. The desktop application remains the primary operations/control interface. The Android app focuses on:

- Dashboard and machine health
- Alerts
- Analytics
- Reports
- Backend connection settings

The Android app communicates with the existing FastAPI backend. It does **not** access the SQLite database directly.

## Backend connection

The emulator defaults to `http://10.0.2.2:8000/`.

For a physical phone connected to the same Wi-Fi as the computer running MAINTAIN AI, set the server URL to the computer's LAN address, for example `http://192.168.1.10:8000/`.

## APK build

GitHub Actions workflow: `.github/workflows/build-apk.yml`

Run it manually from **Actions → Build MAINTAIN AI Android APK → Run workflow**, or push a `v*` tag. The release APK is uploaded as a workflow artifact.
