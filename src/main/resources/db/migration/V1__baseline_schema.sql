-- V1 - baseline schema (Flyway; ROADMAP Phase 2 / KAN-15).
--
-- The four tables of the game, exactly as reverse-engineered from the code's
-- SQL (formerly db/setup.sql). Flyway connects to the amissdb schema created
-- by db/bootstrap.sql, so there is no CREATE DATABASE/USE here.
--
-- Installs that predate Flyway already have these tables: FlywayMigrator runs
-- with baselineOnMigrate/baselineVersion=1, so this migration is *skipped*
-- there (saves are kept) and only V2+ apply.

-- Column order matters: LoginGUI inserts positionally (password is a BCrypt
-- hash, always 60 chars).
--   INSERT INTO tbluser VALUES ('name','<bcrypt-hash>',0,0,72,100,1,'Unemployed',1,1,0,0)
CREATE TABLE tbluser (
    name      VARCHAR(50)  NOT NULL,
    password  VARCHAR(60)  NOT NULL,
    xpos      INT          NOT NULL DEFAULT 0,
    ypos      INT          NOT NULL DEFAULT 0,
    `time`    INT          NOT NULL DEFAULT 72,   -- hours left in the round (60, or 72 if fed)
    cash      INT          NOT NULL DEFAULT 100,
    round     INT          NOT NULL DEFAULT 1,
    job       VARCHAR(50)  NOT NULL DEFAULT 'Unemployed',
    clothing  INT          NOT NULL DEFAULT 1,
    rent      INT          NOT NULL DEFAULT 1,     -- 1 = rent owed, 0 = paid
    eat       INT          NOT NULL DEFAULT 0,
    debt      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (name)
);

-- Column order matters: LoginGUI inserts positionally
--   INSERT INTO tbluserstats VALUES ('name',0,0,0,0)
CREATE TABLE tbluserstats (
    name       VARCHAR(50) NOT NULL,
    happiness  INT         NOT NULL DEFAULT 0,
    education  INT         NOT NULL DEFAULT 0,
    work       INT         NOT NULL DEFAULT 0,
    eduprog    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (name),
    CONSTRAINT fk_stats_user FOREIGN KEY (name)
        REFERENCES tbluser (name) ON DELETE CASCADE
);

CREATE TABLE tbljobs (
    job        VARCHAR(50) NOT NULL,
    education  INT         NOT NULL,   -- min tbluserstats.education required
    salary     INT         NOT NULL,   -- earned per hour worked
    location   VARCHAR(50) NOT NULL,   -- MUST equal a building name the code checks
    clothing   INT         NOT NULL,   -- min clothing level required (1=Casual,2=Formal,3=Suit)
    PRIMARY KEY (job)
);

CREATE TABLE tblhelp (
    topic        VARCHAR(50) NOT NULL,
    description  TEXT        NOT NULL,
    PRIMARY KEY (topic)
);
