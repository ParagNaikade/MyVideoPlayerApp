# YouTube Mini (Android)

Local-video Android app with a YouTube-like browsing and playback flow, built for kid-safe viewing from phone storage.

## What This App Does

- Scans videos from device local storage using `MediaStore`
- Shows a feed-style list with thumbnail previews and duration
- Plays selected videos in a fullscreen `ExoPlayer` dialog
- Lets parent set a PIN and hide/block specific videos
- Adds PIN brute-force protection with escalating lockouts
- Kid view only shows allowed videos
- Uses responsive layouts for phone sizes (feed + adaptive library grid)
- Includes search and a Continue Watching rail
- Includes allowlist mode (only explicitly approved videos are visible)
- Auto-locks parent panel after inactivity

## Important Note

This project uses a familiar video-app layout, but it does not include YouTube trademarks, logos, backend, or account features.

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Media3 ExoPlayer
- DataStore Preferences (PIN + blocked list)
- Coil (video frame thumbnails)

## Project Structure

- `app/src/main/java/com/youtube/mini/MainActivity.kt`
- `app/src/main/java/com/youtube/mini/ui/MiniTubeScreen.kt`
- `app/src/main/java/com/youtube/mini/ui/MiniTubeViewModel.kt`
- `app/src/main/java/com/youtube/mini/data/LocalVideoRepository.kt`
- `app/src/main/java/com/youtube/mini/data/ParentPrefs.kt`

## Build and Run

1. Open this folder in Android Studio.
2. Let Android Studio sync Gradle.
3. Run on a real Android device (recommended for local media testing).
4. Grant video permission when prompted.

## No-Install APK Build (GitHub Actions)

If Android Studio cannot be installed on your laptop, this repo can build APK in GitHub and publish it as a downloadable release asset.

1. Push your latest code to GitHub.
2. Open Actions tab and run workflow: `Build And Publish APK`.
3. After it completes, open Releases and download `app-debug.apk` from `Latest APK`.

Direct links for this repository:

- Actions: https://github.com/ParagNaikade/MyVideoPlayerApp/actions
- Releases: https://github.com/ParagNaikade/MyVideoPlayerApp/releases

Phone install note:

- Android may ask you to allow installs from unknown sources for your browser or file manager.

## Parent Control Flow

1. Tap lock icon in top bar.
2. First time: set a 4-6 digit parent PIN.
3. Next times: enter PIN to unlock parent controls.
4. Toggle videos on/off in Parent Controls sheet.
5. Repeated wrong PIN attempts trigger timed lockouts.
6. Parent panel auto-locks after ~90 seconds of inactivity.

## Discovery UX

- Search videos by title from Home, Library, and Parent tabs
- Continue Watching row appears on Home based on saved playback position

## Current Limitations

- Uses local storage only (no internet/video recommendations/comments)
- Minimal theming; can be refined further for stronger visual similarity
- No background download or account system