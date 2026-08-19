package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportRespVO;

import java.util.List;

public interface FinanceGrossMarginReportService {

    FinanceGrossMarginReportPageRespVO getPage(FinanceGrossMarginReportPageReqVO reqVO);

    List<FinanceGrossMarginReportRespVO> listForExport(FinanceGrossMarginReportPageReqVO reqVO);
}
