package cn.iocoder.yudao.module.hrm.service.employee;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentRespVO;
import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.*;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCreateReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserUpdateReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
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
public class EmployeeServiceImpl implements EmployeeService {

    public static final String ONBOARDING_ATTACHMENT_BUSINESS_TYPE = "hrm_employee_archive_onboarding";

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

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
    private AttachmentService attachmentService;

    @Resource
    private DeptApi deptApi;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private ConfigApi configApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEmployeeArchive(EmployeeSaveReqVO createReqVO) {
        validateRoster(createReqVO);

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

        // 插入关联明细
        saveWorkExperiences(archive.getId(), createReqVO.getWorkExperienceList());
        saveEducations(archive.getId(), createReqVO.getEducationList());
        saveFamilies(archive.getId(), createReqVO.getFamilyList());
        saveContracts(archive.getId(), createReqVO.getContractList());

        attachmentService.saveAttachmentList(
                ONBOARDING_ATTACHMENT_BUSINESS_TYPE, archive.getId(), createReqVO.getOnboardingAttachments());

        return archive.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmployeeArchive(EmployeeSaveReqVO updateReqVO) {
        validateRoster(updateReqVO);

        // 校验存在
        EmployeeDO oldEmployee = employeeArchiveMapper.selectById(updateReqVO.getId());
        if (oldEmployee == null) {
            throw exception(EMPLOYEE_ARCHIVE_NOT_EXISTS);
        }

        // 更新主表
        EmployeeDO updateObj = BeanUtils.toBean(updateReqVO, EmployeeDO.class);
        applyEducationSummary(updateObj, updateReqVO.getEducationList());
        // 社保为否时清空参保年月
        if (Boolean.FALSE.equals(updateObj.getSocialSecurityEnabled())) {
            updateObj.setSocialSecurityStartMonth(null);
        }
        employeeArchiveMapper.updateById(updateObj);

        // 删除旧的关联记录
        employeeWorkExperienceMapper.deleteByEmployeeId(updateReqVO.getId());
        employeeEducationMapper.deleteByEmployeeId(updateReqVO.getId());
        employeeFamilyMapper.deleteByEmployeeId(updateReqVO.getId());
        employeeContractMapper.deleteByEmployeeId(updateReqVO.getId());

        // 插入新的关联记录
        saveWorkExperiences(updateReqVO.getId(), updateReqVO.getWorkExperienceList());
        saveEducations(updateReqVO.getId(), updateReqVO.getEducationList());
        saveFamilies(updateReqVO.getId(), updateReqVO.getFamilyList());
        saveContracts(updateReqVO.getId(), updateReqVO.getContractList());

        attachmentService.saveAttachmentList(
                ONBOARDING_ATTACHMENT_BUSINESS_TYPE, updateReqVO.getId(), updateReqVO.getOnboardingAttachments());

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

        // 获取部门名称（如果数据库中没有保存，则通过部门查找）
        if (archive.getDeptId() != null) {
            if (archive.getDeptName() == null) {
                CommonResult<DeptRespDTO> dept = deptApi.getDept(archive.getDeptId());
                if (dept != null && dept.isSuccess() && dept.getData() != null) {
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

        // 入职资料
        List<AttachmentDO> attachments = attachmentService.getAttachmentListByBusiness(
                ONBOARDING_ATTACHMENT_BUSINESS_TYPE, id);
        respVO.setOnboardingAttachments(BeanUtils.toBean(attachments, AttachmentRespVO.class));

        fillDerivedFields(respVO, LocalDate.now());
        return respVO;
    }

    @Override
    public PageResult<EmployeeRespVO> getEmployeeArchivePage(EmployeePageReqVO pageReqVO) {
        PageResult<EmployeeDO> pageResult = employeeArchiveMapper.selectPage(pageReqVO);
        return buildEmployeeRespPage(pageResult);
    }

    @Override
    public PageResult<EmployeeRespVO> getEmployeeArchiveSelectablePage(EmployeeSelectPageReqVO pageReqVO) {
        PageResult<EmployeeDO> pageResult = employeeArchiveMapper.selectPageExcludeFormal(pageReqVO);
        return buildEmployeeRespPage(pageResult);
    }

    @Override
    public List<EmployeeRosterExportVO> getEmployeeRosterExportList(EmployeePageReqVO pageReqVO) {
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
     * 花名册校验
     */
    void validateRoster(EmployeeSaveReqVO req) {
        if (Boolean.TRUE.equals(req.getSocialSecurityEnabled())) {
            if (StrUtil.isBlank(req.getSocialSecurityStartMonth())) {
                throw exception(EMPLOYEE_ROSTER_SOCIAL_SECURITY_MONTH);
            }
        }
        if (StrUtil.isNotBlank(req.getSocialSecurityStartMonth())) {
            try {
                YEAR_MONTH.parse(req.getSocialSecurityStartMonth());
            } catch (DateTimeParseException ex) {
                throw exception(EMPLOYEE_ROSTER_SOCIAL_SECURITY_MONTH_FORMAT);
            }
        }
        if (Boolean.FALSE.equals(req.getSocialSecurityEnabled())) {
            req.setSocialSecurityStartMonth(null);
        }
        if (req.getProbationSalary() != null && req.getProbationSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw exception(EMPLOYEE_ROSTER_SALARY_NEGATIVE);
        }
        if (req.getRegularSalary() != null && req.getRegularSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw exception(EMPLOYEE_ROSTER_SALARY_NEGATIVE);
        }

        // 教育：第一/最高各至多一条
        if (CollUtil.isNotEmpty(req.getEducationList())) {
            long firstCount = req.getEducationList().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getFirstEducation())).count();
            long highestCount = req.getEducationList().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getHighestEducation())).count();
            if (firstCount > 1 || highestCount > 1) {
                throw exception(EMPLOYEE_ROSTER_EDUCATION_ROLE);
            }
        }

        // 合同
        List<EmployeeContractVO> contracts = req.getContractList();
        if (CollUtil.isEmpty(contracts)) {
            return;
        }
        if (contracts.size() > 4) {
            throw exception(EMPLOYEE_ROSTER_CONTRACT_LIMIT);
        }
        // 按序号排序后校验连续
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
            if (c.getEndDate() != null && c.getStartDate() != null && c.getEndDate().isBefore(c.getStartDate())) {
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
        row.setSex(employee.getSex() == null ? null : String.valueOf(employee.getSex()));
        row.setNation(employee.getNation());
        row.setMarriageChildbearingSummary(buildMarriageChildbearingSummary(
                employee.getMaritalStatus(), employee.getFertilityStatus()));
        row.setHouseholdType(employee.getHouseholdType());
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
            row.setHighestEducation(highest.getEducationLevel());
            row.setEducationType(highest.getEducationType());
            row.setHighestDegree(highest.getDegree());
            row.setHighestSchoolName(highest.getSchoolName());
            row.setHighestMajor(highest.getMajor());
            row.setHighestGraduateDate(formatDate(highest.getEndTime()));
        } else if (StrUtil.isNotBlank(employee.getEducation())) {
            row.setHighestEducation(employee.getEducation());
        }
        if (first != null) {
            row.setFirstEducation(first.getEducationLevel());
            row.setFirstDegree(first.getDegree());
            row.setFirstSchoolName(first.getSchoolName());
            row.setFirstMajor(first.getMajor());
            row.setFirstGraduateDate(formatDate(first.getEndTime()));
        }

        row.setHouseholdAddress(employee.getHouseholdAddress());
        row.setCurrentAddress(employee.getCurrentAddress());
        row.setEmployeeType(employee.getEmployeeStatus() == null ? null : String.valueOf(employee.getEmployeeStatus()));
        row.setEmploymentForm(employee.getEmploymentForm());

        row.setContractSignCount(contracts.size());
        EmployeeContractDO current = contracts.stream()
                .max(Comparator.comparing(EmployeeContractDO::getSequenceNo)).orElse(null);
        if (current != null) {
            row.setCurrentContractType(current.getContractType());
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
