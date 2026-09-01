-- 用印申请：印章改为手填名称后，不再要求章库 ID/编号
SET NAMES utf8mb4;

ALTER TABLE `oa_seal_apply_bill`
  MODIFY COLUMN `seal_id` bigint NULL DEFAULT NULL COMMENT '印章ID',
  MODIFY COLUMN `seal_no` varchar(50) NULL DEFAULT NULL COMMENT '印章编号';
