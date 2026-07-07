-- V5 - Jones-parity catalog + save slots, EXPAND phase (KAN-52).
--
-- Purely additive: creates the wiki-accurate job/degree catalog (tbljob,
-- tbldegrees, tbljob_degrees) and the account/save split (tblsave + children),
-- and copies each existing account's state into one starter save. The legacy
-- shapes (tbljobs, tbluserstats, tbluser's state columns) are untouched so the
-- pre-cutover code keeps validating/running; V6 (the API cutover, KAN-54)
-- drops them. Spec: docs/superpowers/specs/2026-07-07-jobs-degrees-saves-design.md.
--
-- Catalog values are verbatim from the Jones in the Fast Lane wiki
-- (jonesinthefastlane.fandom.com, the canonical rules source): 11 degrees with
-- prerequisites, 39 jobs across 9 workplaces. req_clothing maps the wiki's
-- uniforms onto the existing clothing levels: 1=Casual, 2=Dress, 3=Business.
-- Jobs listing required dependability 10 truly require 0 (wiki anti-frustration
-- rule) - the RULE lives in core, the table stores the listed value.
-- location strings MUST equal domain.board.Location display names.

-- ===== degree catalog =====

CREATE TABLE tbldegrees (
    id                INT         NOT NULL,
    name              VARCHAR(50) NOT NULL,
    prereq_degree_id  INT         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_degree_name (name),
    CONSTRAINT fk_degree_prereq FOREIGN KEY (prereq_degree_id)
        REFERENCES tbldegrees (id)
);

DELETE FROM tbldegrees;

INSERT INTO tbldegrees (id, name, prereq_degree_id) VALUES
    ( 1, 'Junior College',          NULL),
    ( 2, 'Trade School',            NULL),
    ( 3, 'Business Administration',    1),
    ( 4, 'Academic',                   1),
    ( 5, 'Electronics',                2),
    ( 6, 'Pre-Engineering',            2),
    ( 7, 'Graduate School',            4),
    ( 8, 'Engineering',                6),
    ( 9, 'Post-Doctoral',              7),
    (10, 'Research',                   9),
    (11, 'Publishing',                10);

-- ===== job catalog =====

CREATE TABLE tbljob (
    id                 INT         NOT NULL,
    job                VARCHAR(50) NOT NULL,
    location           VARCHAR(50) NOT NULL,
    wage               INT         NOT NULL,   -- R per hour; a full 6h session pays wage*8
    req_experience     INT         NOT NULL,
    req_dependability  INT         NOT NULL,   -- listed value; 10 means 0 in the hiring rule
    req_clothing       INT         NOT NULL,   -- 1=Casual, 2=Dress, 3=Business
    PRIMARY KEY (id),
    UNIQUE KEY uq_job_per_location (location, job)
);

DELETE FROM tbljob;

INSERT INTO tbljob (id, job, location, wage, req_experience, req_dependability, req_clothing) VALUES
    ( 1, 'Clerk',                  'Z-Mart',            5, 10, 10, 1),
    ( 2, 'Assistant Manager',      'Z-Mart',            7, 20, 20, 2),
    ( 3, 'Manager',                'Z-Mart',            8, 30, 30, 3),
    ( 4, 'Cook',                   'Monolith Burgers',  5,  0, 10, 1),
    ( 5, 'Clerk',                  'Monolith Burgers',  6, 10, 20, 1),
    ( 6, 'Assistant Manager',      'Monolith Burgers',  7, 20, 30, 1),
    ( 7, 'Manager',                'Monolith Burgers',  8, 30, 40, 2),
    ( 8, 'Janitor',                'QT Clothing',       6, 10, 20, 1),
    ( 9, 'Salesperson',            'QT Clothing',       8, 30, 30, 2),
    (10, 'Assistant Manager',      'QT Clothing',       9, 40, 40, 3),
    (11, 'Manager',                'QT Clothing',      12, 50, 50, 3),
    (12, 'Clerk',                  'Socket City',       6, 10, 20, 1),
    (13, 'Salesperson',            'Socket City',       7, 30, 30, 2),
    (14, 'Electronics Repairman',  'Socket City',      11, 40, 40, 1),
    (15, 'Manager',                'Socket City',      14, 40, 40, 3),
    (16, 'Janitor',                'Hi-Tech U',         5, 10, 10, 1),
    (17, 'Teacher',                'Hi-Tech U',        11, 40, 50, 2),
    (18, 'Professor',              'Hi-Tech U',        20, 50, 60, 2),
    (19, 'Janitor',                'Factory',           7, 10, 20, 1),
    (20, 'Assembly Worker',        'Factory',           8, 30, 30, 1),
    (21, 'Secretary',              'Factory',           9, 40, 40, 2),
    (22, 'Machinist''s Helper',    'Factory',          10, 40, 40, 1),
    (23, 'Executive Secretary',    'Factory',          18, 50, 50, 3),
    (24, 'Machinist',              'Factory',          19, 50, 50, 1),
    (25, 'Department Manager',     'Factory',          22, 60, 60, 3),
    (26, 'Engineer',               'Factory',          23, 60, 60, 3),
    (27, 'General Manager',        'Factory',          25, 70, 70, 3),
    (28, 'Janitor',                'Bank',              6, 10, 20, 1),
    (29, 'Teller',                 'Bank',             10, 40, 40, 2),
    (30, 'Assistant Manager',      'Bank',             14, 50, 50, 3),
    (31, 'Manager',                'Bank',             19, 60, 60, 3),
    (32, 'Broker',                 'Bank',             22, 70, 70, 3),
    (33, 'Janitor',                'Black''s Market',   6, 10, 10, 1),
    (34, 'Checker',                'Black''s Market',   8, 20, 20, 1),
    (35, 'Butcher',                'Black''s Market',  12, 30, 30, 1),
    (36, 'Assistant Manager',      'Black''s Market',  15, 40, 40, 2),
    (37, 'Manager',                'Black''s Market',  18, 50, 50, 3),
    (38, 'Groundskeeper',          'Rent Office',       7, 10, 20, 1),
    (39, 'Apartment Manager',      'Rent Office',       9, 30, 30, 1);

-- Degree requirements per job (0-2 rows each).
CREATE TABLE tbljob_degrees (
    job_id     INT NOT NULL,
    degree_id  INT NOT NULL,
    PRIMARY KEY (job_id, degree_id),
    CONSTRAINT fk_jobdeg_job    FOREIGN KEY (job_id)    REFERENCES tbljob (id),
    CONSTRAINT fk_jobdeg_degree FOREIGN KEY (degree_id) REFERENCES tbldegrees (id)
);

DELETE FROM tbljob_degrees;

INSERT INTO tbljob_degrees (job_id, degree_id) VALUES
    ( 3, 1),          -- Z-Mart Manager: Junior College
    ( 7, 1),          -- Monolith Manager: Junior College
    (10, 1),          -- QT Assistant Manager: Junior College
    (11, 3),          -- QT Manager: Business Administration
    (14, 5),          -- Electronics Repairman: Electronics
    (15, 5), (15, 1), -- Socket City Manager: Electronics + Junior College
    (17, 4),          -- Teacher: Academic
    (18, 10),         -- Professor: Research
    (20, 2),          -- Assembly Worker: Trade School
    (21, 1),          -- Factory Secretary: Junior College
    (22, 6),          -- Machinist's Helper: Pre-Engineering
    (23, 3),          -- Executive Secretary: Business Administration
    (24, 8),          -- Machinist: Engineering
    (25, 1), (25, 8), -- Department Manager: Junior College + Engineering
    (26, 1), (26, 8), -- Engineer: Junior College + Engineering
    (27, 3), (27, 8), -- General Manager: Business Administration + Engineering
    (29, 1),          -- Teller: Junior College
    (30, 3),          -- Bank Assistant Manager: Business Administration
    (31, 3),          -- Bank Manager: Business Administration
    (32, 3), (32, 4), -- Broker: Business Administration + Academic
    (35, 2),          -- Butcher: Trade School
    (36, 1),          -- Black's Market Assistant Manager: Junior College
    (37, 3),          -- Black's Market Manager: Business Administration
    (39, 1);          -- Apartment Manager: Junior College

-- ===== save slots =====

CREATE TABLE tblsave (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    owner              VARCHAR(50) NOT NULL,
    label              VARCHAR(50) NOT NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),
    xpos               INT         NOT NULL DEFAULT 0,
    ypos               INT         NOT NULL DEFAULT 0,
    `time`             INT         NOT NULL DEFAULT 4320,  -- minutes left this week (fed week, matches tbluser)
    round              INT         NOT NULL DEFAULT 1,
    cash               INT         NOT NULL DEFAULT 100,
    bank               INT         NOT NULL DEFAULT 0,
    debt               INT         NOT NULL DEFAULT 0,
    rent               INT         NOT NULL DEFAULT 1,     -- 1 = rent owed
    eat                INT         NOT NULL DEFAULT 0,     -- weeks of food stored
    clothing           INT         NOT NULL DEFAULT 1,
    job_id             INT         NULL,                   -- NULL = unemployed
    happiness          INT         NOT NULL DEFAULT 0,
    experience         INT         NOT NULL DEFAULT 10,    -- hidden stat (wiki start 10)
    dependability      INT         NOT NULL DEFAULT 20,    -- hidden stat (wiki start 20)
    current_course_id  INT         NULL,                   -- degree being studied
    eduprog            INT         NOT NULL DEFAULT 0,     -- study sessions this course
    goal_wealth        INT         NOT NULL,               -- 10..100 each, set at creation
    goal_happiness     INT         NOT NULL,
    goal_education     INT         NOT NULL,
    goal_career        INT         NOT NULL,
    won                INT         NOT NULL DEFAULT 0,     -- 0/1, sticky once set
    PRIMARY KEY (id),
    CONSTRAINT fk_save_owner  FOREIGN KEY (owner)             REFERENCES tbluser (name)
        ON DELETE CASCADE,
    CONSTRAINT fk_save_job    FOREIGN KEY (job_id)            REFERENCES tbljob (id),
    CONSTRAINT fk_save_course FOREIGN KEY (current_course_id) REFERENCES tbldegrees (id)
);

CREATE TABLE tblsave_degrees (
    save_id    BIGINT NOT NULL,
    degree_id  INT    NOT NULL,
    PRIMARY KEY (save_id, degree_id),
    CONSTRAINT fk_savedeg_save   FOREIGN KEY (save_id)   REFERENCES tblsave (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_savedeg_degree FOREIGN KEY (degree_id) REFERENCES tbldegrees (id)
);

-- Jobs refused for "No Openings" this round: blocked until round moves on.
CREATE TABLE tblsave_turndowns (
    save_id  BIGINT NOT NULL,
    job_id   INT    NOT NULL,
    round    INT    NOT NULL,
    PRIMARY KEY (save_id, job_id),
    CONSTRAINT fk_turndown_save FOREIGN KEY (save_id) REFERENCES tblsave (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_turndown_job  FOREIGN KEY (job_id)  REFERENCES tbljob (id)
);

-- ===== migrate existing accounts: one starter save each =====
--
-- Carries money/position/round/happiness; career and education reset (the old
-- linear education levels don't map onto real degrees - agreed in the spec).
-- Goals default to 50 across the board for pre-existing accounts.

INSERT INTO tblsave (owner, label, xpos, ypos, `time`, round, cash, bank, debt,
                     rent, eat, clothing, happiness, experience, dependability,
                     goal_wealth, goal_happiness, goal_education, goal_career, won)
SELECT u.name, 'Save 1', u.xpos, u.ypos, u.`time`, u.round, u.cash, u.bank, u.debt,
       u.rent, u.eat, u.clothing, s.happiness, 10, 20,
       50, 50, 50, 50, 0
FROM tbluser u
JOIN tbluserstats s ON s.name = u.name;
