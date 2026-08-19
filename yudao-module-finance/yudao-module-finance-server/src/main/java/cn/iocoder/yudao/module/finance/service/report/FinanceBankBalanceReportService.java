package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceBankBalanceReportReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceBankBalanceReportRespVO;

public interface FinanceBankBalanceReportService {

    FinanceBankBalanceReportRespVO query(FinanceBankBalanceReportReqVO reqVO);
}
