package cn.iocoder.yudao.common.server.attachment.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/**
 * 通用附件信息 Service 实现类
 *
 * @author 宇擎源码
 */
@Service
@Validated
public class AttachmentServiceImpl implements AttachmentService {

    /** 入职资料等默认硬限制；业务方可在调用前再收紧 */
    public static final int DEFAULT_MAX_COUNT = 10;
    public static final long DEFAULT_MAX_SIZE_BYTES = 20L * 1024 * 1024;
    public static final Set<String> DEFAULT_ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    @Resource
    private AttachmentMapper attachmentMapper;

    @Override
    public Long createAttachment(@Valid AttachmentSaveReqVO createReqVO) {
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
        // null 表示调用方未提供集合 → 不改动（由业务服务决定是否调用本方法）
        // 空列表表示清空
        Set<Long> existingIds = getAttachmentListByBusiness(businessType, businessId)
                .stream()
                .map(AttachmentDO::getId)
                .collect(Collectors.toSet());

        Set<Long> processedIds = new HashSet<>();

        if (attachments != null && !attachments.isEmpty()) {
            validateAttachmentConstraints(attachments);

            List<AttachmentDO> attachmentDOList = attachments.stream()
                    .map(reqVO -> toOwnedAttachment(reqVO, businessType, businessId))
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

    /**
     * 服务端边界：数量 / 大小 / 扩展名（PDF/JPG/JPEG/PNG）
     */
    void validateAttachmentConstraints(List<AttachmentSaveReqVO> attachments) {
        if (attachments.size() > DEFAULT_MAX_COUNT) {
            throw invalidParamException("附件最多 {} 份", DEFAULT_MAX_COUNT);
        }
        Set<Long> seenIds = new HashSet<>();
        for (AttachmentSaveReqVO att : attachments) {
            if (att.getId() != null && !seenIds.add(att.getId())) {
                throw invalidParamException("附件 ID 重复: {}", att.getId());
            }
            if (att.getFileSize() != null && att.getFileSize() > DEFAULT_MAX_SIZE_BYTES) {
                throw invalidParamException("单个附件不能超过 20MB");
            }
            if (StrUtil.isBlank(att.getFileName()) || StrUtil.isBlank(att.getFileUrl())
                    || StrUtil.isBlank(att.getFilePath())) {
                throw invalidParamException("附件文件名、路径和访问地址不能为空");
            }
            if (att.getFileUrl().startsWith("blob:")) {
                throw invalidParamException("附件必须先上传到文件服务，禁止使用本地临时地址");
            }
            String ext = resolveExtension(att);
            if (StrUtil.isBlank(ext) || !DEFAULT_ALLOWED_EXTENSIONS.contains(ext)) {
                throw invalidParamException("附件仅支持 PDF/JPG/JPEG/PNG");
            }
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

    private static String resolveExtension(AttachmentSaveReqVO att) {
        if (StrUtil.isNotBlank(att.getFileExtension())) {
            return att.getFileExtension().toLowerCase(Locale.ROOT).replace(".", "");
        }
        String name = att.getFileName();
        if (StrUtil.isBlank(name) || !name.contains(".")) {
            return null;
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
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
