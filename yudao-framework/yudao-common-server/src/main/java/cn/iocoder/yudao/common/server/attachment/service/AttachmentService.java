package cn.iocoder.yudao.common.server.attachment.service;

import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 通用附件信息 Service 接口
 *
 * @author 宇擎源码
 */
public interface AttachmentService {

    /**
     * 创建附件信息
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createAttachment(@Valid AttachmentSaveReqVO createReqVO);

    /**
     * 更新附件信息
     *
     * @param updateReqVO 更新信息
     */
    void updateAttachment(@Valid AttachmentSaveReqVO updateReqVO);

    /**
     * 删除附件信息（通用：禁止入职资料保留类型）
     *
     * @param id 编号
     */
    void deleteAttachment(Long id);

    /**
     * 内部删除：允许入职资料（仅 HRM）
     */
    void deleteAttachmentInternal(Long id);

    /**
     * 获得附件信息（通用：禁止返回入职资料）
     *
     * @param id 编号
     * @return 附件信息
     */
    AttachmentDO getAttachment(Long id);

    /**
     * 内部读取：允许入职资料（仅 HRM 鉴权下载）
     */
    AttachmentDO getAttachmentInternal(Long id);

    /**
     * 根据业务类型和业务ID获取附件列表（通用：禁止入职资料类型）
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @return 附件列表
     */
    List<AttachmentDO> getAttachmentListByBusiness(String businessType, Long businessId);

    /**
     * 内部列表：允许入职资料（仅 HRM）
     */
    List<AttachmentDO> getAttachmentListByBusinessInternal(String businessType, Long businessId);

    /**
     * 根据业务类型和业务ID集合批量获取附件列表
     *
     * @param businessType 业务类型
     * @param businessIds 业务ID集合
     * @return 附件列表
     */
    List<AttachmentDO> getAttachmentListByBusinessIds(String businessType, java.util.Collection<Long> businessIds);

    /**
     * 批量保存附件信息（通用入口：禁止保留业务类型，如入职资料）
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @param attachments 附件列表
     */
    void saveAttachmentList(String businessType, Long businessId, List<AttachmentSaveReqVO> attachments);

    /**
     * 内部链路批量保存：允许保留业务类型（仅 HRM claim 消费后调用）。
     * 新行强制 FileAccessApi 权威校验：fileId 对应 FileDO.path，且必须落在私有目录。
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @param attachments 附件列表
     */
    void saveAttachmentListInternal(String businessType, Long businessId, List<AttachmentSaveReqVO> attachments);

    /**
     * 历史/审批转档：从已存在的同租户保留业务源附件复制到目标业务。
     * <p>
     * 身份以权威 FileDO 为准（path 可为迁移后的 public 权威 path），不要求私有目录；
     * 禁止传入客户端自报身份。仅接受已落库的源 attachment 行。
     *
     * @param sourceBusinessType 源业务类型（如 201）
     * @param sourceBusinessId   源业务 ID
     * @param targetBusinessType 目标业务类型（如 hrm_employee_archive_onboarding）
     * @param targetBusinessId   目标业务 ID
     */
    void transferReservedAttachmentsFromSource(String sourceBusinessType, Long sourceBusinessId,
                                               String targetBusinessType, Long targetBusinessId);

    /**
     * 根据业务类型和业务ID删除附件
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     */
    void deleteAttachmentByBusiness(String businessType, Long businessId);

    /**
     * 根据业务类型和业务ID列表批量删除附件
     *
     * @param businessType 业务类型
     * @param businessIds 业务ID列表
     */
    void deleteAttachmentByBusinessIds(String businessType, List<Long> businessIds);

}
