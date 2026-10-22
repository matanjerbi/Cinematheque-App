# Cinematheque

Personal film & series diary for Android — ratings, tags, free-text notes, watchlist, collections and stats.
Full spec (Hebrew): [SPEC.md](SPEC.md)

## Stack

Kotlin · Jetpack Compose (Material 3) · Room · Navigation Compose (type-safe routes) · Retrofit + kotlinx.serialization · Coil · TMDB API

## Setup

1. Get a free TMDB API key (v3): themoviedb.org → Settings → API.
2. Add it to `local.properties` in the project root (this file is git-ignored):
   ```
   TMDB_API_KEY=your_key_here
   ```
3. Open in Android Studio and run, or `./gradlew assembleDebug`.

## Layout

```
app/src/main/java/com/matanjerbi/cinematheque/
├── CinemathequeApp.kt        Application + manual DI container
├── MainActivity.kt
├── data/
│   ├── local/                Room: database, converters
│   │   ├── entity/           Entities and relation POJOs
│   │   └── dao/              DAOs, incl. StatsDao (all stats are local SQL)
│   ├── remote/               TMDB API + DTOs
│   └── repository/           TitleRepository (fetch-once caching), PosterStore
└── ui/
    ├── navigation/           Routes + NavHost with bottom bar
    ├── screens/              Screens (placeholders for now)
    └── theme/
```

Room schemas are exported to `app/schemas/` — commit them so future migrations can be verified.

This product uses the TMDB API but is not endorsed or certified by TMDB.
