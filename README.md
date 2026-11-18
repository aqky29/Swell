# Swell

A sleek, high-fidelity music streaming player for Android, crafted with Jetpack Compose, custom AGSL shaders, and fluid iOS-inspired liquid glass aesthetics.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)

---

## Highlights

### 🌊 Liquid Glass & Fluid Navigation
- **AGSL RuntimeShader Effects**: Lens refraction, chromatic aberration, and dynamic surface vibrancy powered by Android 13+ RuntimeShader.
- **Morphing Capsule Indicator**: Smoothly morphing tab pill with real-time refraction and fluid transitions.
- **Cupertino-Inspired Aesthetics**: Translucent navigation bar and glass controls designed to feel responsive and natural.

### 🎤 Synchronized Motion Lyrics
- **Apple Music-Style Animations**: Fluid line animations, held-word swell, dynamic blur, and vocal lift.
- **Backing Vocals & Duet Support**: Secondary vocal tracks promoted and rendered in real-time.
- **Multi-Source Fetching**: Automatic lookup across providers (LRCLIB, KuGou, BiniLyrics, BetterLyrics, Unison, and more).

### 🎧 High-Fidelity & Versatile Playback
- **Lossless & Hi-Res Streams**: FLAC/ALAC playback through configurable pluggable sources with fallback to YouTube Music.
- **Audio Precision**: Gapless transitions, true crossfade (0–12s), and pitch-preserving speed controls (0.5×–2.0×).
- **Offline & Local Library**: Save audio with embedded metadata, or scan and play tracks from device storage.
- **Automix**: Seamless beat-matched transitions.

### ⚡ Performance & Battery Focused
- **Low Overhead**: Strict lifecycle management stops idle redraws, keeps GPU usage low, and sleeps canvas loops when playback is paused.
- **Lightweight Architecture**: Lean Android-first codebase stripped of unused cross-platform modules for faster builds and smaller APK size.

### 🌐 Ecosystem & Sync
- **Spotify Integration**: Import and listen to your Spotify playlists and library.
- **Discord Rich Presence**: Live playback status directly in Discord.
- **Scrobbling**: Native support for Last.fm and ListenBrainz.

---

## Tech Stack

| Layer | Technologies |
|---|---|
| **Language & Platform** | Kotlin Multiplatform (Android-focused) |
| **UI & Animations** | Jetpack Compose, Material 3, AGSL Shaders |
| **Media Playback** | AndroidX Media3 / ExoPlayer |
| **Networking** | Ktor, Kotlinx Serialization |
| **Storage & State** | DataStore Preferences, Kotlinx Coroutines & Flows |

---

## Building from Source

### Prerequisites
- JDK 17 or higher
- Android SDK (API Level 34+)
- Android Studio Ladybug or later (recommended)

### Build Commands
Clone the repository and build using the Gradle wrapper:

```bash
# Clone the repository
git clone https://github.com/Svel2/Swell.git
cd Swell

# Build debug APK
./gradlew :app:assembleDebug

# Install directly to connected device / emulator
./gradlew :app:installDebug
```

The output APK will be available in `app/build/outputs/apk/debug/`.

---

## Attribution & Acknowledgements

Swell builds upon and acknowledges the following open-source projects and communities:
- [BitChord](https://github.com/kushagrasinghx/BitChord) & [Spotube](https://github.com/KRTirtho/Spotube) for foundational client concepts.
- [am-lyrics](https://github.com/binimum/am-lyrics) by binimum for word-synced lyrics animation inspiration.
- The open lyrics community: [LRCLIB](https://lrclib.net), [KuGou](https://kugou.com), [BetterLyrics](https://github.com/better-lyrics/better-lyrics), and others.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for the full license text.
