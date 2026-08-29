package cn.iocoder.yudao.module.system.service.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportErrorRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportExcelVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.OrgTypeEnum;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_EMPTY;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_FILE_CHANGED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_FILE_TOO_LARGE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_FILE_TYPE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_NO_FULL_DATA_SCOPE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_ROW_LIMIT;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.MAX_DEPTH;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.MAX_FILE_BYTES;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.MAX_ROWS;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.buildPath;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.buildPathIndex;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.error;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.isBlankRow;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.isValidEmail;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.lastPathSegment;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.orgTypeFromLabel;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.pathDepth;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.resolveParentRef;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.applyImportUpdate;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.sameAsExisting;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.sha256Hex;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.statusFromLabel;
import static cn.iocoder.yudao.module.system.service.dept.DeptImportSupport.trimToNull;

/**
 * 组织架构导入：新增或同路径更新 + 校验预览后整批原子提交。
 */
@Service
@Validated
@Slf4j
public class DeptImportServiceImpl implements DeptImportService {

    @Resource
    private DeptService deptService;
    @Resource
    private DeptMutationLock deptMutationLock;
    @Resource
    private PermissionService permissionService;
    @Resource
    private AdminUserService adminUserService;

    /**
     * 测试钩子：每成功 create 一次后回调，参数为本次导入内已创建个数（从 1 起）。
     * 用于故障注入验证整批回滚；生产路径保持 null。
     */
    @VisibleForTesting
    volatile IntConsumer afterEachCreateForTest;

    @Override
    @DataPermission(enable = false)
    public DeptImportRespVO validateImport(MultipartFile file) {
        ParsedFile parsed = parseFile(file);
        assertFullDataScope();
        ValidationResult result = validateRows(parsed.rows(), parsed.digest());
        return toResp(result, parsed.digest(), false);
    }

    @Override
    @DataPermission(enable = false)
    public DeptImportRespVO importDepts(MultipartFile file, String expectedDigest) {
        // 解析/摘要/权限门禁在锁外：无写库；失败无需占锁
        ParsedFile parsed = parseFile(file);
        if (StrUtil.isBlank(expectedDigest) || !parsed.digest().equalsIgnoreCase(expectedDigest.trim())) {
            throw exception(DEPT_IMPORT_FILE_CHANGED);
        }
        assertFullDataScope();
        // F1 闭合：先锁后开事务（DeptMutationLock.execute = lock → TX → work → commit → unlock）
        // 不再使用方法级 @Transactional，避免「方法返回 unlock 后 Spring 才 commit」
        return deptMutationLock.execute(() -> {
            ValidationResult result = validateRows(parsed.rows(), parsed.digest());
            if (!result.errors().isEmpty()) {
                return toResp(result, parsed.digest(), false);
            }
            List<DeptImportSupport.NormalizedRow> toCreate = result.toCreate().stream()
                    .sorted(Comparator.comparingInt(r -> pathDepth(r.orgPath())))
                    .toList();
            for (DeptImportSupport.NormalizedRow row : toCreate) {
                Long parentId = resolveParentId(row.parentPath(), result);
                DeptSaveReqVO createReq = new DeptSaveReqVO();
                createReq.setName(row.name());
                createReq.setParentId(parentId);
                createReq.setSort(row.sort());
                createReq.setStatus(row.status());
                createReq.setOrgType(row.orgType());
                createReq.setFunctionalCurrency(row.functionalCurrency());
                createReq.setLeaderUserId(row.leaderUserId());
                createReq.setPhone(row.phone());
                createReq.setEmail(row.email());
                Long id = deptService.createDept(createReq);
                result.createdPathIds().put(row.orgPath(), id);
                IntConsumer hook = afterEachCreateForTest;
                if (hook != null) {
                    hook.accept(result.createdPathIds().size());
                }
            }
            for (DeptImportSupport.NormalizedRow row : result.toUpdate()) {
                DeptDO existing = result.existingByPath().get(row.orgPath());
                if (existing == null) {
                    continue;
                }
                DeptSaveReqVO updateReq = new DeptSaveReqVO();
                applyImportUpdate(row, existing, updateReq);
                deptService.updateDept(updateReq);
            }
            log.info("[importDepts] tenant import done digest={} total={} create={} update={} skip={}",
                    parsed.digest(), result.totalRows(), toCreate.size(),
                    result.toUpdate().size(), result.skipCount());
            return toResp(result, parsed.digest(), true);
        });
    }

    private Long resolveParentId(String parentPath, ValidationResult result) {
        if (StrUtil.isBlank(parentPath)) {
            return DeptDO.PARENT_ID_ROOT;
        }
        Long created = result.createdPathIds().get(parentPath);
        if (created != null) {
            return created;
        }
        DeptDO existing = result.existingByPath().get(parentPath);
        if (existing != null) {
            return existing.getId();
        }
        // 校验已通过时不应到此
        throw new IllegalStateException("parent path not resolved: " + parentPath);
    }

    private void assertFullDataScope() {
        Long userId = getLoginUserId();
        if (userId == null) {
            throw exception(DEPT_IMPORT_NO_FULL_DATA_SCOPE);
        }
        DeptDataPermissionRespDTO permission = permissionService.getDeptDataPermission(userId);
        if (permission == null || !Boolean.TRUE.equals(permission.getAll())) {
            throw exception(DEPT_IMPORT_NO_FULL_DATA_SCOPE);
        }
    }

    private ParsedFile parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw exception(DEPT_IMPORT_EMPTY);
        }
        String original = StrUtil.blankToDefault(file.getOriginalFilename(), "");
        String lower = original.toLowerCase(Locale.ROOT);
        // 仅 .xlsx（拒绝 .xls / 无扩展名 / 伪装扩展名）
        if (!lower.endsWith(".xlsx")) {
            throw exception(DEPT_IMPORT_FILE_TYPE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw exception(DEPT_IMPORT_FILE_TYPE);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw exception(DEPT_IMPORT_FILE_TOO_LARGE);
        }
        // ZIP/xlsx 魔数：PK
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw exception(DEPT_IMPORT_FILE_TYPE);
        }
        String digest = sha256Hex(bytes);
        List<DeptImportExcelVO> rows;
        try {
            rows = ExcelUtils.read(bytes, DeptImportExcelVO.class, 1);
        } catch (Exception e) {
            log.warn("[parseFile] excel read failed: {}", e.getMessage());
            throw exception(DEPT_IMPORT_FILE_TYPE);
        }
        List<RowWithNumber> numbered = new ArrayList<>();
        int excelRow = 2; // 表头第 1 行，数据从第 2 行
        for (DeptImportExcelVO row : rows) {
            if (!isBlankRow(row)) {
                numbered.add(new RowWithNumber(excelRow, row));
            }
            excelRow++;
        }
        if (numbered.isEmpty()) {
            throw exception(DEPT_IMPORT_EMPTY);
        }
        if (numbered.size() > MAX_ROWS) {
            throw exception(DEPT_IMPORT_ROW_LIMIT);
        }
        return new ParsedFile(digest, numbered);
    }

    private ValidationResult validateRows(List<RowWithNumber> rows, String digest) {
        List<DeptDO> existingDepts = deptService.getDeptList(new DeptListReqVO());
        DeptImportSupport.PathIndex pathIndex = buildPathIndex(existingDepts);
        List<DeptImportErrorRespVO> errors = new ArrayList<>();
        if (CollUtil.isNotEmpty(pathIndex.ambiguousPaths())) {
            for (String amb : pathIndex.ambiguousPaths()) {
                errors.add(error(0, amb, "orgPath", "EXISTING_CONFLICT",
                        "当前租户存在同路径重复或无法解析的组织数据，请先清理后再导入"));
            }
        }
        Map<String, DeptDO> existingByPath = pathIndex.uniquePaths();

        // 预加载负责人
        Set<String> usernames = new HashSet<>();
        for (RowWithNumber r : rows) {
            String u = trimToNull(r.row().getLeaderUsername());
            if (u != null) {
                usernames.add(u);
            }
        }
        Map<String, AdminUserDO> userByUsername = new HashMap<>();
        for (String username : usernames) {
            AdminUserDO user = adminUserService.getUserByUsername(username);
            if (user != null) {
                userByUsername.put(username, user);
            }
        }

        List<DeptImportSupport.NormalizedRow> normalizedAll = new ArrayList<>();

        for (RowWithNumber item : rows) {
            int rowNumber = item.rowNumber();
            DeptImportExcelVO raw = item.row();
            List<DeptImportErrorRespVO> rowErrors = new ArrayList<>();

            String name = trimToNull(raw.getName());
            if (name == null) {
                rowErrors.add(error(rowNumber, null, "name", "NAME_REQUIRED", "组织名称不能为空"));
            } else if (name.length() > 30) {
                rowErrors.add(error(rowNumber, null, "name", "NAME_LENGTH", "组织名称长度不能超过 30 个字符"));
            } else if (name.contains(DeptImportSupport.PATH_SEPARATOR)) {
                rowErrors.add(error(rowNumber, null, "name", "NAME_SEPARATOR", "组织名称不得包含路径分隔符 /"));
            }

            String parentPath = trimToNull(raw.getParentPath());
            if (parentPath != null && parentPath.contains("//")) {
                rowErrors.add(error(rowNumber, null, "parentPath", "PARENT_PATH_INVALID", "上级组织路径格式不正确"));
            }

            String orgType = orgTypeFromLabel(raw.getOrgTypeLabel());
            if (orgType == null) {
                rowErrors.add(error(rowNumber, null, "orgType", "ORG_TYPE_INVALID", "组织类型仅支持 公司/部门"));
            }

            Integer sort = null;
            String sortText = trimToNull(raw.getSortText());
            if (sortText == null) {
                rowErrors.add(error(rowNumber, null, "sort", "SORT_REQUIRED", "显示顺序不能为空"));
            } else {
                try {
                    // EasyExcel 可能把数字读成 "1.0"
                    if (sortText.matches("^-?\\d+\\.0+$")) {
                        sortText = sortText.substring(0, sortText.indexOf('.'));
                    }
                    sort = Integer.parseInt(sortText);
                    if (sort < 0 || sort > 9999) {
                        rowErrors.add(error(rowNumber, null, "sort", "SORT_RANGE", "显示顺序须为 0–9999 整数"));
                    }
                } catch (NumberFormatException ex) {
                    rowErrors.add(error(rowNumber, null, "sort", "SORT_RANGE", "显示顺序须为 0–9999 整数"));
                }
            }

            Integer status = statusFromLabel(raw.getStatusLabel());
            if (status == null) {
                rowErrors.add(error(rowNumber, null, "status", "STATUS_INVALID", "状态仅支持 启用/停用"));
            }

            // 本位币改由财务账户维护，导入列忽略
            String currency = null;

            String phone = trimToNull(raw.getPhone());
            if (phone != null && phone.length() > 11) {
                rowErrors.add(error(rowNumber, null, "phone", "PHONE_LENGTH", "联系电话长度不能超过11个字符"));
            }
            String email = trimToNull(raw.getEmail());
            if (email != null) {
                if (email.length() > 50) {
                    rowErrors.add(error(rowNumber, null, "email", "EMAIL_LENGTH", "邮箱长度不能超过 50 个字符"));
                } else if (!isValidEmail(email)) {
                    rowErrors.add(error(rowNumber, null, "email", "EMAIL_FORMAT", "邮箱格式不正确"));
                }
            }

            String leaderUsername = trimToNull(raw.getLeaderUsername());
            Long leaderUserId = null;
            if (leaderUsername != null) {
                AdminUserDO user = userByUsername.get(leaderUsername);
                if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
                    rowErrors.add(error(rowNumber, null, "leaderUsername", "LEADER_NOT_FOUND",
                            "负责人登录账号不存在或未启用"));
                } else {
                    leaderUserId = user.getId();
                }
            }

            String orgPath = name == null ? null : buildPath(parentPath, name);
            if (orgPath != null && pathDepth(orgPath) > MAX_DEPTH) {
                rowErrors.add(error(rowNumber, orgPath, "orgPath", "DEPTH_LIMIT",
                        "组织树深度不能超过 " + MAX_DEPTH + " 层"));
            }

            if (!rowErrors.isEmpty()) {
                // 回填 orgPath
                for (DeptImportErrorRespVO e : rowErrors) {
                    if (e.getOrgPath() == null) {
                        e.setOrgPath(orgPath);
                    }
                }
                errors.addAll(rowErrors);
                continue;
            }

            DeptImportSupport.NormalizedRow normalized = new DeptImportSupport.NormalizedRow(
                    rowNumber, name, parentPath, orgPath, orgType, sort, status,
                    currency, leaderUsername, leaderUserId, phone, email);
            normalizedAll.add(normalized);
        }

        Map<String, String> lastFullPathByName = new LinkedHashMap<>();
        for (Map.Entry<String, DeptDO> e : existingByPath.entrySet()) {
            lastFullPathByName.put(lastPathSegment(e.getKey()), e.getKey());
        }
        Map<String, DeptImportSupport.NormalizedRow> fileByFullPath = new LinkedHashMap<>();
        Map<Integer, DeptImportSupport.NormalizedRow> resolvedByRow = new LinkedHashMap<>();
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (DeptImportSupport.NormalizedRow row : normalizedAll) {
                if (resolvedByRow.containsKey(row.rowNumber())) {
                    continue;
                }
                String parentFull = resolveParentRef(row.parentPath(), fileByFullPath,
                        existingByPath, lastFullPathByName);
                if (parentFull == null) {
                    continue;
                }
                String fullPath = buildPath(StrUtil.isBlank(parentFull) ? null : parentFull, row.name());
                if (pathDepth(fullPath) > MAX_DEPTH) {
                    errors.add(error(row.rowNumber(), fullPath, "orgPath", "DEPTH_LIMIT",
                            "组织树深度不能超过 " + MAX_DEPTH + " 层"));
                    resolvedByRow.put(row.rowNumber(), row);
                    progressed = true;
                    continue;
                }
                if (fileByFullPath.containsKey(fullPath)) {
                    errors.add(error(row.rowNumber(), fullPath, "orgPath", "PATH_DUPLICATE",
                            "文件内组织路径重复（与第 " + fileByFullPath.get(fullPath).rowNumber() + " 行）"));
                    resolvedByRow.put(row.rowNumber(), row);
                    progressed = true;
                    continue;
                }
                String canonicalParent = StrUtil.isBlank(parentFull) ? null : parentFull;
                DeptImportSupport.NormalizedRow resolved = row.withPaths(canonicalParent, fullPath);
                fileByFullPath.put(fullPath, resolved);
                lastFullPathByName.put(row.name(), fullPath);
                resolvedByRow.put(row.rowNumber(), resolved);
                progressed = true;
            }
        }
        for (DeptImportSupport.NormalizedRow row : normalizedAll) {
            if (!resolvedByRow.containsKey(row.rowNumber())) {
                errors.add(error(row.rowNumber(), row.orgPath(), "parentPath", "PARENT_NOT_FOUND",
                        "上级组织路径不存在：" + row.parentPath()));
            }
        }

        int createCount = 0;
        int skipCount = 0;
        int updateCount = 0;
        List<DeptImportSupport.NormalizedRow> toCreate = new ArrayList<>();
        List<DeptImportSupport.NormalizedRow> toUpdate = new ArrayList<>();
        for (DeptImportSupport.NormalizedRow row : resolvedByRow.values()) {
            if (row.orgPath() == null || errors.stream().anyMatch(e -> e.getRowNumber() == row.rowNumber())) {
                continue;
            }
            String parentOrgType = null;
            if (StrUtil.isNotBlank(row.parentPath())) {
                if (fileByFullPath.containsKey(row.parentPath())) {
                    parentOrgType = fileByFullPath.get(row.parentPath()).orgType();
                } else if (existingByPath.containsKey(row.parentPath())) {
                    parentOrgType = String.valueOf(existingByPath.get(row.parentPath()).getOrgType());
                }
            }
            if (OrgTypeEnum.COMPANY.getValue().equals(row.orgType())
                    && StrUtil.isNotBlank(row.parentPath())
                    && OrgTypeEnum.DEPARTMENT.getValue().equals(String.valueOf(parentOrgType))) {
                errors.add(error(row.rowNumber(), row.orgPath(), "orgType", "PARENT_TYPE_INVALID",
                        "公司不能挂在部门下"));
                continue;
            }
            DeptDO existing = existingByPath.get(row.orgPath());
            if (existing != null) {
                if (sameAsExisting(row, existing)) {
                    skipCount++;
                } else {
                    updateCount++;
                    toUpdate.add(row);
                }
            } else {
                createCount++;
                toCreate.add(row);
            }
        }

        // 二次：文件内新建节点的父若也在文件中且为 create，深度拓扑已由排序保证；
        // 若父在文件中是 skip（已存在），parent 可解析。无需额外检查。

        ValidationResult result = new ValidationResult(
                rows.size(), createCount, skipCount, updateCount, errors, toCreate, toUpdate,
                existingByPath, new HashMap<>());
        // 文件内已存在路径供 resolve：skip 的也在 existingByPath
        // create 的 id 写入 createdPathIds 在提交阶段
        // 供提交时父在文件内新建：先把 skip 的也放进 createdPathIds? 不需要，existing 有。
        return result;
    }

    private DeptImportRespVO toResp(ValidationResult result, String digest, boolean committed) {
        boolean canCommit = result.errors().isEmpty();
        return DeptImportRespVO.builder()
                .fileDigest(digest)
                .totalRows(result.totalRows())
                .createCount(result.createCount())
                .skipCount(result.skipCount())
                .updateCount(result.updateCount())
                .canCommit(canCommit)
                .errors(result.errors())
                .build();
    }

    private record ParsedFile(String digest, List<RowWithNumber> rows) {
    }

    private record RowWithNumber(int rowNumber, DeptImportExcelVO row) {
    }

    private record ValidationResult(
            int totalRows,
            int createCount,
            int skipCount,
            int updateCount,
            List<DeptImportErrorRespVO> errors,
            List<DeptImportSupport.NormalizedRow> toCreate,
            List<DeptImportSupport.NormalizedRow> toUpdate,
            Map<String, DeptDO> existingByPath,
            Map<String, Long> createdPathIds
    ) {
    }

}
