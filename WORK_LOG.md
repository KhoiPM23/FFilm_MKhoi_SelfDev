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

## 2026-09-25 - Establish Project Memory and Operational Workflow
- **Task**: Establish durable project-memory and AI-agent operating system for future continuity.
- **Objective**: Consolidate findings, architecture, and current state into Git-tracked Markdown files and create `.ai-local` workspace.
- **Files Created**:
  - `PROJECT_CONTEXT.md` (Tech stack, purpose, agent rules)
  - `ARCHITECTURE.md` (Current components and data flows)
  - `DECISIONS.md` (Architectural and security decisions)
  - `CURRENT_STATE.md` (Operational baseline)
  - `.ai-local/` structure (Local agent context, logs, and handoffs)
- **Verification**:
  - `.ai-local` explicitly ignored via `.gitignore` to prevent secret leaks.
  - No secret values written to any documentation files (used `[OMITTED]`).
- **Status**: Completed successfully.
- **Current Blockers**: AI Search/Chatbot integration still blocked by invalid local Gemini credential. Git history purge pending human authorization.
## 2026-09-25 - Standardize Development Port
- **Task**: Standardize local development runtime port to 8081.
- **Objective**: Resolve port 8080 conflict with MiniTool ShadowMaker AgentService.
- **Files Modified**:
  - `application.properties` (added `server.port=8081` and updated `app.base.url`)
  - `EmailService.java` (refactored to use `@Value("${app.base.url}")` instead of hardcoded 8080)
  - `VnPayConfig.java` (updated return URL to port 8081)
  - `ManageAccount.html` (changed absolute `http://localhost:8080` fetch to relative `/api/users/${id}`)
  - `share-modal.js` (updated comment reference)
- **Verification**: `mvn test` passed. Spring Boot app starts successfully on port 8081.
- **Security Check**:
  - `application.properties` remains safely ignored.
  - No secret values exposed in commit or logs.
  - [FACT] Current Gemini credential is not present in Git history.
  - [FACT] Current TMDB credential does not match the historical exposed TMDB credential.
  - [FACT] Current Tenor credential does not match the historical exposed Tenor credential.
  - [PENDING] Historical TMDB credential provider-side revocation requires human verification.
  - [PENDING] Historical Tenor credential provider-side revocation requires human verification.
  - [PENDING] Git history purge requires explicit owner authorization.
