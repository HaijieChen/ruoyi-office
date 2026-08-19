-- 无票费用报销 BPM 模型 meta（导入 BPMN 并部署后再执行）
SET NAMES utf8mb4;

UPDATE bpm_process_definition_info
SET category = 'default',
    start_user_ids = '[]',
    form_type = 20,
    form_custom_create_path = '/finance/expense-reimbursement/no-invoice-create',
    form_custom_view_path = '/finance/expense-reimbursement/detail',
    update_time = NOW()
WHERE deleted = b'0'
  AND (
    process_definition_id LIKE 'oa_expense_no_invoice%'
    OR form_custom_create_path LIKE '%/finance/expense-reimbursement/no-invoice%'
  );

UPDATE ACT_RE_MODEL
SET CATEGORY_ = 'default',
    LAST_UPDATE_TIME_ = NOW(3)
WHERE KEY_ = 'oa_expense_no_invoice';
