# Architecture

## Current Architecture

### Frontend
- **HTML/Thymeleaf**: Views are rendered on the server using Thymeleaf templates (e.g., `index`, `search`, `movie/player`).
- **JavaScript Modules**: Vanilla JS scripts manage UI logic (e.g., `script.js`, `search.js`, `player.js`).
- **CSS**: Custom vanilla CSS with Bootstrap for layout.
- **Browser-Side API Calls**: The frontend no longer directly calls TMDB for searching or rendering movies (after the P0 security remediation). Instead, JS uses local endpoints (e.g., `/api/movie/hover-detail/{id}`) to fetch necessary metadata.

### Backend
- **Controllers**: MVC controllers manage view rendering. `@RestController`s (e.g., `MovieApiController`, `AISearchController`) provide JSON responses to the frontend.
- **Services**: Business logic is encapsulated in `@Service` classes (e.g., `MovieService`, `TmdbService`, `AISearchService`, `AIAgentService`). Services are responsible for TMDB data aggregation and Gemini API communication.
- **Repositories**: Standard Spring Data JPA interfaces for database interaction.
- **Entities**: JPA Entities mapping directly to SQL Server tables (e.g., `User`, `Movie`, `UserReaction`). Note that relation tables use the local `movieID` as a primary reference, not `tmdbId` (per `GUIDELINE_DEV_Id_Handle.md`).

### External API Integrations (Data Flow)
**TMDB (The Movie Database):**
- *CURRENT*: `Browser → FFilm Backend → TMDB`
- TMDB requests are routed securely via Backend APIs. The API key is securely injected using `@Value("${tmdb.api.key}")`.

**Gemini AI:**
- *CURRENT*: `Browser → FFilm Backend (AIAgentService / AISearchService) → Gemini`
- Used for AI search processing and conversational AI. The API key is sourced from `@Value("${gemini.api.key:}")`.

**Tenor (Stickers):**
- *CURRENT*: `Browser → FFilm Backend (TenorController) → Tenor API`
- Used for chat stickers in Watch Party and Messenger. The API key is sourced from `@Value("${tenor.api.key}")` and securely proxied.

### Database
- **Engine**: SQL Server
- **Core Entities**:
  - `Users` table (Contains `isPublicFavorites`, `isPublicFriendList`, `isPublicWatchHistory` columns as nullable bits).
  - `Movie` table (Primary internal ID is `movieID`, external reference `tmdbId`).

## Target / Future Architecture
- Migration to full backend-proxied external requests is currently partially implemented (TMDB has been re-routed; AI requests are properly proxied).
- The future architecture aims to fully detach all third-party secrets from any frontend code.
