# Chess

An Android chess app in the style of chess.com, powered by **Stockfish 19**.

* **Play bots** – 9 computer opponents from ~400 to 3190 Elo (Stockfish's `UCI_Elo` limiter, think-time limits and deliberate "sloppiness" for the beginner levels), with undo, hints, optional clocks and increments.
* **Play friends** – online games shared by a 6-character code (Firestore), plus pass-and-play on one device.
* **Unlimited game analysis** – every finished or imported (PGN) game can be reviewed as often as you like: per-move classification (best → blunder), accuracy for both sides, eval bar and graph, engine lines, best-move arrows, three analysis depths. Nothing is capped or paywalled.
* **Sign in with Google** – Credential Manager + Firebase Auth. Guests can still play offline.

Application ID / package: `com.github.lukelloyd1985.chess`

## Layout

| Module | What |
| --- | --- |
| `core/` | Pure Kotlin/JVM: move generation, SAN, PGN, FEN, UCI parsing, bots, analysis maths. Unit-tested (perft + rules). |
| `app/` | Android app (Jetpack Compose). `src/main/cpp/stockfish` is the vendored Stockfish 19 source, built with CMake and shipped as `libstockfish.so`, which the app runs as a UCI child process. |

## Building

Requirements: JDK 17, Android SDK 35, NDK `27.2.12479018`, CMake `3.22.1`.

```sh
./gradlew :core:test          # rules engine tests
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug
```

Stockfish 19 needs its NNUE network (`nn-1a298aa575a0.nnue`). The build downloads it from
`tests.stockfishchess.org` and verifies the SHA-256 prefix. To build offline, put the file in `app/nnue/`.
Only 64-bit ABIs (`arm64-v8a`, `x86_64`) are built.

## Google sign-in & online play (Firebase setup)

The app builds without this, but sign-in and online games stay disabled until you add your own Firebase project:

1. Create a Firebase project and add an Android app with package `com.github.lukelloyd1985.chess` and your debug/release **SHA-1**.
2. Enable **Authentication → Google** and **Firestore**.
3. Download `google-services.json` into `app/` (it is git-ignored). The Google Services plugin is applied automatically when the file exists, and the web client ID is picked up from it.
4. Publish `firestore.rules` to your Firestore database.

Notes on online play: moves are validated by the clients (Firestore rules enforce turn order and one-move-at-a-time appends, but cannot validate chess legality), and online games are untimed.

## Licence

Stockfish is GPLv3 (see `app/src/main/cpp/stockfish/Copying.txt`); because the app bundles and runs it, distribute the app under GPLv3-compatible terms and provide this source.
