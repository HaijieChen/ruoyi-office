-- NON-PRODUCTION ISO FIXTURE. Not a production migration.
-- Source: BpmProcessDefinitionInfoDO + BaseDO + LongListTypeHandler + JacksonTypeHandler
--          + BpmProcessDefinitionInfoMapper (selectByProcessDefinitionId / updateByModelId)
-- Do not dump 33061. Do not run outside oa_u11_iso.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `bpm_process_definition_info` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'BpmProcessDefinitionInfoDO.id',
    `process_definition_id` varchar(64) DEFAULT NULL COMMENT 'processDefinitionId',
    `model_id` varchar(64) DEFAULT NULL COMMENT 'modelId',
    `model_type` int DEFAULT NULL COMMENT 'modelType BpmModelTypeEnum',
    `category` varchar(64) DEFAULT NULL COMMENT 'category -> BpmCategoryDO.code',
    `icon` varchar(512) DEFAULT NULL COMMENT 'icon',
    `description` varchar(1024) DEFAULT NULL COMMENT 'description',
    `form_type` int DEFAULT NULL COMMENT 'formType BpmModelFormTypeEnum',
    `form_id` bigint DEFAULT NULL COMMENT 'formId',
    `form_conf` text COMMENT 'formConf',
    `form_fields` json DEFAULT NULL COMMENT 'formFields JacksonTypeHandler',
    `form_custom_create_path` varchar(255) DEFAULT NULL COMMENT 'formCustomCreatePath',
    `form_custom_view_path` varchar(255) DEFAULT NULL COMMENT 'formCustomViewPath',
    `simple_model` longtext COMMENT 'simpleModel',
    `visible` bit(1) DEFAULT b'1' COMMENT 'visible',
    `sort` bigint DEFAULT NULL COMMENT 'sort',
    `start_user_ids` varchar(1024) DEFAULT NULL COMMENT 'startUserIds LongListTypeHandler',
    `start_dept_ids` varchar(1024) DEFAULT NULL COMMENT 'startDeptIds LongListTypeHandler',
    `manager_user_ids` varchar(1024) DEFAULT NULL COMMENT 'managerUserIds LongListTypeHandler',
    `allow_cancel_running_process` bit(1) DEFAULT NULL COMMENT 'allowCancelRunningProcess',
    `allow_withdraw_task` bit(1) DEFAULT NULL COMMENT 'allowWithdrawTask',
    `process_id_rule` json DEFAULT NULL COMMENT 'processIdRule JacksonTypeHandler',
    `auto_approval_type` int DEFAULT NULL COMMENT 'autoApprovalType',
    `title_setting` json DEFAULT NULL COMMENT 'titleSetting JacksonTypeHandler',
    `summary_setting` json DEFAULT NULL COMMENT 'summarySetting JacksonTypeHandler',
    `process_before_trigger_setting` json DEFAULT NULL COMMENT 'processBeforeTriggerSetting',
    `process_after_trigger_setting` json DEFAULT NULL COMMENT 'processAfterTriggerSetting',
    `task_before_trigger_setting` json DEFAULT NULL COMMENT 'taskBeforeTriggerSetting',
    `task_after_trigger_setting` json DEFAULT NULL COMMENT 'taskAfterTriggerSetting',
    `print_template_setting` json DEFAULT NULL COMMENT 'printTemplateSetting',
    `creator` varchar(64) DEFAULT '' COMMENT 'BaseDO.creator',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'BaseDO.createTime',
    `updater` varchar(64) DEFAULT '' COMMENT 'BaseDO.updater',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'BaseDO.updateTime',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'BaseDO.deleted TableLogic',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'tenant line (DO extends BaseDO not TenantBaseDO)',
    PRIMARY KEY (`id`),
    KEY `idx_pd_id` (`process_definition_id`),
    KEY `idx_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NON-PROD fixture from BpmProcessDefinitionInfoDO';
