# Swell

Swell is a music streaming and local playback app written in Kotlin with Jetpack Compose, Compose Multiplatform, and AndroidX Media3. It targets Android phones and tablets, Android TV, Wear OS, and JVM desktop (macOS, Linux, and Windows).

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20%26%20Multiplatform%20Compose-4285F4?style=flat-square&logo=jetbrains&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Android minSdk](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)

## Project Structure

The Gradle workspace in `settings.gradle.kts` splits shared code from platform shells:

- `:app`: Android phone and tablet application (`com.music.swell`)
- `:tv` and `:wearos`: Android TV and Wear OS companion targets
- `:desktopApp` and `:desktopUi`: JVM desktop app and UI (see [DESKTOP.md](DESKTOP.md))
- `:shared` and `:sharedUi`: Kotlin Multiplatform domain logic, lyrics sources, and shared UI

Android modules (`:app`, `:tv`, `:wearos`) are included only when Gradle detects an Android SDK via `local.properties` (`sdk.dir`), `ANDROID_HOME`, or `ANDROID_SDK_ROOT`.

## What It Includes

### Playback and Audio Pipeline
Swell plays progressive streams, HLS, and DASH manifests through Media3 / ExoPlayer, backed by a vendored FFmpeg decoder (`libffmpegJNI.so`) for codecs the device may lack natively (ALAC, AC-4, E-AC-3, AC-3, TrueHD, and DTS). It supports gapless playback, a configurable crossfade window (0 to 12 seconds), ONNX-based Automix transitions using the `Beat This!` beat/downbeat model, local device scanning, SMB2/3 file shares via `smbj`, and Google Cast.

### UI and Shaders
On Android 13 (API 33) and newer, Swell uses custom AGSL `RuntimeShader` effects (`BackdropEffectScope`) for lens refraction and morphing glass controls, combined with `Haze` frosted blur on supported surfaces and scrim fallbacks on older Android versions.

### Synced Lyrics
Lyrics lookup runs across configurable providers in `LyricsSource`: `lrc.red`, `BiniLyrics`, `BetterLyrics`, `BetterLyrics Portato`, `PaxSenix`, `LyricsPlus`, `Unison`, `YouTube captions`, `YouTube Music`, `KuGou`, `LRCLIB`, `Musixmatch`, and `Genius`. Word-synced providers drive per-word timing and background vocal lines.

### Accounts and Integrations
Swell supports Spotify playlist and library import, live status updates via Discord Rich Presence (Ktor WebSockets), Last.fm scrobbling, ListenBrainz, and Listen Together party sessions.

## Tech Stack

| Layer | Technologies |
|---|---|
| Language and Multiplatform | Kotlin `2.3.20`, Kotlin Multiplatform, Kotlinx Coroutines |
| UI and Graphics | Jetpack Compose, Compose Multiplatform `1.10.3`, Material 3, AGSL `RuntimeShader`, Haze `1.3.1`, Coil 3 |
| Media Playback | AndroidX Media3 / ExoPlayer `1.11.0`, vendored FFmpeg decoder, ONNX Runtime `1.28.0` |
| Networking and Sources | Ktor `3.5.2`, Kotlinx Serialization, InnerTubeX, NewPipeExtractor, QuickJS, `smbj` |
| Storage and Auth | Android `SharedPreferences`, `androidx.security:security-crypto` |

## Building from Source

### Prerequisites
- Android target: JDK 17, Android SDK (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`), NDK `28.2.13676358`, and CMake `3.22.1`
- Desktop target: JDK 21 (see [DESKTOP.md](DESKTOP.md) for Linux native dependencies and platform notes)

### Commands

```bash
git clone https://github.com/aqky29/Swell.git
cd Swell

# Android dev flavor (installed alongside prod as app.swell.music.dev)
./gradlew :app:assembleDevDebug
./gradlew :app:installDevDebug

# Optional: build a single ABI locally for faster compiles
./gradlew :app:assembleDevDebug -Pswell.abi=arm64-v8a

# Desktop JVM target
./gradlew :desktopApp:run
```

Debug APKs are written under `app/build/outputs/apk/dev/debug/` (or `prod/debug/` for `:app:assembleProdDebug`). Signing release builds uses `keystore.properties` (see `keystore.properties.example`), falling back to the debug key when no release keystore is present.

## Attribution and Acknowledgements

Swell builds upon and acknowledges the following open-source projects:
- [BitChord](https://github.com/kushagrasinghx/BitChord) and [Spotube](https://github.com/KRTirtho/Spotube) for foundational client architecture.
- [am-lyrics](https://github.com/binimum/am-lyrics) by binimum for word-synced lyrics animation concepts.
- Lyrics data providers including [LRCLIB](https://lrclib.net), [KuGou](https://kugou.com), and [BetterLyrics](https://github.com/better-lyrics/better-lyrics).

## License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for the full license text.
