-- V12: Relaxation stat (KAN-23, PR 4). Starts at 10 (the wiki floor), rises to
-- a max of 50 via the Relax action, decays -1/turn (never below 10).
-- relaxed_this_turn tracks whether this turn's first-Relax happiness bonus
-- has already been paid out (wiki: only the first Relax each turn grants +2
-- happiness); reset at every rollover.
-- INT, not TINYINT: SaveEntity maps this as an int-as-boolean (0/1) exactly like `won`
-- and `ate_fast_food_last_turn` (see V10) -- TINYINT broke ddl-auto=validate there.
ALTER TABLE tblsave ADD COLUMN relaxation         INT NOT NULL DEFAULT 10 AFTER wage;
ALTER TABLE tblsave ADD COLUMN relaxed_this_turn  INT NOT NULL DEFAULT 0  AFTER relaxation;
