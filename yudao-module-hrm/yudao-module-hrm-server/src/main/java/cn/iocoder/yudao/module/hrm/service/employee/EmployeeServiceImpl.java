package cn.iocoder.yudao.module.hrm.service.employee;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCreateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserUpdateReqDTO;
import cn.iocoder.yudao.module.system.enums.DictTypeConstants;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.*;

/**
 * 员工档案 Service 实现类
 *
 * @author 宇擎源码
 */
@Service
@Validated
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    public static final String ONBOARDING_ATTACHMENT_BUSINESS_TYPE = "hrm_employee_archive_onboarding";

    /** 私有存储目录（与 FilePrivateDirs / 公开下载拦截前缀一致） */
    public static final String ONBOARDING_PRIVATE_DIR =
            cn.iocoder.yudao.module.infra.api.file.FilePrivateDirs.HRM_ONBOARDING_PRIVATE;

    /** claim 有效期 */
    public static final int CLAIM_TTL_MINUTES = 120;

    /** 入职资料专用边界（不作用于其他业务附件） */
    public static final int ONBOARDING_MAX_COUNT = 10;
    public static final long ONBOARDING_MAX_SIZE_BYTES = 20L * 1024 * 1024;
    public static final Set<String> ONBOARDING_ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "rar", "7z");

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 字典类型（导出标签） */
    private static final String DICT_NATION = "hrm_nation";
    private static final String DICT_MARITAL = "hrm_marital_status";
    private static final String DICT_FERTILITY = "hrm_fertility_status";
    private static final String DICT_HOUSEHOLD = "hrm_household_type";
    private static final String DICT_EDUCATION = "hrm_education";
    private static final String DICT_EDUCATION_TYPE = "hrm_education_type";
    private static final String DICT_EMPLOYEE_STATUS = "hrm_employee_status";
    private static final String DICT_EMPLOYMENT_FORM = "hrm_employment_form";
    private static final String DICT_CONTRACT_TYPE = "hrm_contract_type";

    @Resource
    private EmployeeMapper employeeArchiveMapper;

    @Resource
    private EmployeeWorkExperienceMapper employeeWorkExperienceMapper;

    @Resource
    private EmployeeEducationMapper employeeEducationMapper;

    @Resource
    private EmployeeFamilyMapper employeeFamilyMapper;

    @Resource
    private EmployeeContractMapper employeeContractMapper;

    @Resource
    private EmployeeEmploymentMapper employeeEmploymentMapper;

    @Resource
    private AttachmentService attachmentService;

    @Resource
    private FileAccessApi fileAccessApi;

    @Resource
    private OnboardingFileClaimMapper onboardingFileClaimMapper;

    @Resource
    private DeptApi deptApi;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private ConfigApi configApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEmployeeArchive(EmployeeSaveReqVO createReqVO) {
        applySocialSecurityRules(createReqVO, null);
        validateRoster(createReqVO, null);

        // 自动生成员工工号（如果未提供）
        if (createReqVO.getEmployeeNo() == null || createReqVO.getEmployeeNo().trim().isEmpty()) {
            Long maxEmployeeNo = employeeArchiveMapper.selectMaxEmployeeNo();
            Long nextEmployeeNo = maxEmployeeNo + 1;
            createReqVO.setEmployeeNo(String.format("%08d", nextEmployeeNo));
        }

        // 插入主表
        EmployeeDO archive = BeanUtils.toBean(createReqVO, EmployeeDO.class);
        applyEducationSummary(archive, createReqVO.getEducationList());
        employeeArchiveMapper.insert(archive);

        // 插入关联明细（create 时 null 与 empty 均表示无明细）
        saveWorkExperiences(archive.getId(), createReqVO.getWorkExperienceList());
        saveEducations(archive.getId(), createReqVO.getEducationList());
        saveFamilies(archive.getId(), createReqVO.getFamilyList());
        saveContracts(archive.getId(), createReqVO.getContractList());
        saveEmployments(archive.getId(), createReqVO.getEmploymentList(), archive);

        if (createReqVO.getOnboardingAttachments() != null) {
            saveOnboardingAttachments(archive.getId(), createReqVO.getOnboardingAttachments());
        }

        return archive.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmployeeArchive(EmployeeSaveReqVO updateReqVO) {
        // 校验存在
        EmployeeDO oldEmployee = employeeArchiveMapper.selectById(updateReqVO.getId());
        if (oldEmployee == null) {
            throw exception(EMPLOYEE_ARCHIVE_NOT_EXISTS);
        }

        // #7：先按 present 合并有效状态，再校验并规范化社保
        applySocialSecurityRules(updateReqVO, oldEmployee);
        validateRoster(updateReqVO, oldEmployee);

        // 主表：先拷贝全量，再对「未出现」的 11 个花名册字段回填旧值（省略=保留）
        EmployeeDO updateObj = BeanUtils.toBean(updateReqVO, EmployeeDO.class);
        restoreOmittedRosterFields(updateObj, updateReqVO, oldEmployee);
        // 仅回写本次 present 的社保字段；apply 在有效 enabled=false 时会强制 set month=null（present）
        if (updateReqVO.isSocialSecurityEnabledPresent()) {
            updateObj.setSocialSecurityEnabled(updateReqVO.getSocialSecurityEnabled());
        }
        if (updateReqVO.isSocialSecurityStartMonthPresent()) {
            updateObj.setSocialSecurityStartMonth(updateReqVO.getSocialSecurityStartMonth());
        }
        applyEducationSummary(updateObj, updateReqVO.getEducationList());
        employeeArchiveMapper.updateById(updateObj);

        // 集合契约：null/未提供 = 保留；非 null（含空数组）= 整表替换
        if (updateReqVO.getWorkExperienceList() != null) {
            employeeWorkExperienceMapper.deleteByEmployeeId(updateReqVO.getId());
            saveWorkExperiences(updateReqVO.getId(), updateReqVO.getWorkExperienceList());
        }
        if (updateReqVO.getEducationList() != null) {
            employeeEducationMapper.deleteByEmployeeId(updateReqVO.getId());
            saveEducations(updateReqVO.getId(), updateReqVO.getEducationList());
        }
        if (updateReqVO.getFamilyList() != null) {
            employeeFamilyMapper.deleteByEmployeeId(updateReqVO.getId());
            saveFamilies(updateReqVO.getId(), updateReqVO.getFamilyList());
        }
        if (updateReqVO.getContractList() != null) {
            employeeContractMapper.deleteByEmployeeId(updateReqVO.getId());
            saveContracts(updateReqVO.getId(), updateReqVO.getContractList());
        }
        if (updateReqVO.getEmploymentList() != null) {
            employeeEmploymentMapper.deleteByEmployeeId(updateReqVO.getId());
            saveEmployments(updateReqVO.getId(), updateReqVO.getEmploymentList(), updateObj);
            updateReqVO.setDeptId(updateObj.getDeptId());
            updateReqVO.setCompanyId(updateObj.getCompanyId());
            updateReqVO.setCompanyName(updateObj.getCompanyName());
        }
        if (updateReqVO.getOnboardingAttachments() != null) {
            saveOnboardingAttachments(updateReqVO.getId(), updateReqVO.getOnboardingAttachments());
        }

        // 如果已生成用户，同步更新用户信息
        if (oldEmployee.getUserGenerated() != null && oldEmployee.getUserGenerated() && oldEmployee.getUserId() != null) {
            syncEmployeeToUser(updateReqVO, oldEmployee.getUserId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmployeeArchive(Long id) {
        // 校验存在
        EmployeeDO employee = employeeArchiveMapper.selectById(id);
        if (employee == null) {
            throw exception(EMPLOYEE_ARCHIVE_NOT_EXISTS);
        }

        // 如果已生成用户，则删除关联的用户
        if (employee.getUserGenerated() != null && employee.getUserGenerated() && employee.getUserId() != null) {
            adminUserApi.deleteUser(employee.getUserId());
        }

        // 删除主表
        employeeArchiveMapper.deleteById(id);

        // 删除关联记录
        employeeWorkExperienceMapper.deleteByEmployeeId(id);
        employeeEducationMapper.deleteByEmployeeId(id);
        employeeFamilyMapper.deleteByEmployeeId(id);
        employeeContractMapper.deleteByEmployeeId(id);
        employeeEmploymentMapper.deleteByEmployeeId(id);
        attachmentService.deleteAttachmentByBusiness(ONBOARDING_ATTACHMENT_BUSINESS_TYPE, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmployeeArchiveList(List<Long> ids) {
        // 校验存在
        validateEmployeeArchiveExists(ids);

        // 查询所有员工信息，收集需要删除的用户ID
        List<EmployeeDO> employees = employeeArchiveMapper.selectList(EmployeeDO::getId, ids);
        List<Long> userIdsToDelete = employees.stream()
                .filter(emp -> emp.getUserGenerated() != null && emp.getUserGenerated() && emp.getUserId() != null)
                .map(EmployeeDO::getUserId)
                .collect(Collectors.toList());

        // 如果已生成用户，则批量删除关联的用户
        if (!userIdsToDelete.isEmpty()) {
            adminUserApi.deleteUserList(userIdsToDelete);
        }

        // 删除主表
        employeeArchiveMapper.deleteBatchIds(ids);

        // 删除关联记录
        for (Long id : ids) {
            employeeWorkExperienceMapper.deleteByEmployeeId(id);
            employeeEducationMapper.deleteByEmployeeId(id);
            employeeFamilyMapper.deleteByEmployeeId(id);
            employeeContractMapper.deleteByEmployeeId(id);
            employeeEmploymentMapper.deleteByEmployeeId(id);
        }
        attachmentService.deleteAttachmentByBusinessIds(ONBOARDING_ATTACHMENT_BUSINESS_TYPE, ids);
    }

    private void validateEmployeeArchiveExists(Long id) {
        if (employeeArchiveMapper.selectById(id) == null) {
            throw exception(EMPLOYEE_ARCHIVE_NOT_EXISTS);
        }
    }

    private void validateEmployeeArchiveExists(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        List<EmployeeDO> list = employeeArchiveMapper.selectBatchIds(ids);
        if (CollUtil.isEmpty(list) || list.size() != ids.size()) {
            throw exception(EMPLOYEE_ARCHIVE_NOT_EXISTS);
        }
    }

    @Override
    public EmployeeRespVO getEmployeeArchive(Long id) {
        EmployeeDO archive = employeeArchiveMapper.selectById(id);
        if (archive == null) {
            return null;
        }

        EmployeeRespVO respVO = BeanUtils.toBean(archive, EmployeeRespVO.class);

        // 部门/公司展示名补全：编辑页依赖 deptId + deptName；导入写库后打开修改须可渲染
        if (archive.getDeptId() != null) {
            CommonResult<DeptRespDTO> dept = deptApi.getDept(archive.getDeptId());
            if (dept != null && dept.isSuccess() && dept.getData() != null) {
                if (StrUtil.isBlank(respVO.getDeptName())) {
                    respVO.setDeptName(dept.getData().getName());
                }
            }
            if (archive.getCompanyId() == null) {
                Long companyId = findCompanyIdByDeptId(archive.getDeptId());
                if (companyId != null) {
                    respVO.setCompanyId(companyId);
                }
            }
        }
        if (respVO.getCompanyId() != null && StrUtil.isBlank(respVO.getCompanyName())) {
            CommonResult<DeptRespDTO> company = deptApi.getDept(respVO.getCompanyId());
            if (company != null && company.isSuccess() && company.getData() != null) {
                respVO.setCompanyName(company.getData().getName());
            }
        }

        // 获取工作经历
        List<EmployeeWorkExperienceDO> workExperiences = employeeWorkExperienceMapper.selectListByEmployeeId(id);
        respVO.setWorkExperienceList(BeanUtils.toBean(workExperiences, EmployeeWorkExperienceVO.class));

        // 获取教育经历
        List<EmployeeEducationDO> educations = employeeEducationMapper.selectListByEmployeeId(id);
        respVO.setEducationList(BeanUtils.toBean(educations, EmployeeEducationVO.class));

        // 获取家属信息
        List<EmployeeFamilyDO> families = employeeFamilyMapper.selectListByEmployeeId(id);
        respVO.setFamilyList(BeanUtils.toBean(families, EmployeeFamilyVO.class));

        // 合同明细
        List<EmployeeContractDO> contracts = employeeContractMapper.selectListByEmployeeId(id);
        respVO.setContractList(BeanUtils.toBean(contracts, EmployeeContractVO.class));
        fillCurrentContract(respVO, contracts);
        respVO.setEmploymentList(buildEmploymentList(archive));

        // 入职资料（内部列表；不暴露公开 URL；下载走鉴权接口）
        List<AttachmentDO> attachments = attachmentService.getAttachmentListByBusinessInternal(
                ONBOARDING_ATTACHMENT_BUSINESS_TYPE, id);
        respVO.setOnboardingAttachments(toOnboardingRespList(id, attachments));

        fillDerivedFields(respVO, LocalDate.now());
        return respVO;
    }

    @Override
    public PageResult<EmployeeRespVO> getEmployeeArchivePage(EmployeePageReqVO pageReqVO) {
        expandOrgFilterIfCompany(pageReqVO);
        PageResult<EmployeeDO> pageResult = employeeArchiveMapper.selectPage(pageReqVO);
        return buildEmployeeRespPage(pageResult);
    }

    @Override
    public PageResult<EmployeeRespVO> getEmployeeArchiveSelectablePage(EmployeeSelectPageReqVO pageReqVO) {
        PageResult<EmployeeDO> pageResult = employeeArchiveMapper.selectPageExcludeFormal(pageReqVO);
        return buildEmployeeRespPage(pageResult);
    }

    /**
     * 花名册导入：逐行 upsert，外层不加事务，避免单行失败拖垮整批。
     * 行号按「表头在第 2 行」约定：数据行 Excel 行号 = index + 3。
     * <p>
     * P1-A：员工类型解析值不可变；仅真正 create 成功路径默认正式；UK 冲突回退 update 前恢复原始 null。<br>
     * F2：日志与失败明细中的身份证脱敏。<br>
     * P1-B：依赖 active-only 唯一键 uk_hrm_employee_active_id_card（生成列 active_id_card）。
     */
    @Override
    public EmployeeRosterImportRespVO importEmployeeRosterList(List<EmployeeRosterImportExcelVO> rows) {
        if (CollUtil.isEmpty(rows)) {
            throw new IllegalArgumentException("导入数据不能为空");
        }
        EmployeeRosterImportRespVO resp = EmployeeRosterImportRespVO.builder()
                .createNames(new ArrayList<>())
                .updateNames(new ArrayList<>())
                .failureRows(new LinkedHashMap<>())
                .build();
        Set<String> seenIdCards = new HashSet<>();
        EmployeeServiceImpl self = getSelf();
        // 整批一次拉启用部门，按名称绑定 deptId（禁止静默空绑）
        Map<String, List<DeptRespDTO>> deptsByName = loadEnabledDeptsByName();
        for (int i = 0; i < rows.size(); i++) {
            // 附件结构：第1行标题、第2行表头、第3行起数据
            int excelRowNumber = i + 3;
            EmployeeRosterImportExcelVO row = rows.get(i);
            String idCardForMask = null;
            try {
                EmployeeSaveReqVO req = EmployeeRosterImportSupport.toSaveReq(row);
                String idCard = req.getIdCard();
                idCardForMask = idCard;
                // 部门：模板有「部门」列 → 必须解析为当前租户启用部门的 deptId
                bindDeptForImport(req, deptsByName);
                List<EmployeeEmploymentVO> extras = bindExtraEmploymentsForImport(row, deptsByName, req.getCompanyId());
                // P1-A：解析后的员工类型快照不可变（空白保持 null）
                final Integer parsedEmployeeStatus = req.getEmployeeStatus();
                if (!seenIdCards.add(idCard)) {
                    String masked = EmployeeRosterImportSupport.maskIdCard(idCard);
                    String failReason = "文件内身份证号重复：" + masked;
                    log.warn("[importEmployeeRosterList][row={} fail: {}]", excelRowNumber, failReason);
                    resp.getFailureRows().put(excelRowNumber, failReason);
                    continue;
                }
                EmployeeDO existing = employeeArchiveMapper.selectByIdCard(idCard);
                if (existing == null) {
                    // 仅 create 路径：空白 → 正式；失败回退时必须恢复 parsedEmployeeStatus
                    req.setEmployeeStatus(EmployeeRosterImportSupport.defaultEmployeeStatusForCreate(
                            parsedEmployeeStatus));
                    try {
                        if (CollUtil.isNotEmpty(extras)) {
                            req.setEmploymentList(buildImportEmploymentList(req, extras));
                        }
                        self.createEmployeeArchive(req);
                        resp.getCreateNames().add(req.getName());
                    } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                        // active-only UK 或工号 UK 冲突 → 重查后 update
                        existing = employeeArchiveMapper.selectByIdCard(idCard);
                        if (existing == null) {
                            throw dup;
                        }
                        // P1-A：恢复原始解析值，使 applyImportUpdate 在空白时保留获胜行状态
                        req.setEmployeeStatus(parsedEmployeeStatus);
                        applyImportUpdate(self, req, existing, extras);
                        resp.getUpdateNames().add(req.getName());
                        log.info("[importEmployeeRosterList][row={} idCard={} concurrent create conflict, switched to update id={}]",
                                excelRowNumber, EmployeeRosterImportSupport.maskIdCard(idCard), existing.getId());
                    }
                } else {
                    // 直接 update：req 仍为原始 parsedEmployeeStatus（空白=null → 保留旧值）
                    applyImportUpdate(self, req, existing, extras);
                    resp.getUpdateNames().add(req.getName());
                }
            } catch (Exception ex) {
                String safeReason = EmployeeRosterImportSupport.humanizeImportFailure(ex, idCardForMask);
                log.warn("[importEmployeeRosterList][row={} fail: {}]", excelRowNumber, safeReason, ex);
                resp.getFailureRows().put(excelRowNumber, safeReason);
            }
        }
        return resp;
    }

    /**
     * 加载当前租户启用部门，按名称分组（同名多条时导入 fail-closed）。
     */
    Map<String, List<DeptRespDTO>> loadEnabledDeptsByName() {
        CommonResult<List<DeptRespDTO>> result = deptApi.getSimpleDeptList();
        List<DeptRespDTO> list = result != null && result.isSuccess() ? result.getData() : null;
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyMap();
        }
        Map<String, List<DeptRespDTO>> byName = new HashMap<>();
        for (DeptRespDTO d : list) {
            if (d == null || d.getId() == null || StrUtil.isBlank(d.getName())) {
                continue;
            }
            byName.computeIfAbsent(d.getName().trim(), k -> new ArrayList<>(2)).add(d);
        }
        return byName;
    }

    /**
     * 花名册导入：部门名 → 有效 deptId（及公司）。缺失/不存在/重名/禁用均行级失败。
     */
    void bindDeptForImport(EmployeeSaveReqVO req, Map<String, List<DeptRespDTO>> deptsByName) {
        String rawName = req.getDeptName();
        if (StrUtil.isBlank(rawName)) {
            throw new IllegalArgumentException("部门不能为空，请填写系统中已有的部门名称");
        }
        String name = rawName.trim();
        List<DeptRespDTO> matches = deptsByName != null ? deptsByName.get(name) : null;
        if (CollUtil.isEmpty(matches)) {
            throw new IllegalArgumentException(
                    "部门不存在或未启用：" + name + "（须与组织架构中的部门名称完全一致）");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "部门名称在系统中存在多个匹配，无法唯一绑定：" + name + "，请联系管理员处理重名部门");
        }
        DeptRespDTO dept = matches.get(0);
        req.setDeptId(dept.getId());
        req.setDeptName(dept.getName());
        Long companyId = findCompanyIdByDeptId(dept.getId());
        if (companyId != null) {
            req.setCompanyId(companyId);
            // 未填单位名称时用公司节点名称补全，便于编辑页展示
            if (StrUtil.isBlank(req.getCompanyName())) {
                CommonResult<DeptRespDTO> company = deptApi.getDept(companyId);
                if (company != null && company.isSuccess() && company.getData() != null
                        && StrUtil.isNotBlank(company.getData().getName())) {
                    req.setCompanyName(company.getData().getName());
                }
            }
        }
    }

    /** 导入 update：空白员工类型回填旧值，避免误改为正式（P1-A） */
    private void applyImportUpdate(EmployeeServiceImpl self, EmployeeSaveReqVO req, EmployeeDO existing,
                                  List<EmployeeEmploymentVO> extras) {
        req.setId(existing.getId());
        req.setEmployeeNo(existing.getEmployeeNo());
        if (req.getEmployeeStatus() == null) {
            req.setEmployeeStatus(existing.getEmployeeStatus());
        }
        self.updateEmployeeArchive(req);
        if (req.getCompanyId() != null && req.getDeptId() != null) {
            self.applySigningEmployment(existing.getId(), req.getCompanyId(), req.getDeptId(),
                    req.getCompanyName(), req.getDeptName());
        }
        upsertExtraEmployments(existing.getId(), extras);
    }

    private List<EmployeeEmploymentVO> buildImportEmploymentList(EmployeeSaveReqVO req,
                                                                List<EmployeeEmploymentVO> extras) {
        List<EmployeeEmploymentVO> list = new ArrayList<>();
        EmployeeEmploymentVO signed = new EmployeeEmploymentVO();
        signed.setCompanyDeptId(req.getCompanyId());
        signed.setCompanyName(req.getCompanyName());
        signed.setDeptId(req.getDeptId());
        signed.setDeptName(req.getDeptName());
        signed.setSigned(true);
        list.add(signed);
        if (CollUtil.isNotEmpty(extras)) {
            list.addAll(extras);
        }
        return list;
    }

    List<EmployeeEmploymentVO> bindExtraEmploymentsForImport(EmployeeRosterImportExcelVO row,
                                                            Map<String, List<DeptRespDTO>> deptsByName,
                                                            Long signedCompanyId) {
        List<String> companies = splitImportNames(row.getExtraCompanyNames());
        List<String> depts = splitImportNames(row.getExtraDeptNames());
        if (companies.isEmpty() && depts.isEmpty()) {
            return List.of();
        }
        if (companies.size() != depts.size()) {
            throw new IllegalArgumentException("任职单位与任职部门数量须一致（用逗号分隔，按顺序成对）");
        }
        List<EmployeeEmploymentVO> extras = new ArrayList<>();
        for (int i = 0; i < companies.size(); i++) {
            addExtraEmployment(extras, companies.get(i), depts.get(i),
                    deptsByName, signedCompanyId, "任职");
        }
        Set<Long> seen = new HashSet<>();
        if (signedCompanyId != null) {
            seen.add(signedCompanyId);
        }
        for (EmployeeEmploymentVO extra : extras) {
            if (!seen.add(extra.getCompanyDeptId())) {
                throw new IllegalArgumentException("其他任职公司不能与签约公司或其它任职重复");
            }
        }
        return extras;
    }

    private List<String> splitImportNames(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : raw.split("[,，]")) {
            String trimmed = StrUtil.trim(part);
            if (StrUtil.isNotBlank(trimmed)) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private void addExtraEmployment(List<EmployeeEmploymentVO> extras, String companyName, String deptName,
                                    Map<String, List<DeptRespDTO>> deptsByName, Long signedCompanyId, String label) {
        String company = StrUtil.trim(companyName);
        String dept = StrUtil.trim(deptName);
        if (StrUtil.isBlank(company) && StrUtil.isBlank(dept)) {
            return;
        }
        if (StrUtil.isBlank(company) || StrUtil.isBlank(dept)) {
            throw new IllegalArgumentException(label + "单位与部门须成对填写");
        }
        List<DeptRespDTO> matches = deptsByName != null ? deptsByName.get(dept) : null;
        if (CollUtil.isEmpty(matches)) {
            throw new IllegalArgumentException(label + "部门不存在或未启用：" + dept);
        }
        DeptRespDTO picked = null;
        for (DeptRespDTO candidate : matches) {
            Long companyId = findCompanyIdByDeptId(candidate.getId());
            if (companyId == null) {
                continue;
            }
            CommonResult<DeptRespDTO> companyNode = deptApi.getDept(companyId);
            String nodeName = companyNode != null && companyNode.isSuccess() && companyNode.getData() != null
                    ? companyNode.getData().getName() : null;
            if (company.equals(nodeName)) {
                if (picked != null) {
                    throw new IllegalArgumentException(label + "部门名称在该单位下无法唯一绑定：" + dept);
                }
                picked = candidate;
                extras.add(toUnsignedEmployment(companyId, nodeName, candidate));
            }
        }
        if (picked == null) {
            throw new IllegalArgumentException(label + "部门不属于单位：" + company + " / " + dept);
        }
        if (signedCompanyId != null && signedCompanyId.equals(findCompanyIdByDeptId(picked.getId()))) {
            throw new IllegalArgumentException(label + "不能与签约单位相同");
        }
    }

    private EmployeeEmploymentVO toUnsignedEmployment(Long companyId, String companyName, DeptRespDTO dept) {
        EmployeeEmploymentVO vo = new EmployeeEmploymentVO();
        vo.setCompanyDeptId(companyId);
        vo.setCompanyName(companyName);
        vo.setDeptId(dept.getId());
        vo.setDeptName(dept.getName());
        vo.setSigned(false);
        return vo;
    }

    void upsertExtraEmployments(Long employeeId, List<EmployeeEmploymentVO> extras) {
        if (employeeId == null || CollUtil.isEmpty(extras)) {
            return;
        }
        List<EmployeeEmploymentDO> rows = employeeEmploymentMapper.selectListByEmployeeId(employeeId);
        for (EmployeeEmploymentVO extra : extras) {
            Long companyOfDept = findCompanyIdByDeptId(extra.getDeptId());
            if (!extra.getCompanyDeptId().equals(companyOfDept)) {
                throw exception(EMPLOYEE_EMPLOYMENT_DEPT_NOT_UNDER_COMPANY);
            }
            EmployeeEmploymentDO existing = rows.stream()
                    .filter(row -> extra.getCompanyDeptId().equals(row.getCompanyDeptId()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                employeeEmploymentMapper.insert(EmployeeEmploymentDO.builder()
                        .employeeId(employeeId)
                        .companyDeptId(extra.getCompanyDeptId())
                        .deptId(extra.getDeptId())
                        .signed(false)
                        .build());
            } else if (!Boolean.TRUE.equals(existing.getSigned())) {
                existing.setDeptId(extra.getDeptId());
                employeeEmploymentMapper.updateById(existing);
            }
        }
    }

    private EmployeeServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

    /**
     * 列表/导出：若筛选节点是公司（orgType=1），展开为「companyId 匹配 或 deptId 落在公司子树」。
     * 普通部门保持精确 deptId 匹配（原语义）。
     */
    void expandOrgFilterIfCompany(EmployeePageReqVO req) {
        if (req == null || req.getDeptId() == null) {
            return;
        }
        // 已由调用方预展开则跳过
        if (CollUtil.isNotEmpty(req.getDeptIds()) || req.getCompanyId() != null) {
            return;
        }
        CommonResult<DeptRespDTO> result = deptApi.getDept(req.getDeptId());
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return;
        }
        DeptRespDTO node = result.getData();
        // 非公司：保持 eq(deptId)
        if (!"1".equals(node.getOrgType())) {
            return;
        }
        Long companyId = req.getDeptId();
        LinkedHashSet<Long> deptIds = new LinkedHashSet<>();
        deptIds.add(companyId);
        CommonResult<List<DeptRespDTO>> children = deptApi.getChildDeptList(companyId);
        if (children != null && children.isSuccess() && CollUtil.isNotEmpty(children.getData())) {
            for (DeptRespDTO d : children.getData()) {
                if (d != null && d.getId() != null) {
                    deptIds.add(d.getId());
                }
            }
        }
        req.setCompanyId(companyId);
        req.setDeptIds(deptIds);
        // 清空精确 deptId，改由 deptIds/companyId 条件生效
        req.setDeptId(null);
    }

    @Override
    public List<EmployeeRosterExportVO> getEmployeeRosterExportList(EmployeePageReqVO pageReqVO) {
        expandOrgFilterIfCompany(pageReqVO);
        pageReqVO.setPageSize(cn.iocoder.yudao.framework.common.pojo.PageParam.PAGE_SIZE_NONE);
        List<EmployeeDO> employees = employeeArchiveMapper.selectPage(pageReqVO).getList();
        if (CollUtil.isEmpty(employees)) {
            return Collections.emptyList();
        }

        List<Long> employeeIds = employees.stream().map(EmployeeDO::getId).collect(Collectors.toList());

        Map<Long, List<EmployeeEducationDO>> educationMap = employeeEducationMapper.selectListByEmployeeIds(employeeIds)
                .stream().collect(Collectors.groupingBy(EmployeeEducationDO::getEmployeeId));
        Map<Long, List<EmployeeContractDO>> contractMap = employeeContractMapper.selectListByEmployeeIds(employeeIds)
                .stream().collect(Collectors.groupingBy(EmployeeContractDO::getEmployeeId));
        Map<Long, List<AttachmentDO>> attachmentMap = attachmentService
                .getAttachmentListByBusinessIds(ONBOARDING_ATTACHMENT_BUSINESS_TYPE, employeeIds)
                .stream().collect(Collectors.groupingBy(AttachmentDO::getBusinessId));

        LocalDate today = LocalDate.now();
        List<EmployeeRosterExportVO> result = new ArrayList<>(employees.size());
        int sequence = 1;
        for (EmployeeDO employee : employees) {
            List<EmployeeEducationDO> educations = educationMap.getOrDefault(employee.getId(), Collections.emptyList());
            List<EmployeeContractDO> contracts = contractMap.getOrDefault(employee.getId(), Collections.emptyList());
            List<AttachmentDO> attachments = attachmentMap.getOrDefault(employee.getId(), Collections.emptyList());
            result.add(buildRosterExportRow(sequence++, employee, educations, contracts, attachments, today));
        }
        return result;
    }

    /**
     * #7：先合并有效社保状态，再规范化并写回 VO（以便落库）。
     * <ul>
     *   <li>有效 enabled=false ⇒ month 强制 NULL（set 以标记 present 写库）</li>
     *   <li>有效 enabled=true  ⇒ month 必填（由 validateRoster 校验；省略 month 时用 old）</li>
     * </ul>
     */
    void applySocialSecurityRules(EmployeeSaveReqVO req, EmployeeDO old) {
        Boolean enabled = effectiveSocialSecurityEnabled(req, old);
        if (Boolean.FALSE.equals(enabled)) {
            // 有效未参保：无论请求是否带 month，强制清空并写库
            req.setSocialSecurityStartMonth(null);
        }
    }

    Boolean effectiveSocialSecurityEnabled(EmployeeSaveReqVO req, EmployeeDO old) {
        if (req.isSocialSecurityEnabledPresent()) {
            return req.getSocialSecurityEnabled();
        }
        return old != null ? old.getSocialSecurityEnabled() : null;
    }

    String effectiveSocialSecurityStartMonth(EmployeeSaveReqVO req, EmployeeDO old) {
        if (req.isSocialSecurityStartMonthPresent()) {
            return req.getSocialSecurityStartMonth();
        }
        return old != null ? old.getSocialSecurityStartMonth() : null;
    }

    /**
     * 花名册校验（社保用合并后的有效状态）
     */
    void validateRoster(EmployeeSaveReqVO req, EmployeeDO old) {
        Boolean enabled = effectiveSocialSecurityEnabled(req, old);
        String month = effectiveSocialSecurityStartMonth(req, old);

        if (Boolean.TRUE.equals(enabled) && StrUtil.isBlank(month)) {
            throw exception(EMPLOYEE_ROSTER_SOCIAL_SECURITY_MONTH);
        }
        if (StrUtil.isNotBlank(month)) {
            try {
                YEAR_MONTH.parse(month);
            } catch (DateTimeParseException ex) {
                throw exception(EMPLOYEE_ROSTER_SOCIAL_SECURITY_MONTH_FORMAT);
            }
        }

        if (req.getProbationSalary() != null && req.getProbationSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw exception(EMPLOYEE_ROSTER_SALARY_NEGATIVE);
        }
        if (req.getRegularSalary() != null && req.getRegularSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw exception(EMPLOYEE_ROSTER_SALARY_NEGATIVE);
        }

        if (CollUtil.isNotEmpty(req.getEducationList())) {
            long firstCount = req.getEducationList().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getFirstEducation())).count();
            long highestCount = req.getEducationList().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getHighestEducation())).count();
            if (firstCount > 1 || highestCount > 1) {
                throw exception(EMPLOYEE_ROSTER_EDUCATION_ROLE);
            }
        }

        List<EmployeeContractVO> contracts = req.getContractList();
        if (contracts == null || contracts.isEmpty()) {
            return;
        }
        if (contracts.size() > 4) {
            throw exception(EMPLOYEE_ROSTER_CONTRACT_LIMIT);
        }
        List<EmployeeContractVO> sorted = contracts.stream()
                .sorted(Comparator.comparing(EmployeeContractVO::getSequenceNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            EmployeeContractVO c = sorted.get(i);
            if (c.getSequenceNo() == null || c.getSequenceNo() != i + 1 || !seen.add(c.getSequenceNo())) {
                throw exception(EMPLOYEE_ROSTER_CONTRACT_SEQUENCE);
            }
            if (c.getStartDate() == null) {
                throw exception(EMPLOYEE_ROSTER_CONTRACT_START_REQUIRED);
            }
            if (c.getEndDate() != null && c.getEndDate().isBefore(c.getStartDate())) {
                throw exception(EMPLOYEE_ROSTER_CONTRACT_DATE);
            }
        }
    }

    private void applyEducationSummary(EmployeeDO employee, List<EmployeeEducationVO> educationList) {
        if (CollUtil.isEmpty(educationList)) {
            return;
        }
        educationList.stream()
                .filter(e -> Boolean.TRUE.equals(e.getHighestEducation()))
                .map(EmployeeEducationVO::getEducationLevel)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .ifPresent(employee::setEducation);
    }

    private void saveWorkExperiences(Long employeeId, List<EmployeeWorkExperienceVO> workExperienceList) {
        if (CollUtil.isEmpty(workExperienceList)) {
            return;
        }
        List<EmployeeWorkExperienceDO> workExperiences = BeanUtils.toBean(workExperienceList, EmployeeWorkExperienceDO.class);
        workExperiences.forEach(item -> {
            item.setId(null);
            item.setEmployeeId(employeeId);
            employeeWorkExperienceMapper.insert(item);
        });
    }

    private void saveEducations(Long employeeId, List<EmployeeEducationVO> educationList) {
        if (CollUtil.isEmpty(educationList)) {
            return;
        }
        List<EmployeeEducationDO> educations = BeanUtils.toBean(educationList, EmployeeEducationDO.class);
        educations.forEach(item -> {
            item.setId(null);
            item.setEmployeeId(employeeId);
            if (item.getFirstEducation() == null) {
                item.setFirstEducation(false);
            }
            if (item.getHighestEducation() == null) {
                item.setHighestEducation(false);
            }
            employeeEducationMapper.insert(item);
        });
    }

    private void saveFamilies(Long employeeId, List<EmployeeFamilyVO> familyList) {
        if (CollUtil.isEmpty(familyList)) {
            return;
        }
        List<EmployeeFamilyDO> families = BeanUtils.toBean(familyList, EmployeeFamilyDO.class);
        families.forEach(item -> {
            item.setId(null);
            item.setEmployeeId(employeeId);
            employeeFamilyMapper.insert(item);
        });
    }

    void saveEmployments(Long employeeId, List<EmployeeEmploymentVO> employmentList, EmployeeDO archive) {
        List<EmployeeEmploymentVO> list = employmentList;
        if (CollUtil.isEmpty(list) && archive != null && archive.getCompanyId() != null) {
            EmployeeEmploymentVO fallback = new EmployeeEmploymentVO();
            fallback.setCompanyDeptId(archive.getCompanyId());
            fallback.setDeptId(archive.getDeptId());
            fallback.setSigned(true);
            list = List.of(fallback);
        }
        if (CollUtil.isEmpty(list)) {
            return;
        }
        long signedCount = list.stream().filter(item -> Boolean.TRUE.equals(item.getSigned())).count();
        if (signedCount != 1) {
            throw exception(EMPLOYEE_EMPLOYMENT_SIGNED_REQUIRED);
        }
        Set<Long> seen = new HashSet<>();
        for (EmployeeEmploymentVO item : list) {
            if (item.getCompanyDeptId() == null || !seen.add(item.getCompanyDeptId())) {
                throw exception(EMPLOYEE_EMPLOYMENT_COMPANY_DUPLICATE);
            }
            if (item.getDeptId() == null) {
                throw exception(EMPLOYEE_EMPLOYMENT_DEPT_REQUIRED);
            }
            Long companyOfDept = findCompanyIdByDeptId(item.getDeptId());
            if (!item.getCompanyDeptId().equals(companyOfDept)) {
                throw exception(EMPLOYEE_EMPLOYMENT_DEPT_NOT_UNDER_COMPANY);
            }
        }
        for (EmployeeEmploymentVO item : list) {
            employeeEmploymentMapper.insert(EmployeeEmploymentDO.builder()
                    .employeeId(employeeId)
                    .companyDeptId(item.getCompanyDeptId())
                    .deptId(item.getDeptId())
                    .signed(Boolean.TRUE.equals(item.getSigned()))
                    .build());
            if (Boolean.TRUE.equals(item.getSigned()) && archive != null) {
                archive.setCompanyId(item.getCompanyDeptId());
                archive.setDeptId(item.getDeptId());
                if (StrUtil.isNotBlank(item.getCompanyName())) {
                    archive.setCompanyName(item.getCompanyName());
                }
                if (StrUtil.isNotBlank(item.getDeptName())) {
                    archive.setDeptName(item.getDeptName());
                }
                employeeArchiveMapper.updateById(archive);
            }
        }
    }

    List<EmployeeEmploymentVO> buildEmploymentList(EmployeeDO archive) {
        List<EmployeeEmploymentDO> rows = employeeEmploymentMapper.selectListByEmployeeId(archive.getId());
        if (CollUtil.isEmpty(rows) && archive.getCompanyId() != null) {
            EmployeeEmploymentVO vo = new EmployeeEmploymentVO();
            vo.setCompanyDeptId(archive.getCompanyId());
            vo.setCompanyName(archive.getCompanyName());
            vo.setDeptId(archive.getDeptId());
            vo.setDeptName(archive.getDeptName());
            vo.setSigned(true);
            return List.of(vo);
        }
        return rows.stream().map(row -> {
            EmployeeEmploymentVO vo = new EmployeeEmploymentVO();
            vo.setCompanyDeptId(row.getCompanyDeptId());
            vo.setDeptId(row.getDeptId());
            vo.setSigned(Boolean.TRUE.equals(row.getSigned()));
            if (row.getCompanyDeptId() != null) {
                CommonResult<DeptRespDTO> company = deptApi.getDept(row.getCompanyDeptId());
                if (company != null && company.isSuccess() && company.getData() != null) {
                    vo.setCompanyName(company.getData().getName());
                }
            }
            if (row.getDeptId() != null) {
                CommonResult<DeptRespDTO> dept = deptApi.getDept(row.getDeptId());
                if (dept != null && dept.isSuccess() && dept.getData() != null) {
                    vo.setDeptName(dept.getData().getName());
                }
            }
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applySigningEmployment(Long employeeId, Long companyDeptId, Long deptId,
                                       String companyName, String deptName) {
        if (employeeId == null || companyDeptId == null || deptId == null) {
            return;
        }
        Long companyOfDept = findCompanyIdByDeptId(deptId);
        if (!companyDeptId.equals(companyOfDept)) {
            throw exception(EMPLOYEE_EMPLOYMENT_DEPT_NOT_UNDER_COMPANY);
        }
        EmployeeDO archive = employeeArchiveMapper.selectById(employeeId);
        if (archive == null) {
            return;
        }
        List<EmployeeEmploymentDO> rows = employeeEmploymentMapper.selectListByEmployeeId(employeeId);
        EmployeeEmploymentDO target = rows.stream()
                .filter(row -> companyDeptId.equals(row.getCompanyDeptId()))
                .findFirst()
                .orElse(null);
        for (EmployeeEmploymentDO row : rows) {
            boolean signed = companyDeptId.equals(row.getCompanyDeptId());
            if (Boolean.TRUE.equals(row.getSigned()) == signed
                    && (!signed || deptId.equals(row.getDeptId()))) {
                continue;
            }
            row.setSigned(signed);
            if (signed) {
                row.setDeptId(deptId);
            }
            employeeEmploymentMapper.updateById(row);
        }
        if (target == null) {
            employeeEmploymentMapper.insert(EmployeeEmploymentDO.builder()
                    .employeeId(employeeId)
                    .companyDeptId(companyDeptId)
                    .deptId(deptId)
                    .signed(true)
                    .build());
        }
        archive.setCompanyId(companyDeptId);
        archive.setDeptId(deptId);
        if (StrUtil.isNotBlank(companyName)) {
            archive.setCompanyName(companyName);
        }
        if (StrUtil.isNotBlank(deptName)) {
            archive.setDeptName(deptName);
        }
        employeeArchiveMapper.updateById(archive);
        if (Boolean.TRUE.equals(archive.getUserGenerated()) && archive.getUserId() != null) {
            EmployeeSaveReqVO sync = new EmployeeSaveReqVO();
            sync.setEmployeeNo(archive.getEmployeeNo());
            sync.setName(archive.getName());
            sync.setMobile(archive.getMobile());
            sync.setEmail(archive.getEmail());
            sync.setSex(archive.getSex());
            sync.setAvatar(archive.getAvatar());
            sync.setDeptId(deptId);
            sync.setRemark(archive.getRemark());
            syncEmployeeToUser(sync, archive.getUserId());
        }
    }

    @Override
    public List<EmployeeEmploymentVO> listMyEmployments(Long userId) {
        if (userId == null) {
            return List.of();
        }
        EmployeeDO archive = employeeArchiveMapper.selectByUserId(userId);
        if (archive == null) {
            return List.of();
        }
        return buildEmploymentList(archive);
    }

    void saveContracts(Long employeeId, List<EmployeeContractVO> contracts) {
        if (CollUtil.isEmpty(contracts)) {
            return;
        }
        List<EmployeeContractDO> list = BeanUtils.toBean(contracts, EmployeeContractDO.class);
        list.forEach(item -> {
            item.setId(null);
            item.setEmployeeId(employeeId);
            employeeContractMapper.insert(item);
        });
    }

    /**
     * #7：省略的可空字段回填旧值，避免 FieldStrategy.ALWAYS 误清空。
     */
    void restoreOmittedRosterFields(EmployeeDO updateObj, EmployeeSaveReqVO req, EmployeeDO old) {
        if (!req.isEmergencyRelationshipPresent()) {
            updateObj.setEmergencyRelationship(old.getEmergencyRelationship());
        }
        if (!req.isSocialSecurityEnabledPresent()) {
            updateObj.setSocialSecurityEnabled(old.getSocialSecurityEnabled());
        }
        if (!req.isHousingFundEnabledPresent()) {
            updateObj.setHousingFundEnabled(old.getHousingFundEnabled());
        }
        if (!req.isSocialSecurityStartMonthPresent()) {
            updateObj.setSocialSecurityStartMonth(old.getSocialSecurityStartMonth());
        }
        if (!req.isProbationSalaryPresent()) {
            updateObj.setProbationSalary(old.getProbationSalary());
        }
        if (!req.isRegularSalaryPresent()) {
            updateObj.setRegularSalary(old.getRegularSalary());
        }
        if (!req.isFertilityStatusPresent()) {
            updateObj.setFertilityStatus(old.getFertilityStatus());
        }
        if (!req.isHouseholdTypePresent()) {
            updateObj.setHouseholdType(old.getHouseholdType());
        }
        if (!req.isEmploymentFormPresent()) {
            updateObj.setEmploymentForm(old.getEmploymentForm());
        }
        if (!req.isRecruitmentChannelPresent()) {
            updateObj.setRecruitmentChannel(old.getRecruitmentChannel());
        }
        if (!req.isInterviewerNamePresent()) {
            updateObj.setInterviewerName(old.getInterviewerName());
        }
    }

    /**
     * 上传入职资料：私有目录 + 一次性 claim（不返回公开 URL/path/configId）。
     */
    public OnboardingFileClaimRespVO uploadOnboardingFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_EMPTY);
        }
        if (file.getSize() > ONBOARDING_MAX_SIZE_BYTES) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_TOO_LARGE);
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = resolveExt(original);
        if (StrUtil.isBlank(ext) || !ONBOARDING_ALLOWED_EXTENSIONS.contains(ext)) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_TYPE);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        byte[] content = file.getBytes();
        FileRespDTO stored;
        try {
            stored = fileAccessApi.createFile(content, original, ONBOARDING_PRIVATE_DIR, file.getContentType());
        } catch (Exception ex) {
            log.error("[uploadOnboardingFile] store failed, name={}", original, ex);
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_STORE_FAILED);
        }
        if (stored == null || stored.getId() == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_STORE_FAILED);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expire = LocalDateTime.now().plusMinutes(CLAIM_TTL_MINUTES);
        OnboardingFileClaimDO claim = OnboardingFileClaimDO.builder()
                .claimToken(token)
                .fileId(stored.getId())
                .uploaderUserId(userId)
                .purpose(OnboardingFileClaimDO.PURPOSE)
                .expireTime(expire)
                .build();
        onboardingFileClaimMapper.insert(claim);

        OnboardingFileClaimRespVO resp = new OnboardingFileClaimRespVO();
        resp.setClaimToken(token);
        resp.setFileName(stored.getName() != null ? stored.getName() : original);
        resp.setFileSize(stored.getSize());
        resp.setFileExtension(ext);
        resp.setExpireTime(expire.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        return resp;
    }

    /**
     * 入职资料：已有附件 id 或 claimToken；元数据以权威 file 记录为准。
     */
    void saveOnboardingAttachments(Long employeeId, List<OnboardingAttachmentSaveReqVO> reqs) {
        if (reqs == null) {
            return;
        }
        if (reqs.size() > ONBOARDING_MAX_COUNT) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_LIMIT);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        List<AttachmentSaveReqVO> resolved = new ArrayList<>();
        Set<Long> seenAttachmentIds = new HashSet<>();
        Set<String> seenClaims = new HashSet<>();
        for (OnboardingAttachmentSaveReqVO req : reqs) {
            if (req.getId() != null) {
                if (!seenAttachmentIds.add(req.getId())) {
                    throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
                }
                AttachmentSaveReqVO keep = new AttachmentSaveReqVO();
                keep.setId(req.getId());
                keep.setBusinessType(ONBOARDING_ATTACHMENT_BUSINESS_TYPE);
                keep.setBusinessId(employeeId);
                keep.setFileName(".");
                keep.setFilePath(".");
                keep.setFileUrl(".");
                keep.setFileSize(0L);
                keep.setSortOrder(req.getSortOrder());
                keep.setRemark(req.getRemark());
                resolved.add(keep);
                continue;
            }
            if (StrUtil.isBlank(req.getClaimToken()) || !seenClaims.add(req.getClaimToken())) {
                throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
            }
            OnboardingFileClaimDO claim = consumeClaim(req.getClaimToken(), userId, employeeId);
            FileRespDTO file = fileAccessApi.getFile(claim.getFileId());
            if (file == null) {
                throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
            }
            validateOnboardingFile(file);
            AttachmentSaveReqVO att = new AttachmentSaveReqVO();
            att.setBusinessType(ONBOARDING_ATTACHMENT_BUSINESS_TYPE);
            att.setBusinessId(employeeId);
            att.setFileId(file.getId());
            att.setFileName(file.getName());
            // 不把可公开直链交给客户端；库内保留 path 供鉴权下载/回填
            att.setFilePath(file.getPath());
            att.setFileUrl(""); // 不落公开 URL
            att.setFileSize(file.getSize());
            att.setFileType(file.getType());
            att.setFileExtension(resolveExt(file.getName()));
            att.setSortOrder(req.getSortOrder());
            att.setRemark(req.getRemark());
            resolved.add(att);
        }
        // 内部链路：保留业务类型仅允许此路径写入
        attachmentService.saveAttachmentListInternal(ONBOARDING_ATTACHMENT_BUSINESS_TYPE, employeeId, resolved);
    }

    /**
     * 原子消费 claim：条件 UPDATE（token + uploader + purpose + 未过期 + consumed_at IS NULL），
     * 影响行数必须为 1，防止并发双消费。
     */
    OnboardingFileClaimDO consumeClaim(String claimToken, Long userId, Long employeeId) {
        if (StrUtil.isBlank(claimToken) || userId == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = onboardingFileClaimMapper.consumeIfOpen(
                claimToken, userId, OnboardingFileClaimDO.PURPOSE, employeeId, now);
        if (rows != 1) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID); // 他人/过期/已消费/不存在/并发失败
        }
        OnboardingFileClaimDO claim = onboardingFileClaimMapper.selectByClaimToken(claimToken);
        if (claim == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        return claim;
    }

    void validateOnboardingFile(FileRespDTO file) {
        if (file.getSize() != null && file.getSize() > ONBOARDING_MAX_SIZE_BYTES) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_TOO_LARGE);
        }
        String ext = resolveExt(file.getName());
        if (StrUtil.isBlank(ext) || !ONBOARDING_ALLOWED_EXTENSIONS.contains(ext)) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_TYPE);
        }
        // 必须落在私有目录
        if (file.getPath() == null || !file.getPath().contains(ONBOARDING_PRIVATE_DIR)) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
    }

    private static String resolveExt(String name) {
        if (StrUtil.isBlank(name) || !name.contains(".")) {
            return null;
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    List<OnboardingAttachmentRespVO> toOnboardingRespList(Long employeeId, List<AttachmentDO> attachments) {
        if (CollUtil.isEmpty(attachments)) {
            return Collections.emptyList();
        }
        return attachments.stream().map(a -> {
            OnboardingAttachmentRespVO vo = new OnboardingAttachmentRespVO();
            vo.setId(a.getId());
            vo.setFileName(a.getFileName());
            vo.setFileSize(a.getFileSize());
            vo.setFileExtension(a.getFileExtension());
            vo.setFileType(a.getFileType());
            vo.setSortOrder(a.getSortOrder());
            vo.setRemark(a.getRemark());
            vo.setUploadTime(a.getUploadTime());
            // 仅返回鉴权下载路径，不暴露 fileId/path/url
            vo.setDownloadPath("/hrm/employee-archive/onboarding-attachment/download?employeeId="
                    + employeeId + "&attachmentId=" + a.getId());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 鉴权下载：登录 + query 权限 + 归属校验；内容经 FileAccessApi 本地 Bean，不走匿名 RPC。
     */
    public void downloadOnboardingAttachment(Long employeeId, Long attachmentId, HttpServletResponse response)
            throws Exception {
        if (employeeArchiveMapper.selectById(employeeId) == null) {
            throw exception(EMPLOYEE_ARCHIVE_NOT_EXISTS);
        }
        AttachmentDO att = attachmentService.getAttachmentInternal(attachmentId);
        if (att == null
                || !ONBOARDING_ATTACHMENT_BUSINESS_TYPE.equals(att.getBusinessType())
                || !employeeId.equals(att.getBusinessId())) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        // 仅通过权威 fileId 或唯一 URL/(path) 解析；禁止裸 path fallback（路径穿越）
        Long fileId = resolveAuthoritativeFileId(att);
        if (fileId == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        byte[] content = fileAccessApi.getFileContent(fileId);
        if (content == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        String fileName = att.getFileName();
        FileRespDTO meta = fileAccessApi.getFile(fileId);
        if (meta != null && StrUtil.isNotBlank(meta.getName())) {
            fileName = meta.getName();
        }
        writeDownload(response, fileName, content);
    }

    /**
     * 历史附件身份：优先已有 fileId；否则唯一精确 URL；再否则唯一 path。
     * 歧义（0 或多条）返回 null，不猜测。
     */
    Long resolveAuthoritativeFileId(AttachmentDO att) {
        if (att.getFileId() != null) {
            FileRespDTO byId = fileAccessApi.getFile(att.getFileId());
            return byId != null ? byId.getId() : null;
        }
        if (StrUtil.isNotBlank(att.getFileUrl())) {
            FileRespDTO byUrl = fileAccessApi.getUniqueFileByUrl(att.getFileUrl());
            if (byUrl != null) {
                return byUrl.getId();
            }
        }
        if (StrUtil.isNotBlank(att.getFilePath())) {
            FileRespDTO byPath = fileAccessApi.getUniqueFileByPath(att.getFilePath());
            if (byPath != null) {
                return byPath.getId();
            }
        }
        return null;
    }

    private static void writeDownload(HttpServletResponse response, String fileName, byte[] content)
            throws Exception {
        response.setContentType("application/octet-stream");
        String encoded = java.net.URLEncoder.encode(fileName == null ? "file" : fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    void fillDerivedFields(EmployeeRespVO resp, LocalDate today) {
        if (resp.getBirthday() != null) {
            resp.setAge(Period.between(resp.getBirthday(), today).getYears());
        }
        if (resp.getEntryDate() != null) {
            Period p = Period.between(resp.getEntryDate(), today);
            resp.setCompanyTenureMonths(p.getYears() * 12 + p.getMonths());
        }
        resp.setMarriageChildbearingSummary(buildMarriageChildbearingSummary(
                resp.getMaritalStatus(), resp.getFertilityStatus()));
    }

    void fillCurrentContract(EmployeeRespVO resp, List<EmployeeContractDO> contracts) {
        if (CollUtil.isEmpty(contracts)) {
            resp.setContractSignCount(0);
            return;
        }
        resp.setContractSignCount(contracts.size());
        EmployeeContractDO current = contracts.stream()
                .max(Comparator.comparing(EmployeeContractDO::getSequenceNo))
                .orElse(null);
        if (current != null) {
            resp.setCurrentContractType(current.getContractType());
            resp.setCurrentContractStartDate(current.getStartDate());
            resp.setCurrentContractEndDate(current.getEndDate());
        }
    }

    private PageResult<EmployeeRespVO> buildEmployeeRespPage(PageResult<EmployeeDO> pageResult) {
        PageResult<EmployeeRespVO> respPageResult = BeanUtils.toBean(pageResult, EmployeeRespVO.class);
        LocalDate today = LocalDate.now();

        // 批量获取部门名称
        List<Long> deptIds = respPageResult.getList().stream()
                .map(EmployeeRespVO::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollUtil.isNotEmpty(deptIds)) {
            Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
            respPageResult.getList().forEach(respVO -> {
                if (respVO.getDeptId() != null && deptMap.containsKey(respVO.getDeptId())) {
                    respVO.setDeptName(deptMap.get(respVO.getDeptId()).getName());
                }
            });
        }

        // 列表仅填充可从主档计算的年龄/司龄
        respPageResult.getList().forEach(respVO -> fillDerivedFields(respVO, today));
        return respPageResult;
    }

    private EmployeeRosterExportVO buildRosterExportRow(int sequenceNo,
                                                        EmployeeDO employee,
                                                        List<EmployeeEducationDO> educations,
                                                        List<EmployeeContractDO> contracts,
                                                        List<AttachmentDO> attachments,
                                                        LocalDate today) {
        EmployeeRosterExportVO row = new EmployeeRosterExportVO();
        row.setSequenceNo(sequenceNo);
        row.setSocialSecurityEnabled(formatYesNo(employee.getSocialSecurityEnabled()));
        row.setHousingFundEnabled(formatYesNo(employee.getHousingFundEnabled()));
        row.setCompanyName(employee.getCompanyName());
        row.setDeptName(employee.getDeptName());
        row.setJobPost(employee.getJobPost());
        row.setName(employee.getName());
        row.setEntryDate(formatDate(employee.getEntryDate()));
        row.setFormalDate(formatDate(employee.getFormalDate()));
        row.setProbationSalary(employee.getProbationSalary());
        row.setRegularSalary(employee.getRegularSalary());
        row.setSocialSecurityStartMonth(employee.getSocialSecurityStartMonth());
        row.setIdCard(employee.getIdCard());
        row.setMobile(employee.getMobile());
        row.setSex(dictLabel(DictTypeConstants.USER_SEX, employee.getSex()));
        row.setNation(dictLabel(DICT_NATION, employee.getNation()));
        row.setMarriageChildbearingSummary(buildMarriageChildbearingSummary(
                dictLabel(DICT_MARITAL, employee.getMaritalStatus()),
                dictLabel(DICT_FERTILITY, employee.getFertilityStatus())));
        row.setHouseholdType(dictLabel(DICT_HOUSEHOLD, employee.getHouseholdType()));
        row.setNativePlace(employee.getNativePlace());
        row.setBirthdayMonth(employee.getBirthday() == null ? null : employee.getBirthday().format(YEAR_MONTH));
        if (employee.getBirthday() != null) {
            row.setAge(Period.between(employee.getBirthday(), today).getYears());
        }
        if (employee.getEntryDate() != null) {
            Period p = Period.between(employee.getEntryDate(), today);
            int months = p.getYears() * 12 + p.getMonths();
            row.setCompanyTenure(formatTenure(months));
        }

        EmployeeEducationDO highest = educations.stream()
                .filter(e -> Boolean.TRUE.equals(e.getHighestEducation())).findFirst().orElse(null);
        EmployeeEducationDO first = educations.stream()
                .filter(e -> Boolean.TRUE.equals(e.getFirstEducation())).findFirst().orElse(null);
        if (highest != null) {
            row.setHighestEducation(dictLabel(DICT_EDUCATION, highest.getEducationLevel()));
            row.setEducationType(dictLabel(DICT_EDUCATION_TYPE, highest.getEducationType()));
            row.setHighestDegree(highest.getDegree());
            row.setHighestSchoolName(highest.getSchoolName());
            row.setHighestMajor(highest.getMajor());
            row.setHighestGraduateDate(formatDate(highest.getEndTime()));
        } else if (StrUtil.isNotBlank(employee.getEducation())) {
            row.setHighestEducation(dictLabel(DICT_EDUCATION, employee.getEducation()));
        }
        if (first != null) {
            row.setFirstEducation(dictLabel(DICT_EDUCATION, first.getEducationLevel()));
            row.setFirstDegree(first.getDegree());
            row.setFirstSchoolName(first.getSchoolName());
            row.setFirstMajor(first.getMajor());
            row.setFirstGraduateDate(formatDate(first.getEndTime()));
        }

        row.setHouseholdAddress(employee.getHouseholdAddress());
        row.setCurrentAddress(employee.getCurrentAddress());
        row.setEmployeeType(dictLabel(DICT_EMPLOYEE_STATUS, employee.getEmployeeStatus()));
        row.setEmploymentForm(dictLabel(DICT_EMPLOYMENT_FORM, employee.getEmploymentForm()));

        row.setContractSignCount(contracts.size());
        EmployeeContractDO current = contracts.stream()
                .max(Comparator.comparing(EmployeeContractDO::getSequenceNo)).orElse(null);
        if (current != null) {
            row.setCurrentContractType(dictLabel(DICT_CONTRACT_TYPE, current.getContractType()));
            row.setCurrentContractStartDate(formatDate(current.getStartDate()));
            row.setCurrentContractEndDate(formatDate(current.getEndDate()));
        }
        row.setContract1Range(formatContractRange(findContract(contracts, 1)));
        row.setContract2Range(formatContractRange(findContract(contracts, 2)));
        row.setContract3Range(formatContractRange(findContract(contracts, 3)));
        row.setContract4Range(formatContractRange(findContract(contracts, 4)));

        row.setBankAccount(employee.getBankAccount());
        row.setBankName(employee.getBankName());
        row.setEmergencyContactRelation(formatEmergency(employee.getEmergencyContact(), employee.getEmergencyRelationship()));
        row.setEmergencyPhone(employee.getEmergencyPhone());
        row.setRecruitmentChannel(employee.getRecruitmentChannel());
        row.setInterviewerName(employee.getInterviewerName());
        row.setOnboardingAttachmentStatus(attachments.isEmpty()
                ? "未上传"
                : "已上传" + attachments.size() + "份");
        return row;
    }

    private static EmployeeContractDO findContract(List<EmployeeContractDO> contracts, int sequenceNo) {
        return contracts.stream().filter(c -> Objects.equals(c.getSequenceNo(), sequenceNo)).findFirst().orElse(null);
    }

    private static String formatContractRange(EmployeeContractDO contract) {
        if (contract == null || contract.getStartDate() == null) {
            return null;
        }
        String start = formatDate(contract.getStartDate());
        if (contract.getEndDate() == null) {
            return start;
        }
        return start + "至" + formatDate(contract.getEndDate());
    }

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String formatYesNo(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "是" : "否";
    }

    /**
     * 导出字典标签；解析失败时回退原值，避免整列空白。
     */
    static String dictLabel(String dictType, Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value);
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            String label = value instanceof Integer
                    ? DictFrameworkUtils.parseDictDataLabel(dictType, (Integer) value)
                    : DictFrameworkUtils.parseDictDataLabel(dictType, raw);
            return StrUtil.isNotBlank(label) ? label : raw;
        } catch (Exception ex) {
            return raw;
        }
    }

    private static String formatTenure(int months) {
        int years = months / 12;
        int remain = months % 12;
        return years + "年" + remain + "个月";
    }

    private static String buildMarriageChildbearingSummary(String maritalStatus, String fertilityStatus) {
        if (StrUtil.isBlank(maritalStatus) && StrUtil.isBlank(fertilityStatus)) {
            return null;
        }
        if (StrUtil.isBlank(maritalStatus)) {
            return fertilityStatus;
        }
        if (StrUtil.isBlank(fertilityStatus)) {
            return maritalStatus;
        }
        return maritalStatus + "/" + fertilityStatus;
    }

    private static String formatEmergency(String contact, String relationship) {
        if (StrUtil.isBlank(contact) && StrUtil.isBlank(relationship)) {
            return null;
        }
        if (StrUtil.isBlank(relationship)) {
            return contact;
        }
        if (StrUtil.isBlank(contact)) {
            return relationship;
        }
        return contact + "/" + relationship;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateUserForEmployee(Long employeeId) {
        // 1. 校验员工存在
        EmployeeDO employee = employeeArchiveMapper.selectById(employeeId);
        if (employee == null) {
            throw exception(EMPLOYEE_ARCHIVE_NOT_EXISTS);
        }

        // 2. 校验是否已生成用户
        if (employee.getUserGenerated() != null && employee.getUserGenerated() && employee.getUserId() != null) {
            throw new RuntimeException("该员工已生成用户，无需重复生成");
        }

        // 3. 创建用户
        AdminUserCreateReqDTO userCreateReqDTO = new AdminUserCreateReqDTO();
        userCreateReqDTO.setUsername(employee.getEmployeeNo());
        userCreateReqDTO.setNickname(employee.getName());
        userCreateReqDTO.setMobile(employee.getMobile());
        userCreateReqDTO.setEmail(employee.getEmail());
        userCreateReqDTO.setSex(employee.getSex());
        userCreateReqDTO.setAvatar(employee.getAvatar());
        userCreateReqDTO.setDeptId(employee.getDeptId());
        userCreateReqDTO.setRemark(employee.getRemark());

        String initPassword = "123456";
        CommonResult<String> configResult = configApi.getConfigValueByKey("system.user.init-password");
        if (configResult != null && configResult.isSuccess() && configResult.getData() != null) {
            initPassword = configResult.getData();
        }
        userCreateReqDTO.setPassword(initPassword);

        Long userId = adminUserApi.createUser(userCreateReqDTO).getCheckedData();

        EmployeeDO updateObj = new EmployeeDO();
        updateObj.setId(employeeId);
        updateObj.setUserId(userId);
        updateObj.setUserGenerated(true);
        employeeArchiveMapper.updateById(updateObj);

        return userId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchGenerateUserForEmployee(List<Long> employeeIds) {
        if (CollUtil.isEmpty(employeeIds)) {
            return;
        }
        for (Long employeeId : employeeIds) {
            try {
                generateUserForEmployee(employeeId);
            } catch (Exception e) {
                // 记录错误，继续处理下一个
            }
        }
    }

    private void syncEmployeeToUser(EmployeeSaveReqVO employee, Long userId) {
        AdminUserUpdateReqDTO userUpdateReqDTO = new AdminUserUpdateReqDTO();
        userUpdateReqDTO.setId(userId);
        userUpdateReqDTO.setUsername(employee.getEmployeeNo());
        userUpdateReqDTO.setNickname(employee.getName());
        userUpdateReqDTO.setMobile(employee.getMobile());
        userUpdateReqDTO.setEmail(employee.getEmail());
        userUpdateReqDTO.setSex(employee.getSex());
        userUpdateReqDTO.setAvatar(employee.getAvatar());
        userUpdateReqDTO.setDeptId(employee.getDeptId());
        userUpdateReqDTO.setRemark(employee.getRemark());
        adminUserApi.updateUser(userUpdateReqDTO);
    }

    private Long findCompanyIdByDeptId(Long deptId) {
        if (deptId == null) {
            return null;
        }
        for (int i = 0; i < 100; i++) {
            CommonResult<DeptRespDTO> deptResult = deptApi.getDept(deptId);
            if (deptResult == null || !deptResult.isSuccess() || deptResult.getData() == null) {
                break;
            }
            DeptRespDTO dept = deptResult.getData();
            if ("1".equals(dept.getOrgType())) {
                return dept.getId();
            }
            if (dept.getParentId() == null || dept.getParentId() == 0) {
                break;
            }
            deptId = dept.getParentId();
        }
        return null;
    }

}
