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
- **TMDB historical exposure**: Present in Git logs. [OPEN]
- **Gemini historical exposure**: Confirmed present in Git logs. [OPEN]
- **TMDB rotation/revocation**: Awaiting explicit human intervention. [PENDING HUMAN]
- **Gemini credential replacement**: The current local `gemini.api.key` is rejected by Google (HTTP 400). Requires local update. [PENDING HUMAN]
- **Git history purge**: Requires explicit authorization to execute destructive history rewrite. [PENDING HUMAN]

## AI
- **Gemini Wiring**: Verified. Spring handles configuration and constructs correct API parameters. [FACT]
- **AI Search**: Cannot be verified due to invalid Gemini key. [BLOCKED]
- **AI Chatbot**: Cannot be verified due to invalid Gemini key. [BLOCKED]

## DOCUMENTATION
- `WORK_LOG.md`: Present and updated. [FACT]
- Migration File: Present. [FACT]
- Project Memory Files: Established (`PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `DECISIONS.md`, `CURRENT_STATE.md`). [FACT]

## GIT
- **Latest Relevant Commits**:
  - `security: secure external AI and TMDB credentials`
  - `fix(schema): sync user privacy fields with current entity`
- **Status**: Working tree is clean at the time of verification. [FACT]

## OPEN RISKS
- Historical exposure of API keys (TMDB, Gemini) on remote repositories if not purged/rotated.
- GIPHY frontend configuration missing/undefined. [TECHNICAL DEBT]

## NEXT TASK
- **Provide a valid local Gemini API Key** and execute the Git history purge (after key rotation).
