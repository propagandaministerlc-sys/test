# UltraGrid for Android

Turn one photo into a seamless Instagram profile grid.

## Features

- **Pick any photo** with the system photo picker, or use *Share → UltraGrid* from your gallery.
- **Pinch and drag** to frame the photo inside a live 3-column grid preview.
- **1–5 rows** (3 to 15 posts).
- **Tile shapes**
  - **3:4 Grid** (1080×1440): matches Instagram's current profile grid.
  - **4:5 Feed** (1080×1350): the largest size the feed shows uncropped.
  - **1:1 Square** (1080×1080): classic square posts.
- **Posting order**: each tile is numbered. Post #1 first. Instagram puts the newest post top-left, so the picture builds up correctly.
- Tiles are saved as high-quality JPEGs to `Pictures/UltraGrid`.
- **Posting assistant**: tap a tile (or "Post #n") to open Instagram with that tile. Posted tiles get a check mark. Long-press a tile to toggle it.

Requires Android 10 (API 29) or newer. The app needs no permissions.

## Getting the APK

Every push runs the **Build APK** GitHub Actions workflow. Open the latest run under **Actions** and download the `ultragrid-apk` artifact. It contains:

- `app-debug.apk`
- `app-release.apk` (minified, signed with the debug key so you can sideload it)

Install either one on your phone. You may need to allow installs from unknown sources.

## Building locally

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # crop-math unit tests
```

This needs JDK 17+ and the Android SDK, with `ANDROID_HOME` set or a `local.properties` file. You can also open the project in Android Studio.

## Project layout

| File | Purpose |
| --- | --- |
| `GridModel.kt` | Tile ratios, crop math, posting order |
| `ImageLoader.kt` | Decodes photos with EXIF rotation and caps their size |
| `TileExporter.kt` | Slices the crop into tiles and saves them through MediaStore |
| `Sharing.kt` | Hands a tile to Instagram, or to the share sheet |
| `GridViewModel.kt` | Editor and result state |
| `ui/` | Jetpack Compose screens: empty, editor, result |

Before publishing to Google Play, set up a real release signing config in `app/build.gradle.kts`.
