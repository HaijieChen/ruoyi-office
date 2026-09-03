-- 商务单提单人：数据权限按申请人，导入必填。历史行回填导入人。
ALTER TABLE finance_business_order
    ADD COLUMN applicant_user_id BIGINT NULL COMMENT '提单人用户编号' AFTER importer_id,
    ADD COLUMN applicant_dept_id BIGINT NULL COMMENT '提单人部门编号' AFTER applicant_user_id;

UPDATE finance_business_order bo
    LEFT JOIN system_users u ON u.id = bo.importer_id AND u.deleted = 0
SET bo.applicant_user_id = bo.importer_id,
    bo.applicant_dept_id = u.dept_id
WHERE bo.deleted = 0
  AND bo.applicant_user_id IS NULL;
