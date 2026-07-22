package cn.iocoder.yudao.module.finance.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    // ========== 银行到款 1-040-000-000 ==========
    ErrorCode RECEIPT_NOT_EXISTS = new ErrorCode(1_040_000_000, "银行到款记录不存在");
    ErrorCode RECEIPT_NO_EXISTS = new ErrorCode(1_040_000_001, "到款流水号已存在");
    ErrorCode RECEIPT_BANK_SERIAL_NO_EXISTS = new ErrorCode(1_040_000_002, "银行流水号已存在");

}
