-- OA 补卡 BPM 模型 meta（幂等）。设计器导入 sql/mysql/bpmn/oa_punch_correction.bpmn20.xml 并部署后再执行。
-- 不 INSERT 流程定义。清空 start_user_ids → 全员可发起；分类归 attendance；业务表单 form_type=20。

SET NAMES utf8mb4;

UPDATE `bpm_process_definition_info`
SET `category` = 'attendance',
    `start_user_ids` = NULL,
    `form_type` = 20,
    `form_custom_create_path` = '/bpm/oa/punch/create',
    `form_custom_view_path` = '/bpm/oa/punch/detail',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'oa_punch_correction%'
    OR `form_custom_create_path` LIKE '%/bpm/oa/punch/%'
    OR `form_custom_view_path` LIKE '%/bpm/oa/punch/%'
  );

UPDATE `ACT_RE_MODEL`
SET `CATEGORY_` = 'attendance',
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` = 'oa_punch_correction';
