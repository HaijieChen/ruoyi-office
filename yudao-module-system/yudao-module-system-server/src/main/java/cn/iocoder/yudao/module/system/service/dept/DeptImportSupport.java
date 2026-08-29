package cn.iocoder.yudao.module.system.service.dept;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportErrorRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportExcelVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.enums.OrgTypeEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 组织导入纯函数：规范化、路径、字段比较、错误构造。
 */
public final class DeptImportSupport {

    public static final int MAX_ROWS = 1000;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_DEPTH = 20;
    public static final String PATH_SEPARATOR = "/";
    public static final Set<String> CURRENCIES = DeptServiceImpl.FUNCTIONAL_CURRENCIES;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private DeptImportSupport() {
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String buildPath(String parentPath, String name) {
        String n = Objects.requireNonNull(name);
        if (StrUtil.isBlank(parentPath)) {
            return n;
        }
        return parentPath + PATH_SEPARATOR + n;
    }

    public static int pathDepth(String orgPath) {
        if (StrUtil.isBlank(orgPath)) {
            return 0;
        }
        return orgPath.split(PATH_SEPARATOR, -1).length;
    }

    /**
     * 由当前租户组织列表构建 路径 → 节点 映射；同路径多节点返回歧义集合。
     */
    public static PathIndex buildPathIndex(List<DeptDO> depts) {
        Map<Long, DeptDO> byId = new LinkedHashMap<>();
        for (DeptDO dept : depts) {
            byId.put(dept.getId(), dept);
        }
        Map<String, List<DeptDO>> pathToNodes = new LinkedHashMap<>();
        for (DeptDO dept : depts) {
            String path = resolveExistingPath(dept, byId);
            if (path == null) {
                // 孤儿/环：路径无法唯一解析，记为空路径歧义，由调用方 fail-closed
                pathToNodes.computeIfAbsent("", k -> new ArrayList<>()).add(dept);
                continue;
            }
            pathToNodes.computeIfAbsent(path, k -> new ArrayList<>()).add(dept);
        }
        Map<String, DeptDO> unique = new LinkedHashMap<>();
        List<String> ambiguous = new ArrayList<>();
        for (Map.Entry<String, List<DeptDO>> e : pathToNodes.entrySet()) {
            if (e.getKey().isEmpty()) {
                ambiguous.add("(orphan)");
                continue;
            }
            if (e.getValue().size() != 1) {
                ambiguous.add(e.getKey());
            } else {
                unique.put(e.getKey(), e.getValue().get(0));
            }
        }
        return new PathIndex(unique, ambiguous);
    }

    public static String resolveExistingPath(DeptDO dept, Map<Long, DeptDO> byId) {
        List<String> parts = new ArrayList<>();
        DeptDO current = dept;
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            if (current == null || StrUtil.isBlank(current.getName())) {
                return null;
            }
            parts.add(current.getName());
            Long parentId = current.getParentId();
            if (parentId == null || DeptDO.PARENT_ID_ROOT.equals(parentId)) {
                break;
            }
            if (Objects.equals(parentId, current.getId())) {
                return null;
            }
            current = byId.get(parentId);
            if (current == null) {
                return null;
            }
        }
        Collections.reverse(parts);
        return String.join(PATH_SEPARATOR, parts);
    }

    public static boolean sameAsExisting(NormalizedRow row, DeptDO existing) {
        if (!Objects.equals(row.orgType(), String.valueOf(existing.getOrgType()))) {
            return false;
        }
        if (!Objects.equals(row.sort(), existing.getSort())) {
            return false;
        }
        if (!Objects.equals(row.status(), existing.getStatus())) {
            return false;
        }
        if (row.leaderUserId() != null && !Objects.equals(row.leaderUserId(), existing.getLeaderUserId())) {
            return false;
        }
        if (trimToNull(row.phone()) != null
                && !Objects.equals(trimToNull(row.phone()), trimToNull(existing.getPhone()))) {
            return false;
        }
        if (trimToNull(row.email()) != null
                && !Objects.equals(trimToNull(row.email()), trimToNull(existing.getEmail()))) {
            return false;
        }
        return true;
    }

    /** Excel 空负责人/电话/邮箱保留库中原值；不改上级。 */
    public static void applyImportUpdate(NormalizedRow row, DeptDO existing, DeptSaveReqVO updateReq) {
        updateReq.setId(existing.getId());
        updateReq.setName(row.name());
        updateReq.setParentId(existing.getParentId());
        updateReq.setSort(row.sort());
        updateReq.setStatus(row.status());
        updateReq.setOrgType(row.orgType());
        updateReq.setFunctionalCurrency(existing.getFunctionalCurrency());
        updateReq.setLeaderUserId(row.leaderUserId() != null ? row.leaderUserId() : existing.getLeaderUserId());
        updateReq.setPhone(trimToNull(row.phone()) != null ? row.phone() : existing.getPhone());
        updateReq.setEmail(trimToNull(row.email()) != null ? row.email() : existing.getEmail());
    }

    public static DeptImportErrorRespVO error(int rowNumber, String orgPath, String field, String code, String message) {
        return DeptImportErrorRespVO.builder()
                .rowNumber(rowNumber)
                .orgPath(orgPath)
                .field(field)
                .code(code)
                .message(message)
                .build();
    }

    public static boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    public static String orgTypeFromLabel(String label) {
        if (label == null) {
            return null;
        }
        String t = label.trim();
        if ("公司".equals(t) || OrgTypeEnum.COMPANY.getValue().equals(t)) {
            return OrgTypeEnum.COMPANY.getValue();
        }
        if ("部门".equals(t) || OrgTypeEnum.DEPARTMENT.getValue().equals(t)) {
            return OrgTypeEnum.DEPARTMENT.getValue();
        }
        return null;
    }

    public static Integer statusFromLabel(String label) {
        if (label == null) {
            return null;
        }
        String t = label.trim();
        if ("启用".equals(t) || "开启".equals(t) || "0".equals(t)) {
            return CommonStatusEnum.ENABLE.getStatus();
        }
        if ("停用".equals(t) || "关闭".equals(t) || "1".equals(t)) {
            return CommonStatusEnum.DISABLE.getStatus();
        }
        return null;
    }

    public static boolean isBlankRow(DeptImportExcelVO row) {
        if (row == null) {
            return true;
        }
        return StrUtil.isAllBlank(row.getName(), row.getParentPath(), row.getOrgTypeLabel(),
                row.getSortText(), row.getStatusLabel(), row.getFunctionalCurrency(),
                row.getLeaderUsername(), row.getPhone(), row.getEmail());
    }

    public static String utf8(String s) {
        return s == null ? "" : new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    public static String lastPathSegment(String path) {
        if (StrUtil.isBlank(path)) {
            return path;
        }
        int idx = path.lastIndexOf(PATH_SEPARATOR);
        return idx < 0 ? path : path.substring(idx + 1);
    }

    /**
     * 上级列：完整路径优先，否则按名称取最近一次出现的节点。
     *
     * @return 父节点完整路径；根返回空串；无法解析返回 null
     */
    public static String resolveParentRef(String parentRef,
                                          Map<String, NormalizedRow> fileByFullPath,
                                          Map<String, DeptDO> existingByPath,
                                          Map<String, String> lastFullPathByName) {
        if (StrUtil.isBlank(parentRef)) {
            return "";
        }
        if (fileByFullPath.containsKey(parentRef) || existingByPath.containsKey(parentRef)) {
            return parentRef;
        }
        return lastFullPathByName.get(parentRef);
    }

    public record PathIndex(Map<String, DeptDO> uniquePaths, List<String> ambiguousPaths) {
    }

    /**
     * 规范化后的导入行（字段级校验通过后的中间态）。
     */
    public record NormalizedRow(
            int rowNumber,
            String name,
            String parentPath,
            String orgPath,
            String orgType,
            Integer sort,
            Integer status,
            String functionalCurrency,
            String leaderUsername,
            Long leaderUserId,
            String phone,
            String email
    ) {
        public NormalizedRow withPaths(String newParentPath, String newOrgPath) {
            return new NormalizedRow(rowNumber, name, newParentPath, newOrgPath, orgType, sort, status,
                    functionalCurrency, leaderUsername, leaderUserId, phone, email);
        }
    }

}
