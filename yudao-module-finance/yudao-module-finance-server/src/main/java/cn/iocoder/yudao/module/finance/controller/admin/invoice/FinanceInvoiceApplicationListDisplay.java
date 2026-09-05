package cn.iocoder.yudao.module.finance.controller.admin.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 开票申请列表展示补全：申请人姓名、开票时间。
 */
public final class FinanceInvoiceApplicationListDisplay {

    private FinanceInvoiceApplicationListDisplay() {
    }

    public static void fill(List<FinanceInvoiceApplicationRespVO> list,
                            AdminUserApi adminUserApi,
                            FinanceInvoiceApplicationLineMapper lineMapper) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        fillApplicantNames(list, adminUserApi);
        fillIssuedAt(list, lineMapper);
    }

    static void fillApplicantNames(List<FinanceInvoiceApplicationRespVO> list, AdminUserApi adminUserApi) {
        Collection<Long> userIds = convertSet(list, FinanceInvoiceApplicationRespVO::getApplicantUserId);
        userIds.removeIf(Objects::isNull);
        if (userIds.isEmpty() || adminUserApi == null) {
            return;
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        if (userMap == null || userMap.isEmpty()) {
            return;
        }
        for (FinanceInvoiceApplicationRespVO row : list) {
            if (row.getApplicantUserId() == null) {
                continue;
            }
            AdminUserRespDTO user = userMap.get(row.getApplicantUserId());
            if (user == null) {
                continue;
            }
            if (StrUtil.isNotBlank(user.getNickname())) {
                row.setApplicantName(user.getNickname());
            }
        }
    }

    static void fillIssuedAt(List<FinanceInvoiceApplicationRespVO> list,
                             FinanceInvoiceApplicationLineMapper lineMapper) {
        if (lineMapper == null) {
            return;
        }
        Collection<Long> ids = convertSet(list, FinanceInvoiceApplicationRespVO::getId);
        ids.removeIf(Objects::isNull);
        if (ids.isEmpty()) {
            return;
        }
        List<FinanceInvoiceApplicationLineDO> lines = lineMapper.selectListByApplicationIds(ids);
        if (CollUtil.isEmpty(lines)) {
            return;
        }
        Map<Long, LocalDateTime> latest = new HashMap<>();
        for (FinanceInvoiceApplicationLineDO line : lines) {
            if (line.getApplicationId() == null || line.getIssuedAt() == null) {
                continue;
            }
            latest.merge(line.getApplicationId(), line.getIssuedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        for (FinanceInvoiceApplicationRespVO row : list) {
            LocalDateTime issuedAt = latest.get(row.getId());
            if (issuedAt != null) {
                row.setIssuedAt(issuedAt);
            }
        }
    }
}
