-- Convert the weekly time budget from whole hours to whole minutes (KAN-29).
--
-- Time is a spendable resource like money: integer arithmetic only, so fractional
-- costs (e.g. 40-minute walking steps) stay exact and the time = 0 week-over check
-- stays reliable. The full cost table lives in code (ActionCosts) with config
-- overrides; the week is 3600 minutes, 4320 when fed.
--
-- ONE-WAY MIGRATION: builds older than KAN-29 read this column as hours and will
-- misinterpret migrated saves. Do not run pre-KAN-29 jars against a V3 database.

UPDATE tbluser SET `time` = `time` * 60;

ALTER TABLE tbluser ALTER COLUMN `time` SET DEFAULT 4320;
