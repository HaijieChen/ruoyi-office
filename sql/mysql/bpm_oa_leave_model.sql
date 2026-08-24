-- OA 请假 BPM 模型 meta（幂等）。设计器导入 sql/mysql/bpmn/oa_leave.bpmn20.xml 并部署后再执行。
SET NAMES utf8mb4;

UPDATE `bpm_process_definition_info`
SET `category` = 'attendance',
    `start_user_ids` = NULL,
    `form_type` = 20,
    `form_custom_create_path` = '/bpm/oa/leave/create',
    `form_custom_view_path` = '/bpm/oa/leave/detail',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'oa_leave%'
    OR `form_custom_create_path` LIKE '%/bpm/oa/leave/%'
    OR `form_custom_view_path` LIKE '%/bpm/oa/leave/%'
  );

UPDATE `ACT_RE_MODEL`
SET `CATEGORY_` = 'attendance',
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` = 'oa_leave';
