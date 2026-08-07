-- 付款申请 BPM 可见性兜底（幂等）
-- 1) 可发起人清空 → 统一发起入口「全员」可见（仍受菜单 create 约束）
-- 2) 模型分类归到 default → 流程模型管理页按分类分组才能显示
-- 背景：API 部署时 CATEGORY_ 曾写成 '1'，与 bpm_category.code=default 对不上，admin 列表也看不到

SET NAMES utf8mb4;

-- A. 已部署定义：可发起人 + 分类
UPDATE `bpm_process_definition_info`
SET `start_user_ids` = NULL,
    `category` = 'default',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `process_definition_id` LIKE 'finance_payment_apply%';

-- B. Flowable 模型：分类必须是已有分类 code（默认 default）
UPDATE `ACT_RE_MODEL`
SET `CATEGORY_` = 'default',
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` = 'finance_payment_apply';

-- C. 模型 META_INFO 内 startUserIds 清空为全员（JSON 内字段；若 META 为空则跳过人工检查）
-- 注意：不同环境 META_INFO 结构一致时可用；否则请在「流程模型 → 编辑 → 谁可以发起 = 全员」保存
UPDATE `ACT_RE_MODEL`
SET `META_INFO_` = JSON_SET(
        COALESCE(NULLIF(`META_INFO_`, ''), '{}'),
        '$.startUserIds', JSON_ARRAY(),
        '$.visible', TRUE
    ),
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` = 'finance_payment_apply'
  AND `META_INFO_` IS NOT NULL
  AND `META_INFO_` <> '';

-- 回读
SELECT ID_, NAME_, KEY_, CATEGORY_,
       JSON_EXTRACT(META_INFO_, '$.startUserIds') AS startUserIds,
       JSON_EXTRACT(META_INFO_, '$.visible') AS visible
FROM `ACT_RE_MODEL`
WHERE `KEY_` = 'finance_payment_apply';

SELECT id, process_definition_id, category, start_user_ids, start_dept_ids, visible
FROM `bpm_process_definition_info`
WHERE `deleted` = b'0'
  AND `process_definition_id` LIKE 'finance_payment_apply%';
