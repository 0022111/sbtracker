# T-011 — Session Notes + Rating

**Status**: blocked
**Phase**: 2
**Blocked by**: T-007
**Blocks**: T-016

---

## Goal
Users have no way to annotate sessions. Add a free-text `notes` field and a
1–5 star `rating` to each session. This requires a Room schema migration to v3.

---

## High-level steps
1. Add `notes: String? = null` and `rating: Int? = null` to `Session` entity.
2. Bump `AppDatabase` version to 3.
3. Write `Migration(2, 3)`:
   ```sql
   ALTER TABLE sessions ADD COLUMN notes TEXT;
   ALTER TABLE sessions ADD COLUMN rating INTEGER;
   ```
4. Add edit UI to `SessionReportActivity` — note input field + star rating widget.
5. Surface rating as a star badge in `SessionHistoryAdapter` row.
6. Include notes and rating columns in CSV export.
7. Update `SessionSummary` if notes/rating should be surfaced in analytics views.

*(Fill in full steps when T-007 is done.)*

## Do NOT touch
- `device_status` table
- `hits` or `charge_cycles` tables
- Analytics computation logic (unless surfacing notes/rating in summaries)
