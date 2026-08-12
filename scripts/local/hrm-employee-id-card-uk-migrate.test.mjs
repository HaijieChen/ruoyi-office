/**
 * EXP-82 MySQL migration integration tests for active-only id_card UK (P1-1/2/3).
 *
 * Requires Docker MySQL (ruoyi-office-mysql) and does NOT touch production hrm_employee data:
 * all cases run on isolated tables / a temporary schema clone of DDL only.
 *
 * Usage:
 *   node scripts/local/hrm-employee-id-card-uk-migrate.test.mjs
 *
 * Env (optional):
 *   MYSQL_CONTAINER=ruoyi-office-mysql
 *   MYSQL_ROOT_PASSWORD=123456
 */
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import assert from 'node:assert/strict';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '../..');
const migrateSqlPath = join(
  repoRoot,
  'yudao-module-hrm/yudao-module-hrm-server/src/main/resources/sql/hrm_employee_id_card_uk.sql',
);
const container = process.env.MYSQL_CONTAINER || 'ruoyi-office-mysql';
const password = process.env.MYSQL_ROOT_PASSWORD || '123456';
const schema = `exp82_uk_it_${Date.now()}`;

function mysql(sql, { expectFail = false } = {}) {
  try {
    const out = execFileSync(
      'docker',
      [
        'exec',
        '-i',
        container,
        'mysql',
        '-uroot',
        `-p${password}`,
        '-N',
        '-e',
        sql,
      ],
      { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 },
    );
    if (expectFail) {
      throw new Error(`expected SQL failure, got success:\n${out}`);
    }
    return out.replace(/mysql: \[Warning\].*\n/g, '');
  } catch (e) {
    if (expectFail) {
      return String(e.stderr || e.stdout || e.message);
    }
    throw e;
  }
}

function mysqlFile(filePath, database) {
  // Keep DELIMITER — mysql client stdin supports procedure scripts
  const body = readFileSync(filePath, 'utf8');
  return execFileSync(
    'docker',
    ['exec', '-i', container, 'mysql', '-uroot', `-p${password}`, database],
    { input: body, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 },
  ).replace(/mysql: \[Warning\].*\n/g, '');
}

function setupBaseTable(db) {
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
`);
}

test('migrate: old UK upgrade + lifecycle + blank + active dup', () => {
  setupBaseTable(schema);
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`);
  mysqlFile(migrateSqlPath, schema);

  // lifecycle
  mysql(`USE \`${schema}\`; UPDATE hrm_employee SET deleted=1 WHERE id_card='ID-A' AND deleted=0;`);
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`);
  mysql(`USE \`${schema}\`; UPDATE hrm_employee SET deleted=1 WHERE id_card='ID-A' AND deleted=0;`);
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`);
  // tombstones
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-B', 1, 1), ('ID-B', 1, 1);`);
  // blank cards multiple active allowed (normalized to NULL)
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('  ', 0, 1), ('', 0, 1);`);
  // active duplicate rejected
  const fail = mysql(
    `USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);

  // old UK gone, new UK present
  const idx = mysql(
    `USE \`${schema}\`; SELECT index_name FROM information_schema.statistics WHERE table_schema='${schema}' AND table_name='hrm_employee' AND index_name IN ('uk_hrm_employee_id_card','uk_hrm_employee_active_id_card') GROUP BY index_name;`,
  );
  assert.ok(idx.includes('uk_hrm_employee_active_id_card'));
  assert.ok(!idx.includes('uk_hrm_employee_id_card'));

  // generation has nullif+trim
  const gen = mysql(
    `USE \`${schema}\`; SELECT LOWER(generation_expression) FROM information_schema.columns WHERE table_schema='${schema}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  );
  assert.match(gen, /nullif/);
  assert.match(gen, /trim/);
});

test('migrate: wrong plain column same name is rebuilt (P1-1)', () => {
  const db = `${schema}_badcol`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  active_id_card varchar(18) DEFAULT NULL
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id, active_id_card) VALUES
  ('ID-X', 0, 1, NULL),
  ('ID-X', 0, 1, NULL);
`);
  // precheck fails on active dups
  const fail = (() => {
    try {
      mysqlFile(migrateSqlPath, db);
      return '';
    } catch (e) {
      return String(e.stderr || e.stdout || e.message);
    }
  })();
  assert.match(fail, /precheck failed|45000|Duplicate active/i);

  // clean dups then succeed; wrong column replaced
  mysql(`USE \`${db}\`; DELETE FROM hrm_employee WHERE id > 1;`);
  mysqlFile(migrateSqlPath, db);
  const extra = mysql(
    `SELECT EXTRA FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  );
  assert.match(extra.toUpperCase(), /STORED/);
  // now active dup rejected
  const fail2 = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-X', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail2, /1062|Duplicate/i);
});

test('migrate: wrong non-unique same-name index is rebuilt (P1-1)', () => {
  const db = `${schema}_badidx`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  active_id_card varchar(18) GENERATED ALWAYS AS (IF(deleted = 0, NULLIF(TRIM(id_card), ''), NULL)) STORED,
  KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Y', 0, 1);
`);
  mysqlFile(migrateSqlPath, db);
  const nonUnique = mysql(
    `SELECT non_unique FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_active_id_card' LIMIT 1;`,
  ).trim();
  assert.equal(nonUnique, '0');
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Y', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});

test('migrate: empty-string id_card precheck + expression (P1-2)', () => {
  const db = `${schema}_blank`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1
);
-- two active blanks: precheck must pass (normalized NULL); UK allows both
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('', 0, 1), ('   ', 0, 1);
`);
  mysqlFile(migrateSqlPath, db);
  const cnt = mysql(`USE \`${db}\`; SELECT COUNT(*) FROM hrm_employee WHERE deleted=0;`).trim();
  assert.equal(cnt, '2');
  // real active cards still unique
  mysql(`USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Z', 0, 1);`);
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Z', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});

test('migrate: re-run is idempotent (partial re-run)', () => {
  const db = `${schema}_idem`;
  setupBaseTable(db);
  mysqlFile(migrateSqlPath, db);
  mysqlFile(migrateSqlPath, db); // second run
  const idx = mysql(
    `SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_active_id_card';`,
  ).trim();
  assert.equal(idx, '1');
});

test('migrate: rollback docs — drop new only after asserting we do not reinstall broken old UK', () => {
  // Documents rollback order; validates that after full migrate, DROP old already done
  // and re-adding old (tenant,id_card,deleted) is not performed by script.
  const db = `${schema}_rb`;
  setupBaseTable(db);
  mysqlFile(migrateSqlPath, db);
  const old = mysql(
    `SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_id_card';`,
  ).trim();
  assert.equal(old, '0');
  // schema still protected by new UK
  mysql(`USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-R', 0, 1);`);
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-R', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});

test.after(() => {
  try {
    mysql(`DROP DATABASE IF EXISTS \`${schema}\``);
    for (const s of [`${schema}_badcol`, `${schema}_badidx`, `${schema}_blank`, `${schema}_idem`, `${schema}_rb`]) {
      mysql(`DROP DATABASE IF EXISTS \`${s}\``);
    }
  } catch {
    // ignore cleanup errors
  }
});
