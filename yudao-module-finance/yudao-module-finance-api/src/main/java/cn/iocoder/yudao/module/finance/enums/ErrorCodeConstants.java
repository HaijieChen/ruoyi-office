package cn.iocoder.yudao.module.finance.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    // ========== 银行到款 1-040-000-000 ==========
    ErrorCode RECEIPT_NOT_EXISTS = new ErrorCode(1_040_000_000, "银行到款记录不存在");
    ErrorCode RECEIPT_NO_EXISTS = new ErrorCode(1_040_000_001, "到款流水号已存在");
    ErrorCode RECEIPT_BANK_SERIAL_NO_EXISTS = new ErrorCode(1_040_000_002, "银行流水号已存在");
    ErrorCode RECEIPT_CLOSE_REASON_REQUIRED = new ErrorCode(1_040_000_003, "关闭原因不能为空");
    ErrorCode RECEIPT_REOPEN_REASON_REQUIRED = new ErrorCode(1_040_000_004, "重开原因不能为空");
    ErrorCode RECEIPT_CLOSE_STATUS_INVALID = new ErrorCode(1_040_000_005,
            "仅可关闭存在未认领金额的待认领或部分认领银行到款");
    ErrorCode RECEIPT_REOPEN_STATUS_INVALID = new ErrorCode(1_040_000_006, "仅可重开已关闭的银行到款");
    ErrorCode RECEIPT_CONCURRENT_MODIFICATION = new ErrorCode(1_040_000_007,
            "银行到款状态已变化，请刷新后重试");
    ErrorCode RECEIPT_UPDATE_STATUS_INVALID = new ErrorCode(1_040_000_008,
            "仅未认领且无认领金额的银行到款可修改");
    ErrorCode RECEIPT_DELETE_STATUS_INVALID = new ErrorCode(1_040_000_009,
            "仅未认领且无认领金额的银行到款可删除");

    // ========== 商务单 1-040-001-000 ==========
    ErrorCode BUSINESS_ORDER_NOT_EXISTS = new ErrorCode(1_040_001_000, "商务单不存在");
    ErrorCode BUSINESS_ORDER_NO_EXISTS = new ErrorCode(1_040_001_001, "商务单号已存在");
    ErrorCode BUSINESS_ORDER_AMOUNT_INVALID = new ErrorCode(1_040_001_003, "应收或应付金额至少有一项大于 0，且金额不能为负数");
    ErrorCode BUSINESS_ORDER_CLOSED = new ErrorCode(1_040_001_004, "已关闭的商务单不能修改");
    ErrorCode BUSINESS_ORDER_DELETE_ONLY_DRAFT = new ErrorCode(1_040_001_005, "只能删除草稿状态的商务单");
    ErrorCode BUSINESS_ORDER_STATUS_INVALID = new ErrorCode(1_040_001_006, "商务单状态无效");
    ErrorCode BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED = new ErrorCode(1_040_001_007,
            "应收金额不能低于已确认认领金额");
    ErrorCode BUSINESS_ORDER_DELETE_HAS_CLAIM = new ErrorCode(1_040_001_008,
            "已有确认认领金额的商务单不能删除");

    // ========== 到款认领 1-040-002-000 ==========
    ErrorCode RECEIPT_CLAIM_NOT_EXISTS = new ErrorCode(1_040_002_000, "到款认领单不存在");
    ErrorCode RECEIPT_CLAIM_STATUS_INVALID = new ErrorCode(1_040_002_001, "当前状态不允许执行该操作");
    ErrorCode RECEIPT_CLAIM_NOT_OWNER = new ErrorCode(1_040_002_002, "只能操作本人提交的到款认领单");
    ErrorCode RECEIPT_CLAIM_ITEMS_EMPTY = new ErrorCode(1_040_002_003, "认领明细不能为空");
    ErrorCode RECEIPT_CLAIM_AMOUNT_INVALID = new ErrorCode(1_040_002_004, "认领金额必须大于 0");
    ErrorCode RECEIPT_CLAIM_ITEM_DUPLICATE = new ErrorCode(1_040_002_005, "同一到款和商务单不能重复分摊");
    ErrorCode RECEIPT_CLAIM_RECEIPT_INVALID = new ErrorCode(1_040_002_006, "银行到款不存在或可认领金额不足");
    ErrorCode RECEIPT_CLAIM_BUSINESS_ORDER_INVALID = new ErrorCode(1_040_002_007,
            "商务单不存在、非有效状态、非本人负责或待回款金额不足");
    ErrorCode RECEIPT_CLAIM_REJECT_REASON_REQUIRED = new ErrorCode(1_040_002_008, "驳回原因不能为空");
    ErrorCode RECEIPT_CLAIM_CONCURRENT_MODIFICATION = new ErrorCode(1_040_002_009,
            "认领单已处理或余额已变化，请刷新后重试");
    ErrorCode RECEIPT_CLAIM_REVOKE_REASON_REQUIRED = new ErrorCode(1_040_002_010, "撤销原因不能为空");
    ErrorCode RECEIPT_CLAIM_RECEIPT_CLOSED = new ErrorCode(1_040_002_011, "已关闭的银行到款不能认领");

}
