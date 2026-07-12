-- V7: hidden per-save economy state (KAN-48, PR 1).
-- Index = the economy's trend (-3..+3); Reading drives every price:
-- price = base + base*reading/60 (50%..250% of base). Both hidden stats —
-- never exposed on a wire DTO. Defaults 0/0 = exactly pre-economy prices,
-- so existing saves migrate with zero price shock.
ALTER TABLE tblsave
    ADD COLUMN economy_index   TINYINT  NOT NULL DEFAULT 0 AFTER won,
    ADD COLUMN economy_reading SMALLINT NOT NULL DEFAULT 0 AFTER economy_index;
