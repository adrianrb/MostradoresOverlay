# AGENTS.md

## Project

Android app **Mostradores** (`com.mostradores.overlay`): floating analog gauges for CPU/RAM/storage drawn over other apps. Pure Java, framework-only — **no Gradle, no AndroidX, no external dependencies**. Built directly with `aapt2` + `javac` + D8.

## Build and verify

- `./build.sh` is the only supported build. It needs `aapt2`, `javac`, `jar`, `keytool`, `zipalign`, `apksigner`, `unzip`, and `zip` on `PATH`; the script aborts and names whatever is missing.
- Toolchain (Windows-hosted): SDK `C:\Desenv\Android\Sdk` (build-tools `34.0.0`, `platforms/android-34`) and JDK 21 `C:\Desenv\JDKs\jdk-21.0.7`; from WSL these are `/mnt/c/Desenv/...`.
- Those binaries are `.exe`. From WSL use `tools/wsl-build.sh`, which creates temporary extensionless symlinks + `apksigner`/`zip` shims and calls `build.sh` with `ANDROID_JAR=tools/android.jar` / `R8_JAR=tools/r8.jar` — relative paths, because Windows `javac.exe`/`aapt2.exe` cannot read `/mnt/c/...` absolute paths.
- Tool jars ship in the repo: `tools/android.jar` and `tools/r8.jar`. Override paths with `ANDROID_JAR` / `R8_JAR`; if `tools/` is absent the script falls back to `/tmp/opencode/...`.
- Compiles with `javac --release 8`; minSdk 26 / targetSdk 34; dexed by D8 from `r8.jar`. There are no unit/instrumentation tests, lint config, or CI — the build plus `apksigner verify` / `aapt2 dump badging out/*.apk` is the whole verification.
- `build.sh` starts with `rm -rf build out`. Never edit anything in `build/`: `build/gen/R.java`, `build/sources.txt`, and `build/dex` are generated.
- New `.java` files under `src/` and new resources under `res/` are picked up automatically (`find src ...`, `aapt2 compile --dir res`).
- `tools/debug.keystore` is reused when present. Deleting it changes the signing key and breaks in-place updates on the device.
- `version.txt` is the single source of truth (`X.Y.Z`). `build.sh` reads it and derives `versionCode = X*10000 + Y*100 + Z`; do not hardcode versions in the script. Increment `Z` in `version.txt` on every commit (`Y` for features, `X` for major changes). APK filename follows the version: `out/mostradores-<X.Y.Z>-debug.apk`. Keep `AndroidManifest.xml` in sync with the same version and derived code.
- minSdk is 26 partly because the launcher icon exists only as `res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive icon). Lowering minSdk requires raster mipmaps.

## Architecture

- `MainActivity` = config UI and start/stop. `OverlayService` = foreground service that builds the entire overlay **programmatically** (there is no overlay layout XML) and updates it on a scheduled executor; `GaugeView` is a custom Canvas-drawn dial.
- Do not remove the API 34 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` branch in `OverlayService.startForegroundCompat()` — without it the service is killed on Android 14.
- `Metrics` samples CPU from `/proc/stat`, falling back to per-core cpufreq because `/proc/stat` is often blocked; RAM via `ActivityManager`; storage via `StatFs` + `StorageVolume` (internal plus the first non-primary external volume).
- SharedPreferences file `"cfg"` is the only channel between UI and service. Keys: `escala`, `opacidade`, `intervalo`, `x`, `y`, `ativo`, `cpu_fonte`; defaults are duplicated in `MainActivity` and `OverlayService`, so change both.
- `DebugActivity` + `ArquivoTeste` create/delete a 5 GB dummy file on the external SD to exercise the disk gauge. `DebugActivity` is declared in the manifest but not reachable from any UI.
- `MostradoresApp` installs a global uncaught-exception handler that opens `CrashActivity` in the separate `:crash` process using plain views (no resources there).
- User-facing strings and code identifiers are Portuguese (pt-BR). Put UI text in `res/values/strings.xml`.

## Runtime/deploy notes

- Needs overlay permission, notifications on API 33+, and `MANAGE_EXTERNAL_STORAGE` for the external-SD gauge and dummy file.
- "deploy" in a request means build and install/update on the connected device, preserving the debug signature. Previous on-device sessions (Termux) used:
  `unset LD_PRELOAD; ./build.sh && cp out/mostradores-<ver>-debug.apk /sdcard/Download/ && termux-open --content-type application/vnd.android.package-archive /sdcard/Download/mostradores-<ver>-debug.apk`
- Git has no commits yet; all files are untracked. `build/`, `out/`, the two screenshots, and `session-ses_ea4d.md` are artifacts/transcripts, not app sources.
