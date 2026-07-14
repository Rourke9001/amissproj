-- ===========================================================================
--  AmissProj  -  One-time database bootstrap  (MySQL 8.x / 9.x)
-- ===========================================================================
--  Creates ONLY what Flyway cannot create for itself: the database and the
--  two MySQL accounts. The schema and seed data live in versioned Flyway
--  migrations (src/main/resources/db/migration/), which the game applies
--  automatically at startup - this script replaces the old db/setup.sql,
--  which used to hand-maintain the whole schema.
--
--  Accounts (least privilege):
--    amiss           - what the game plays as. Row access only: no DDL/DROP,
--                      no GRANT, no other schemas. DELETE is needed since
--                      save-slot removal shipped (KAN-45/KAN-57); it is
--                      schema-level because MySQL cannot grant on a table
--                      that does not exist yet, and this script runs before
--                      Flyway creates the schema at first launch.
--    amiss_migrator  - what FlywayMigrator connects as, only while applying
--                      migrations at startup. Needs DDL + full DML on amissdb
--                      (migrations create/alter tables and re-seed reference
--                      data) but nothing beyond that schema.
--
--  Change a password here and in src/main/resources/application.properties
--  to match, or override at runtime with the AMISS_DB_USER /
--  AMISS_DB_PASSWORD / AMISS_DB_MIGRATOR_USER / AMISS_DB_MIGRATOR_PASSWORD
--  environment variables. These are local-dev defaults, not real secrets.
--
--  Run it once with:   mysql -u root -p < db/bootstrap.sql
--  Safe to re-run: everything below is IF NOT EXISTS / idempotent.
-- ===========================================================================

CREATE DATABASE IF NOT EXISTS amissdb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'amiss'@'localhost' IDENTIFIED BY 'amisspw';
GRANT SELECT, INSERT, UPDATE, DELETE ON amissdb.* TO 'amiss'@'localhost';

CREATE USER IF NOT EXISTS 'amiss_migrator'@'localhost' IDENTIFIED BY 'amissmigratorpw';
GRANT CREATE, ALTER, DROP, INDEX, REFERENCES,
      SELECT, INSERT, UPDATE, DELETE
    ON amissdb.* TO 'amiss_migrator'@'localhost';

FLUSH PRIVILEGES;
