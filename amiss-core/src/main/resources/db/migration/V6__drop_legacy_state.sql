-- V6__drop_legacy_state.sql
-- Contract phase of the V5 expand / V6 contract pair (KAN-54).
-- Per-save state lives in tblsave (V5); tbluser shrinks to credentials.
DROP TABLE tbluserstats;
DROP TABLE tbljobs;
ALTER TABLE tbluser
  DROP COLUMN xpos,
  DROP COLUMN ypos,
  DROP COLUMN `time`,
  DROP COLUMN round,
  DROP COLUMN cash,
  DROP COLUMN bank,
  DROP COLUMN debt,
  DROP COLUMN rent,
  DROP COLUMN eat,
  DROP COLUMN clothing,
  DROP COLUMN job;
