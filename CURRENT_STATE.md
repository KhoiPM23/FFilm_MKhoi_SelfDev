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

## AI
- **Gemini Wiring**: Verified. Spring handles configuration and constructs correct API parameters. [FACT]
- **AI Search**: Cannot be verified due to invalid Gemini key. [BLOCKED]
- **AI Chatbot**: Cannot be verified due to invalid Gemini key. [BLOCKED]

## DOCUMENTATION
- `WORK_LOG.md`: Present and updated. [FACT]
- Migration File: Present. [FACT]
- Project Memory Files: Established and updated to reflect SQL Server/8081 runtime. [FACT]

## GIT
- **Status**: Working tree is clean at the time of verification, pending commit of stabilization changes. [FACT]

## OPEN RISKS
- Historical exposure of API keys (TMDB, Tenor) on remote repositories if not purged/rotated.

## NEXT TASK
- **Provide a valid local Gemini API Key** and execute the Git history purge (after key rotation).
