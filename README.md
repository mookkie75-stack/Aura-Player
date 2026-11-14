# Aura Player

A Kotlin + Jetpack Compose music-player starter for Android 8.0+ (tested target baseline: Android 13 and newer). It includes a Media3 playback service, system audio focus/noisy-device handling, a compact low-shelf PCM biquad processor, a glass-style player screen, a MediaStore library, document-picker playback, Thai/English UI labels, and an ID3/Vorbis tag editor with optional cover art.

## Project layout

```text
.
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/auraplayer/MainActivity.kt
        ├── java/com/auraplayer/playback/
        │   ├── AudioLibrary.kt
        │   ├── BiquadBassProcessor.kt
        │   └── MusicPlaybackService.kt
        └── res/values*/strings.xml
```

## Build

Use JDK 17 and Android SDK 35. Open the root folder in Android Studio and sync Gradle, or run `./gradlew :app:assembleDebug` after adding the Gradle wrapper. The first run asks for audio-library permission; individual files can also be opened with Android's system document picker.

## Notes

- `BiquadBassProcessor` is a native PCM low-shelf DSP stage: `BiquadFilterNode` itself is part of the Web Audio API and is not an Android/Kotlin API. The filter is wired into Media3's audio sink and uses a modest 5.5 dB low-frequency shelf.
- Audio focus is delegated to ExoPlayer, and `setHandleAudioBecomingNoisy(true)` pauses on headphone disconnection. Playback runs in a Media3 `MediaSessionService` foreground service.
- The tag editor edits common title/artist/album tags and optional embedded artwork. Writing depends on the selected document provider granting write access and on the audio format being supported by jaudiotagger; keep a backup of original files.
- System volume keys control playback volume. There is no in-app hardware volume control, night-playback mode, animal artwork, or spinning-disc animation.
- Thai labels are intentionally kept free of the excluded Thai character specified in the brief.
