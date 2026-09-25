# Current Verified State

This file records the MOST RECENT verified operational state of the project.

## RUNTIME
- **Database**: `FFilm3` [FACT]
- **Spring Boot Startup**: `SUCCESS` [FACT]
- **Maven Test**: `PASSED` (Tests run: 1, Failures: 0, Errors: 0) [FACT]
- **Homepage Status**: Loads and fetches movies successfully [FACT]

## ARCHITECTURE & BOUNDARIES
- **Controller Data Access Boundaries**: 100% of controllers delegate data access through services. `MovieDetailController` now delegates to `UserFavoriteService.isFavorite`. [FIXED]
- **Encapsulation**: Removed public `MovieService.getMovieRepository()` leak. [FIXED]
- **External Integration Boundaries**: TMDB via `TmdbClient`, Gemini via `GeminiClient`, Tenor via `TenorService`. [FIXED]
- **Messenger Performance**: `MessengerApiController.getConversations()` returns decorated list without duplicate query. `MessengerService.getChatStats()` calculates counts and earliest message directly at database level. [FIXED]
- **Frontend Constants**: Stale empty `TMDB_API_KEY` and `TMDB_BASE_URL` constants removed from `script.js` and `player.js`. [FIXED]
- **Error Handling & Logging (Batch 4A-4C)**: 100% of `e.printStackTrace()` eliminated and standardized to SLF4J; `GlobalExceptionHandler` standardized for REST controllers with dual-compatible envelope and sanitized 500 responses; 13 truly redundant controller try/catch blocks safely removed. [FIXED]

## SCHEMA
- **Synchronized User Privacy Columns**: `isPublicFavorites`, `isPublicFriendList`, `isPublicWatchHistory` [FIXED]
- **Migration Artifact**: `project/migration-7de319a-user-privacy.sql` [FACT]

## SECURITY
- **TMDB current-source exposure**: Hardcoded API keys removed from JS/HTML. Backend uses `@Value`. [FIXED]
- **Tenor current-source exposure**: Proxied successfully. [FIXED]
- **API Keys Historical exposure**: TMDB and Tenor present in Git history. Gemini NEVER exposed in git history. [FACT]
- **TMDB/Tenor rotation/revocation**: Awaiting explicit human intervention on provider side. [PENDING HUMAN]
- **Git history purge**: BFG/filter-repo requires explicit authorization. [PENDING HUMAN]
- **IDOR Vulnerability**: Fixed in UserReactionController (userId sourced from session). [FIXED]
- **IDOR Vulnerability**: Fixed in SecurityConfig for /api/users endpoint. [FIXED]
- **IDOR Vulnerability**: Fixed in WatchPartyController WebSockets (enforced host permissions via SimpMessageHeaderAccessor). [FIXED]
- **XSS Vulnerability**: Fixed Stored/Reflected XSS in WatchPartyController chat (added escapeHtml to user messages and names). [FIXED]
- **Hardcoded Configs**: Cleaned up unused vnp_ReturnUrl from VnPayConfig. [FIXED]

## WATCH PARTY & ROOM MANAGEMENT (PHASE 6)
- **Schema Synchronization (P6-A.2)**: `WatchRoom`, `FriendRequests`, `Notification`, `UserFollow` synchronized in SQL Server `FFilm3` via `migration-p6a2-watch-party-schema.sql` (commit `66a576f`). [FACT]
- **Create Room Flow (P6-A.3)**: Restored and operational on both `/watch-party` and `/my-rooms` without framework rewrite. [FIXED]
- **Core Watch Party Room Lifecycle (P6-A.5)**: Implementation is complete.
  - The room lifecycle now includes: authenticated STOMP join, server-owned membership identity, disconnect cleanup, deterministic host migration, private-room waiting list, host approval, and a shared Create Room fragment.
  - Automated tests pass.
  - However, genuine two-browser runtime E2E has NOT been performed in the current automated environment.
  - Therefore: P6-A.5 = IMPLEMENTED / PARTIAL ACCEPTANCE / NEEDS REPRO for multi-session runtime. Do not assume Watch Party lifecycle is fully runtime-certified.

## AI
- **Gemini Model**: `gemini-2.5-flash` active and operational. [FACT]
- **RestTemplate Timeout**: Configured to 5s connect timeout / 30s read timeout. [FIXED]
- **[PENDING] Future AI Search Runtime Validation**: Full validation backlog recorded for future execution. [PENDING]
- **[PENDING] Future AI Chatbot Runtime Validation**: Full validation backlog recorded for future execution. [PENDING]

## DOCUMENTATION & WORKFLOW
- `WORK_LOG.md`: Present and updated through P6-A.5. [FACT]
- Migration File: Present. [FACT]
- Project Memory Files: Established and tracked. [FACT]

## GIT
- **Branch**: `main` [FACT]
- **Remote**: Synchronized with `origin/main`. [FACT]

## OPEN RISKS
- Historical exposure of API keys (TMDB, Tenor) on remote repositories if not purged/rotated.
- Gemini credential exposure detected in diagnostic command transcript — HUMAN ROTATION REQUIRED.

## NEXT TASKS
- P6-A.6 WebRTC Signaling & Call Lifecycle
- P6-A.7 Movie Synchronization Hardening
- P6-A.8 Watch Party Chat / Reactions
- P6-A.9 Watch Party Layout / UX
