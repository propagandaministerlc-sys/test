# UltraGrid for Android

Turn one photo into a seamless Instagram profile grid.

## Features

The home screen has four tools.

### Profile grid
- Split one photo across **1–5 rows** of Instagram's 3-column grid (3 to 15 posts).
- Pinch and drag to frame the photo in a live preview.
- **Tile shapes**: 3:4 (matches the profile grid exactly), 4:5 (largest uncropped feed size) or 1:1.
- Every tile is numbered in **posting order**. Post #1 first. Instagram puts the newest post top-left, so the picture builds up correctly.
- **Posting assistant**: tap a tile (or "Post #n") to open Instagram with that tile. Posted tiles get a check mark. Long-press a tile to toggle it.

### Carousel
- Cut a wide or panoramic photo into **2–10 slides** that swipe as one continuous picture.
- "Post carousel to Instagram" hands every slide to Instagram at once, in order.

### Collage
- Six layouts: 2 across, 2 stacked, 3 stacked, 2×2, 1 + 2 and 2×3. Tap a cell to pick its photo.
- Choose the shape, filter, spacing between photos and a white or black background.
- Save it, or save and share straight to Instagram.

### Feed planner
- Add the photos you plan to post and see them laid out like your profile, newest top-left.
- Tap two photos to swap them. Tap one to share it, mark it as posted or remove it.
- Grids and carousel covers can be sent to the planner from the result screen.
- The plan is saved on the device.

### In the grid and carousel editors
- **Filters**: Vivid, Warm, Cool, Fade, B&W and Noir.
- **Text overlay**: type text, then drag and pinch it on the preview. It can span several tiles. Choose a colour and bold or regular.

All images are saved as high-quality JPEGs to `Pictures/UltraGrid`. You can also *Share → UltraGrid* from your gallery.

Requires Android 10 (API 29) or newer. The app needs no permissions.

## Getting the APK

Every push runs the **Build APK** GitHub Actions workflow. Open the latest run under **Actions** and download the `ultragrid-apk` artifact. It contains:

- `app-debug.apk`
- `app-release.apk` (minified, signed with the debug key so you can sideload it)

Install either one on your phone. You may need to allow installs from unknown sources.

## Building locally

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
```

This needs JDK 17+ and the Android SDK, with `ANDROID_HOME` set or a `local.properties` file. You can also open the project in Android Studio.

## Project layout

| File | Purpose |
| --- | --- |
| `GridModel.kt` | Modes, tile ratios, crop math, posting order |
| `PhotoFilter.kt` | Colour-matrix filter presets |
| `TextOverlay.kt` | Text drawn across the full picture before it is split |
| `Collage.kt` | Collage templates and renderer |
| `ImageLoader.kt` | Decodes photos with EXIF rotation and caps their size |
| `TileExporter.kt` | Slices the crop into tiles and saves them through MediaStore |
| `Sharing.kt` | Hands a tile to Instagram, or to the share sheet |
| `Thumbnails.kt` | Cached thumbnails for the planner |
| `GridViewModel.kt` | Grid and carousel editor state |
| `CollageViewModel.kt` | Collage state and export |
| `PlannerViewModel.kt` | Feed plan, saved in SharedPreferences |
| `ui/` | Jetpack Compose screens: home, editor, result, collage, planner |

Before publishing to Google Play, set up a real release signing config in `app/build.gradle.kts`.
