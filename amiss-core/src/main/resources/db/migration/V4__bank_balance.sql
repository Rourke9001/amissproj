-- Adds the player's bank savings balance (KAN-31). Greenfield: no bank column
-- existed before this; deposits/withdrawals move money between cash and bank.

ALTER TABLE tbluser ADD COLUMN bank INT NOT NULL DEFAULT 0;
