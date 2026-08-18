package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationImportRespVO;

import java.util.List;

public interface FinanceContractApplicationImportService {

    FinanceContractApplicationImportRespVO importApprovedList(List<FinanceContractApplicationImportExcelVO> rows);
}
