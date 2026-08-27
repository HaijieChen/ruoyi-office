package cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 手续费付款导入结果")
@Data
@Builder
public class FinanceHandlingFeePaymentImportRespVO {

    private List<Long> createdIds;
    private Map<Integer, String> failureRows;

}
