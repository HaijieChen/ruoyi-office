package cn.iocoder.yudao.module.finance.service.allocation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportExcelVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.allocation.FinanceDeptCostAllocationDO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class FinanceDeptCostAllocationImportSupport {

    static final Set<String> SOURCE_TYPES = Set.of(
            FinanceDeptCostAllocationDO.SOURCE_SALARY,
            FinanceDeptCostAllocationDO.SOURCE_CLOUD,
            FinanceDeptCostAllocationDO.SOURCE_OTHER
    );

    private static final Pattern PERIOD = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Pattern PERIOD_DATE = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-\\d{2}$");

    private FinanceDeptCostAllocationImportSupport() {
    }

    record ParsedRow(String period, Long deptId, String deptName, BigDecimal amount, String remark) {
    }

    static boolean isValidSourceType(String sourceType) {
        return StrUtil.isNotBlank(sourceType) && SOURCE_TYPES.contains(sourceType.trim());
    }

    static boolean isBlankRow(FinanceDeptCostAllocationImportExcelVO row) {
        if (row == null) {
            return true;
        }
        return StrUtil.isBlank(row.getPeriod())
                && StrUtil.isBlank(row.getDeptName())
                && StrUtil.isBlank(row.getDeptId())
                && StrUtil.isBlank(row.getAmount())
                && StrUtil.isBlank(row.getRemark());
    }

    static String validateAndParse(FinanceDeptCostAllocationImportExcelVO row,
                                   Map<String, List<DeptRespDTO>> deptsByName,
                                   Map<Long, DeptRespDTO> deptsById,
                                   ParsedRow[] out) {
        if (row == null) {
            return "导入行不能为空";
        }
        String period = normalizePeriod(row.getPeriod());
        if (period == null) {
            return "期间不能为空且须为 YYYY-MM";
        }
        BigDecimal amount;
        try {
            amount = parseAmount(row.getAmount());
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        DeptRespDTO dept;
        try {
            dept = resolveDept(row.getDeptName(), row.getDeptId(), deptsByName, deptsById);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        out[0] = new ParsedRow(period, dept.getId(), dept.getName(), amount, trimToNull(row.getRemark()));
        return null;
    }

    static Map<String, List<DeptRespDTO>> indexByName(List<DeptRespDTO> depts) {
        Map<String, List<DeptRespDTO>> byName = new HashMap<>();
        if (CollUtil.isEmpty(depts)) {
            return byName;
        }
        for (DeptRespDTO dept : depts) {
            if (dept == null || dept.getId() == null || StrUtil.isBlank(dept.getName())) {
                continue;
            }
            byName.computeIfAbsent(dept.getName().trim(), key -> new ArrayList<>(2)).add(dept);
        }
        return byName;
    }

    static Map<Long, DeptRespDTO> indexById(List<DeptRespDTO> depts) {
        Map<Long, DeptRespDTO> byId = new HashMap<>();
        if (CollUtil.isEmpty(depts)) {
            return byId;
        }
        for (DeptRespDTO dept : depts) {
            if (dept == null || dept.getId() == null) {
                continue;
            }
            byId.put(dept.getId(), dept);
        }
        return byId;
    }

    private static String normalizePeriod(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        if (PERIOD.matcher(value).matches()) {
            return value;
        }
        if (PERIOD_DATE.matcher(value).matches()) {
            return value.substring(0, 7);
        }
        return null;
    }

    private static BigDecimal parseAmount(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new IllegalArgumentException("金额不能为空");
        }
        try {
            BigDecimal amount = new BigDecimal(raw.trim());
            if (amount.scale() > 2) {
                throw new IllegalArgumentException("金额最多两位小数");
            }
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("金额不能为负数");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("金额格式无效");
        }
    }

    private static DeptRespDTO resolveDept(String deptName, String deptIdRaw,
                                           Map<String, List<DeptRespDTO>> deptsByName,
                                           Map<Long, DeptRespDTO> deptsById) {
        String name = trimToNull(deptName);
        Long deptId = parseDeptId(deptIdRaw);
        if (name == null && deptId == null) {
            throw new IllegalArgumentException("部门名称或部门ID不能为空");
        }
        DeptRespDTO byId = deptId == null ? null : deptsById.get(deptId);
        if (deptId != null && byId == null) {
            throw new IllegalArgumentException("部门不存在或未启用：" + deptId);
        }
        DeptRespDTO byName = null;
        if (name != null) {
            List<DeptRespDTO> matches = deptsByName.get(name);
            if (CollUtil.isEmpty(matches)) {
                throw new IllegalArgumentException("部门不存在或未启用：" + name);
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("部门名称存在多个匹配，无法唯一绑定：" + name);
            }
            byName = matches.get(0);
        }
        if (byId != null && byName != null && !byId.getId().equals(byName.getId())) {
            throw new IllegalArgumentException("部门名称与部门ID不一致");
        }
        return byId != null ? byId : byName;
    }

    private static Long parseDeptId(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim()).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("部门ID格式无效");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
