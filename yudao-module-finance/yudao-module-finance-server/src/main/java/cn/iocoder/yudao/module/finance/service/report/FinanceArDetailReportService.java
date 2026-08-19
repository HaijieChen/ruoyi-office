package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportRespVO;

import java.util.List;

public interface FinanceArDetailReportService {

    FinanceArDetailReportPageRespVO getPage(FinanceArDetailReportPageReqVO reqVO, Long loginUserId, boolean queryAll);

    /** 与分页同一套过滤，供 U2 导出复用。 */
    List<FinanceArDetailReportRespVO> listForExport(FinanceArDetailReportPageReqVO reqVO,
                                                    Long loginUserId, boolean queryAll);
}
