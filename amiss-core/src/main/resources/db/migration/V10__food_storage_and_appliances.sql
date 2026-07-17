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
