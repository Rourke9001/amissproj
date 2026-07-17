-- V10: food-storage split (KAN-23, PR 2). `eat` becomes specifically "weeks of
-- Fresh Food stored" (fast food's 1-turn effect is the new boolean flag below,
-- never banked); appliance ownership (Fridge/Freezer here; Computer/Books
-- follow in a later PR with zero schema change) is a generic join table, the
-- same @ElementCollection shape as tbljob_degrees.
-- INT, not TINYINT: SaveEntity maps this as an int-as-boolean (0/1) exactly like `won`
-- and `rent`, and Hibernate's ddl-auto=validate rejects a TINYINT column behind an int
-- field ("found [tinyint], but expecting [integer]") — the app would not start.
ALTER TABLE tblsave ADD COLUMN ate_fast_food_last_turn INT NOT NULL DEFAULT 0 AFTER eat;

CREATE TABLE tblsave_appliance (
    save_id   BIGINT      NOT NULL,
    appliance VARCHAR(20) NOT NULL,
    PRIMARY KEY (save_id, appliance),
    CONSTRAINT fk_tblsave_appliance_save FOREIGN KEY (save_id) REFERENCES tblsave(id) ON DELETE CASCADE
);

-- Backfill: pre-V10, nobody could own a Fridge and buyGroceries added packs outright, so a
-- row can legitimately hold `eat` up to 8 with no appliance ownership. From this migration
-- forward the fridgeless capacity rule caps storage at 1 week, making `eat > 1` with an empty
-- appliance set an unreachable state — and since the appliance table above is brand new,
-- every existing row is fridgeless right now, so an unconditional clamp is correct with no
-- join needed. Left uncapped, the first post-deploy rollover would read `eat = 8` as spoiled
-- fresh food, wipe it, and roll a Doctor Visit out of nowhere for a save that never bought a
-- Fridge. Mirrors the V9 clamp on `time > 3600`.
UPDATE tblsave SET eat = 1 WHERE eat > 1;
