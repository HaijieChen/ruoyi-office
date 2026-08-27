SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_handling_fee_payment` (
    `id`                       bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `fee_date`                 date NOT NULL COMMENT '付款日期',
    `amount`                   decimal(18,2) NOT NULL COMMENT '金额',
    `currency`                 varchar(16) NOT NULL COMMENT '币种 CNY/USD/HKD',
    `entity_company_dept_id`   bigint NOT NULL COMMENT '主体公司=system_dept.id',
    `entity_company_name`      varchar(255) NOT NULL COMMENT '主体公司名称快照',
    `company_bank_account_id`  bigint NOT NULL COMMENT '公司银行账户编号',
    `account_name`             varchar(128) NOT NULL COMMENT '户名快照',
    `bank_name`                varchar(255) NOT NULL COMMENT '开户行快照',
    `account_no`               varchar(128) NOT NULL COMMENT '银行账号快照',
    `account_no_masked`        varchar(64) NOT NULL COMMENT '账号掩码',
    `creator`                  varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                  varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                  bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_fhfp_fee_date` (`fee_date`),
    KEY `idx_fhfp_entity_company` (`entity_company_dept_id`),
    KEY `idx_fhfp_tenant` (`tenant_id`)
) ENGINE=InnoDB COMMENT='财务手续费付款台账';
