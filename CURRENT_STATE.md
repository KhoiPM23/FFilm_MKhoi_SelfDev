# Current Verified State

This file records the MOST RECENT verified operational state of the project.

## RUNTIME
- **Database**: `FFilm3` [FACT]
- **Spring Boot Startup**: `SUCCESS` [FACT]
- **Maven Test**: `PASSED` [FACT]
- **Homepage Status**: Loads and fetches movies successfully [FACT]

## SCHEMA
- **Synchronized User Privacy Columns**: `isPublicFavorites`, `isPublicFriendList`, `isPublicWatchHistory` [FIXED]
- **Migration Artifact**: `project/migration-7de319a-user-privacy.sql` [FACT]

## SECURITY
- **TMDB current-source exposure**: Hardcoded API keys removed from JS/HTML. Backend uses `@Value`. [FIXED]
- **Tenor current-source exposure**: Proxied successfully. [FIXED]
- **API Keys Historical exposure**: TMDB and Tenor present in Git history. Gemini NEVER exposed. [FACT]
- **TMDB/Tenor rotation/revocation**: Awaiting explicit human intervention on provider side. [PENDING HUMAN]
- **Git history purge**: BFG/filter-repo requires explicit authorization. [PENDING HUMAN]
- **IDOR Vulnerability**: Fixed in UserReactionController (userId sourced from session). [FIXED]
- **IDOR Vulnerability**: Fixed in SecurityConfig for /api/users endpoint. [FIXED]
- **IDOR Vulnerability**: Fixed in WatchPartyController WebSockets (enforced host permissions via SimpMessageHeaderAccessor). [FIXED]
- **XSS Vulnerability**: Fixed Stored/Reflected XSS in WatchPartyController chat (added escapeHtml to user messages and names). [FIXED]
- **Hardcoded Configs**: Cleaned up unused vnp_ReturnUrl from VnPayConfig. [FIXED]

## AI
- **Gemini Model**: `gemini-2.5-flash` active and operational. [FACT]
- **RestTemplate Timeout**: Configured to 5s connect timeout / 30s read timeout (resolved false-positive auth failure caused by 10s socket timeout). [FIXED]
- **AI Search**: Verified working via `/api/ai-search/suggest` (HTTP 200 OK). [FACT]
- **AI Chatbot**: Verified working via `/api/ai-agent/chat` (HTTP 200 OK). [FACT]

## DOCUMENTATION
- `WORK_LOG.md`: Present and updated. [FACT]
- Migration File: Present. [FACT]
- Project Memory Files: Established and updated to reflect SQL Server/8081 runtime. [FACT]

## GIT
- **Status**: Working tree is clean at the time of verification, pending commit of stabilization changes. [FACT]

## OPEN RISKS
- Historical exposure of API keys (TMDB, Tenor) on remote repositories if not purged/rotated.
- Gemini credential exposure detected in diagnostic command transcript — HUMAN ROTATION REQUIRED.

## NEXT TASK
- Human rotation of exposed Gemini API key; proceed to source cleanup and technical debt reduction.
