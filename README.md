# Haskell REPL for Android

A native Android app that runs a full **GHCi 9.12.2** REPL on your phone, offline, with an optional voice-to-code input.

Built with Kotlin, Jetpack Compose, and Termux-packaged GHC cross-compiled for ARM64.

## Features

- **Full GHCi REPL** — evaluate arbitrary Haskell expressions, `:type`, `:info`, `:kind`, `:load` modules
- **Fully offline** — GHCi runs natively on-device, no server, no internet needed for evaluation
- **Custom Haskell keyboard** — dedicated keys for `->`, `::`, `$`, `\`, `|`, `{`, `}`, and other Haskell operators
- **Voice input** — speak Haskell code and have it transcribed using local whisper.cpp or Groq's cloud LLM
- **Landscape & portrait** — adaptive keyboard sizing for both orientations
- **Dark terminal theme** — VSCode-inspired color scheme

## Architecture

```
┌─────────────────────────────────────┐
│  Jetpack Compose UI (ReplScreen)    │
│  ┌──────────┐ ┌──────────────────┐  │
│  │InputArea │ │  OutputArea      │  │
│  │(text fld)│ │  (LazyColumn)    │  │
│  └──────────┘ └──────────────────┘  │
│  ┌────────────────────────────────┐ │
│  │  Custom Haskell Keyboard       │ │
│  └────────────────────────────────┘ │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  ReplService (Foreground Service)   │
│  ┌────────────────────────────────┐ │
│  │  GHCiProcess → PTY → GHCi 9.12 │ │
│  └────────────────────────────────┘ │
│  ┌────────────────────────────────┐ │
│  │  OutputParser (ANSI strip,     │ │
│  │  prompt detection, error parse)│ │
│  └────────────────────────────────┘ │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  JNI Layer (libpty_bridge.so)       │
│  ┌──────────┐ ┌──────────────────┐  │
│  │ openpty()│ │ whisper.cpp      │  │
│  │ setwinsz │ │ tiny.en Q5_1     │  │
│  └──────────┘ └──────────────────┘  │
└─────────────────────────────────────┘
```

## How GHCi Runs on Android

GHCi is **not cross-compiled from source** by this project. Instead, a pre-built GHC 9.12.2 binary from the [Termux package repository](https://github.com/termux/termux-packages/tree/master/packages/ghc) is bundled into the APK.

### Bundling process

The Gradle task `fetchGhci` (`app/fetchGhci.gradle.kts`) downloads 6 `.deb` packages:

| Package | Purpose |
|---------|---------|
| `ghc_9.12.2-2_aarch64.deb` | GHC compiler, GHCi, base libraries, package database |
| `libiconv_1.18-1_aarch64.deb` | Text encoding conversion (Haskell `text` needs it) |
| `libffi_3.5.2_aarch64.deb` | Foreign Function Interface (Haskell FFI to C) |
| `libgmp_6.3.0-2_aarch64.deb` | Arbitrary-precision arithmetic (Haskell `Integer`) |
| `ncurses_6.6_aarch64.deb` | Terminal handling (GHCi line editing) |
| `libandroid-posix-semaphore_0.1-4_aarch64.deb` | POSIX semaphore support on Android |

Each `.deb` is extracted (`ar` → `data.tar.xz`), and the contents are placed into `app/src/main/assets/ghci-root/`. Dependency shared libraries (`.so` files) are also copied to `app/src/main/jniLibs/arm64-v8a/`.

At **first launch**, `GhciExtractor` unpacks everything from APK assets into the app's internal storage (`filesDir/ghci-root/`). Subsequent launches skip extraction.

### Runtime launch

`GHCiProcess` launches GHCi through these steps:

1. **Opens a PTY** (pseudo-terminal) via JNI — calls `posix_openpt()`, `grantpt()`, `unlockpt()`
2. **Creates a shell script** that sets `LD_LIBRARY_PATH` to include the bundled `.so` files and the GHC library directory
3. **Launches GHC via Android's dynamic linker**: `/system/bin/linker64 /path/to/ghc-9.12.2 --interactive -fbyte-code -ignore-dot-ghci`
4. **Redirects stdin/stdout/stderr** through the PTY slave, while the app reads/writes the PTY master
5. **Patches the GHC settings file** to replace the C compiler path (`clang`) with `/system/bin/true`, since Android has no host C compiler at the expected location
6. After startup, sends `:set prompt "GHCI_END_MARKER"` so the output parser can reliably detect where each GHCi response ends

### Why linker64?

Android enforces SELinux policies that prevent executing binaries from the app's data directory directly. The workaround is to invoke the binary through `/system/bin/linker64`, Android's own dynamic linker, which is already whitelisted.

### PTY instead of pipes

A PTY (pseudo-terminal) is used instead of plain pipes because:
- GHCi detects whether it's connected to a TTY and adjusts its behavior (colored output, line editing, prompt)
- PTY provides proper terminal semantics (window size, signal handling)
- The PTY slave acts as the "other end" for the process's stdin/stdout/stderr

## Voice Input Pipeline

Voice input converts spoken words to Haskell code through a layered pipeline:

1. **AudioRecord** captures 16kHz mono PCM audio (max 10 seconds)
2. **whisper.cpp** (tiny.en, Q5_1 quantized, ~32 MB) transcribes speech to text locally via JNI
3. **Groq API** (optional) sends the transcription to `llama-3.3-70b-versatile` with a Haskell-aware system prompt that maps spoken operators ("arrow" → `->`, "double colon" → `::`, etc.)
4. **Local fallback** applies a word-map of 50+ speech-to-operator mappings when Groq is unavailable

Press-and-hold the mic button to record, release to transcribe. The transcribed code is inserted into the input field for review before execution — nothing is auto-executed.

## Build

### Prerequisites

- Android SDK 35 with NDK
- JDK 17
- Git (for the whisper.cpp submodule)

### Steps

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/your-org/haskell-repl-android.git
cd haskell-repl-android

# Download and bundle GHCi (required before building the APK)
./gradlew fetchGhci

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

The `fetchGhci` task downloads ~200 MB of Termux packages. It only needs to run once (or when upgrading the GHC version in `app/fetchGhci.gradle.kts`).

### Optional: Groq API Key

Voice-to-code via Groq is optional. To use it:
1. Get a free API key at [console.groq.com](https://console.groq.com)
2. In the app, tap the ⚙ gear icon next to the mic button and enter the key

Without a Groq key, voice input uses the local word-map fallback (basic operator conversion).

## Project Structure

```
app/
├── build.gradle.kts              # Android 35, Compose, CMake, arm64-v8a only
├── fetchGhci.gradle.kts          # Downloads Termux GHC .deb packages
└── src/main/
    ├── AndroidManifest.xml       # App permissions and configuration
    ├── jni/
    │   ├── CMakeLists.txt        # NDK build for pty_bridge + whisper.cpp
    │   ├── pty_bridge.c          # JNI: PTY operations + whisper bindings
    │   └── whisper.cpp/          # Git submodule (ggml-org/whisper.cpp)
    └── java/com/example/haskellrepl/
        ├── MainActivity.kt       # Compose entry point
        ├── extract/
        │   └── GhciExtractor.kt  # Runtime asset extraction
        ├── service/
        │   ├── ReplService.kt    # Foreground service, state machine
        │   ├── GHCiProcess.kt    # PTY-based GHCi process management
        │   ├── PtyBridge.kt      # JNI wrapper for PTY operations
        │   └── OutputParser.kt   # GHCi output parsing, prompt detection
        ├── ui/
        │   ├── ReplScreen.kt     # Main REPL composable
        │   ├── HaskellKeyboard.kt # Custom on-screen keyboard
        │   └── theme/            # VSCode-dark terminal theme
        ├── voice/
        │   ├── VoiceInputManager.kt
        │   ├── WhisperEngine.kt   # JNI wrapper for whisper.cpp
        │   ├── GroqCodeConverter.kt
        │   └── ModelDownloader.kt
        └── learning/
            ├── ErrorExplainer.kt
            ├── TypeHintProvider.kt
            └── TutorialSnippets.kt
```

## Technical Constraints

- **ARM64 only** — no x86 or 32-bit ARM support (GHC packages are aarch64-only)
- **Android 8+** (API 26) — required for PTY support and foreground service types
- **~900 MB** of storage for the extracted GHCi environment on first launch
- **No `cabal`/`stack`** — only GHCi interactive evaluation; no package installation on-device
- **No Template Haskell** — `-fbyte-code` flag disables native code generation to avoid W^X conflicts on Android

## License

Apache 2.0
