package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportRespVO;

public interface FinanceDeptProfitReportService {

    FinanceDeptProfitReportRespVO query(FinanceDeptProfitReportReqVO reqVO);
}
