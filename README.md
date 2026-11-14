# SAJI TXTFX Mobile

A native Android text-motion editor for lyric videos, captions and title overlays.

## Features

- 22 built-in text animation styles with live preview
- Editable multi-line text
- Four typefaces, adjustable text size and eight color swatches
- Portrait 9:16, square 1:1 and landscape 16:9 canvases
- H.264 MP4 export with a pure green background for Chroma Key in video editors
- Animated transparent GIF export
- Custom adaptive launcher icon and a dark mobile-first editor
- No third-party runtime libraries and no native/NDK code

## Export details

- **MP4:** 720p-class canvas, 24 FPS, 3 seconds. Saved to Movies/SAJI TXTFX.
- **GIF:** 360-pixel canvas width (portrait/square) or 640-pixel landscape width, 12 FPS, 3 seconds. Saved to Pictures/SAJI TXTFX.

GIF transparency uses the GIF format's single transparent palette entry, so antialiased text edges can look less smooth than the green-screen MP4. For the cleanest overlay in InShot, import the MP4 and use Chroma Key on the green background.

MP4 export requires an H.264 encoder on the Android device that accepts YUV420 input. The app checks for a compatible encoder and reports an error if the phone does not expose one.

## Compatibility

- Android 10 (API 29) or newer
- Java-only Android app; no 64-bit-only native library is bundled
- Built for ARM32/ARM64 Android devices, subject to the device's available H.264 encoder

## Build

Pushes to main trigger the GitHub Actions workflow in .github/workflows/android.yml. It builds app-debug.apk and uploads it as the SAJI-TXTFX-Mobile-debug artifact. Open the repository's Actions tab, select the latest successful run, and download the artifact.

The build uses JDK 17, Gradle 8.9 and Android Gradle Plugin 8.7.3.
