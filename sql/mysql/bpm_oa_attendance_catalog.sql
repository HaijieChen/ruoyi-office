-- 已跑过 bpm_process_start_catalog.sql 的环境补加班/补卡假勤分类（幂等）。

SET NAMES utf8mb4;

UPDATE `bpm_process_definition_info` i
INNER JOIN `ACT_RE_PROCDEF` p ON p.`ID_` = i.`process_definition_id`
SET i.`category` = 'attendance', i.`update_time` = NOW()
WHERE i.`deleted` = b'0'
  AND p.`KEY_` IN ('oa_overtime', 'oa_punch_correction');
