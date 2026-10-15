# SCANFORGE AI

Native Android document scanner built with Kotlin and Jetpack Compose.

## First milestone
- Premium graphite, emerald, and gold interface
- Import one or multiple images from the device
- Generate multi-page PDF files locally
- OCR text extraction with Google ML Kit
- Search recognized text in saved scans
- Android CI builds and uploads a debug APK

## Build
GitHub Actions builds the debug APK on pushes to `main` and via manual workflow dispatch.

## Privacy
Scans and OCR are processed on-device. The app does not upload documents to a server.
