-- V2 - seed the reference data (Flyway; ROADMAP Phase 2 / KAN-15).
--
-- tbljobs / tblhelp hold no player state, so this migration owns their
-- contents outright: DELETE + INSERT. On a clean database the DELETEs are
-- no-ops; on a pre-Flyway install (baselined at V1) they replace the rows
-- that db/setup.sql used to load, with identical values.
--
-- NOTE: the values are a *reconstruction* - the originals lived only in the
-- old NetBeans database and were lost. Job names and location strings are
-- taken verbatim from the code (a work screen only shows its Work button if
-- tbljobs.location equals the building name, e.g. 'Monolith Burgers');
-- the numeric columns are tunable design values. Tuning them later = a new
-- versioned migration, not an edit to this file.

DELETE FROM tbljobs;

INSERT INTO tbljobs (job, education, salary, location, clothing) VALUES
    ('Cook',                   0,  6, 'Monolith Burgers', 1),

    ('Clerk',                  1, 10, 'Socket City', 2),
    ('SalesPerson',            1, 12, 'Socket City', 2),
    ('Repairsman',             2, 16, 'Socket City', 2),
    ('Assistant Manager',      2, 20, 'Socket City', 3),
    ('Store Manager',          3, 28, 'Socket City', 3),

    ('Secretary',              1, 12, 'Factory',         2),
    ('Machinist',              2, 18, 'Factory',         2),
    ('Engineer',               3, 35, 'Factory',         3),

    ('Bank Janitor',           0,  6, 'Bank',            1),
    ('Bank Assistant Manager', 3, 30, 'Bank',            3),
    ('Broker',                 3, 40, 'Bank',            3),

    ('Market Janitor',         0,  6, 'Black''s Market',          1),
    ('Checker',                1, 10, 'Black''s Market',          2),
    ('Butcher',                2, 16, 'Black''s Market',          2),

    ('Rent Office Janitor',    0,  6, 'Rent Office',     1),
    ('Apartment Manager',      3, 30, 'Rent Office',     3);

DELETE FROM tblhelp;

-- topic strings must match the Help buttons' action commands exactly.
INSERT INTO tblhelp (topic, description) VALUES
    ('Controls',
     'Move around the city by clicking the grid squares on the main screen. '
     'Step onto a building to enter it. Each move and each activity uses up '
     'time from your 60-72 hour work week. When the clock runs out the '
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
