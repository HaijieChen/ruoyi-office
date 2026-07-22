package cn.iocoder.yudao.module.finance.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    // ========== 银行到款 1-040-000-000 ==========
    ErrorCode RECEIPT_NOT_EXISTS = new ErrorCode(1_040_000_000, "银行到款记录不存在");
    ErrorCode RECEIPT_NO_EXISTS = new ErrorCode(1_040_000_001, "到款流水号已存在");
    ErrorCode RECEIPT_BANK_SERIAL_NO_EXISTS = new ErrorCode(1_040_000_002, "银行流水号已存在");

    // ========== 商务单 1-040-001-000 ==========
    ErrorCode BUSINESS_ORDER_NOT_EXISTS = new ErrorCode(1_040_001_000, "商务单不存在");
    ErrorCode BUSINESS_ORDER_NO_EXISTS = new ErrorCode(1_040_001_001, "商务单号已存在");
    ErrorCode BUSINESS_ORDER_CURRENCY_INVALID = new ErrorCode(1_040_001_002, "币种必须为 3 位大写字母");
    ErrorCode BUSINESS_ORDER_AMOUNT_INVALID = new ErrorCode(1_040_001_003, "应收或应付金额至少有一项大于 0，且金额不能为负数");
    ErrorCode BUSINESS_ORDER_CLOSED = new ErrorCode(1_040_001_004, "已关闭的商务单不能修改");
    ErrorCode BUSINESS_ORDER_DELETE_ONLY_DRAFT = new ErrorCode(1_040_001_005, "只能删除草稿状态的商务单");
    ErrorCode BUSINESS_ORDER_STATUS_INVALID = new ErrorCode(1_040_001_006, "商务单状态无效");

}
