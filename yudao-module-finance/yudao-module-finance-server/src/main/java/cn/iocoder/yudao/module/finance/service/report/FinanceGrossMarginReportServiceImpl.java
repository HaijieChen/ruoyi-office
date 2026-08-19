package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Validated
public class FinanceGrossMarginReportServiceImpl implements FinanceGrossMarginReportService {

    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinancePaymentPayLineMapper payLineMapper;
    private final FinancePaymentApplicationMapper paymentApplicationMapper;
    private final AdminUserApi adminUserApi;

    public FinanceGrossMarginReportServiceImpl(FinanceBusinessOrderMapper businessOrderMapper,
                                               FinancePaymentPayLineMapper payLineMapper,
                                               FinancePaymentApplicationMapper paymentApplicationMapper,
                                               AdminUserApi adminUserApi) {
        this.businessOrderMapper = businessOrderMapper;
        this.payLineMapper = payLineMapper;
        this.paymentApplicationMapper = paymentApplicationMapper;
        this.adminUserApi = adminUserApi;
    }

    @Override
    public FinanceGrossMarginReportPageRespVO getPage(FinanceGrossMarginReportPageReqVO reqVO) {
        Result result = query(reqVO);
        FinanceGrossMarginReportPageRespVO resp = new FinanceGrossMarginReportPageRespVO();
        resp.setTotal((long) result.rows().size());
        resp.setExcludedNonCnyCount(result.excludedNonCnyCount());
        resp.setList(paginate(result.rows(), reqVO));
        return resp;
    }

    @Override
    public List<FinanceGrossMarginReportRespVO> listForExport(FinanceGrossMarginReportPageReqVO reqVO) {
        return query(reqVO).rows();
    }

    Result query(FinanceGrossMarginReportPageReqVO reqVO) {
        LocalDate from = parseMonthStart(reqVO.getFromMonth());
        LocalDate to = parseMonthEnd(reqVO.getToMonth());
        List<FinanceBusinessOrderDO> orders = businessOrderMapper.selectList(
                new LambdaQueryWrapperX<FinanceBusinessOrderDO>()
                        .geIfPresent(FinanceBusinessOrderDO::getOrderDate, from)
                        .leIfPresent(FinanceBusinessOrderDO::getOrderDate, to));
        List<FinancePaymentPayLineDO> lines = payLineMapper.selectList(
                new LambdaQueryWrapperX<FinancePaymentPayLineDO>()
                        .geIfPresent(FinancePaymentPayLineDO::getActualPayDate, from)
                        .leIfPresent(FinancePaymentPayLineDO::getActualPayDate, to));
        Set<Long> paymentIds = lines.stream()
                .map(FinancePaymentPayLineDO::getPaymentApplicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, FinancePaymentApplicationDO> payments = new HashMap<>();
        if (!paymentIds.isEmpty()) {
            for (FinancePaymentApplicationDO payment : paymentApplicationMapper.selectBatchIds(paymentIds)) {
                payments.put(payment.getId(), payment);
            }
        }
        return aggregate(orders, lines, payments, reqVO);
    }

    Result aggregate(List<FinanceBusinessOrderDO> orders,
                     List<FinancePaymentPayLineDO> lines,
                     Map<Long, FinancePaymentApplicationDO> payments,
                     FinanceGrossMarginReportPageReqVO reqVO) {
        Map<Long, Long> deptByUser = new HashMap<>();
        Map<String, FinanceGrossMarginReportRespVO> acc = FinanceGrossMarginCalculator.newAcc();
        long excluded = 0L;
        for (FinanceBusinessOrderDO order : orders) {
            if (!FinanceGrossMarginCalculator.isCny(order.getCurrency())) {
                excluded++;
                continue;
            }
            String ym = FinanceGrossMarginCalculator.yearMonth(order.getOrderDate());
            if (ym == null || !inMonthRange(ym, reqVO)) {
                continue;
            }
            Long deptId = FinanceGrossMarginCalculator.deptOrUnassigned(
                    deptOf(order.getImporterId(), deptByUser));
            String product = FinanceGrossMarginCalculator.productOrUnclassified(
                    FinanceArDetailCalculator.effectiveProductType(
                            order.getProductTypeSnapshot(), order.getProductName()));
            if (!matchFilter(reqVO, deptId, product)) {
                continue;
            }
            FinanceGrossMarginReportRespVO row = acc.computeIfAbsent(
                    FinanceGrossMarginCalculator.key(ym, deptId, product),
                    k -> FinanceGrossMarginCalculator.row(ym, deptId, null, product));
            FinanceGrossMarginCalculator.addIncome(row, order.getSettlementAmount());
        }
        for (FinancePaymentPayLineDO line : lines) {
            FinancePaymentApplicationDO payment = payments.get(line.getPaymentApplicationId());
            if (payment == null) {
                continue;
            }
            String kind = payment.getApplicationKind();
            if (kind != null && !kind.isBlank() && !"ORDINARY".equalsIgnoreCase(kind)) {
                continue;
            }
            if (!FinanceGrossMarginCalculator.isCny(
                    line.getCurrencySnapshot() != null ? line.getCurrencySnapshot() : payment.getCurrency())) {
                excluded++;
                continue;
            }
            String ym = FinanceGrossMarginCalculator.yearMonth(line.getActualPayDate());
            if (ym == null || !inMonthRange(ym, reqVO)) {
                continue;
            }
            Long deptId = FinanceGrossMarginCalculator.deptOrUnassigned(
                    deptOf(payment.getApplicantUserId(), deptByUser));
            String product = FinanceGrossMarginCalculator.productOrUnclassified(payment.getCostProject());
            if (!matchFilter(reqVO, deptId, product)) {
                continue;
            }
            FinanceGrossMarginReportRespVO row = acc.computeIfAbsent(
                    FinanceGrossMarginCalculator.key(ym, deptId, product),
                    k -> FinanceGrossMarginCalculator.row(ym, deptId, null, product));
            FinanceGrossMarginCalculator.addCost(row, line.getPayAmount());
        }
        return new Result(FinanceGrossMarginCalculator.toSortedRows(acc), excluded);
    }

    private Long deptOf(Long userId, Map<Long, Long> cache) {
        if (userId == null) {
            return null;
        }
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        Long deptId = null;
        try {
            CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
            AdminUserRespDTO user = result == null ? null : result.getCheckedData();
            if (user != null) {
                deptId = user.getDeptId();
            }
        } catch (Exception ignored) {
            deptId = null;
        }
        cache.put(userId, deptId);
        return deptId;
    }

    private static boolean matchFilter(FinanceGrossMarginReportPageReqVO reqVO, Long deptId, String product) {
        if (reqVO.getDeptId() != null && !reqVO.getDeptId().equals(deptId)) {
            return false;
        }
        return reqVO.getProductType() == null || reqVO.getProductType().isBlank()
                || reqVO.getProductType().trim().equals(product);
    }

    private static boolean inMonthRange(String yearMonth, FinanceGrossMarginReportPageReqVO reqVO) {
        if (reqVO.getFromMonth() != null && !reqVO.getFromMonth().isBlank()
                && yearMonth.compareTo(reqVO.getFromMonth().trim()) < 0) {
            return false;
        }
        return reqVO.getToMonth() == null || reqVO.getToMonth().isBlank()
                || yearMonth.compareTo(reqVO.getToMonth().trim()) <= 0;
    }

    private static LocalDate parseMonthStart(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        return YearMonth.parse(month.trim()).atDay(1);
    }

    private static LocalDate parseMonthEnd(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        return YearMonth.parse(month.trim()).atEndOfMonth();
    }

    private static List<FinanceGrossMarginReportRespVO> paginate(
            List<FinanceGrossMarginReportRespVO> rows, FinanceGrossMarginReportPageReqVO reqVO) {
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

    record Result(List<FinanceGrossMarginReportRespVO> rows, long excludedNonCnyCount) {
    }
}
