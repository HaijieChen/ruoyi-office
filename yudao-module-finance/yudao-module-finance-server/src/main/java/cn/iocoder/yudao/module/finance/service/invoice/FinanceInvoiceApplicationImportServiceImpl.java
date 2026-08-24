package cn.iocoder.yudao.module.finance.service.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
@Validated
public class FinanceInvoiceApplicationImportServiceImpl implements FinanceInvoiceApplicationImportService {

    private final FinanceInvoiceApplicationMapper applicationMapper;
    private final FinanceInvoiceApplicationLineMapper lineMapper;
    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final AdminUserApi adminUserApi;

    public FinanceInvoiceApplicationImportServiceImpl(FinanceInvoiceApplicationMapper applicationMapper,
                                                      FinanceInvoiceApplicationLineMapper lineMapper,
                                                      FinanceBusinessOrderMapper businessOrderMapper,
                                                      AdminUserApi adminUserApi) {
        this.applicationMapper = applicationMapper;
        this.lineMapper = lineMapper;
        this.businessOrderMapper = businessOrderMapper;
        this.adminUserApi = adminUserApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinanceInvoiceApplicationImportRespVO importHistorical(
            List<FinanceInvoiceApplicationImportExcelVO> rows) {
        if (CollUtil.isEmpty(rows)) {
            throw new IllegalArgumentException("导入开票申请数据不能为空");
        }
        FinanceInvoiceApplicationImportRespVO resp = FinanceInvoiceApplicationImportRespVO.builder()
                .createdNos(new ArrayList<>())
                .failureRows(new LinkedHashMap<>())
                .build();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            FinanceInvoiceApplicationImportExcelVO row = rows.get(i);
            String no = StrUtil.trim(row.getApplicationNo());
            if (StrUtil.isBlank(no)) {
                resp.getFailureRows().put(rowNumber, "开票申请单号不能为空");
                continue;
            }
            if (!seen.add(no)) {
                resp.getFailureRows().put(rowNumber, "本文件内开票申请单号重复");
                continue;
            }
            if (applicationMapper.selectByApplicationNo(no) != null) {
                resp.getFailureRows().put(rowNumber, "开票申请单号已存在");
                continue;
            }
            if (row.getTotalAmount() == null || row.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                resp.getFailureRows().put(rowNumber, "开票金额必须大于 0");
                continue;
            }
            if (StrUtil.isBlank(row.getApplicantUsername())) {
                resp.getFailureRows().put(rowNumber, "申请人账号不能为空");
                continue;
            }
            if (StrUtil.isBlank(row.getBuyerName())) {
                resp.getFailureRows().put(rowNumber, "购方名称不能为空");
                continue;
            }
            AdminUserRespDTO user = adminUserApi.getUserByUsername(row.getApplicantUsername().trim()).getData();
            if (user == null) {
                resp.getFailureRows().put(rowNumber, "申请人账号不存在");
                continue;
            }
            if (!Integer.valueOf(CommonStatusEnum.ENABLE.getStatus()).equals(user.getStatus())) {
                resp.getFailureRows().put(rowNumber, "申请人账号已停用");
                continue;
            }
            Long boId = null;
            if (StrUtil.isNotBlank(row.getBusinessOrderNo())) {
                FinanceBusinessOrderDO bo = businessOrderMapper.selectByOrderNo(row.getBusinessOrderNo().trim());
                if (bo == null) {
                    resp.getFailureRows().put(rowNumber, "商务单号不存在");
                    continue;
                }
                boId = bo.getId();
            }
            String currency = StrUtil.blankToDefault(StrUtil.trim(row.getCurrency()), "CNY");
            FinanceInvoiceApplicationDO app = FinanceInvoiceApplicationDO.builder()
                    .applicationNo(no)
                    .applicantUserId(user.getId())
                    .approvalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus())
                    .issueStatus(FinanceInvoiceIssueStatusEnum.FULL.getStatus())
                    .totalAmount(row.getTotalAmount())
                    .confirmedClaimedAmount(BigDecimal.ZERO)
                    .pendingClaimedAmount(BigDecimal.ZERO)
                    .buyerName(row.getBuyerName().trim())
                    .currency(currency)
                    .voided(Boolean.FALSE)
                    .redFlushed(Boolean.FALSE)
                    .remark(StrUtil.blankToDefault(row.getInvoiceNo(), null))
                    .build();
            applicationMapper.insert(app);
            if (boId != null) {
                lineMapper.insert(FinanceInvoiceApplicationLineDO.builder()
                        .applicationId(app.getId())
                        .businessOrderId(boId)
                        .amount(row.getTotalAmount())
                        .invoiceNo(StrUtil.trim(row.getInvoiceNo()))
                        .sort(1)
                        .build());
            }
            resp.getCreatedNos().add(no);
        }
        return resp;
    }
}
