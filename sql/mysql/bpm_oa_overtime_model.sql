-- OA 加班 BPM 模型 meta（幂等）。设计器导入 sql/mysql/bpmn/oa_overtime.bpmn20.xml 并部署后再执行。
-- 不 INSERT 流程定义。清空 start_user_ids → 全员可发起；分类归 attendance；业务表单 form_type=20。
-- KD6：nickname=王鹏 tenant_id=1 必须唯一存在且 id=221，否则种子失败关闭。
-- 无行时 MySQL SELECT INTO 常为 warning 并保留会话旧值，不是必定 ERROR 1329。
-- 因此先把变量置 NULL，避免同连接残留 221 绕过缺失校验。

SET NAMES utf8mb4;

SET @oa_overtime_wangpeng_id = NULL;

SELECT `id`
INTO @oa_overtime_wangpeng_id
FROM `system_users`
WHERE `nickname` = '王鹏'
  AND `deleted` = b'0'
  AND `tenant_id` = 1;

-- 与 BPMN candidateParam=221 对齐；环境 userId 不同则标量子查询多行失败，须改 XML 后重新导入
SET @oa_overtime_wangpeng_assert := IF(
    @oa_overtime_wangpeng_id = 221,
    1,
    (SELECT 1 FROM (SELECT 1 AS n UNION ALL SELECT 2) oa_overtime_wangpeng_mismatch)
);

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
