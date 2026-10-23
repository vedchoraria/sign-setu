# 06. Physical Android 10 ARM64 Hardware Baseline & Verification Protocol

## 1. Executive Context

The thresholds below apply to physical ARM64 phones. MediaPipe Holistic, CameraX ImageAnalysis (640x360), the Canvas avatar and SQLCipher run on the phone's SoC and GPU delegate, so an emulator is not a valid benchmark target.

This protocol specifies the standard operating procedure, acceptance thresholds, device matrix, and `adb` instrumentation commands for QA and security test engineers.

---

## 2. Reference Target Device Matrix

In accordance with project requirements (Android 10+, >= 3 GB RAM, arm64-v8a architecture, budget-to-midrange departmental or personal devices):

| Tier | Reference SoC | Typical Model | RAM / Storage | Target OS | Primary Validation Purpose |
|---|---|---|---|---|---|
| **Tier 1 (Minimum Floor)** | MediaTek Helio G80 / Snapdragon 665 | Redmi 9 Prime / Vivo Y20 / Realme 5 | 3 GB / 32 GB | Android 10 (API 29) | Performance floor, memory pressure, CPU thermal throttling |
| **Tier 2 (Mid-Range Reference)** | Snapdragon 720G / MediaTek Helio G95 | Redmi Note 9 Pro / Samsung Galaxy M21 | 4 GB / 64 GB | Android 11 / 12 (API 30/31) | Representative departmental device profile |
| **Tier 3 (Modern Target)** | Snapdragon 695 / Dimensity 6020 | Moto G62 / Redmi 12 5G | 6 GB / 128 GB | Android 13 / 14 (API 33/34) | Biometric API 30+ strong auth, GPU delegate acceleration |

---

## 3. Benchmark Procedures & Acceptance Thresholds

### 3.1 Cold Startup Latency
- **Objective:** Ensure the application launches and presents the interactive Start / Unlock interface without perceptibly lagging an officer in an emergency triage situation.
- **Requirement:** (Cold start < 2.0 s; target on Tier 1 hardware < 1.2 s).
- **Execution Command:**
  ```bash
  adb shell am force-stop in.signsetu
  adb shell am start-W -n in.signsetu/app.signsetu.MainActivity
  ```
- **Measurement:** Record `TotalTime` and `WaitTime` output by ActivityManager across 5 cold-start runs (clearing OS memory cache between runs: `adb shell echo 3 > /proc/sys/vm/drop_caches` when rooted, or device reboot).
- **Acceptance Gate:** Median cold `TotalTime` <= 1200 ms; warm resume <= 400 ms.

---

### 3.2 Steady-State Vision & Sign Capture Frame Rate
- **Objective:** Guarantee smooth camera tracking, accurate landmark extraction, and responsive skeletal overlay during sign windows.
- **Requirement:** Steady-state analysis >= 20 fps during active sign windows; UI rendering >= 58 fps without jank.
- **Execution Command:**
  ```bash
  # Reset frame stats
  adb shell dumpsys gfxinfo in.signsetu reset
  # [Execute 30 seconds of continuous signing in Stage mode]
  adb shell dumpsys gfxinfo in.signsetu
  ```
- **Acceptance Gate:**
  - Janky frames <= 5.0% of total rendered frames.
  - 90th percentile frame rendering time <= 16.6 ms.
  - Video analysis pipeline processes frames at >= 20 fps without dropping wrist landmark tracking.

---

### 3.3 Thermal Envelope & Battery Soak Test
- **Objective:** Confirm the device does not throttle severely, overheat, or experience battery drain during an outdoor field interview.
- **Requirement:** Battery and SoC temperature rise <= 5.0 °C over 15 minutes of continuous camera mirror and stage operation in an ambient temperature of ~30–35 °C.
- **Execution Procedure:**
  1. Charge test device to 80% capacity and disconnect charger (preventing battery charging heat).
  2. Baseline thermal readout:
     ```bash
     adb shell dumpsys battery | grep temperature
     adb shell cat /sys/class/thermal/thermal_zone*/temp
     ```
  3. Start a Session, switch to Stage mode with Camera active, and conduct continuous signing / card selection for 15 minutes.
  4. Capture end thermal readout and battery discharge percentage.
- **Acceptance Gate:**
  - `Δ temperature` <= 5.0 °C.
  - Battery discharge rate <= 6% over 15 minutes (<= 24%/hr under continuous maximum load).
  - Thermal throttling status must remain `NONE` or `LIGHT` (no `SEVERE` throttling or CPU core shutdown).

---

### 3.4 Runtime Memory Footprint (PSS)
- **Objective:** Verify SQLCipher, CameraX, MediaPipe Holistic, and Jetpack Compose stay strictly within bounded memory, avoiding Out-Of-Memory (OOM) kills on 3 GB devices.
- **Requirement:** (Total PSS < 250 MB under full stage operation without local Gemma model; memory immediately returned upon leaving stage).
- **Execution Command:**
  ```bash
  adb shell dumpsys meminfo in.signsetu
  ```
- **Acceptance Gate:**
  - **TOTAL PSS:** <= 250,000 KB (244 MB).
  - **Native Heap:** <= 95,000 KB.
  - **Dalvik Heap:** <= 65,000 KB.
  - **Graphics / EGL:** <= 65,000 KB.
  - **Post-Session Memory Reclamation:** Exiting Stage back to StartScreen must drop Total PSS by at least 40 MB within 5 seconds as camera and landmark buffers are disposed.
