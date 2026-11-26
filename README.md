# GlassGPT

A highly customizable personal Android AI client built with Kotlin and Jetpack Compose.

## Customization studio

- Liquid Glass on/off
- Adjustable blur, panel transparency and corner radius
- Accent and background colors, including custom HEX values
- Glass, solid and outlined chat bubbles
- Compact layout, chat labels and adjustable text size
- Upload custom TTF/OTF fonts
- Upload a wallpaper/background image
- Upload separate user and assistant avatar images
- Export and import theme settings as JSON

Changes apply immediately and are saved locally.

## Build the APK

GitHub Actions builds a debug APK whenever code is pushed to `main`. Open the **Actions** tab, select the latest **Build GlassGPT APK** run, and download the `GlassGPT-debug-apk` artifact.

To build locally, open this repository in Android Studio (JDK 17, Android SDK Platform 35), sync Gradle, then run **Build → Build APK(s)**.

## First launch

Open Customize → AI Connection and enter your OpenAI API key and model (default `gpt-4.1-mini`). API keys: https://platform.openai.com/api-keys

Open Customize → Fonts & Uploads to add your Mojangles font, wallpaper and avatars.

## Billing and security

OpenAI API usage is billed separately from ChatGPT subscriptions. This is a personal prototype: the API key is stored in local app preferences and sent over HTTPS. Do not distribute an APK with your personal key. A public app should use an authenticated backend.

## Notes

- Theme JSON exports visual settings; uploaded image/font files stay on the device and are not embedded in theme exports.
- The app is an early prototype and has not yet been verified on a physical Android device.
