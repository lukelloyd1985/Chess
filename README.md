# Chess

An Android chess app in the style of chess.com, powered by **Stockfish 19**.

* **Play bots** – 9 computer opponents from ~400 to 3190 Elo (Stockfish's `UCI_Elo` limiter, think-time limits and deliberate "sloppiness" for the beginner levels), with undo, hints, optional clocks and increments.
* **Play friends** – online games shared by a 6-character code (Appwrite), plus pass-and-play on one device.
* **Unlimited game analysis** – every finished or imported (PGN) game can be reviewed as often as you like: per-move classification (best → blunder), accuracy for both sides, eval bar and graph, engine lines, best-move arrows, three analysis depths. Nothing is capped or paywalled.
* **Sign in with Google** – Credential Manager bridged into an Appwrite session. Guests can still play offline.

Application ID / package: `com.github.lukelloyd1985.chess`

## Layout

| Module | What |
| --- | --- |
| `core/` | Pure Kotlin/JVM: move generation, SAN, PGN, FEN, UCI parsing, bots, analysis maths. Unit-tested (perft + rules). |
| `app/` | Android app (Jetpack Compose). `src/main/cpp/stockfish` is the vendored Stockfish 19 source, built with CMake and shipped as `libstockfish.so`, which the app runs as a UCI child process. |

## Building

Requirements: JDK 17, Android SDK 36, NDK `27.2.12479018`, CMake `3.22.1` (AGP 9.3, Kotlin 2.4, Gradle 9.5).

```sh
./gradlew :core:test          # rules engine tests
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug
```

Stockfish 19 needs its NNUE network (`nn-1a298aa575a0.nnue`). The build downloads it from
`tests.stockfishchess.org` and verifies the SHA-256 prefix. To build offline, put the file in `app/nnue/`.
Only 64-bit ABIs (`arm64-v8a`, `x86_64`) are built.

## CI

Both workflows run from the Actions tab.

* `android-build.yml` - `debug` or `release` APK on demand; publishing a GitHub Release builds the signed release APK + AAB,
  attaches them to the release and uploads to Google Play's closed-testing track.
* `deploy-appwrite.yml` - creates/updates the database table (via `bootstrap-tables.mjs`, additive only), pushes the
  `maintenance` Function, sets its `GOOGLE_WEB_CLIENT_ID` variable and prunes old deployments.

Repository secrets (optional unless you want that feature):

| Secret | Used by | Purpose |
| --- | --- | --- |
| `APPWRITE_ENDPOINT` | both | e.g. `https://fra.cloud.appwrite.io/v1` |
| `APPWRITE_API_KEY` | deploy | Server API key (scopes: databases/tables + columns + indexes read/write, functions read/write, `rules.read`, execution/variables) |
| `GOOGLE_WEB_CLIENT_ID` | both | OAuth **Web application** client ID - the app and the Function must agree on it |
| `DEBUG_KEYSTORE_BASE64`, `DEBUG_KEYSTORE_PASSWORD` | build | Stable debug keystore (alias `chessdebug`) so its SHA-1 can be registered once |
| `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD` | build | Release keystore (alias `chess`) |
| `PLAY_SERVICE_ACCOUNT_JSON` | build | Play Console service account key for publishing |

The Appwrite project ID is not secret: set `"projectId"` in `appwrite/appwrite.json` (the app and the deploy workflow both
read it from there). Until you do, sign-in and online play report "not configured"; offline play works regardless.

## Backend setup (Appwrite)

1. Create an Appwrite Cloud project, put its ID in `appwrite/appwrite.json`, and add `APPWRITE_ENDPOINT` / `APPWRITE_API_KEY`
   as repository secrets.
2. Console -> **Add Platform -> Android** twice: `com.github.lukelloyd1985.chess` and `com.github.lukelloyd1985.chess.debug`.
3. In Google Cloud Console create an OAuth **Web application** client (-> `GOOGLE_WEB_CLIENT_ID`) plus Android clients for each
   package name + signing SHA-1 (debug keystore, release keystore, and Play App Signing's certificate).
4. Run **Deploy Appwrite (Functions)**. It creates the `chess` database and `games` table and deploys `maintenance`
   (Google sign-in, join-game, delete-account).

How online play works: the creator makes a `games` row whose ID is the share code; the friend "joins" through the
`maintenance` Function, which seats them and widens the row's permissions to both players. Moves are appended to the shared
row by the players' devices and followed through Realtime. Moves are validated by the clients only (Appwrite permissions
restrict the row to its two players but cannot check chess legality), and online games are untimed.

## Publishing to Google Play

1. **Host the privacy policy and account deletion pages** Play requires: `docs/privacy.html` and `docs/delete-account.html`
   are published by `.github/workflows/pages.yml` (only when `docs/` changes, on pushes to `main` or a manual run). Enable
   Settings -> Pages -> *Source* -> **GitHub Actions**; they are then served at
   `https://lukelloyd1985.github.io/Chess/privacy.html` and `.../delete-account.html` - use those URLs in Play Console
   (privacy policy, and the account-deletion link in the Data safety section). Update the pages if the data the app collects changes.
2. The first release of a new app must be created manually in Play Console (with Play App Signing); after that a
published GitHub Release uploads the AAB to the closed-testing (`alpha`) track via `publishReleaseBundle`, and
`publishListing` pushes the text under `app/src/main/play/`. Add icon / feature graphic / screenshots under
`app/src/main/play/listings/en-US/graphics/` to publish them too.

## Licence

This project is licensed under the **GNU General Public License v3.0** (see `LICENSE`). The app bundles and runs
[Stockfish](https://stockfishchess.org) (GPLv3, see `app/src/main/cpp/stockfish/Copying.txt`), so the whole app is distributed
under the same licence and its source must be made available to anyone who receives the app.
