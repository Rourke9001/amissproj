-- V11: three independent clothing-category tracks (KAN-23, PR 3), replacing
-- the single non-decaying `clothing` level. Wiki-exact: Casual/Dress/Business
-- each count down in weeks and decay independently; a purchase adds to its
-- category rather than overwriting it. New-game seed matches the wiki
-- (6/0/0); existing saves get the same seed since there is no way to recover
-- which category an old flat level actually represented in weeks.
ALTER TABLE tblsave
    ADD COLUMN clothing_casual_weeks   INT NOT NULL DEFAULT 6 AFTER eat,
    ADD COLUMN clothing_dress_weeks    INT NOT NULL DEFAULT 0 AFTER clothing_casual_weeks,
    ADD COLUMN clothing_business_weeks INT NOT NULL DEFAULT 0 AFTER clothing_dress_weeks;

ALTER TABLE tblsave DROP COLUMN clothing;
