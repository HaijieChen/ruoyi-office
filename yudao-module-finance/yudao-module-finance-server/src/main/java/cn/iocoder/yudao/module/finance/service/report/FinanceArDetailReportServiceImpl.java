package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.mysql.report.FinanceArDetailReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Validated
public class FinanceArDetailReportServiceImpl implements FinanceArDetailReportService {

    private final FinanceArDetailReportMapper arDetailReportMapper;

    public FinanceArDetailReportServiceImpl(FinanceArDetailReportMapper arDetailReportMapper) {
        this.arDetailReportMapper = arDetailReportMapper;
    }

    @Override
    public FinanceArDetailReportPageRespVO getPage(FinanceArDetailReportPageReqVO reqVO,
                                                   Long loginUserId, boolean queryAll) {
        FilteredRows filtered = queryRows(reqVO, loginUserId, queryAll);
        List<FinanceArDetailReportRespVO> rows = filtered.rows();
        FinanceArDetailReportPageRespVO respVO = new FinanceArDetailReportPageRespVO();
        respVO.setTotal((long) rows.size());
        respVO.setExcludedNonCnyCount(filtered.excludedNonCnyCount());
        respVO.setList(paginate(rows, reqVO));
        return respVO;
    }

    @Override
    public List<FinanceArDetailReportRespVO> listForExport(FinanceArDetailReportPageReqVO reqVO,
                                                           Long loginUserId, boolean queryAll) {
        return queryRows(reqVO, loginUserId, queryAll).rows();
    }

    private FilteredRows queryRows(FinanceArDetailReportPageReqVO reqVO, Long loginUserId, boolean queryAll) {
        Long importerId = queryAll ? reqVO.getImporterId() : loginUserId;
        List<FinanceBusinessOrderDO> orders = arDetailReportMapper.selectReportList(reqVO, importerId);
        long excludedNonCnyCount = 0L;
        List<FinanceArDetailReportRespVO> rows = new ArrayList<>();
        for (FinanceBusinessOrderDO order : orders) {
            if (!FinanceArDetailCalculator.isCny(order.getCurrency())) {
                excludedNonCnyCount++;
                continue;
            }
            FinanceArDetailReportRespVO row = toRow(order);
            if (Boolean.TRUE.equals(reqVO.getUninvoicedOnly())
                    && row.getUninvoicedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (Boolean.TRUE.equals(reqVO.getInvoicedArOnly())
                    && row.getInvoicedArAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            rows.add(row);
        }
        return new FilteredRows(rows, excludedNonCnyCount);
    }

    private static List<FinanceArDetailReportRespVO> paginate(List<FinanceArDetailReportRespVO> rows,
                                                              FinanceArDetailReportPageReqVO reqVO) {
        int pageSize = reqVO.getPageSize() == null ? 10 : reqVO.getPageSize();
        if (PageParam.PAGE_SIZE_NONE.equals(pageSize)) {
            return rows;
        }
        int pageNo = reqVO.getPageNo() == null ? 1 : reqVO.getPageNo();
        int from = Math.max((pageNo - 1) * pageSize, 0);
        if (from >= rows.size()) {
            return List.of();
        }
        return rows.subList(from, Math.min(from + pageSize, rows.size()));
    }

    private static FinanceArDetailReportRespVO toRow(FinanceBusinessOrderDO order) {
        FinanceArDetailReportRespVO row = new FinanceArDetailReportRespVO();
        row.setId(order.getId());
        row.setOrderNo(order.getOrderNo());
        row.setEntityCompanyDeptId(order.getEntityCompanyDeptId());
        row.setEntityCompanyName(order.getEntityCompanyName());
        row.setProductType(FinanceArDetailCalculator.effectiveProductType(
                order.getProductTypeSnapshot(), order.getProductName()));
        row.setSettlementAmount(FinanceArDetailCalculator.nz(order.getSettlementAmount()));
        row.setInvoicedOccupiedAmount(FinanceArDetailCalculator.nz(order.getInvoicedOccupiedAmount()));
        row.setConfirmedClaimedAmount(FinanceArDetailCalculator.nz(order.getConfirmedClaimedAmount()));
        row.setUninvoicedAmount(FinanceArDetailCalculator.uninvoiced(
                order.getSettlementAmount(), order.getInvoicedOccupiedAmount()));
        row.setInvoicedArAmount(FinanceArDetailCalculator.invoicedAr(
                order.getInvoicedOccupiedAmount(), order.getConfirmedClaimedAmount()));
        row.setArTotalAmount(FinanceArDetailCalculator.arTotal(
                order.getSettlementAmount(), order.getConfirmedClaimedAmount()));
        row.setCurrency("CNY");
        return row;
    }

    private record FilteredRows(List<FinanceArDetailReportRespVO> rows, long excludedNonCnyCount) {
    }
}
