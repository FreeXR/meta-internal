# Meta-Internal – Hidden Debug Menu Access for Meta Quest

## Overview

**Meta-Internal** is an LSPosed module that unlocks the hidden **Internal Settings** debug menu inside the Meta Quest `vrshell` system app.  
This panel contains advanced diagnostics and options normally reserved for internal builds.

⚠️ **Research only:** This project is for **educational and research purposes**. It does **not** enable functional controls – most dropdowns and blue action buttons do **not work** on retail devices.

---

## Prerequisites

- Meta Quest device (Quest 2, Quest 3/3S, or Quest Pro) with **Magisk** root (v24+).
- **Zygisk** enabled in Magisk settings.
- **LSPosed** (Zygisk version) installed as a Magisk module.
- **LSPosed Manager** app (from the LSPosed ZIP).
- **ADB access** from a PC.
- Meta-Internal module APK (from this repo).

---

## Installation

### 1. Enable Zygisk
- Open **Magisk app** → **Settings** → enable **Zygisk** → reboot.

### 2. Install LSPosed Framework
- Download **LSPosed Zygisk release ZIP** from [LSPosed GitHub Releases](https://github.com/LSPosed/LSPosed/releases).
- In Magisk → **Modules** → *Install from storage* → select the LSPosed ZIP.
- Reboot.

### 3. Install LSPosed Manager
- Extract the LSPosed ZIP on your PC.
- Find the **LSPosed Manager APK** inside.
- Install it with ADB:
  ```bash
  adb install LSPosedManager.apk
  ```
- Open LSPosed Manager to confirm framework is active.

### 4. Install Meta-Internal
- Build from source or download the release APK.

- Download Debug Release [here](https://github.com/FreeXR/meta-internal/raw/refs/heads/main/QuestUnlock/app/build/outputs/apk/debug/app-debug.apk)

- Install with ADB:
  ```bash
  adb install app-debug.apk
  ```

### 5. Enable Module in LSPosed
- Open **LSPosed Manager** → **Modules** → enable **Meta-Internal**.
- In the module scope, add:
  - `com.oculus.vrshell`

 - In the app click **restart vrshell** unless you used BuildAndInstall.cmd
  
---

## Usage

- After restarting vrshell, the **Internal Settings** debug panel will be accessible inside `VRShell Patcher` app , click the button to make dogfood app show the hidden settings.
- **Limitation:** Dropdowns and blue buttons currently do **not** function. 

---

## Known Issues

- Dropdown menus and blue buttons are non-functional.

---

## Disclaimer

- Rooting and modifying system apps may brick your device and void warranty.
- This project is **not supported by Meta**.

---

## Contributing

Contributions are welcome to improve module stability or documentation.  
Focus should remain on **XR system research** and not consumer features.

---
