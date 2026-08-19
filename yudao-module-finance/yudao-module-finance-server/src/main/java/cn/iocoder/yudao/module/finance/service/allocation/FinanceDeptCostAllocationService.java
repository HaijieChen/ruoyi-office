package cn.iocoder.yudao.module.finance.service.allocation;

import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportRespVO;

import java.util.List;

public interface FinanceDeptCostAllocationService {

    FinanceDeptCostAllocationImportRespVO importAllocationList(
            List<FinanceDeptCostAllocationImportExcelVO> rows, String sourceType, Long importerId);
}
