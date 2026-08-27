package cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 手续费付款 Response VO")
@Data
public class FinanceHandlingFeePaymentRespVO {

    private Long id;
    private LocalDate feeDate;
    private BigDecimal amount;
    private String currency;
    private Long entityCompanyDeptId;
    private String entityCompanyName;
    private Long companyBankAccountId;
    private String accountName;
    private String bankName;
    private String accountNo;
    private String accountNoMasked;
    private LocalDateTime createTime;

}
