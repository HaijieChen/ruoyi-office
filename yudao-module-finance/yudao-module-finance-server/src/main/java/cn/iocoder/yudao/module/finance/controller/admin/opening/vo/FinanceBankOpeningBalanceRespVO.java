package cn.iocoder.yudao.module.finance.controller.admin.opening.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 银行期初余额 Response VO")
@Data
public class FinanceBankOpeningBalanceRespVO {

    private Long id;
    private Long accountId;
    private LocalDate asOfDate;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
