package cn.iocoder.yudao.module.finance.service.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceContractApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

/**
 * 合同签约申请。CS-T2：createAndStart / resubmit / cancel / onApprovalOutcome。
 */
@Service
@Validated
public class FinanceContractApplicationServiceImpl implements FinanceContractApplicationService {

    public static final String PROCESS_KEY = "finance_contract_sign";

    /** 用印及之后节点：禁止申请人撤回（C17） */
    private static final Set<String> SEAL_OR_LATER_NODES = Set.of("seal", "archive", "mail");

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "采购合同", "销售合同", "租赁合同", "借款合同", "推广充值业务合同");

    private static final Set<String> PRE_PROCESS_REQUIRED_TYPES = Set.of("采购合同", "租赁合同");

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceContractApplicationMapper applicationMapper;
    private final FinanceContractApplicationNoRedisDAO applicationNoRedisDAO;
    private final BpmProcessInstanceApi processInstanceApi;
    private final FinanceCustomerCompanyService customerCompanyService;

    public FinanceContractApplicationServiceImpl(FinanceContractApplicationMapper applicationMapper,
                                                 FinanceContractApplicationNoRedisDAO applicationNoRedisDAO,
                                                 BpmProcessInstanceApi processInstanceApi,
                                                 FinanceCustomerCompanyService customerCompanyService) {
        this.applicationMapper = applicationMapper;
        this.applicationNoRedisDAO = applicationNoRedisDAO;
        this.processInstanceApi = processInstanceApi;
        this.customerCompanyService = customerCompanyService;
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
    public void resubmit(Long id, FinanceContractApplicationResubmitReqVO reqVO, Long userId) {
        FinanceContractApplicationDO application = getApplication(id);
        if (!FinanceContractApprovalStatusEnum.REJECTED.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_STATUS_INVALID);
        }
        validateBusinessFields(reqVO);
        FinanceCustomerCompanyDO counterparty = resolveCounterparty(reqVO.getCounterpartyCompanyId());

        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .set("approval_status", FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .set("voided", Boolean.FALSE)
                .set("current_node_key", null)
                .set("current_node_name", null)
                .set("counterparty_company_id", counterparty.getId())
                .set("counterparty_name", counterparty.getName())
                .set("amount_na", Boolean.TRUE.equals(reqVO.getAmountNa()))
                .set("contract_amount", Boolean.TRUE.equals(reqVO.getAmountNa()) ? null : reqVO.getContractAmount())
                .set("sign_company", reqVO.getSignCompany())
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
        if (!FinanceContractApprovalStatusEnum.PENDING.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_STATUS_INVALID);
        }
        if (isSealOrLater(application.getCurrentNodeKey())) {
            throw exception(CONTRACT_APPLICATION_CANCEL_NOT_ALLOWED);
        }
        onApprovalOutcome(id, FinanceContractApprovalStatusEnum.CANCELLED.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalOutcome(Long appId, String outcome) {
        if (appId == null || StrUtil.isBlank(outcome)) {
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        String normalized = outcome.trim().toUpperCase();
        if (!isTerminalOutcome(normalized)) {
            throw exception(CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }

        FinanceContractApplicationDO application = getApplication(appId);
        String current = application.getApprovalStatus();
        if (Objects.equals(current, normalized)) {
            if (FinanceContractApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)
                    && !Boolean.TRUE.equals(application.getVoided())) {
                FinanceContractApplicationDO voidUpdate = new FinanceContractApplicationDO();
                voidUpdate.setId(appId);
                voidUpdate.setVoided(Boolean.TRUE);
                applicationMapper.updateById(voidUpdate);
            }
            return;
        }
        if (Boolean.TRUE.equals(application.getVoided())) {
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

        FinanceContractApplicationDO update = new FinanceContractApplicationDO();
        update.setId(appId);
        update.setApprovalStatus(normalized);
        if (FinanceContractApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)) {
            update.setVoided(Boolean.TRUE);
        }
        if (FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(normalized)
                || FinanceContractApprovalStatusEnum.REJECTED.getStatus().equals(normalized)
                || FinanceContractApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)) {
            update.setCurrentNodeKey(null);
            update.setCurrentNodeName(null);
        }
        applicationMapper.updateById(update);
    }

    @Override
    public FinanceContractApplicationDO getApplication(Long id) {
        FinanceContractApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            throw exception(CONTRACT_APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    @Override
    public PageResult<FinanceContractApplicationDO> getApplicationPage(FinanceContractApplicationPageReqVO pageReqVO) {
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

    private void validateBusinessFields(FinanceContractApplicationCreateAndStartReqVO reqVO) {
        if (!ALLOWED_FILE_TYPES.contains(reqVO.getFileType())) {
            throw exception(CONTRACT_APPLICATION_FILE_TYPE_INVALID);
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
        return FinanceContractApplicationDO.builder()
                .applicantUserId(applicantUserId)
                .applicantDeptId(reqVO.getApplicantDeptId())
                .counterpartyCompanyId(counterparty.getId())
                .counterpartyName(counterparty.getName())
                .amountNa(amountNa)
                .contractAmount(amountNa ? null : reqVO.getContractAmount())
                .signCompany(reqVO.getSignCompany())
                .fileName(reqVO.getFileName())
                .fileType(reqVO.getFileType())
                .productType(reqVO.getProductType())
                .rebateRatio(reqVO.getRebateRatio())
                .settlementMethod(reqVO.getSettlementMethod())
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

    private String startProcess(Long userId, Long appId, FinanceContractApplicationDO application,
                                FinanceContractApplicationCreateAndStartReqVO reqVO) {
        Map<String, Object> variables = buildProcessVariables(application);
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
        variables.put("applicantUserId", application.getApplicantUserId());
        variables.put("signCompany", application.getSignCompany());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordSeal(Long id, String sealFileUrl, Long actualSealerUserId) {
        FinanceContractApplicationDO application = getApplication(id);
        requirePending(application);
        if (StrUtil.isBlank(sealFileUrl)) {
            throw exception(CONTRACT_APPLICATION_SEAL_FILE_REQUIRED);
        }
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .set("seal_file_url", sealFileUrl.trim())
                .set("actual_sealer_user_id",
                        actualSealerUserId != null ? actualSealerUserId : application.getApplicantUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordArchive(Long id) {
        FinanceContractApplicationDO application = getApplication(id);
        requirePending(application);
        if (StrUtil.isBlank(application.getSealFileUrl())) {
            throw exception(CONTRACT_APPLICATION_SEAL_FILE_REQUIRED);
        }
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .set("archived_at", LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordMail(Long id, String mailTrackingNo) {
        FinanceContractApplicationDO application = getApplication(id);
        requirePending(application);
        if (!Boolean.TRUE.equals(application.getNeedMail())) {
            throw exception(CONTRACT_APPLICATION_EXEC_NOT_ALLOWED);
        }
        if (StrUtil.isBlank(mailTrackingNo)) {
            throw exception(CONTRACT_APPLICATION_MAIL_TRACKING_REQUIRED);
        }
        applicationMapper.update(null, new UpdateWrapper<FinanceContractApplicationDO>()
                .eq("id", id)
                .set("mail_tracking_no", mailTrackingNo.trim()));
    }

    @Override
    public List<FinanceContractApplicationDO> listSelectableForBo(Long applicantUserId) {
        return applicationMapper.selectList(new LambdaQueryWrapperX<FinanceContractApplicationDO>()
                .eq(FinanceContractApplicationDO::getApprovalStatus,
                        FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .eq(FinanceContractApplicationDO::getApplicantUserId, applicantUserId)
                .eq(FinanceContractApplicationDO::getVoided, Boolean.FALSE)
                .orderByDesc(FinanceContractApplicationDO::getId));
    }

    private static void requirePending(FinanceContractApplicationDO application) {
        if (!FinanceContractApprovalStatusEnum.PENDING.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(CONTRACT_APPLICATION_EXEC_NOT_ALLOWED);
        }
    }
}
