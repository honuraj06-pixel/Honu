# HONU PDF Pro

An Android PDF editing app built for HONU, featuring a family-inspired turtle, fish, tiger and bird animated intro.

## Features (first Android build)
- Read multi-page PDFs
- Add text and visually replace text with a whiteout overlay
- Highlight, whiteout, draw signatures and add an image
- Rotate/delete pages; undo page edits
- Export an edited, flattened PDF using Android's native PDF APIs

**Note:** This first build uses bitmap-based editing. Exports are flattened: original PDF text, links, forms and signatures are not retained as native PDF objects.

## APK

An unsigned-for-release but debug-signed APK is built with the GitHub Actions workflow `Android APK` and offered as a downloadable artifact after a successful build.

## Android
Android 8.0+ (API 26), Java, built with Android Gradle Plugin.
