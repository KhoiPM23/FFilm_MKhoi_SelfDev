# Decisions

| Decision | Reason | Evidence / Date | Status |
|---|---|---|---|
| **Use SQL Server (Local target `FFilm3`)** | Matches the local owner's environment setup and avoids destructive reset of existing data. | Local configuration & baseline verification (2026-09-25) | Active |
| **No `ddl-auto=update` in production/schema syncing** | Avoids unpredictable schema rewrites. Additive changes should be handled via explicit SQL migrations. | Baseline verification & schema drift resolution (2026-09-25) | Active |
| **User Privacy Schema Synchronization** | The `User` entity received `isPublicFavorites`, `isPublicFriendList`, and `isPublicWatchHistory` fields in commit `7de319a`. These must map to `BIT NULL` in the DB safely. | Explicit additive migration script `migration-7de319a-user-privacy.sql` (2026-09-25) | Active |
| **No Frontend Exposure of Secrets** | Hardcoded TMDB keys in JS files introduce P0 security risks. All requests requiring external secrets must be proxied through the Backend. | TMDB current-source exposure remediation (2026-09-25) | Active |
| **Local Internal IDs for Relational Tables** | `tmdbId` is an external reference and not a primary key in `Movie`. All satellite tables (Favorites, History, Reactions) MUST join using the internal `movieID`. | `GUIDELINE_DEV_Id_Handle.md` | Active |
| **Credential Rotation & History Purge Requires Authorization** | Erasing Git history or revoking active credentials is a destructive action that requires human oversight to avoid crippling dependent services. | Task constraint (2026-09-25) | Active |
| **Do not refactor unrelated code** | Focus must be kept strictly on the objective at hand to avoid introducing unintentional side effects. | Project workflow rules (2026-09-25) | Active |
| **Do NOT increase hover-card scale by default** | Batch 2 audit concluded `[NO_CHANGE_RECOMMENDED]` — no browser evidence of a concrete UX problem requiring a scale increase. | Batch 2 audit (2026-09-29) | Active |
| **Browser-first UI verification is mandatory** | Source inspection ≠ runtime verification. Build success ≠ browser acceptance. Any claim of FIXED or VERIFIED for UI requires browser evidence. If quota is exhausted, mark `[SOURCE_ONLY]`. | Workflow rule (2026-09-29) | Active |
| **Read roadmap before acting each session** | Future agents must read `CURRENT_STATE.md` BATCH ROADMAP STATE section before touching any code. Never redo accepted work. Mark completed-early items `[ALREADY_DONE_EARLY]`. | Agent rule (2026-09-29) | Active |
| **No AI backend optimization before Batch 5** | Do not jump to RAG, vector DB, fine-tuning, or embeddings without explicit authorization. | Batch roadmap rule (2026-09-29) | Active |
| **No commit or push without explicit human authorization** | Applies at all times regardless of batch or task context. | Project safety rule | Active |

## Runtime Configuration
- [DECISION] FFilm local development runtime uses port 8081 because host port 8080 is occupied by MiniTool ShadowMaker AgentService.
