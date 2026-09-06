-- 发布前执行。仅增加定义快照列；旧定义保持 NULL，不回填推断策略。
-- 人工审批状态表由独立迁移维护。
SET @initiator_withdraw_column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'bpm_process_definition_info'
      AND COLUMN_NAME = 'initiator_withdraw_mode'
);
SET @initiator_withdraw_ddl = IF(
    @initiator_withdraw_column_exists = 0,
    'ALTER TABLE `bpm_process_definition_info` ADD COLUMN `initiator_withdraw_mode` INT NULL DEFAULT NULL COMMENT ''发起人撤回首节点：0禁止/1无人审批/2审批中允许；NULL兼容旧规则''',
    'SELECT 1'
);
PREPARE initiator_withdraw_statement FROM @initiator_withdraw_ddl;
EXECUTE initiator_withdraw_statement;
DEALLOCATE PREPARE initiator_withdraw_statement;
