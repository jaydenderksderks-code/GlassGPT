# GlassGPT

A personal Android AI client built with Kotlin and Jetpack Compose. It uses a liquid-glass-inspired interface and Mojangles typography.

## Build the APK

GitHub Actions builds a debug APK whenever code is pushed to `main`. Open the **Actions** tab, select the latest **Build GlassGPT APK** run, and download the `GlassGPT-debug-apk` artifact.

To build locally, open this repository in Android Studio (JDK 17, Android SDK Platform 35), sync Gradle, then run **Build → Build APK(s)**.

## First launch

Open Settings and enter your OpenAI API key and model (default `gpt-4.1-mini`). API keys: https://platform.openai.com/api-keys

## Billing and security

OpenAI API usage is billed separately from ChatGPT subscriptions. This is a personal prototype: the API key is stored in local app preferences and sent over HTTPS. Do not distribute an APK with your personal key. A public app should use an authenticated backend.

## Mojangles font

Place your supplied `mojangles.otf` at `app/src/main/assets/fonts/mojangles.otf` before building. The app falls back to the system font if the file is absent. The font file itself is not yet committed to this repository.
