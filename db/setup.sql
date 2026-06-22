-- ===========================================================================
--  AmissProj  -  Database setup script  (MySQL 8.x / 9.x)
-- ===========================================================================
--  Recreates the schema that NetBeans used to host for this game.
--
--  The four tables and every column/type below were reverse-engineered from
--  the SQL embedded in the Java source (see SETUP.md for the mapping).
--
--    tbluser       - one row per saved player        (starts empty)
--    tbluserstats  - one row per player's stats       (starts empty)
--    tbljobs       - reference data: the jobs offered  (seeded below)
--    tblhelp       - reference data: in-game help text (seeded below)
--
--  NOTE: the contents of tbljobs / tblhelp were never in source control - the
--  original values lived only in the old NetBeans database and are lost. The
--  rows below are a *reconstruction*: job names + locations are taken verbatim
--  from the code (they must match exactly), while the education / salary /
--  clothing numbers are sensible, playable values you are free to tune.
--
--  Run it once with:   mysql -u root -p < db/setup.sql
-- ===========================================================================

CREATE DATABASE IF NOT EXISTS amissdb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE amissdb;

-- ---------------------------------------------------------------------------
--  Player save tables  (CREATE IF NOT EXISTS so re-running keeps your saves)
-- ---------------------------------------------------------------------------

-- Column order matters: LoginGUI inserts positionally
--   INSERT INTO tbluser VALUES ('name','pass',0,0,720,100,1,'Unemployed',1,1,0,0)
CREATE TABLE IF NOT EXISTS tbluser (
    name      VARCHAR(50)  NOT NULL,
    password  VARCHAR(50)  NOT NULL,
    xpos      INT          NOT NULL DEFAULT 0,
    ypos      INT          NOT NULL DEFAULT 0,
    `time`    INT          NOT NULL DEFAULT 720,   -- minutes left in the round (12h)
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
CREATE TABLE IF NOT EXISTS tbluserstats (
    name       VARCHAR(50) NOT NULL,
    happiness  INT         NOT NULL DEFAULT 0,
    education  INT         NOT NULL DEFAULT 0,
    work       INT         NOT NULL DEFAULT 0,
    eduprog    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (name),
    CONSTRAINT fk_stats_user FOREIGN KEY (name)
        REFERENCES tbluser (name) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
--  Reference data  (dropped & re-seeded every run - safe, contains no saves)
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS tbljobs;
CREATE TABLE tbljobs (
    job        VARCHAR(50) NOT NULL,
    education  INT         NOT NULL,   -- min tbluserstats.education required
    salary     INT         NOT NULL,   -- earned per hour worked
    location   VARCHAR(50) NOT NULL,   -- MUST equal a building name the code checks
    clothing   INT         NOT NULL,   -- min clothing level required (1=Casual,2=Formal,3=Suit)
    PRIMARY KEY (job)
);

-- location strings below are required verbatim by the *GUI classes
-- (e.g. BankGUI checks loc.equals("Bank")).
INSERT INTO tbljobs (job, education, salary, location, clothing) VALUES
    ('Cook',                   0,  6, 'Fast Food Place', 1),

    ('Clerk',                  1, 10, 'Appliance Store', 2),
    ('SalesPerson',            1, 12, 'Appliance Store', 2),
    ('Repairsman',             2, 16, 'Appliance Store', 2),
    ('Assistant Manager',      2, 20, 'Appliance Store', 3),
    ('Store Manager',          3, 28, 'Appliance Store', 3),

    ('Secretary',              1, 12, 'Factory',         2),
    ('Machinist',              2, 18, 'Factory',         2),
    ('Engineer',               3, 35, 'Factory',         3),

    ('Bank Janitor',           0,  6, 'Bank',            1),
    ('Bank Assistant Manager', 3, 30, 'Bank',            3),
    ('Broker',                 3, 40, 'Bank',            3),

    ('Market Janitor',         0,  6, 'Market',          1),
    ('Checker',                1, 10, 'Market',          2),
    ('Butcher',                2, 16, 'Market',          2),

    ('Rent Office Janitor',    0,  6, 'Rent Office',     1),
    ('Apartment Manager',      3, 30, 'Rent Office',     3);

DROP TABLE IF EXISTS tblhelp;
CREATE TABLE tblhelp (
    topic        VARCHAR(50) NOT NULL,
    description  TEXT        NOT NULL,
    PRIMARY KEY (topic)
);

-- topic strings must match the Help buttons' action commands exactly.
INSERT INTO tblhelp (topic, description) VALUES
    ('Controls',
     'Move around the city by clicking the grid squares on the main screen. '
     'Step onto a building to enter it. Each move and each activity uses up '
     'time from your 12-hour day (720 minutes). When the clock runs out the '
     'round ends - click New Round to start the next day. Use Save And Exit '
     'to store your progress and quit; log back in with the same username to '
     'resume.'),

    ('How to Win',
     'Build a successful life: earn cash, raise your education, hold down a '
     'job and keep your happiness up across the rounds. Pay your rent on time '
     'to stay out of debt. The further you progress your career and stats, the '
     'better your score on the High Score board.'),

    ('Tips',
     'Start with a Janitor job - it needs no education and only casual clothes. '
     'Eat regularly to keep your happiness from dropping. Study at the '
     'University to unlock better-paid careers, and buy smarter clothes at the '
     'Clothes Store before applying for senior roles. Rent falls due every 4th '
     'round - keep enough cash aside or it becomes debt.'),

    ('Getting A Job',
     'Go to the Employment Office (top-right of the city). Pick a job you '
     'qualify for: each role needs a minimum education level and a minimum '
     'standard of clothing. Janitor roles need neither, so they are the safe '
     'first job. Raise your education at the University and buy better clothing '
     'at the Clothes Store to qualify for higher-paying careers. Once hired, '
     'go to that job''s building and work to earn your hourly salary.');
