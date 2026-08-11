package cn.iocoder.yudao.common.server.attachment.service;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.dal.mysql.AttachmentMapper;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/**
 * 通用附件信息 Service 实现类
 * <p>
 * 注意：类型/大小/数量等业务边界由各业务方在调用前校验。
 * 本服务仅保证：非空附件 ID 必须已归属当前 businessType+businessId（防同租户跨业务 rebind）。
 * <p>
 * 保留业务类型 {@link #RESERVED_ONBOARDING_BUSINESS_TYPE} 禁止经通用 create/update/save-list 写入，
 * 仅允许 {@link #saveAttachmentListInternal}（HRM claim 链路）。
 *
 * @author 宇擎源码
 */
@Service
@Validated
public class AttachmentServiceImpl implements AttachmentService {

    /** 入职资料保留业务类型：禁止通用写入口 */
    public static final String RESERVED_ONBOARDING_BUSINESS_TYPE = "hrm_employee_archive_onboarding";

    @Resource
    private AttachmentMapper attachmentMapper;

    @Override
    public Long createAttachment(@Valid AttachmentSaveReqVO createReqVO) {
        rejectReservedBusinessType(createReqVO.getBusinessType());
        AttachmentDO attachment = BeanUtils.toBean(createReqVO, AttachmentDO.class);
        if (attachment.getUploadTime() == null) {
            attachment.setUploadTime(LocalDateTime.now());
        }
        if (attachment.getSortOrder() == null) {
            attachment.setSortOrder(0);
        }
        attachmentMapper.insert(attachment);
        return attachment.getId();
    }

    @Override
    public void updateAttachment(@Valid AttachmentSaveReqVO updateReqVO) {
        validateAttachmentExists(updateReqVO.getId());
        rejectReservedBusinessType(updateReqVO.getBusinessType());
        AttachmentDO existing = attachmentMapper.selectById(updateReqVO.getId());
        if (existing != null) {
            rejectReservedBusinessType(existing.getBusinessType());
        }
        AttachmentDO updateObj = BeanUtils.toBean(updateReqVO, AttachmentDO.class);
        attachmentMapper.updateById(updateObj);
    }

    @Override
    public void deleteAttachment(Long id) {
        validateAttachmentExists(id);
        attachmentMapper.deleteById(id);
    }

    private void validateAttachmentExists(Long id) {
        if (attachmentMapper.selectById(id) == null) {
            throw invalidParamException("附件不存在");
        }
    }

    @Override
    public AttachmentDO getAttachment(Long id) {
        return attachmentMapper.selectById(id);
    }

    @Override
    public List<AttachmentDO> getAttachmentListByBusiness(String businessType, Long businessId) {
        return attachmentMapper.selectListByBusiness(businessType, businessId);
    }

    @Override
    public List<AttachmentDO> getAttachmentListByBusinessIds(String businessType, Collection<Long> businessIds) {
        return attachmentMapper.selectListByBusinessIds(businessType, businessIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAttachmentList(String businessType, Long businessId, List<AttachmentSaveReqVO> attachments) {
        rejectReservedBusinessType(businessType);
        doSaveAttachmentList(businessType, businessId, attachments);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAttachmentListInternal(String businessType, Long businessId, List<AttachmentSaveReqVO> attachments) {
        // 允许保留类型：仅 HRM claim 消费后调用
        doSaveAttachmentList(businessType, businessId, attachments);
    }

    private void doSaveAttachmentList(String businessType, Long businessId, List<AttachmentSaveReqVO> attachments) {
        // null 表示调用方未提供集合 → 由业务服务决定是否调用本方法
        // 空列表表示清空
        Set<Long> existingIds = getAttachmentListByBusiness(businessType, businessId)
                .stream()
                .map(AttachmentDO::getId)
                .collect(Collectors.toSet());

        Set<Long> processedIds = new HashSet<>();

        if (attachments != null && !attachments.isEmpty()) {
            Set<Long> seenIds = new HashSet<>();
            List<AttachmentDO> attachmentDOList = attachments.stream()
                    .map(reqVO -> {
                        if (reqVO.getId() != null && !seenIds.add(reqVO.getId())) {
                            throw invalidParamException("附件 ID 重复: {}", reqVO.getId());
                        }
                        return toOwnedAttachment(reqVO, businessType, businessId);
                    })
                    .collect(Collectors.toList());

            for (int i = 0; i < attachmentDOList.size(); i++) {
                attachmentDOList.get(i).setSortOrder(i + 1);
            }

            attachmentMapper.insertOrUpdate(attachmentDOList);

            processedIds = attachmentDOList.stream()
                    .map(AttachmentDO::getId)
                    .collect(Collectors.toSet());
        }

        existingIds.removeAll(processedIds);
        if (!existingIds.isEmpty()) {
            attachmentMapper.deleteBatchIds(existingIds);
        }
    }

    private void rejectReservedBusinessType(String businessType) {
        if (RESERVED_ONBOARDING_BUSINESS_TYPE.equals(businessType)) {
            throw invalidParamException(
                    "业务类型 {} 仅允许经 HRM 入职资料 claim 内部链路写入，禁止通用附件接口",
                    businessType);
        }
    }

    /**
     * 将请求转为归属当前业务的 DO；非空 ID 必须已属于同一 businessType+businessId。
     */
    private AttachmentDO toOwnedAttachment(AttachmentSaveReqVO reqVO, String businessType, Long businessId) {
        if (reqVO.getId() != null) {
            AttachmentDO existing = attachmentMapper.selectById(reqVO.getId());
            if (existing == null) {
                throw invalidParamException("附件不存在: {}", reqVO.getId());
            }
            // 同租户跨业务劫持：拒绝将其他业务附件 rebind 到当前业务
            if (!businessType.equals(existing.getBusinessType())
                    || !businessId.equals(existing.getBusinessId())) {
                throw invalidParamException("附件不属于当前业务，禁止修改归属");
            }
            // 仅允许更新备注/排序等元数据，文件本体与归属保持原记录
            existing.setRemark(reqVO.getRemark());
            if (reqVO.getSortOrder() != null) {
                existing.setSortOrder(reqVO.getSortOrder());
            }
            return existing;
        }

        AttachmentDO attachmentDO = BeanUtils.toBean(reqVO, AttachmentDO.class);
        attachmentDO.setId(null);
        attachmentDO.setBusinessType(businessType);
        attachmentDO.setBusinessId(businessId);
        if (attachmentDO.getUploadTime() == null) {
            attachmentDO.setUploadTime(LocalDateTime.now());
        }
        return attachmentDO;
    }

    @Override
    public void deleteAttachmentByBusiness(String businessType, Long businessId) {
        attachmentMapper.deleteByBusiness(businessType, businessId);
    }

    @Override
    public void deleteAttachmentByBusinessIds(String businessType, List<Long> businessIds) {
        if (CollUtil.isNotEmpty(businessIds)) {
            attachmentMapper.deleteByBusinessIds(businessType, businessIds);
        }
    }

}
