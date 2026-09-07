package cn.iocoder.yudao.common.server.attachment.service;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.dal.mysql.AttachmentMapper;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.FilePrivateDirs;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/**
 * 通用附件信息 Service 实现类
 * <p>
 * 注意：类型/大小/数量等业务边界由各业务方在调用前校验。
 * 本服务仅保证：非空附件 ID 必须已归属当前 businessType+businessId（防同租户跨业务 rebind）。
 * <p>
 * 保留业务类型（规范化后匹配：trim + 忽略大小写 + 遗留 {@code 201}）禁止经通用
 * create/update/delete/get/list/save-list 入口；仅 {@code *Internal} 与 HRM claim 链路可访问。
 *
 * @author 宇擎源码
 */
@Service
@Validated
public class AttachmentServiceImpl implements AttachmentService {

    /** 入职档案资料规范业务类型 */
    public static final String RESERVED_ONBOARDING_BUSINESS_TYPE = "hrm_employee_archive_onboarding";

    /** 入职申请单遗留类型码（与 HrmBillTypeEnum.HRM_EMPLOYEE_ENTRY_BILL 一致） */
    public static final String RESERVED_ENTRY_BILL_BUSINESS_TYPE = "201";

    @Resource
    private AttachmentMapper attachmentMapper;

    /**
     * 权威文件身份（yudao-server 单体注入；缺失则 reserved 写入 fail-closed）。
     */
    @Resource
    private FileAccessApi fileAccessApi;

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
        AttachmentDO existing = attachmentMapper.selectById(id);
        if (existing == null) {
            throw invalidParamException("附件不存在");
        }
        rejectReservedBusinessType(existing.getBusinessType());
        attachmentMapper.deleteById(id);
    }

    @Override
    public void deleteAttachmentInternal(Long id) {
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
        AttachmentDO att = attachmentMapper.selectById(id);
        if (att != null) {
            rejectReservedBusinessType(att.getBusinessType());
        }
        return att;
    }

    @Override
    public AttachmentDO getAttachmentInternal(Long id) {
        return attachmentMapper.selectById(id);
    }

    @Override
    public List<AttachmentDO> getAttachmentListByBusiness(String businessType, Long businessId) {
        rejectReservedBusinessType(businessType);
        return enrichFileMetadata(attachmentMapper.selectListByBusiness(businessType, businessId));
    }

    @Override
    public List<AttachmentDO> getAttachmentListByBusinessInternal(String businessType, Long businessId) {
        return attachmentMapper.selectListByBusiness(businessType, businessId);
    }

    @Override
    public List<AttachmentDO> getAttachmentListByBusinessIds(String businessType, Collection<Long> businessIds) {
        // 批量接口供 HRM 导出等内部调用；controller 不暴露
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
        // 直接查 mapper，避免通用 getAttachmentListByBusiness 对保留类型拒绝
        Set<Long> existingIds = attachmentMapper.selectListByBusiness(businessType, businessId)
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

    /**
     * 规范化业务类型：trim + 小写，便于别名比对。
     */
    public static String normalizeBusinessType(String businessType) {
        if (businessType == null) {
            return null;
        }
        return StrUtil.trim(businessType).toLowerCase(Locale.ROOT);
    }

    /**
     * 是否保留业务类型（入职档案资料 / 入职单 201 及其大小写、空白别名）。
     */
    public static boolean isReservedBusinessType(String businessType) {
        String n = normalizeBusinessType(businessType);
        if (n == null || n.isEmpty()) {
            return false;
        }
        return RESERVED_ONBOARDING_BUSINESS_TYPE.equals(n)
                || RESERVED_ENTRY_BILL_BUSINESS_TYPE.equals(n);
    }

    private void rejectReservedBusinessType(String businessType) {
        if (isReservedBusinessType(businessType)) {
            throw invalidParamException(
                    "业务类型 {} 仅允许经 HRM 内部链路访问，禁止通用附件接口",
                    businessType);
        }
    }

    /**
     * 将请求转为归属当前业务的 DO；非空 ID 必须已属于同一 businessType+businessId。
     * <p>
     * 保留类型新附件：必须已由 HRM claim 消费链路派生身份（fileId + 私有 path + 空 url），
     * 禁止客户端伪造任意 fileId/path 抬升为全局 reserved。
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

        if (isReservedBusinessType(businessType)) {
            applyAuthoritativeReservedIdentity(reqVO, true);
        }

        AttachmentDO attachmentDO = BeanUtils.toBean(reqVO, AttachmentDO.class);
        attachmentDO.setId(null);
        attachmentDO.setBusinessType(businessType);
        attachmentDO.setBusinessId(businessId);
        if (isReservedBusinessType(businessType)) {
            // 权威派生后强制不落公开 URL
            attachmentDO.setFileUrl("");
        } else {
            applyPublicFileMetadata(attachmentDO);
        }
        if (attachmentDO.getUploadTime() == null) {
            attachmentDO.setUploadTime(LocalDateTime.now());
        }
        return attachmentDO;
    }

    /**
     * 列表只读补齐：按 fileId / 精确 url / 精确 path 命中权威 FileDO。
     * 不写库、不读文件字节。0 条或 &gt;1 条不猜；无法确认时把 0 显示为未知。
     */
    private List<AttachmentDO> enrichFileMetadata(List<AttachmentDO> rows) {
        if (CollUtil.isEmpty(rows)) {
            return rows;
        }
        for (AttachmentDO row : rows) {
            if (!needsFileMetadata(row)) {
                continue;
            }
            FileRespDTO file = resolveUniqueFile(row);
            if (file != null && file.getId() != null) {
                applyFileMetadata(row, file);
            } else if (row.getFileSize() != null && row.getFileSize() == 0L) {
                row.setFileSize(null);
            }
        }
        return rows;
    }

    private void applyPublicFileMetadata(AttachmentDO row) {
        if (!needsFileMetadata(row)) {
            return;
        }
        FileRespDTO file = resolveUniqueFile(row);
        if (file != null && file.getId() != null) {
            applyFileMetadata(row, file);
        } else if (row.getFileSize() != null && row.getFileSize() == 0L) {
            row.setFileSize(null);
        }
    }

    private boolean needsFileMetadata(AttachmentDO row) {
        return row.getFileId() == null
                || row.getFileSize() == null
                || row.getFileSize() == 0L
                || StrUtil.isBlank(row.getFileType())
                || StrUtil.isBlank(row.getFileExtension());
    }

    private FileRespDTO resolveUniqueFile(AttachmentDO row) {
        if (fileAccessApi == null) {
            return null;
        }
        FileRespDTO file = null;
        if (row.getFileId() != null) {
            file = fileAccessApi.getFile(row.getFileId());
            if (file != null && !sameFileIdentity(row, file)) {
                file = null;
            }
        }
        if (file == null && StrUtil.isNotBlank(row.getFileUrl())) {
            file = fileAccessApi.getUniqueFileByUrl(row.getFileUrl());
        }
        if (file == null && StrUtil.isNotBlank(row.getFilePath())) {
            file = fileAccessApi.getUniqueFileByPath(row.getFilePath());
        }
        return file;
    }

    private static boolean sameFileIdentity(AttachmentDO row, FileRespDTO file) {
        if (StrUtil.isNotBlank(row.getFilePath()) && StrUtil.isNotBlank(file.getPath())
                && !Objects.equals(row.getFilePath(), file.getPath())) {
            return false;
        }
        if (StrUtil.isNotBlank(row.getFileUrl()) && StrUtil.isNotBlank(file.getUrl())
                && !Objects.equals(row.getFileUrl(), file.getUrl())) {
            return false;
        }
        return true;
    }

    private void applyFileMetadata(AttachmentDO row, FileRespDTO file) {
        if (row.getFileId() == null) {
            row.setFileId(file.getId());
        }
        if (StrUtil.isBlank(row.getFilePath()) && StrUtil.isNotBlank(file.getPath())) {
            row.setFilePath(file.getPath());
        }
        if (StrUtil.isBlank(row.getFileName()) && StrUtil.isNotBlank(file.getName())) {
            row.setFileName(file.getName());
        }
        if (file.getSize() != null && (row.getFileSize() == null || row.getFileSize() == 0L)) {
            row.setFileSize(file.getSize());
        }
        if (StrUtil.isBlank(row.getFileType()) && StrUtil.isNotBlank(file.getType())) {
            row.setFileType(file.getType());
        }
        if (StrUtil.isBlank(row.getFileExtension())) {
            row.setFileExtension(extensionOf(file.getType(), row.getFileName(), file.getName(), row.getFilePath(), file.getPath()));
        }
    }

    private static String extensionOf(String type, String... names) {
        for (String name : names) {
            String ext = shortExtension(name);
            if (ext != null) {
                return ext;
            }
        }
        if (StrUtil.isNotBlank(type)) {
            String mime = type.toLowerCase(Locale.ROOT);
            if (mime.contains("jpeg")) {
                return "jpg";
            }
            if (mime.contains("png")) {
                return "png";
            }
            if (mime.contains("pdf")) {
                return "pdf";
            }
            if (mime.contains("gif")) {
                return "gif";
            }
        }
        return null;
    }

    private static String shortExtension(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        int query = base.indexOf('?');
        if (query >= 0) {
            base = base.substring(0, query);
        }
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return null;
        }
        String ext = base.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.length() > 8 || ext.contains("/") || ext.contains(".")) {
            return null;
        }
        return ext;
    }

    /**
     * 保留业务新行：以 FileAccessApi 权威 FileDO 覆盖身份。
     * <ul>
     *   <li>fileId 必填且必须能查到 FileDO</li>
     *   <li>客户端 path 若提供必须与 FileDO.path 一致（禁止「私有样式 path + 任意 fileId」）</li>
     *   <li>claim 新写（requirePrivate=true）path 必须落私有目录</li>
     *   <li>强制清空 fileUrl；name/size/type 以 FileDO 为准</li>
     * </ul>
     */
    private void applyAuthoritativeReservedIdentity(AttachmentSaveReqVO reqVO, boolean requirePrivate) {
        if (reqVO.getFileId() == null) {
            throw invalidParamException("保留业务附件必须经 claim/FileDO 派生 fileId，禁止无 claim 写入");
        }
        if (StrUtil.isNotBlank(reqVO.getFileUrl())) {
            throw invalidParamException("保留业务附件禁止写入公开 fileUrl");
        }
        if (fileAccessApi == null) {
            throw invalidParamException("保留业务附件写入失败：FileAccessApi 不可用（fail-closed）");
        }
        FileRespDTO file = fileAccessApi.getFile(reqVO.getFileId());
        if (file == null || file.getId() == null) {
            throw invalidParamException("保留业务附件 fileId 不存在或不可访问: {}", reqVO.getFileId());
        }
        if (StrUtil.isNotBlank(reqVO.getFilePath())
                && !Objects.equals(reqVO.getFilePath(), file.getPath())) {
            throw invalidParamException("保留业务附件 path 与权威 FileDO 不一致，禁止伪造");
        }
        if (requirePrivate && !FilePrivateDirs.isPrivateDirectory(file.getPath())) {
            throw invalidParamException("保留业务新附件必须来自 claim 私有目录上传");
        }
        // 覆盖为权威身份
        reqVO.setFileId(file.getId());
        reqVO.setFilePath(file.getPath());
        if (StrUtil.isNotBlank(file.getName())) {
            reqVO.setFileName(file.getName());
        }
        if (file.getSize() != null) {
            reqVO.setFileSize(file.getSize());
        }
        if (StrUtil.isNotBlank(file.getType())) {
            reqVO.setFileType(file.getType());
        }
        reqVO.setFileUrl("");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferReservedAttachmentsFromSource(String sourceBusinessType, Long sourceBusinessId,
                                                      String targetBusinessType, Long targetBusinessId) {
        if (!isReservedBusinessType(sourceBusinessType) || !isReservedBusinessType(targetBusinessType)) {
            throw invalidParamException("转档仅允许保留业务类型之间复制");
        }
        if (sourceBusinessId == null || targetBusinessId == null) {
            throw invalidParamException("转档业务 ID 不能为空");
        }
        if (fileAccessApi == null) {
            throw invalidParamException("保留业务附件转档失败：FileAccessApi 不可用（fail-closed）");
        }
        List<AttachmentDO> sources = attachmentMapper.selectListByBusiness(sourceBusinessType, sourceBusinessId);
        if (CollUtil.isEmpty(sources)) {
            return;
        }
        List<AttachmentDO> copies = new ArrayList<>();
        int order = 1;
        for (AttachmentDO src : sources) {
            Long fileId = src.getFileId();
            FileRespDTO file = null;
            if (fileId != null) {
                file = fileAccessApi.getFile(fileId);
            }
            if (file == null && StrUtil.isNotBlank(src.getFilePath())) {
                file = fileAccessApi.getUniqueFileByPath(src.getFilePath());
            }
            if (file == null || file.getId() == null) {
                throw invalidParamException(
                        "历史保留附件无法解析权威 FileDO，拒绝转档: attachmentId={}", src.getId());
            }
            AttachmentDO copy = new AttachmentDO();
            copy.setBusinessType(targetBusinessType);
            copy.setBusinessId(targetBusinessId);
            copy.setFileId(file.getId());
            copy.setFilePath(file.getPath());
            copy.setFileName(StrUtil.blankToDefault(file.getName(), src.getFileName()));
            copy.setFileUrl("");
            copy.setFileSize(file.getSize() != null ? file.getSize() : src.getFileSize());
            copy.setFileType(StrUtil.blankToDefault(file.getType(), src.getFileType()));
            copy.setFileExtension(src.getFileExtension());
            copy.setUploadTime(src.getUploadTime() != null ? src.getUploadTime() : LocalDateTime.now());
            copy.setSortOrder(src.getSortOrder() != null ? src.getSortOrder() : order);
            copy.setRemark(src.getRemark());
            copies.add(copy);
            order++;
        }
        // 目标业务已有行时先清（转档幂等：同员工重复审批不应堆叠）
        attachmentMapper.deleteByBusiness(targetBusinessType, targetBusinessId);
        if (!copies.isEmpty()) {
            attachmentMapper.insertOrUpdate(copies);
        }
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
