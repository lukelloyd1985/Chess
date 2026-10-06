# Play Store listing assets

Ready-to-upload assets for Play Console's Store listing:

| File | Use | Size |
| --- | --- | --- |
| `icon-512.png` | App icon | 512x512 |
| `feature-graphic-1024x500.png` | Feature graphic | 1024x500 |
| `screenshot-1-sign-in.png` | Phone screenshot | 1080x1920 |
| `screenshot-2-game.png` | Phone screenshot | 1080x1920 |
| `screenshot-3-analysis.png` | Phone screenshot | 1080x1920 |
| `screenshot-4-appearance.png` | Phone screenshot | 1080x1920 |

The icon and feature graphic use the app's real launcher knight and brand colours. The four
screenshots are **recreations** of the real screens (`SignInScreen`, `GameScreen`,
`AnalysisScreen`, `SettingsScreen`) with sample data, built as HTML from the app's own colours and
the same bundled piece artwork - they are not captures from a running device. Swap in real device
screenshots once you have them if you prefer; these exist so the listing isn't blocked on capturing them.

## Regenerating

```sh
python3 src/generate.py          # rewrites the HTML pages in src/
npm install -g playwright        # if not already available
node src/render.js               # screenshots each page to the PNGs in this folder
```

`src/generate.py` holds the layouts (and reads the piece artwork from `app/src/main/assets/pieces/`);
`src/render.js` opens each page at its exact target size in headless Chromium (set `CHROMIUM_PATH`
to use a specific browser binary).

## CI publishing

The PNGs are also copied into
[`app/src/main/play/listings/en-US/graphics/`](../../app/src/main/play/listings/en-US/graphics/) and
`en-GB/graphics/`, which is what CI's `publishListing` Gradle task uploads on every release. They are
plain copies, so after regenerating copy the updated files into the matching
`graphics/{icon,feature-graphic,phone-screenshots}/` folders (`icon-512.png` -> `icon/1.png`,
`feature-graphic-1024x500.png` -> `feature-graphic/1.png`, screenshots -> `phone-screenshots/1..4.png`).
Other languages fall back to these graphics.

## Languages

Listing text and release notes live under `app/src/main/play/listings/<locale>/` and
`app/src/main/play/release-notes/<locale>/`: en-US (default), en-GB, de-DE, es-ES, fr-FR, it-IT,
ru-RU, cs-CZ and sk. The non-English texts are translations that have not been reviewed by native
speakers.
