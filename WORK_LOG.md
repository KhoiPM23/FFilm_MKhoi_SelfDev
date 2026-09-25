# Project Work Log

## 2026-09-25 - Schema Synchronization and Runtime Baseline
- **Task**: Synchronize User privacy schema with current entity
- **Objective**: Complete bounded User schema drift investigation for commit 7de319a, resolve missing persistence fields, and establish a verified runtime baseline.
- **Verified Facts**: 
  - `User.java` was updated in commit `7de319a` to include three privacy settings: `isPublicFavorites`, `isPublicFriendList`, and `isPublicWatchHistory`.
  - Database `FFilm3` was missing these columns in the `Users` table.
  - The fields map to nullable boolean columns and Java logic gracefully handles `null`.
- **Files Changed**:
  - Created `migration-7de319a-user-privacy.sql`
- **Database Changes Executed Locally**:
  - `ALTER TABLE Users ADD isPublicFavorites BIT NULL`
  - `ALTER TABLE Users ADD isPublicFriendList BIT NULL`
  - `ALTER TABLE Users ADD isPublicWatchHistory BIT NULL`
- **Migration Artifact Created**: `migration-7de319a-user-privacy.sql` (added to repo)
- **Tests Executed**: `.\mvnw.cmd test` passed.
- **Runtime Result**: Application startup succeeded (Spring Boot run on port 8081).
- **Smoke Test Result**: Homepage loads correctly with movies listed. Database connection verified.
- **Remaining Blockers**: None for startup.
- **Next Recommended Task**: 
  - Fix P0 Security: TMDB credential exposed in frontend JS / Git history.

## 2026-09-25 - Security Remediation: External Credentials
- **Task**: Remediate P0 TMDB API exposure and diagnose Gemini AI Chatbot integration.
- **P0 Security Findings**: 
  - TMDB API key was hardcoded in multiple tracked frontend JS files (`script.js`, `search.js`, `player.js`, `searchResult.js`, `resp.html`) and backend files (`MovieService.java`, `SearchController.java`).
  - Historical exposure of TMDB key exists in Git history.
  - Gemini key may also have been historically exposed.
- **Gemini Configuration Finding**: 
  - Property `gemini.api.key` exists in `application.properties` and matches backend `@Value` mapping.
  - Endpoint/model configurations are correct (`v1beta/models/gemini-2.5-flash:generateContent`).
- **Gemini Runtime Error**: `API_KEY_INVALID` 400 Bad Request.
- **Root Cause (Gemini)**: The key currently provided in the local `application.properties` is genuinely invalid/rejected by Google. The application wiring is 100% correct.
- **Fixes Applied**:
  - Removed all hardcoded TMDB keys from frontend JS and HTML files.
  - Rewired `player.js` to fetch movie titles securely from the backend endpoint `/api/movie/hover-detail/{id}` instead of TMDB directly.
  - Refactored `MovieService.java` and `SearchController.java` to use `@Value("${tmdb.api.key}")`.
- **Verification**: `.\mvnw.cmd test` passed. Context loads correctly.
- **Remaining Blockers**: [BLOCKED — INVALID LOCAL GEMINI CREDENTIAL] AI features cannot be verified until the human operator provides a valid Gemini key in `application.properties`.
- **Pending Human Actions**: 
  - Provide a valid `gemini.api.key` in `application.properties`.
  - Rotate/revoke the previously exposed TMDB credential.
  - Approve and execute Git history purge to erase historical credential exposure.
