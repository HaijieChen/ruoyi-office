-- OA 出差/外出 BPM 模型 meta（幂等）
-- 设计器导入 sql/mysql/bpmn/oa_business_trip.bpmn20.xml 与
-- sql/mysql/bpmn/oa_outing.bpmn20.xml 并部署后再执行；按 process_definition_id 前缀匹配。
-- 不 INSERT 流程定义。清空 start_user_ids → 全员可发起；分类归 default；业务表单 form_type=20。

SET NAMES utf8mb4;

-- 出差申请
UPDATE `bpm_process_definition_info`
SET `category` = 'default',
    `start_user_ids` = NULL,
    `form_type` = 20,
    `form_custom_create_path` = '/bpm/oa/trip/create',
    `form_custom_view_path` = '/bpm/oa/trip/detail',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'oa_business_trip%'
    OR `form_custom_create_path` LIKE '%/bpm/oa/trip/%'
    OR `form_custom_view_path` LIKE '%/bpm/oa/trip/%'
  );

-- 外出申请
UPDATE `bpm_process_definition_info`
SET `category` = 'default',
    `start_user_ids` = NULL,
    `form_type` = 20,
    `form_custom_create_path` = '/bpm/oa/outing/create',
    `form_custom_view_path` = '/bpm/oa/outing/detail',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'oa_outing%'
    OR `form_custom_create_path` LIKE '%/bpm/oa/outing/%'
    OR `form_custom_view_path` LIKE '%/bpm/oa/outing/%'
  );

-- Flowable 模型分类必须是已有分类 code（默认 default）
UPDATE `ACT_RE_MODEL`
SET `CATEGORY_` = 'default',
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` IN ('oa_business_trip', 'oa_outing');
