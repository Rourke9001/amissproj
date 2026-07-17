-- V9: the week is a flat 60h (KAN-23). V5 seeded `time` at 4320 (72h) because the old
-- model gave a fed week a 12h bonus; that bonus is gone — a week is 3600 minutes always,
-- and going unfed spends a 20h Starvation penalty out of it rather than withholding a
-- bonus. The old default also documented itself as "matches tbluser", a table V6 dropped.
ALTER TABLE tblsave ALTER COLUMN `time` SET DEFAULT 3600;

-- Backfill in-flight weeks that still hold more than a full week's minutes: rows seeded at
-- 4320 by V5, plus any save whose last rollover ran under the old fed-week rule. Clamping
-- (rather than subtracting 720) errs in the player's favour and makes no assumption that
-- every over-budget row started at exactly 4320 — a save part-way through a 72h week holds
-- an arbitrary remainder. Rows already at or below 3600 are a legitimate spent week and
-- are left alone.
UPDATE tblsave SET `time` = 3600 WHERE `time` > 3600;
