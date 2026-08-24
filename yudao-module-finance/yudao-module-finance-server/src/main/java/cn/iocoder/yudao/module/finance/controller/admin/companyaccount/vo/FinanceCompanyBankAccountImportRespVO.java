package cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 公司银行账户导入结果")
@Data
@Builder
public class FinanceCompanyBankAccountImportRespVO {
    private List<String> createdNos;
    private Map<Integer, String> failureRows;
}
