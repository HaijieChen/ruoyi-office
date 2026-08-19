-- 费用报销 BPM 模型 meta（STORY-0011 U2，幂等）
-- 设计器导入 sql/mysql/bpmn/oa_expense_reimbursement.bpmn20.xml 后再执行；
-- 按 process_definition_id 前缀匹配。不 INSERT 流程定义，不部署。
-- 清空 start_user_ids → 全员可发起；分类归 default；业务表单 form_type=20。

SET NAMES utf8mb4;

UPDATE `bpm_process_definition_info`
SET `category` = 'default',
    `start_user_ids` = NULL,
    `form_type` = 20,
    `form_custom_create_path` = '/finance/expense-reimbursement/create',
    `form_custom_view_path` = '/finance/expense-reimbursement/detail',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'oa_expense_reimbursement%'
    OR `form_custom_create_path` LIKE '%/finance/expense-reimbursement/%'
    OR `form_custom_view_path` LIKE '%/finance/expense-reimbursement/%'
  );

-- Flowable 模型分类必须是已有分类 code（默认 default）
UPDATE `ACT_RE_MODEL`
SET `CATEGORY_` = 'default',
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` = 'oa_expense_reimbursement';
