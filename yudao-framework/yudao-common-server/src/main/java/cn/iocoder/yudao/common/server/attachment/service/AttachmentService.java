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
     * 删除附件信息
     *
     * @param id 编号
     */
    void deleteAttachment(Long id);

    /**
     * 获得附件信息
     *
     * @param id 编号
     * @return 附件信息
     */
    AttachmentDO getAttachment(Long id);

    /**
     * 根据业务类型和业务ID获取附件列表
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @return 附件列表
     */
    List<AttachmentDO> getAttachmentListByBusiness(String businessType, Long businessId);

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
     * 内部链路批量保存：允许保留业务类型（仅 HRM 入职资料 claim 消费后调用）
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @param attachments 附件列表
     */
    void saveAttachmentListInternal(String businessType, Long businessId, List<AttachmentSaveReqVO> attachments);

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
