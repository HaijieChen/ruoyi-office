package cn.iocoder.yudao.module.hrm.service.employee;

import cn.iocoder.yudao.framework.common.enums.SystemEnum;
import cn.iocoder.yudao.framework.common.util.bill.BillCodeUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.BpmProcessVariableConstants;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.util.BpmProcessVariableUtils;
import cn.iocoder.yudao.module.hrm.enums.HrmBillTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.*;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentRespVO;
import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import cn.iocoder.yudao.framework.common.service.FlowBillService;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.FilePrivateDirs;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;

import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ENTRY_BILL_ID_CARD_EXISTS;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ENTRY_BILL_MOBILE_EXISTS;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ENTRY_BILL_NOT_EXISTS;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ROSTER_ATTACHMENT_INVALID;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ROSTER_ATTACHMENT_LIMIT;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ROSTER_ATTACHMENT_TOO_LARGE;
import static cn.iocoder.yudao.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ROSTER_ATTACHMENT_TYPE;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum.APPROVE;

/**
 * 员工入职申请单 Service 实现类
 *
 * @author 宇擎源码
 */
@Slf4j
@Service
@Validated
public class EmployeeEntryBillServiceImpl implements EmployeeEntryBillService, FlowBillService<HrmBillTypeEnum> {

    @Resource
    private EmployeeEntryBillMapper employeeEntryBillMapper;

    @Resource
    private AttachmentService attachmentService;

    @Resource
    private FileAccessApi fileAccessApi;

    @Resource
    private OnboardingFileClaimMapper onboardingFileClaimMapper;

    @Resource
    private EmployeeService employeeService;

    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Resource
    private EmployeeMapper employeeMapper;

    @Resource
    private EmployeeWorkExperienceMapper employeeWorkExperienceMapper;

    @Resource
    private EmployeeEducationMapper employeeEducationMapper;

    @Resource
    private EmployeeFamilyMapper employeeFamilyMapper;

    @Resource
    private EmployeeEntryBillWorkExperienceMapper entryBillWorkExperienceMapper;

    @Resource
    private EmployeeEntryBillEducationMapper entryBillEducationMapper;

    @Resource
    private EmployeeEntryBillFamilyMapper entryBillFamilyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveEmployeeEntryBill(EmployeeEntryBillSaveReqVO saveReqVO) {
        // 如果单号为空，需要生成
        if(StringUtils.isBlank(saveReqVO.getBillCode())){
            saveReqVO.setBillCode(BillCodeUtils.generateBillCode(SystemEnum.HRM, HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL));
        }

        // 校验手机号与身份证号在员工档案中唯一
        validateMobileAndIdCardUnique(saveReqVO);

        // 插入或更新
        EmployeeEntryBillDO entryBill = BeanUtils.toBean(saveReqVO, EmployeeEntryBillDO.class);
        employeeEntryBillMapper.insertOrUpdate(entryBill);

        // 保存明细信息
        saveDetailLists(entryBill.getId(), saveReqVO);

        // 保存附件：仅 id 保留或 claim 消费；禁止客户端自报 fileId/path 抬升 reserved
        saveEntryBillAttachments(entryBill.getId(), saveReqVO.getAttachments());

        // 返回
        return entryBill.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitEmployeeEntryBill(EmployeeEntryBillSaveReqVO saveReqVO) {
        // 如果单号为空，需要生成
        if(StringUtils.isBlank(saveReqVO.getBillCode())){
            saveReqVO.setBillCode(BillCodeUtils.generateBillCode(SystemEnum.HRM, HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL));
        }

        // 校验手机号与身份证号在员工档案中唯一
        validateMobileAndIdCardUnique(saveReqVO);

        // 保存或更新
        EmployeeEntryBillDO entryBill = BeanUtils.toBean(saveReqVO, EmployeeEntryBillDO.class)
                .setProcessStatus(BpmTaskStatusEnum.RUNNING.getStatus());
        employeeEntryBillMapper.insertOrUpdate(entryBill);

        // 保存明细信息
        saveDetailLists(entryBill.getId(), saveReqVO);

        // H3：claim 校验/附件落库必须在 BPM 远端提交之前，避免远端副作用不可回滚
        saveEntryBillAttachments(entryBill.getId(), saveReqVO.getAttachments());

        // 智能提交 BPM 流程（如果流程实例不存在则创建，存在则审批发起人任务）
        Map<String, Object> processInstanceVariables = BpmProcessVariableUtils.buildBillVariables(saveReqVO);
        processInstanceVariables.put(BpmProcessVariableConstants.CAUSE, entryBill.getName()+"入职申请");
        String processInstanceId = processInstanceApi.submitProcessInstance(Long.valueOf(saveReqVO.getCreator()),
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL.getProcessDefinitionKey())
                        .setVariables(processInstanceVariables).setBusinessKey(String.valueOf(entryBill.getId()))
        ).getCheckedData();

        // 将工作流的编号，更新到单据中
        employeeEntryBillMapper.updateById(new EmployeeEntryBillDO().setId(entryBill.getId()).setProcessInstanceId(processInstanceId));
        
        // 返回
        return entryBill.getId();
    }

    /**
     * 入职单附件（business_type=201）：与档案 onboarding claim 同级权威。
     * <ul>
     *   <li>已有附件：仅允许 id（同业务归属）</li>
     *   <li>新附件：必须 claimToken；fileId/path 从权威 FileDO 派生，url 清空</li>
     *   <li>无 claim 不得写入可被全局 reserved 提升的身份</li>
     * </ul>
     */
    void saveEntryBillAttachments(Long billId, List<OnboardingAttachmentSaveReqVO> reqs) {
        if (reqs == null) {
            return;
        }
        if (reqs.size() > EmployeeServiceImpl.ONBOARDING_MAX_COUNT) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_LIMIT);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String businessType = HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL.getTypeCode();
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
                keep.setBusinessType(businessType);
                keep.setBusinessId(billId);
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
            OnboardingFileClaimDO claim = consumeEntryBillClaim(req.getClaimToken(), userId, billId);
            FileRespDTO file = fileAccessApi.getFile(claim.getFileId());
            if (file == null) {
                throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
            }
            validateEntryBillClaimFile(file);
            AttachmentSaveReqVO att = new AttachmentSaveReqVO();
            att.setBusinessType(businessType);
            att.setBusinessId(billId);
            att.setFileId(file.getId());
            att.setFileName(file.getName());
            att.setFilePath(file.getPath());
            att.setFileUrl("");
            att.setFileSize(file.getSize());
            att.setFileType(file.getType());
            att.setFileExtension(resolveExt(file.getName()));
            att.setSortOrder(req.getSortOrder());
            att.setRemark(req.getRemark());
            resolved.add(att);
        }
        attachmentService.saveAttachmentListInternal(businessType, billId, resolved);
    }

    /**
     * 原子消费 claim（purpose=hrm-onboarding，与档案入职资料同级）。
     * consumed_employee_id 复用字段存入职单 id。
     */
    OnboardingFileClaimDO consumeEntryBillClaim(String claimToken, Long userId, Long billId) {
        if (StrUtil.isBlank(claimToken) || userId == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int rows = onboardingFileClaimMapper.consumeIfOpen(
                claimToken, userId, OnboardingFileClaimDO.PURPOSE, billId, now);
        if (rows != 1) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        OnboardingFileClaimDO claim = onboardingFileClaimMapper.selectByClaimToken(claimToken);
        if (claim == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        return claim;
    }

    void validateEntryBillClaimFile(FileRespDTO file) {
        if (file.getSize() != null && file.getSize() > EmployeeServiceImpl.ONBOARDING_MAX_SIZE_BYTES) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_TOO_LARGE);
        }
        String ext = resolveExt(file.getName());
        if (StrUtil.isBlank(ext) || !EmployeeServiceImpl.ONBOARDING_ALLOWED_EXTENSIONS.contains(ext)) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_TYPE);
        }
        if (file.getPath() == null || !FilePrivateDirs.isPrivateDirectory(file.getPath())) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
    }

    private static String resolveExt(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    @Override
    public OnboardingFileClaimRespVO uploadEntryBillFile(org.springframework.web.multipart.MultipartFile file)
            throws Exception {
        // 与档案共用权威 claim 签发（私有目录 + 一次性 token + 上传者绑定）
        return employeeService.uploadOnboardingFile(file);
    }

    @Override
    public void downloadEntryBillAttachment(Long billId, Long attachmentId, HttpServletResponse response)
            throws Exception {
        validateEmployeeEntryBillExists(billId);
        AttachmentDO att = attachmentService.getAttachmentInternal(attachmentId);
        String typeCode = HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL.getTypeCode();
        if (att == null
                || !typeCode.equals(att.getBusinessType())
                || !billId.equals(att.getBusinessId())) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        Long fileId = att.getFileId();
        if (fileId == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        FileRespDTO meta = fileAccessApi.getFile(fileId);
        if (meta == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        byte[] content = fileAccessApi.getFileContent(fileId);
        if (content == null) {
            throw exception(EMPLOYEE_ROSTER_ATTACHMENT_INVALID);
        }
        String fileName = StrUtil.blankToDefault(meta.getName(), att.getFileName());
        response.setContentType("application/octet-stream");
        String encoded = java.net.URLEncoder.encode(fileName == null ? "file" : fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=UTF-8''" + encoded);
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    @Override
    public Long createEmployeeEntryBill(EmployeeEntryBillSaveReqVO createReqVO) {
        // 插入
        String billCode = BillCodeUtils.generateBillCode(SystemEnum.HRM, HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL);
        createReqVO.setBillCode(billCode);
        // 校验手机号与身份证号在员工档案中唯一
        validateMobileAndIdCardUnique(createReqVO);
        // 插入
        EmployeeEntryBillDO entryBill = BeanUtils.toBean(createReqVO, EmployeeEntryBillDO.class);
        employeeEntryBillMapper.insertOrUpdate(entryBill);

        // 返回
        return entryBill.getId();
    }

    @Override
    public void updateEmployeeEntryBill(EmployeeEntryBillSaveReqVO updateReqVO) {
        // 校验存在
        validateEmployeeEntryBillExists(updateReqVO.getId());
        // 更新
        EmployeeEntryBillDO updateObj = BeanUtils.toBean(updateReqVO, EmployeeEntryBillDO.class);
        employeeEntryBillMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmployeeEntryBill(Long id) {
        // 校验存在
        validateEmployeeEntryBillExists(id);
        
        // 删除明细信息
        entryBillWorkExperienceMapper.deleteByEntryBillId(id);
        entryBillEducationMapper.deleteByEntryBillId(id);
        entryBillFamilyMapper.deleteByEntryBillId(id);
        
        // 删除主表
        employeeEntryBillMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmployeeEntryBillListByIds(List<Long> ids) {
        // 删除明细信息
        for (Long id : ids) {
            entryBillWorkExperienceMapper.deleteByEntryBillId(id);
            entryBillEducationMapper.deleteByEntryBillId(id);
            entryBillFamilyMapper.deleteByEntryBillId(id);
        }
        
        // 删除主表
        employeeEntryBillMapper.deleteByIds(ids);
    }

    private void validateEmployeeEntryBillExists(Long id) {
        if (employeeEntryBillMapper.selectById(id) == null) {
            throw exception(EMPLOYEE_ENTRY_BILL_NOT_EXISTS);
        }
    }

    @Override
    public EmployeeEntryBillDO getEmployeeEntryBill(Long id) {
        return employeeEntryBillMapper.selectById(id);
    }

    @Override
    public EmployeeEntryBillRespVO getEmployeeEntryBillInfo(Long id) {
        EmployeeEntryBillDO entryBill = employeeEntryBillMapper.selectById(id);
        if (entryBill == null) {
            return null;
        }
        
        EmployeeEntryBillRespVO respVO = BeanUtils.toBean(entryBill, EmployeeEntryBillRespVO.class);
        
        // 附件：不暴露 fileId/path/url；仅 id + 元数据 + 鉴权 downloadPath
        List<AttachmentDO> atts = attachmentService.getAttachmentListByBusinessInternal(
                HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL.getTypeCode(), id);
        if (CollUtil.isNotEmpty(atts)) {
            respVO.setAttachments(atts.stream().map(a -> {
                AttachmentRespVO vo = new AttachmentRespVO();
                vo.setId(a.getId());
                vo.setBusinessType(a.getBusinessType());
                vo.setBusinessId(a.getBusinessId());
                vo.setFileName(a.getFileName());
                vo.setFileSize(a.getFileSize());
                vo.setFileType(a.getFileType());
                vo.setFileExtension(a.getFileExtension());
                vo.setUploadTime(a.getUploadTime());
                vo.setSortOrder(a.getSortOrder());
                vo.setRemark(a.getRemark());
                vo.setFilePath("");
                vo.setFileUrl("");
                vo.setDownloadPath("/hrm/employee-entry-bill/attachment/download?billId="
                        + id + "&attachmentId=" + a.getId());
                return vo;
            }).collect(Collectors.toList()));
        }
        
        // 获取明细信息：优先从员工档案明细表获取（如果已创建员工档案），否则从入职申请单明细表获取
        if (entryBill.getEmployeeId() != null) {
            // 如果已经创建了员工档案，从员工档案的明细表中获取明细信息
            List<EmployeeWorkExperienceDO> workExperiences = employeeWorkExperienceMapper.selectListByEmployeeId(entryBill.getEmployeeId());
            respVO.setWorkExperienceList(BeanUtils.toBean(workExperiences, EmployeeWorkExperienceVO.class));
            
            List<EmployeeEducationDO> educations = employeeEducationMapper.selectListByEmployeeId(entryBill.getEmployeeId());
            respVO.setEducationList(BeanUtils.toBean(educations, EmployeeEducationVO.class));
            
            List<EmployeeFamilyDO> families = employeeFamilyMapper.selectListByEmployeeId(entryBill.getEmployeeId());
            respVO.setFamilyList(BeanUtils.toBean(families, EmployeeFamilyVO.class));
        } else {
            // 如果还没有创建员工档案，从入职申请单明细表中获取明细信息
            List<EmployeeEntryBillWorkExperienceDO> workExperiences = entryBillWorkExperienceMapper.selectListByEntryBillId(id);
            respVO.setWorkExperienceList(BeanUtils.toBean(workExperiences, EmployeeWorkExperienceVO.class));
            
            List<EmployeeEntryBillEducationDO> educations = entryBillEducationMapper.selectListByEntryBillId(id);
            respVO.setEducationList(BeanUtils.toBean(educations, EmployeeEducationVO.class));
            
            List<EmployeeEntryBillFamilyDO> families = entryBillFamilyMapper.selectListByEntryBillId(id);
            respVO.setFamilyList(BeanUtils.toBean(families, EmployeeFamilyVO.class));
        }
        
        return respVO;
    }
    
    @Override
    public EmployeeEntryBillDO getEmployeeEntryBillByCode(String code) {
        return employeeEntryBillMapper.selectOne(new LambdaQueryWrapperX<EmployeeEntryBillDO>().eq(EmployeeEntryBillDO::getBillCode, code));
    }

    @Override
    public PageResult<EmployeeEntryBillDO> getEmployeeEntryBillPage(EmployeeEntryBillPageReqVO pageReqVO) {
        // 自动添加创建人过滤条件（当前登录用户）
        Long currentUserId = SecurityFrameworkUtils.getLoginUserId();
        if (currentUserId != null) {
            pageReqVO.setCreator(String.valueOf(currentUserId));
        }
        return employeeEntryBillMapper.selectPage(pageReqVO);
    }

    // ==================== FlowBillService 接口实现 ====================

    @Override
    public HrmBillTypeEnum getSupportedBillType() {
        return HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProcessStatus(String businessKey, Integer status) {
        Long id = Long.parseLong(businessKey);
        log.info("[updateProcessStatus] 更新员工入职申请单流程状态，id: {}, status: {}", id, status);

        // 校验员工入职申请单存在
        validateEmployeeEntryBillExists(id);

        // 更新流程状态
        EmployeeEntryBillDO updateObj = new EmployeeEntryBillDO();
        updateObj.setId(id);
        updateObj.setProcessStatus(status);
        
        // 如果审批通过，创建员工档案
        if (APPROVE.getStatus().equals(status)) {
            createEmployeeFromEntryBill(id);
        }
        
        employeeEntryBillMapper.updateById(updateObj);

        log.info("[updateProcessStatus] 员工入职申请单流程状态更新成功，id: {}, status: {}", id, status);
    }

    /**
     * 从入职申请单创建员工档案
     *
     * @param entryBillId 入职申请单ID
     */
    private void createEmployeeFromEntryBill(Long entryBillId) {
        // 获取最新的入职申请单信息（包含明细信息）
        EmployeeEntryBillRespVO entryBillRespVO = getEmployeeEntryBillInfo(entryBillId);
        if (entryBillRespVO == null) {
            log.error("[createEmployeeFromEntryBill] 入职申请单不存在，id: {}", entryBillId);
            return;
        }

        // 如果已经创建过员工档案，不再重复创建
        if (entryBillRespVO.getEmployeeId() != null) {
            log.warn("[createEmployeeFromEntryBill] 员工档案已创建，entryBillId: {}, employeeId: {}", entryBillId, entryBillRespVO.getEmployeeId());
            return;
        }
        AtomicReference<EmployeeEntryBillDO> entryBill = new AtomicReference<>(new EmployeeEntryBillDO());
        // 查询入职申请单头信息（包含租户等）
        TenantUtils.executeIgnore(()->{
            entryBill.set(employeeEntryBillMapper.selectById(entryBillId));
        });

        // 构建员工档案保存VO
        EmployeeSaveReqVO employeeSaveReqVO = new EmployeeSaveReqVO();
        // 基本信息
        employeeSaveReqVO.setName(entryBillRespVO.getName());
        employeeSaveReqVO.setSex(entryBillRespVO.getSex());
        employeeSaveReqVO.setBirthday(entryBillRespVO.getBirthday());
        employeeSaveReqVO.setIdCard(entryBillRespVO.getIdCard());
        employeeSaveReqVO.setMobile(entryBillRespVO.getMobile());
        employeeSaveReqVO.setEmail(entryBillRespVO.getEmail());
        employeeSaveReqVO.setNation(entryBillRespVO.getNation());
        employeeSaveReqVO.setNativePlace(entryBillRespVO.getNativePlace());
        employeeSaveReqVO.setHouseholdAddress(entryBillRespVO.getHouseholdAddress());
        employeeSaveReqVO.setCurrentAddress(entryBillRespVO.getCurrentAddress());
        employeeSaveReqVO.setEmergencyContact(entryBillRespVO.getEmergencyContact());
        employeeSaveReqVO.setEmergencyPhone(entryBillRespVO.getEmergencyPhone());
        employeeSaveReqVO.setAvatar(entryBillRespVO.getAvatar());
        employeeSaveReqVO.setPoliticalStatus(entryBillRespVO.getPoliticalStatus());
        employeeSaveReqVO.setMaritalStatus(entryBillRespVO.getMaritalStatus());

        // 工作信息
        employeeSaveReqVO.setEntryDate(entryBillRespVO.getEntryDate());
        employeeSaveReqVO.setDeptId(entryBillRespVO.getEmpDeptId());
        employeeSaveReqVO.setCompanyId(entryBillRespVO.getEmpCompanyId());
        employeeSaveReqVO.setCompanyName(entryBillRespVO.getEmpCompanyName());
        if (entryBillRespVO.getEmpCompanyId() != null && entryBillRespVO.getEmpDeptId() != null) {
            EmployeeEmploymentVO signed = new EmployeeEmploymentVO();
            signed.setCompanyDeptId(entryBillRespVO.getEmpCompanyId());
            signed.setCompanyName(entryBillRespVO.getEmpCompanyName());
            signed.setDeptId(entryBillRespVO.getEmpDeptId());
            signed.setSigned(true);
            employeeSaveReqVO.setEmploymentList(java.util.List.of(signed));
        }
        employeeSaveReqVO.setJobPost(entryBillRespVO.getJobPost());
        employeeSaveReqVO.setJobPosition(entryBillRespVO.getJobPosition());
        employeeSaveReqVO.setJobTitle(entryBillRespVO.getJobTitle());
        employeeSaveReqVO.setEmployeeStatus(entryBillRespVO.getEmployeeStatus());
        employeeSaveReqVO.setEducation(entryBillRespVO.getEducation());
        employeeSaveReqVO.setBankName(entryBillRespVO.getBankName());
        employeeSaveReqVO.setBankAccount(entryBillRespVO.getBankAccount());
        employeeSaveReqVO.setRemark(entryBillRespVO.getRemark());

        // 计算转正日期（如果试用期不为空）
        if (entryBill.get() != null) {
            if (entryBill.get().getProbationPeriod() != null && entryBill.get().getEntryDate() != null) {
                employeeSaveReqVO.setFormalDate(entryBill.get().getEntryDate().plusMonths(entryBill.get().getProbationPeriod()));
            } else if (entryBill.get().getExpectedFormalDate() != null) {
                employeeSaveReqVO.setFormalDate(entryBill.get().getExpectedFormalDate());
            }
        }

        // 设置明细信息（从RespVO中获取，如果已创建员工档案则从员工档案明细表获取，否则从入职申请单明细表获取）
        employeeSaveReqVO.setWorkExperienceList(entryBillRespVO.getWorkExperienceList());
        employeeSaveReqVO.setEducationList(entryBillRespVO.getEducationList());
        employeeSaveReqVO.setFamilyList(entryBillRespVO.getFamilyList());

        // 创建员工档案（包含明细信息）
        TenantUtils.execute(entryBill.get().getTenantId(), () -> {
//            TenantContextHolder.setTenantId(entryBill.get().getTenantId());

            Long employeeId = employeeService.createEmployeeArchive(employeeSaveReqVO);

            // 入职资料：从已落库 201 源行转档（权威 FileDO，允许历史 public path；不经私有目录门禁）
            attachmentService.transferReservedAttachmentsFromSource(
                    HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL.getTypeCode(),
                    entryBillId,
                    EmployeeServiceImpl.ONBOARDING_ATTACHMENT_BUSINESS_TYPE,
                    employeeId);

            // 更新入职申请单的employeeId
            EmployeeEntryBillDO updateObj = new EmployeeEntryBillDO();
            updateObj.setId(entryBillId);
            updateObj.setEmployeeId(employeeId);
            employeeEntryBillMapper.updateById(updateObj);
            log.info("[createEmployeeFromEntryBill] 从入职申请单创建员工档案成功，entryBillId: {}, employeeId: {}", entryBillId, employeeId);

        });



    }

    /**
     * 保存明细信息到临时表
     *
     * @param entryBillId 入职申请单ID
     * @param saveReqVO 保存请求VO
     */
    private void saveDetailLists(Long entryBillId, EmployeeEntryBillSaveReqVO saveReqVO) {
        // 删除旧的明细记录
        entryBillWorkExperienceMapper.deleteByEntryBillId(entryBillId);
        entryBillEducationMapper.deleteByEntryBillId(entryBillId);
        entryBillFamilyMapper.deleteByEntryBillId(entryBillId);

        // 保存工作经历
        if (CollUtil.isNotEmpty(saveReqVO.getWorkExperienceList())) {
            List<EmployeeEntryBillWorkExperienceDO> workExperiences = BeanUtils.toBean(saveReqVO.getWorkExperienceList(), EmployeeEntryBillWorkExperienceDO.class);
            workExperiences.forEach(item -> {
                item.setId(null);
                item.setBillId(entryBillId);
                entryBillWorkExperienceMapper.insert(item);
            });
        }

        // 保存教育经历
        if (CollUtil.isNotEmpty(saveReqVO.getEducationList())) {
            List<EmployeeEntryBillEducationDO> educations = BeanUtils.toBean(saveReqVO.getEducationList(), EmployeeEntryBillEducationDO.class);
            educations.forEach(item -> {
                item.setId(null);
                item.setBillId(entryBillId);
                entryBillEducationMapper.insert(item);
            });
        }

        // 保存家属信息
        if (CollUtil.isNotEmpty(saveReqVO.getFamilyList())) {
            List<EmployeeEntryBillFamilyDO> families = BeanUtils.toBean(saveReqVO.getFamilyList(), EmployeeEntryBillFamilyDO.class);
            families.forEach(item -> {
                item.setId(null);
                item.setBillId(entryBillId);
                entryBillFamilyMapper.insert(item);
            });
        }
    }

    /**
     * 校验手机号与身份证号在员工档案表唯一
     *
     * @param reqVO 入职申请单保存/提交请求
     */
    private void validateMobileAndIdCardUnique(EmployeeEntryBillSaveReqVO reqVO) {
        // 校验手机号
        if (StringUtils.isNotBlank(reqVO.getMobile())) {
            Long mobileCount = employeeMapper.selectCount(
                    new LambdaQueryWrapperX<EmployeeDO>()
                            .eq(EmployeeDO::getMobile, reqVO.getMobile())
            );
            if (mobileCount != null && mobileCount > 0) {
                throw exception(EMPLOYEE_ENTRY_BILL_MOBILE_EXISTS);
            }
        }
        // 校验身份证号
        if (StringUtils.isNotBlank(reqVO.getIdCard())) {
            Long idCardCount = employeeMapper.selectCount(
                    new LambdaQueryWrapperX<EmployeeDO>()
                            .eq(EmployeeDO::getIdCard, reqVO.getIdCard())
            );
            if (idCardCount != null && idCardCount > 0) {
                throw exception(EMPLOYEE_ENTRY_BILL_ID_CARD_EXISTS);
            }
        }
    }

}

