-- OA 加班 BPM 模型 meta（幂等）。设计器导入 sql/mysql/bpmn/oa_overtime.bpmn20.xml 并部署后再执行。
-- 不 INSERT 流程定义。清空 start_user_ids → 全员可发起；分类归 attendance；业务表单 form_type=20。
-- BPMN 抄送节点仍带测试库 userId=221。上线后在流程设计器把抄送人改成生产「王鹏」，不必要求库内 id=221。
-- 下面只查询、不失败闭合，避免生产导入被挡。

SET NAMES utf8mb4;

SET @oa_overtime_wangpeng_id = NULL;

SELECT `id`
INTO @oa_overtime_wangpeng_id
FROM `system_users`
WHERE `nickname` = '王鹏'
  AND `deleted` = b'0'
  AND `tenant_id` = 1;

UPDATE `bpm_process_definition_info`
SET `category` = 'attendance',
    `start_user_ids` = NULL,
    `form_type` = 20,
    `form_custom_create_path` = '/bpm/oa/overtime/create',
    `form_custom_view_path` = '/bpm/oa/overtime/detail',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'oa_overtime%'
    OR `form_custom_create_path` LIKE '%/bpm/oa/overtime/%'
    OR `form_custom_view_path` LIKE '%/bpm/oa/overtime/%'
  );

UPDATE `ACT_RE_MODEL`
SET `CATEGORY_` = 'attendance',
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` = 'oa_overtime';
