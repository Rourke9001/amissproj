-- V8: wage snapshot (KAN-48, PR 2). The wage the player was hired at — the
-- Employment Office listing's economy-adjusted value, frozen until a raise
-- (KAN-49) or a crash pay-cut (PR 3). NULL = unemployed. Work pay and the
-- state DTO read this, never tbljob's base wage.
ALTER TABLE tblsave ADD COLUMN wage INT NULL AFTER job_id;

-- Backfill: pre-economy saves were all at Reading 0, so the base wage IS the
-- wage they saw when they were hired.
UPDATE tblsave s
JOIN tbljob j ON j.id = s.job_id
SET s.wage = j.wage
WHERE s.job_id IS NOT NULL;
