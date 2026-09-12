# MAINTAIN AI Android

[![Ask DeepWiki](https://devin.ai/assets/askdeepwiki.png)](https://deepwiki.com/jadhavdurvesh/Maintain.ai.android)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Design-757575?logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![Gradle](https://img.shields.io/badge/Gradle-Build-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![JDK 17](https://img.shields.io/badge/JDK-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)

**Native Android operations client for the MAINTAIN AI predictive maintenance platform.**

MAINTAIN AI Android brings fleet monitoring, maintenance intelligence, alerts, analytics, work orders, and reporting to Android devices. The application connects to the MAINTAIN AI FastAPI backend through a REST API and is designed as a mobile operations interface rather than a direct database client.

## Overview

The Android application provides a focused mobile view of the MAINTAIN AI maintenance system. It retrieves machine and maintenance data from the backend, presents fleet health and predictive insights, surfaces operational alerts, manages work-order visibility, and generates maintenance reports.

The application does **not** access the backend SQLite database directly. All application data is exchanged through the MAINTAIN AI API.

```text
                    MAINTAIN AI PLATFORM
                             │
                             ▼
                     ┌──────────────┐
                     │ FastAPI API  │
                     └──────┬───────┘
                            │
                         REST API
                            │
                     ┌──────▼───────┐
                     │ MAINTAIN AI  │
                     │    Android   │
                     └──────┬───────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
      Dashboard         Analytics         Operations
          │                 │                 │
      Machines        AI Predictions      Work Orders
      Alerts          Model Status          Reports
```

## Features

### Fleet Dashboard

- Fleet-wide health score and health distribution
- Machine count and machine health classification
- Healthy, attention, and critical machine indicators
- Urgent and critical maintenance alerts
- Open maintenance work orders
- Live machine health information
- Predictive model publication status
- Automatic dashboard synchronization

### Alerts

- Maintenance and machine alerts
- Severity classification
- Acknowledgement and resolution state
- Background alert checking
- Android notification support

### Predictive Analytics

- AI-generated machine risk predictions
- Actual versus predicted health scores
- Risk-level information and prediction reasoning
- Model training status
- Model version and training metadata
- Sensor-aware model metadata
- Integration with the MAINTAIN AI Random Forest model published by the backend

### Work Orders

- Machine-linked maintenance jobs
- Problem descriptions
- Priority levels
- Work-order status
- Recommended maintenance actions
- Assigned personnel
- Creation and completion metadata
- Maintenance resolution information

### Reports

- Generate maintenance reports as PDF documents
- Structured machine and maintenance information
- Android file sharing through the platform FileProvider

### Backend Configuration

- Configurable MAINTAIN AI server URL
- Hosted backend support
- Local development backend support
- Android emulator networking support
- Physical-device/LAN backend configuration

## Application Architecture

The application follows a lightweight layered architecture that separates the Compose UI from networking and backend data access.

```text
┌──────────────────────────────────────┐
│          Jetpack Compose UI          │
│ Dashboard • Alerts • Analytics       │
│ Work Orders • Reports • Settings     │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│       ViewModel / Application State  │
│   UI state • synchronization • errors │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│           MaintainRepository         │
│        Data aggregation layer        │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│        Retrofit + OkHttp API         │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│       MAINTAIN AI FastAPI API        │
└──────────────────────────────────────┘
```

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Design System | Material 3 |
| Navigation | Navigation Compose |
| Networking | Retrofit |
| HTTP Client | OkHttp |
| JSON Serialization | Gson |
| State & Lifecycle | AndroidX ViewModel + Compose State |
| Background Processing | WorkManager |
| Local Preferences | Jetpack DataStore |
| PDF Generation | Android PDF / Canvas APIs |
| Build System | Gradle |
| Java Runtime | JDK 17 |
| Android Compile SDK | API 37 |
| Android Target SDK | API 36 |
| Minimum Android Version | API 26 |

## API Integration

The Android client communicates with the MAINTAIN AI backend through the following endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/machines` | Retrieve fleet machine information |
| `GET` | `/api/alerts` | Retrieve maintenance alerts |
| `GET` | `/api/work-orders` | Retrieve maintenance work orders |
| `GET` | `/api/machines/{id}/readings` | Retrieve sensor readings for a machine |
| `GET` | `/api/analytics/model-status` | Retrieve predictive-model status |
| `GET` | `/api/analytics/risk-predictions` | Retrieve machine risk predictions |
| `POST` | `/api/analytics/train` | Request backend model training |

The Android data layer maps these responses into application models such as `Machine`, `SensorReading`, `Alert`, `WorkOrder`, `ModelStatus`, and `RiskPrediction`.

## Backend Configuration

The application currently uses the following default backend:

```text
https://maintain-ai-3.vercel.app/
```

### Android Emulator

When running the MAINTAIN AI backend locally on the development computer, the Android emulator can reach the host machine through:

```text
http://10.0.2.2:8000/
```

`10.0.2.2` is the Android emulator's special address for the host computer's loopback interface.

### Physical Android Device

For a physical Android device connected to the same local network as the development computer, use the computer's LAN address:

```text
http://<YOUR-PC-LAN-IP>:8000/
```

The server must be reachable from the device and configured to accept connections on the appropriate network interface.

## Requirements

Before building the application, install:

- Android Studio
- Android SDK
- Android SDK Platform 37
- JDK 17
- A device or Android emulator running Android 8.0 / API 26 or newer
- Access to a running MAINTAIN AI backend for live application data

## Getting Started

Clone the repository:

```bash
git clone https://github.com/jadhavdurvesh/Maintain.ai.android.git
cd Maintain.ai.android
```

Open the project in Android Studio and allow Gradle to synchronize the project.

Configure the backend URL from the application's server settings when using a backend other than the default hosted service.

Run the `app` configuration on an Android emulator or connected Android device.

## Building the APK

The debug APK can be built from the project root with Gradle:

```bash
gradle --no-daemon :app:assembleDebug
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```text
Maintain.ai.android/
├── .github/
│   └── workflows/
│       └── build-apk.yml
├── app/
│   ├── src/main/
│   │   ├── java/com/dmjgroup/maintainai/
│   │   │   ├── data/
│   │   │   │   ├── Api.kt
│   │   │   │   ├── Models.kt
│   │   │   │   └── Repository.kt
│   │   │   ├── AlertWorker.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── ReportPdf.kt
│   │   │   └── WorkOrdersScreen.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

### Key Components

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Application entry point, Compose navigation, dashboard and primary application UI |
| `AlertWorker.kt` | Periodic background alert checking and notification handling |
| `ReportPdf.kt` | Maintenance report PDF generation |
| `WorkOrdersScreen.kt` | Work-order presentation and status information |
| `data/Api.kt` | Retrofit API endpoint definitions |
| `data/Models.kt` | Backend response and application data models |
| `data/Repository.kt` | API client construction, data loading, settings persistence, and backend integration |

## Data Synchronization

The dashboard maintains live application state through the repository layer and refreshes backend data periodically. Synchronization is guarded against overlapping requests, while background alert processing is handled separately through Android WorkManager.

The application also keeps the configured server URL in Jetpack DataStore preferences, allowing the backend connection to persist between application launches.

## Notifications

MAINTAIN AI Android creates a dedicated notification channel for maintenance alerts and requests notification permission on Android versions that require runtime notification authorization.

Background alert processing uses Android WorkManager so alert checks can continue independently of the primary Compose UI.

## PDF Reports

Maintenance reports are generated directly on the Android device and exposed through Android's `FileProvider` mechanism for secure file sharing with compatible applications.

## Application Permissions

The application declares the permissions required for its core functionality:

| Permission | Purpose |
|---|---|
| `INTERNET` | Communication with the MAINTAIN AI backend |
| `POST_NOTIFICATIONS` | Display maintenance notifications on supported Android versions |

---

**MAINTAIN AI Android** — mobile visibility for predictive maintenance operations.