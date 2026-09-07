#!/bin/sh
# U10 SQL fixture on disposable oa_u10_iso only. Never oa_u9_iso / 33061 / 48080 / 6379.
# Asserts sentinel model rows, not just mysql exit codes.
set -eu
CTR=royi-oa-u9u10-iso-mysql
DB=oa_u10_iso
SQL=/tmp/bpm_oa_overtime_model.sql
mysql() { docker exec "$CTR" mysql -uroot --default-character-set=utf8mb4 "$DB" "$@"; }

docker cp sql/mysql/bpm_oa_overtime_model.sql "$CTR:$SQL"

seed_sentinel() {
  mysql -e "
    DELETE FROM bpm_process_definition_info;
    DELETE FROM ACT_RE_MODEL;
    INSERT INTO bpm_process_definition_info
      (process_definition_id, category, start_user_ids, form_type, form_custom_create_path, form_custom_view_path, update_time, deleted)
    VALUES ('oa_overtime:1', 'sentinel_old', 'keep-me', 99, '/old/create', '/old/view', '2020-01-01 00:00:00', b'0');
    INSERT INTO ACT_RE_MODEL (ID_, KEY_, CATEGORY_, LAST_UPDATE_TIME_)
    VALUES ('m1', 'oa_overtime', 'sentinel_model', '2020-01-01 00:00:00.000');
  "
}

assert_unchanged() {
  name=$1
  got=$(mysql -N -e "SELECT CONCAT(category,'|',IFNULL(start_user_ids,''),'|',form_type,'|',form_custom_create_path,'|',form_custom_view_path) FROM bpm_process_definition_info;")
  model=$(mysql -N -e "SELECT CATEGORY_ FROM ACT_RE_MODEL WHERE KEY_='oa_overtime';")
  test "$got" = "sentinel_old|keep-me|99|/old/create|/old/view" || { echo "FAIL $name def $got"; exit 1; }
  test "$model" = "sentinel_model" || { echo "FAIL $name model $model"; exit 1; }
  echo "PASS $name: model rows unchanged"
}

run_file() {
  docker exec "$CTR" sh -c "mysql -uroot --default-character-set=utf8mb4 $DB < $SQL"
}

seed_sentinel
mysql -e "TRUNCATE TABLE system_users;"
run_file && { echo FAIL missing: expected error; exit 1; }
assert_unchanged missing

seed_sentinel
mysql -e "TRUNCATE TABLE system_users; INSERT INTO system_users VALUES (221,'王鹏',b'0',1),(222,'王鹏',b'0',1);"
run_file && { echo FAIL duplicate: expected error; exit 1; }
assert_unchanged duplicate

seed_sentinel
mysql -e "TRUNCATE TABLE system_users; INSERT INTO system_users VALUES (999,'王鹏',b'0',1);"
run_file && { echo FAIL wrong_id: expected error; exit 1; }
assert_unchanged wrong_id

seed_sentinel
mysql -e "TRUNCATE TABLE system_users;"
if docker exec "$CTR" sh -c "mysql -uroot --default-character-set=utf8mb4 $DB -e \"SET @oa_overtime_wangpeng_id=221; SOURCE $SQL;\""; then
  echo FAIL stale: expected error
  exit 1
fi
assert_unchanged stale_source

seed_sentinel
mysql -e "TRUNCATE TABLE system_users; INSERT INTO system_users VALUES (221,'王鹏',b'0',1);"
run_file
got=$(mysql -N -e "SELECT CONCAT(category,'|',IFNULL(start_user_ids,'NULL'),'|',form_type,'|',form_custom_create_path,'|',form_custom_view_path) FROM bpm_process_definition_info;")
model=$(mysql -N -e "SELECT CATEGORY_ FROM ACT_RE_MODEL WHERE KEY_='oa_overtime';")
test "$got" = "attendance|NULL|20|/bpm/oa/overtime/create|/bpm/oa/overtime/detail" || { echo "FAIL unique_221 def $got"; exit 1; }
test "$model" = "attendance" || { echo "FAIL unique_221 model $model"; exit 1; }
echo "PASS unique_221: wrote attendance/path/form_type=20"
echo "ALL_WANGPENG_SENTINEL_CASES_OK"
