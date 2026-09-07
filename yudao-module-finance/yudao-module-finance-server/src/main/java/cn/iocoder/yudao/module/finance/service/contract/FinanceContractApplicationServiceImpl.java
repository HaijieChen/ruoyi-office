package cn.iocoder.yudao.module.finance.service.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.api.task.BpmFinanceAttachAccess;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceContractApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceBusinessStaffSupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceCurrencySupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

/**
 * 合同签约申请。含 CS-F1～F4 安全硬化。
 */
@Service
@Validated
public class FinanceContractApplicationServiceImpl implements FinanceContractApplicationService {

    public static final String PROCESS_KEY = "finance_contract_sign";

    public static final String TASK_SEAL = "taskSeal";
    public static final String TASK_ARCHIVE = "taskArchive";
    public static final String TASK_MAIL = "taskMail";

    /** 用印及之后节点：禁止申请人撤回（C17） */
    private static final Set<String> SEAL_OR_LATER_NODES = Set.of("seal", "archive", "mail");
    private static final Set<String> SEAL_OR_LATER_TASK_KEYS = Set.of(TASK_SEAL, TASK_ARCHIVE, TASK_MAIL);

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "采购合同", "销售合同", "付款业务合同", "租赁合同", "借款合同", "推广充值业务合同", "其他");

    private static final Set<String> SALES_LIKE_FILE_TYPES = Set.of("销售合同", "付款业务合同");
    private static final Set<String> PRE_PROCESS_REQUIRED_TYPES = Set.of("采购合同", "租赁合同");

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String DICT_PRODUCT_TYPE = "finance_product_type";

    private final FinanceContractApplicationMapper applicationMapper;
    private final FinanceContractApplicationNoRedisDAO applicationNoRedisDAO;
    private final BpmProcessInstanceApi processInstanceApi;
    private final FinanceCustomerCompanyService customerCompanyService;
    private final FinanceEntityCompanyResolver entityCompanyResolver;
    private final ObjectProvider<TaskService> taskServiceProvider;
    private final DictDataApi dictDataApi;
    private final ObjectProvider<FinanceApprovedSalesBusinessOrderService> autoBusinessOrderProvider;
    @Resource
    private FinanceBusinessStaffSupport businessStaffSupport;
    @Resource
    private FinanceProcessParticipantSupport processParticipantSupport;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(FinanceContractApplicationServiceImpl.class);

    @Resource
    private ObjectProvider<BpmFinanceAttachAccess> financeAttachAccessProvider;
    @Resource
    private FinanceBusinessOrderMapper businessOrderMapper;
    @Resource
    private FinancePaymentApplicationMapper paymentApplicationMapper;

    public FinanceContractApplicationServiceImpl(FinanceContractApplicationMapper applicationMapper,
                                                 FinanceContractApplicationNoRedisDAO applicationNoRedisDAO,
                                                 // EXP-87 F4：必须注入带 identity interceptor 的 Finance 专用 BPM 客户端
                                                 FinanceBpmProcessInstanceApi processInstanceApi,
                                                 FinanceCustomerCompanyService customerCompanyService,
                                                 FinanceEntityCompanyResolver entityCompanyResolver,
                                                 ObjectProvider<TaskService> taskServiceProvider,
                                                 DictDataApi dictDataApi,
                                                 ObjectProvider<FinanceApprovedSalesBusinessOrderService> autoBusinessOrderProvider) {
        this.applicationMapper = applicationMapper;
        this.applicationNoRedisDAO = applicationNoRedisDAO;
        this.processInstanceApi = processInstanceApi;
        this.customerCompanyService = customerCompanyService;
        this.entityCompanyResolver = entityCompanyResolver;
        this.taskServiceProvider = taskServiceProvider;
        this.dictDataApi = dictDataApi;
        this.autoBusinessOrderProvider = autoBusinessOrderProvider;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStart(FinanceContractApplicationCreateAndStartReqVO reqVO, Long applicantUserId) {
        validateBusinessFields(reqVO);
        FinanceCustomerCompanyDO counterparty = resolveCounterparty(reqVO.getCounterpartyCompanyId());

        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinanceContractApplicationDO application = buildFromReq(reqVO, applicantUserId, counterparty)
                .applicationNo(applicationNo)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(Boolean.FALSE)
                .build();
        applicationMapper.insert(application);

        String processInstanceId = startProcess(applicantUserId, application.getId(), application, reqVO);
        FinanceContractApplicationDO processUpdate = new FinanceContractApplicationDO();
        processUpdate.setId(application.getId());
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FinanceContractApplicationCreateAndStartReqVO reqVO) {
        FinanceContractApplicationDO current = getApplication(id);
        if (FinanceContractApprovalStatusEnum.PENDING.getStatus().equals(current.getApprovalStatus())
                && !Boolean.TRUE.equals(current.getVoided())) {
            throw exception(CONTRACT_APPLICATION_STATUS_INVALID);
        }
        validateBusinessFields(reqVO);
        FinanceCustomerCompanyDO counterparty = resolveCounterparty(reqVO.getCounterpartyCompanyId());
        FinanceContractApplicationDO built = buildFromReq(reqVO, current.getApplicantUserId(), counterparty).build();
        built.setId(id);
        built.setApplicationNo(current.getApplicationNo());
        built.setApprovalStatus(current.getApprovalStatus());
        built.setProcessInstanceId(current.getProcessInstanceId());
        built.setVoided(current.getVoided());
        built.setCurrentNodeKey(current.getCurrentNodeKey());
        built.setCurrentNodeName(current.getCurrentNodeName());
        built.setSealFileUrl(current.getSealFileUrl());
        built.setActualSealerUserId(current.getActualSealerUserId());
        built.setArchivedAt(current.getArchivedAt());
        built.setArchiveFileUrls(current.getArchiveFileUrls());
        built.setMailTrackingNo(current.getMailTrackingNo());
        applicationMapper.updateById(built);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long id, FinanceContractApplicationResubmitReqVO reqVO, Long userId) {
        FinanceContractApplicationDO application = getApplication(id);
        assertOwner(application, userId);
        if (!FinanceContractApprovalStatusEnum.REJECTED.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_STATUS_INVALID);
        }
        validateBusinessFields(reqVO);
        FinanceCustomerCompanyDO counterparty = resolveCounterparty(reqVO.getCounterpartyCompanyId());
        FinanceEntityCompanyResolver.ResolvedCompany entity = resolveEntityCompany(reqVO);
        String currency = resolveCurrency(reqVO);

        // CS-R3：REJECTED→PENDING 条件更新（单胜者）；0 行则不 startProcess，避免双实例
        int claimed = applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .eq("applicant_user_id", userId)
                .eq("approval_status", FinanceContractApprovalStatusEnum.REJECTED.getStatus())
                .and(w -> w.eq("voided", Boolean.FALSE).or().isNull("voided"))
                .set("approval_status", FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .set("voided", Boolean.FALSE)
                .set("current_node_key", null)
                .set("current_node_name", null)
                .set("counterparty_company_id", counterparty.getId())
                .set("counterparty_name", counterparty.getName())
                .set("amount_na", Boolean.TRUE.equals(reqVO.getAmountNa()))
                .set("contract_amount", Boolean.TRUE.equals(reqVO.getAmountNa()) ? null : reqVO.getContractAmount())
                .set("currency", currency)
                .set("entity_company_dept_id", entity.deptId())
                .set("entity_company_name", entity.name())
                .set("sign_company", entity.name())
                .set("file_name", reqVO.getFileName())
                .set("file_type", reqVO.getFileType())
                .set("product_type", reqVO.getProductType())
                .set("rebate_ratio", reqVO.getRebateRatio())
                .set("settlement_method", reqVO.getSettlementMethod())
                .set("copy_count", reqVO.getCopyCount())
                .set("seal_types", reqVO.getSealTypes())
                .set("need_mail", Boolean.TRUE.equals(reqVO.getNeedMail()))
                .set("mail_address", reqVO.getMailAddress())
                .set("pre_process_ref", reqVO.getPreProcessRef())
                .set("start_date", reqVO.getStartDate())
                .set("end_date", reqVO.getEndDate())
                .set("draft_file_url", reqVO.getDraftFileUrl())
                .set("remark", reqVO.getRemark())
                .set("applicant_dept_id", reqVO.getApplicantDeptId())
                .set("seal_file_url", null)
                .set("actual_sealer_user_id", null)
                .set("archived_at", null)
                .set("mail_tracking_no", null));
        if (claimed == 0) {
            throw exception(CONTRACT_APPLICATION_STATUS_INVALID);
        }

        FinanceContractApplicationDO refreshed = getApplication(id);
        String processInstanceId = startProcess(userId, id, refreshed, reqVO);
        FinanceContractApplicationDO processUpdate = new FinanceContractApplicationDO();
        processUpdate.setId(id);
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long userId) {
        FinanceContractApplicationDO application = getApplication(id);
        assertOwner(application, userId);
        if (!FinanceContractApprovalStatusEnum.PENDING.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_STATUS_INVALID);
        }
        // 台账节点快路径（最终以 BPM 侧 active task 禁止集为准，CS-F10）
        if (isSealOrLater(application.getCurrentNodeKey())) {
            throw exception(CONTRACT_APPLICATION_CANCEL_NOT_ALLOWED);
        }
        String processInstanceId = application.getProcessInstanceId();
        if (StrUtil.isNotBlank(processInstanceId)) {
            // 禁止集：用印/归档/邮寄 active task 存在则拒绝取消，且不落 CANCELLED
            processInstanceApi.cancelProcessInstanceByStartUser(
                    userId, processInstanceId, "申请人撤回合同签约",
                    SEAL_OR_LATER_TASK_KEYS).checkError();
        }
        // 同步落账（StatusListener 为辅路径，可能异步）；绑定当前 processInstanceId
        onApprovalOutcome(id, FinanceContractApprovalStatusEnum.CANCELLED.getStatus(), processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceContractApplicationDO application = getApplication(id);
        if (FinanceContractApprovalStatusEnum.PENDING.getStatus().equals(application.getApprovalStatus())
                && !Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_DELETE_NOT_ALLOWED);
        }
        Long boCount = businessOrderMapper.selectCount(new LambdaQueryWrapperX<FinanceBusinessOrderDO>()
                .eq(FinanceBusinessOrderDO::getContractApplicationId, id));
        if (boCount != null && boCount > 0) {
            throw exception(CONTRACT_APPLICATION_IN_USE);
        }
        Long payCount = paymentApplicationMapper.selectCount(new LambdaQueryWrapperX<FinancePaymentApplicationDO>()
                .and(w -> w.eq(FinancePaymentApplicationDO::getLeaseContractApplicationId, id)
                        .or()
                        .eq(FinancePaymentApplicationDO::getRelatedContractApplicationId, id)));
        if (payCount != null && payCount > 0) {
            throw exception(CONTRACT_APPLICATION_IN_USE);
        }
        applicationMapper.deleteById(id);
    }

    @Override
    public cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO deleteList(
            List<Long> ids) {
        cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO resp =
                new cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO();
        if (ids == null) {
            return resp;
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            try {
                delete(id);
                resp.setDeleted(resp.getDeleted() + 1);
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
                resp.getErrors().add(id + "：" + ex.getMessage());
            }
        }
        return resp;
    }

    @Override
    public cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO deleteByQuery(
            FinanceContractApplicationPageReqVO reqVO) {
        List<FinanceContractApplicationDO> rows = applicationMapper.selectListByQuery(reqVO);
        return deleteList(rows.stream().map(FinanceContractApplicationDO::getId).toList());
    }

    @Override
    public List<FinanceContractApplicationDO> listForExport(FinanceContractApplicationPageReqVO reqVO) {
        return applicationMapper.selectListByQuery(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalOutcome(Long appId, String outcome, String processInstanceId) {
        if (appId == null || StrUtil.isBlank(outcome)) {
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        String normalized = outcome.trim().toUpperCase();
        if (!isTerminalOutcome(normalized)) {
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }

        FinanceContractApplicationDO application = getApplication(appId);

        // CS-F7：旧流程实例的延迟终态不得改写重提后的台账
        if (StrUtil.isNotBlank(processInstanceId)
                && StrUtil.isNotBlank(application.getProcessInstanceId())
                && !Objects.equals(processInstanceId, application.getProcessInstanceId())) {
            return;
        }

        String current = application.getApprovalStatus();
        if (Objects.equals(current, normalized)) {
            if (FinanceContractApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)
                    && !Boolean.TRUE.equals(application.getVoided())) {
                applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                        .eq("id", appId)
                        .eq(StrUtil.isNotBlank(processInstanceId), "process_instance_id", processInstanceId)
                        .set("voided", Boolean.TRUE));
            }
            return;
        }
        if (Boolean.TRUE.equals(application.getVoided())) {
            // 旧实例打到已 void 台账：若 process 不匹配已在上方 return；匹配则非法
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        if (FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(current)
                || FinanceContractApprovalStatusEnum.REJECTED.getStatus().equals(current)
                || FinanceContractApprovalStatusEnum.CANCELLED.getStatus().equals(current)) {
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        if (!FinanceContractApprovalStatusEnum.PENDING.getStatus().equals(current)) {
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }

        if (FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(normalized)) {
            assertApprovedEvidence(application);
        }

        UpdateWrapper<FinanceContractApplicationDO> uw = new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", appId)
                .eq("approval_status", FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .and(w -> w.eq("voided", Boolean.FALSE).or().isNull("voided"))
                .set("approval_status", normalized)
                .set("current_node_key", null)
                .set("current_node_name", null);
        if (StrUtil.isNotBlank(processInstanceId)) {
            uw.eq("process_instance_id", processInstanceId);
        }
        if (FinanceContractApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)) {
            uw.set("voided", Boolean.TRUE);
        }
        int rows = applicationMapper.update(null, uw);
        if (rows > 0 && FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(normalized)) {
            tryCreateSalesBusinessOrder(appId);
        }
        if (rows == 0) {
            FinanceContractApplicationDO again = getApplication(appId);
            if (Objects.equals(again.getApprovalStatus(), normalized)) {
                return;
            }
            if (StrUtil.isNotBlank(processInstanceId)
                    && StrUtil.isNotBlank(again.getProcessInstanceId())
                    && !Objects.equals(processInstanceId, again.getProcessInstanceId())) {
                return;
            }
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
    }

    private void tryCreateSalesBusinessOrder(Long appId) {
        FinanceApprovedSalesBusinessOrderService autoBo = autoBusinessOrderProvider.getIfAvailable();
        if (autoBo == null) {
            return;
        }
        try {
            autoBo.createIfEligible(getApplication(appId));
        } catch (Exception ex) {
            log.warn("[auto-bo] contract {} generate business order failed: {}", appId, ex.toString());
        }
    }

    @Override
    @DataPermission(enable = false)
    public FinanceContractApplicationDO getApplication(Long id) {
        FinanceContractApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            throw exception(CONTRACT_APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    @Override
    // Detail authorization follows process participation; list scope must not hide the bill first.
    @DataPermission(enable = false)
    public FinanceContractApplicationDO getApplication(Long id, Long userId, boolean manageAll) {
        FinanceContractApplicationDO application = getApplication(id);
        if (manageAll || processParticipantSupport.canReadBill(
                userId, application.getApplicantUserId(), application.getProcessInstanceId())) {
            return application;
        }
        if (canReadViaAttachingPayment(userId, id)) {
            return application;
        }
        throw exception(CONTRACT_APPLICATION_ACCESS_DENIED);
    }

    private boolean canReadViaAttachingPayment(Long userId, Long contractId) {
        BpmFinanceAttachAccess access = financeAttachAccessProvider == null
                ? null : financeAttachAccessProvider.getIfAvailable();
        return access != null && access.canReadContractViaBill(userId, contractId);
    }

    @Override
    // Detail authorization follows process participation; list scope must not hide the bill first.
    @DataPermission(enable = false)
    public boolean canAccessDetail(Long id, Long userId) {
        if (id == null || userId == null) {
            return false;
        }
        FinanceContractApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            return false;
        }
        if (processParticipantSupport.canReadBill(
                userId, application.getApplicantUserId(), application.getProcessInstanceId())) {
            return true;
        }
        return canReadViaAttachingPayment(userId, id);
    }

    /**
     * 用户是否为该申请绑定 process 上任意 active 任务的候选人或办理人。
     * 仅用于详情读权；不扩大列表（C29）。
     */
    private boolean isActiveTaskCandidateOrAssignee(FinanceContractApplicationDO application, Long userId) {
        if (userId == null || StrUtil.isBlank(application.getProcessInstanceId())) {
            return false;
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            return false;
        }
        long count = taskService.createTaskQuery()
                .processInstanceId(application.getProcessInstanceId())
                .taskCandidateOrAssigned(String.valueOf(userId))
                .count();
        return count > 0;
    }

    @Override
    public PageResult<FinanceContractApplicationDO> getApplicationPage(FinanceContractApplicationPageReqVO pageReqVO) {
        return applicationMapper.selectPage(pageReqVO);
    }

    @Override
    public PageResult<FinanceContractApplicationDO> getApplicationPage(FinanceContractApplicationPageReqVO pageReqVO,
                                                                       Long userId, boolean manageAll) {
        return applicationMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentNode(Long appId, String nodeKey, String nodeName) {
        getApplication(appId);
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", appId)
                .set("current_node_key", nodeKey)
                .set("current_node_name", nodeName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordSeal(Long id, String taskId, String sealFileUrl, Long userId) {
        FinanceContractApplicationDO application = getApplication(id);
        requirePending(application);
        if (StrUtil.isBlank(sealFileUrl)) {
            throw exception(CONTRACT_APPLICATION_SEAL_FILE_REQUIRED);
        }
        Task task = requireTask(taskId, application, TASK_SEAL, userId);
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .set("seal_file_url", sealFileUrl.trim())
                .set("actual_sealer_user_id", userId));
        completeTask(task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordArchive(Long id, Long userId) {
        FinanceContractApplicationDO application = getApplication(id, userId, true);
        if (Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_STATUS_INVALID);
        }
        if (!FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(application.getApprovalStatus())) {
            throw exception(CONTRACT_APPLICATION_EXEC_NOT_ALLOWED);
        }
        if (application.getArchivedAt() != null) {
            return;
        }
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .isNull("archived_at")
                .set("archived_at", LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordArchive(Long id, List<String> archiveFileUrls, Long userId) {
        FinanceContractApplicationDO application = getApplication(id);
        if (!FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())
                || application.getArchivedAt() != null) {
            throw exception(CONTRACT_APPLICATION_EXEC_NOT_ALLOWED);
        }
        String json = toArchiveFileJson(archiveFileUrls);
        if (StrUtil.isBlank(json) || "[]".equals(json)) {
            throw exception(CONTRACT_APPLICATION_ARCHIVE_FILE_REQUIRED);
        }
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .set("archive_file_urls", json)
                .set("archived_at", LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordMail(Long id, String taskId, String mailTrackingNo, Long userId) {
        FinanceContractApplicationDO application = getApplication(id);
        requirePending(application);
        if (!Boolean.TRUE.equals(application.getNeedMail())) {
            throw exception(CONTRACT_APPLICATION_EXEC_NOT_ALLOWED);
        }
        if (StrUtil.isBlank(mailTrackingNo)) {
            throw exception(CONTRACT_APPLICATION_MAIL_TRACKING_REQUIRED);
        }
        Task task = requireTask(taskId, application, TASK_MAIL, userId);
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .set("mail_tracking_no", mailTrackingNo.trim()));
        completeTask(task.getId());
    }

    @Override
    public void assertExecutionEvidenceForComplete(Long appId, String taskDefinitionKey) {
        FinanceContractApplicationDO application = getApplication(appId);
        requirePending(application);
        if (TASK_SEAL.equals(taskDefinitionKey)) {
            if (StrUtil.isBlank(application.getSealFileUrl())) {
                throw exception(CONTRACT_APPLICATION_SEAL_FILE_REQUIRED);
            }
            return;
        }
        if (TASK_ARCHIVE.equals(taskDefinitionKey)) {
            if (StrUtil.isBlank(application.getSealFileUrl()) || application.getArchivedAt() == null) {
                throw exception(CONTRACT_APPLICATION_APPROVED_EVIDENCE_INCOMPLETE);
            }
            return;
        }
        if (TASK_MAIL.equals(taskDefinitionKey)) {
            if (Boolean.TRUE.equals(application.getNeedMail()) && StrUtil.isBlank(application.getMailTrackingNo())) {
                throw exception(CONTRACT_APPLICATION_MAIL_TRACKING_REQUIRED);
            }
        }
    }

    @Override
    public boolean resolveNeedMailFromLedger(Long appId) {
        FinanceContractApplicationDO application = getApplication(appId);
        return Boolean.TRUE.equals(application.getNeedMail());
    }

    @Override
    public List<FinanceContractApplicationDO> listSelectableForBo(Long applicantUserId) {
        // EXP-70：可选合同须产品类型非空，否则无法派生商务单快照
        Set<String> sharedIds = processInstanceApi.listSharedInstanceIds(applicantUserId).getCheckedData();
        return applicationMapper.selectList(new LambdaQueryWrapperX<FinanceContractApplicationDO>()
                .eq(FinanceContractApplicationDO::getApprovalStatus,
                        FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .and(w -> {
                    w.eq(FinanceContractApplicationDO::getApplicantUserId, applicantUserId);
                    if (sharedIds != null && !sharedIds.isEmpty()) {
                        w.or().in(FinanceContractApplicationDO::getProcessInstanceId, sharedIds);
                    }
                })
                .eq(FinanceContractApplicationDO::getVoided, Boolean.FALSE)
                .isNotNull(FinanceContractApplicationDO::getProductType)
                .ne(FinanceContractApplicationDO::getProductType, "")
                .apply("TRIM(product_type) <> ''")
                .orderByDesc(FinanceContractApplicationDO::getId));
    }

    @Override
    public List<FinanceContractApplicationDO> listSelectableForBusinessPayment(Long applicantUserId) {
        Set<String> sharedIds = processInstanceApi.listSharedInstanceIds(applicantUserId).getCheckedData();
        return applicationMapper.selectList(new LambdaQueryWrapperX<FinanceContractApplicationDO>()
                .eq(FinanceContractApplicationDO::getApprovalStatus,
                        FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .eq(FinanceContractApplicationDO::getFileType, "付款业务合同")
                .and(w -> {
                    w.eq(FinanceContractApplicationDO::getApplicantUserId, applicantUserId);
                    if (sharedIds != null && !sharedIds.isEmpty()) {
                        w.or().in(FinanceContractApplicationDO::getProcessInstanceId, sharedIds);
                    }
                })
                .eq(FinanceContractApplicationDO::getVoided, Boolean.FALSE)
                .orderByDesc(FinanceContractApplicationDO::getId));
    }

    private void assertApprovedEvidence(FinanceContractApplicationDO application) {
        if (StrUtil.isBlank(application.getSealFileUrl())) {
            throw exception(CONTRACT_APPLICATION_APPROVED_EVIDENCE_INCOMPLETE);
        }
        if (Boolean.TRUE.equals(application.getNeedMail()) && StrUtil.isBlank(application.getMailTrackingNo())) {
            throw exception(CONTRACT_APPLICATION_APPROVED_EVIDENCE_INCOMPLETE);
        }
    }

    private static void assertOwner(FinanceContractApplicationDO application, Long userId) {
        if (!Objects.equals(application.getApplicantUserId(), userId)) {
            throw exception(CONTRACT_APPLICATION_ACCESS_DENIED);
        }
    }

    private Task requireTask(String taskId, FinanceContractApplicationDO application,
                             String expectedTaskKey, Long userId) {
        if (StrUtil.isBlank(taskId)) {
            throw exception(CONTRACT_APPLICATION_TASK_INVALID);
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            throw exception(CONTRACT_APPLICATION_TASK_INVALID);
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw exception(CONTRACT_APPLICATION_TASK_INVALID);
        }
        if (!expectedTaskKey.equals(task.getTaskDefinitionKey())) {
            throw exception(CONTRACT_APPLICATION_TASK_INVALID);
        }
        if (StrUtil.isBlank(application.getProcessInstanceId())
                || !Objects.equals(application.getProcessInstanceId(), task.getProcessInstanceId())) {
            throw exception(CONTRACT_APPLICATION_TASK_INVALID);
        }
        // 候选人/办理人：须对用户可见
        long visible = taskService.createTaskQuery()
                .taskId(taskId)
                .taskCandidateOrAssigned(String.valueOf(userId))
                .count();
        if (visible <= 0) {
            throw exception(CONTRACT_APPLICATION_TASK_INVALID);
        }
        return task;
    }

    private void completeTask(String taskId) {
        completeTask(taskId, null);
    }

    private void completeTask(String taskId, Map<String, Object> variables) {
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            throw exception(CONTRACT_APPLICATION_TASK_INVALID);
        }
        if (variables == null || variables.isEmpty()) {
            taskService.complete(taskId);
        } else {
            taskService.complete(taskId, variables);
        }
    }

    private boolean hasActiveSealOrLaterTask(FinanceContractApplicationDO application) {
        if (StrUtil.isBlank(application.getProcessInstanceId())) {
            return false;
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            return false;
        }
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(application.getProcessInstanceId())
                .list();
        for (Task task : tasks) {
            if (SEAL_OR_LATER_TASK_KEYS.contains(task.getTaskDefinitionKey())) {
                return true;
            }
        }
        return false;
    }

    private void validateBusinessFields(FinanceContractApplicationCreateAndStartReqVO reqVO) {
        if (!ALLOWED_FILE_TYPES.contains(reqVO.getFileType())) {
            throw exception(CONTRACT_APPLICATION_FILE_TYPE_INVALID);
        }
        if (SALES_LIKE_FILE_TYPES.contains(reqVO.getFileType())) {
            if (StrUtil.isBlank(reqVO.getProductType())
                    || StrUtil.isBlank(reqVO.getRebateRatio())
                    || StrUtil.isBlank(reqVO.getSettlementMethod())) {
                throw exception(CONTRACT_APPLICATION_FIELD_REQUIRED);
            }
            validateProductType(reqVO.getProductType().trim());
        }
        if (PRE_PROCESS_REQUIRED_TYPES.contains(reqVO.getFileType()) && StrUtil.isBlank(reqVO.getPreProcessRef())) {
            throw exception(CONTRACT_APPLICATION_PRE_PROCESS_REQUIRED);
        }
        boolean amountNa = Boolean.TRUE.equals(reqVO.getAmountNa());
        if (!amountNa) {
            if (reqVO.getContractAmount() == null || reqVO.getContractAmount().compareTo(ZERO) <= 0) {
                throw exception(CONTRACT_APPLICATION_AMOUNT_INVALID);
            }
        }
        if (Boolean.TRUE.equals(reqVO.getNeedMail()) && StrUtil.isBlank(reqVO.getMailAddress())) {
            throw exception(CONTRACT_APPLICATION_MAIL_ADDRESS_REQUIRED);
        }
        if (reqVO.getCopyCount() == null || reqVO.getCopyCount() <= 0) {
            throw exception(CONTRACT_APPLICATION_FIELD_REQUIRED);
        }
    }

    private void validateProductType(String productType) {
        if (dictDataApi == null) {
            throw exception(CONTRACT_APPLICATION_PRODUCT_TYPE_INVALID);
        }
        try {
            dictDataApi.validateDictDataList(DICT_PRODUCT_TYPE, Collections.singletonList(productType)).checkError();
        } catch (Exception ex) {
            throw exception(CONTRACT_APPLICATION_PRODUCT_TYPE_INVALID);
        }
    }

    private FinanceCustomerCompanyDO resolveCounterparty(Long companyId) {
        if (companyId == null) {
            throw exception(CONTRACT_APPLICATION_COUNTERPARTY_REQUIRED);
        }
        return customerCompanyService.getEnabledCustomerCompany(companyId);
    }

    private FinanceContractApplicationDO.FinanceContractApplicationDOBuilder buildFromReq(
            FinanceContractApplicationCreateAndStartReqVO reqVO,
            Long applicantUserId,
            FinanceCustomerCompanyDO counterparty) {
        boolean amountNa = Boolean.TRUE.equals(reqVO.getAmountNa());
        FinanceEntityCompanyResolver.ResolvedCompany entity = resolveEntityCompany(reqVO);
        String currency = resolveCurrency(reqVO);
        return FinanceContractApplicationDO.builder()
                .applicantUserId(applicantUserId)
                .businessStaffUserId(businessStaffSupport.resolve(applicantUserId, reqVO.getBusinessStaffUserId()))
                .applicantDeptId(reqVO.getApplicantDeptId())
                .counterpartyCompanyId(counterparty.getId())
                .counterpartyName(counterparty.getName())
                .amountNa(amountNa)
                .contractAmount(amountNa ? null : reqVO.getContractAmount())
                .currency(currency)
                .entityCompanyDeptId(entity.deptId())
                .entityCompanyName(entity.name())
                // 兼容旧读路径：signCompany 与名称快照双写
                .signCompany(entity.name())
                .fileName(reqVO.getFileName())
                .fileType(reqVO.getFileType())
                .productType(SALES_LIKE_FILE_TYPES.contains(reqVO.getFileType()) ? reqVO.getProductType() : null)
                .rebateRatio(SALES_LIKE_FILE_TYPES.contains(reqVO.getFileType()) ? reqVO.getRebateRatio() : null)
                .settlementMethod(SALES_LIKE_FILE_TYPES.contains(reqVO.getFileType()) ? reqVO.getSettlementMethod() : null)
                .copyCount(reqVO.getCopyCount())
                .sealTypes(reqVO.getSealTypes())
                .needMail(Boolean.TRUE.equals(reqVO.getNeedMail()))
                .mailAddress(reqVO.getMailAddress())
                .preProcessRef(reqVO.getPreProcessRef())
                .startDate(reqVO.getStartDate())
                .endDate(reqVO.getEndDate())
                .draftFileUrl(reqVO.getDraftFileUrl())
                .remark(reqVO.getRemark());
    }

    private FinanceEntityCompanyResolver.ResolvedCompany resolveEntityCompany(
            FinanceContractApplicationCreateAndStartReqVO reqVO) {
        return entityCompanyResolver.requireByDeptId(reqVO.getEntityCompanyDeptId());
    }

    /**
     * 有金额时币种必填；金额不适用时允许空币种。
     */
    private static String resolveCurrency(FinanceContractApplicationCreateAndStartReqVO reqVO) {
        if (Boolean.TRUE.equals(reqVO.getAmountNa())) {
            return FinanceCurrencySupport.normalizeOptional(reqVO.getCurrency());
        }
        return FinanceCurrencySupport.requireSupported(reqVO.getCurrency());
    }

    private String startProcess(Long userId, Long appId, FinanceContractApplicationDO application,
                                FinanceContractApplicationCreateAndStartReqVO reqVO) {
        Map<String, Object> variables = buildProcessVariables(application);
        if (reqVO.getStartCompanyDeptId() != null) {
            variables.put("startCompanyDeptId", reqVO.getStartCompanyDeptId());
        }
        if (reqVO.getStartDeptId() != null) {
            variables.put("startDeptId", reqVO.getStartDeptId());
        }
        return processInstanceApi.createProcessInstance(userId,
                        new BpmProcessInstanceCreateReqDTO()
                                .setProcessDefinitionKey(PROCESS_KEY)
                                .setBusinessKey(String.valueOf(appId))
                                .setVariables(variables)
                                .setStartUserSelectAssignees(reqVO.getStartUserSelectAssignees()))
                .getCheckedData();
    }

    private Map<String, Object> buildProcessVariables(FinanceContractApplicationDO application) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("contractApplicationId", application.getId());
        variables.put("applicationNo", application.getApplicationNo());
        variables.put("billCode", application.getApplicationNo());
        variables.put("applicantUserId", application.getApplicantUserId());
        variables.put("signCompany", application.getSignCompany());
        if (application.getEntityCompanyDeptId() != null) {
            variables.put("companyId", application.getEntityCompanyDeptId());
            variables.put("entityCompanyDeptId", application.getEntityCompanyDeptId());
        }
        if (StrUtil.isNotBlank(application.getEntityCompanyName())) {
            variables.put("companyName", application.getEntityCompanyName());
            variables.put("entityCompanyName", application.getEntityCompanyName());
        }
        if (StrUtil.isNotBlank(application.getCurrency())) {
            variables.put("currency", application.getCurrency());
        }
        variables.put("fileName", application.getFileName());
        variables.put("fileType", application.getFileType());
        variables.put("counterpartyName", application.getCounterpartyName());
        variables.put("contractAmount", application.getContractAmount());
        variables.put("needMail", Boolean.TRUE.equals(application.getNeedMail()));
        variables.put("approvalStatus", application.getApprovalStatus());
        return variables;
    }

    private static boolean isTerminalOutcome(String outcome) {
        return FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(outcome)
                || FinanceContractApprovalStatusEnum.REJECTED.getStatus().equals(outcome)
                || FinanceContractApprovalStatusEnum.CANCELLED.getStatus().equals(outcome);
    }

    private static boolean isSealOrLater(String nodeKey) {
        return nodeKey != null && SEAL_OR_LATER_NODES.contains(nodeKey);
    }

    private static void requirePending(FinanceContractApplicationDO application) {
        if (!FinanceContractApprovalStatusEnum.PENDING.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_EXEC_NOT_ALLOWED);
        }
    }

    private static String toArchiveFileJson(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "[]";
        }
        List<String> cleaned = new java.util.ArrayList<>();
        for (String url : urls) {
            if (StrUtil.isNotBlank(url)) {
                cleaned.add(url.trim());
            }
        }
        if (cleaned.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(cleaned.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }
}
