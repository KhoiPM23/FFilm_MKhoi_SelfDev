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

## AI
- **Gemini Model**: `gemini-2.5-flash` active and operational. [FACT]
- **RestTemplate Timeout**: Configured to 5s connect timeout / 30s read timeout. [FIXED]
- **AI Search**: Verified working via `/api/ai-search/suggest` (HTTP 200 OK). [FACT]
- **AI Chatbot**: Verified working via `/api/ai-agent/chat` (HTTP 200 OK). [FACT]

## DOCUMENTATION & WORKFLOW
- `WORK_LOG.md`: Present and updated through Batch 3C. [FACT]
- Migration File: Present. [FACT]
- Project Memory Files: Established and tracked. [FACT]
- GitHub Issues Workflow: Issue #1 created, tracked across sub-batches, and closed upon verification. [FACT]

## GIT
- **Branch**: `refactor/batch-3-quick-wins` [FACT]
- **Remote**: Synchronized with `origin/refactor/batch-3-quick-wins`. [FACT]

## OPEN RISKS
- Historical exposure of API keys (TMDB, Tenor) on remote repositories if not purged/rotated.
- Gemini credential exposure detected in diagnostic command transcript — HUMAN ROTATION REQUIRED.

## NEXT TASK
- Batch 4 — Centralized Error Handling & SLF4J Logging (replace `e.printStackTrace()` and un-bypass `GlobalExceptionHandler`).
