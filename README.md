# Food Recommender

Approximate-area restaurant search for Android, plus a browser demo that uses built-in sample places.

## Browser demo

```bash
npm ci
npm test
npm run dev
```

The demo does not request location and does not call a remote API.

## Android

Requires Android SDK 34. Copy `local.properties.example` to `local.properties` (that file is gitignored):

```
sdk.dir=/path/to/android/sdk
GOOGLE_PLACES_API_KEY=
```

Leave the key empty to use the public Overpass fallback. A Places API (New) key must be restricted to package `com.foodrecommender.app` and your signing certificate. The app sends it as a request header. Do not commit the key.

Location permission is approximate (`ACCESS_COARSE_LOCATION`). Coordinates are rounded to about 1 km before a lookup. Precise fixes and the rounded area are not written to disk. Results stay in memory until the app process ends or you clear them. Backups are disabled. Review text is not scraped and is not sent to an AI provider.

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

`:app` is configured only when an Android SDK is present.

This repository is not set up to publish to the Play Store or any other store.
