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

Requirements: JDK 17, Android SDK 36, NDK `27.2.12479018`, CMake `3.22.1` (versions mirror the
MyTaskList repo's working CI: AGP 9.3, Kotlin 2.4, Gradle 9.5).

```sh
./gradlew :core:test          # rules engine tests
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug
```

Stockfish 19 needs its NNUE network (`nn-1a298aa575a0.nnue`). The build downloads it from
`tests.stockfishchess.org` and verifies the SHA-256 prefix. To build offline, put the file in `app/nnue/`.
Only 64-bit ABIs (`arm64-v8a`, `x86_64`) are built.

## CI (`.github/workflows/android-build.yml`)

Copied from MyTaskList. Run it from the Actions tab (`debug` or `release` APK), or publish a GitHub
Release to build the signed release APK + AAB, attach them to the release and upload to Google Play's
closed-testing track. Repository secrets (all optional unless you want that feature):

| Secret | Purpose |
| --- | --- |
| `FIREBASE_PROJECT_ID`, `FIREBASE_API_KEY`, `FIREBASE_SENDER_ID` | Firebase project values (shared by debug + release) |
| `FIREBASE_APPLICATION_ID` / `FIREBASE_APPLICATION_ID_DEBUG` | Firebase App ID of the `com.github.lukelloyd1985.chess` / `.debug` Android app |
| `GOOGLE_WEB_CLIENT_ID` | OAuth **Web application** client ID used by Credential Manager |
| `DEBUG_KEYSTORE_BASE64`, `DEBUG_KEYSTORE_PASSWORD` | Stable debug keystore (alias `chessdebug`) so its SHA-1 can be registered once |
| `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD` | Release keystore (alias `chess`) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console service account key for publishing |

Without the Firebase secrets the app still builds and plays offline; sign-in and online games show "not configured".

## Firebase setup (Google sign-in & online play)

1. Create a Firebase project; register two Android apps: `com.github.lukelloyd1985.chess` and
   `com.github.lukelloyd1985.chess.debug`, each with its signing SHA-1. Skip the `google-services.json` download -
   the app initialises Firebase manually from the secrets above.
2. Enable **Authentication -> Google** and **Firestore**, and publish `firestore.rules`.
3. In Google Cloud Console create the OAuth **Web application** client (`GOOGLE_WEB_CLIENT_ID`). The "Android key
   (auto created by Firebase)" is `FIREBASE_API_KEY`.

Notes on online play: moves are validated by the clients (Firestore rules enforce turn order and one-move-at-a-time appends, but cannot validate chess legality), and online games are untimed.

## Publishing to Google Play

The first release of a new app must be created manually in Play Console (with Play App Signing); after that a
published GitHub Release uploads the AAB to the closed-testing (`alpha`) track via `publishReleaseBundle`, and
`publishListing` pushes the text under `app/src/main/play/`. Add icon / feature graphic / screenshots under
`app/src/main/play/listings/en-US/graphics/` to publish them too.

## Licence

Stockfish is GPLv3 (see `app/src/main/cpp/stockfish/Copying.txt`); because the app bundles and runs it, distribute the app under GPLv3-compatible terms and provide this source.
