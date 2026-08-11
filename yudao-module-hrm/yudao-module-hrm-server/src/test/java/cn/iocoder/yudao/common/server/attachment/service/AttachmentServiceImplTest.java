package cn.iocoder.yudao.common.server.attachment.service;

import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.dal.mysql.AttachmentMapper;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 通用附件：归属校验 + 保留业务类型禁止通用写（#2）。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @InjectMocks
    private AttachmentServiceImpl attachmentService;

    @Mock
    private AttachmentMapper attachmentMapper;

    private AttachmentSaveReqVO base(String name) {
        AttachmentSaveReqVO vo = new AttachmentSaveReqVO();
        vo.setFileName(name);
        vo.setFilePath("/files/" + name);
        vo.setFileUrl("https://files.example.com/" + name);
        vo.setFileSize(1024L);
        vo.setFileExtension(name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "");
        vo.setBusinessType("x");
        vo.setBusinessId(1L);
        return vo;
    }

    @Test
    void rejectsCrossBusinessAttachmentIdHijack() {
        when(attachmentMapper.selectListByBusiness("seal_apply_bill", 100L))
                .thenReturn(List.of());

        AttachmentDO foreign = new AttachmentDO();
        foreign.setId(99L);
        foreign.setBusinessType("201");
        foreign.setBusinessId(7L);
        foreign.setFileName("a.pdf");
        foreign.setFilePath("/a.pdf");
        foreign.setFileUrl("https://x/a.pdf");
        when(attachmentMapper.selectById(99L)).thenReturn(foreign);

        AttachmentSaveReqVO hijack = base("a.pdf");
        hijack.setId(99L);

        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList("seal_apply_bill", 100L, List.of(hijack)));
        verify(attachmentMapper, never()).insertOrUpdate(anyList());
    }

    @Test
    void allowsDocxForNonOnboardingBusiness() {
        when(attachmentMapper.selectListByBusiness("seal_apply_bill", 1L)).thenReturn(List.of());
        AttachmentSaveReqVO docx = base("合同.docx");
        docx.setFileExtension("docx");
        docx.setFileSize(5L * 1024 * 1024);

        attachmentService.saveAttachmentList("seal_apply_bill", 1L, List.of(docx));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("合同.docx", captor.getValue().get(0).getFileName());
    }

    @Test
    void genericSaveListRejectsOnboardingBusinessType() {
        AttachmentSaveReqVO neu = base("new.pdf");
        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList(
                        AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 100L, List.of(neu)));
        verify(attachmentMapper, never()).insertOrUpdate(anyList());
    }

    @Test
    void genericCreateRejectsOnboardingBusinessType() {
        AttachmentSaveReqVO vo = base("x.pdf");
        vo.setBusinessType(AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE);
        assertThrows(ServiceException.class, () -> attachmentService.createAttachment(vo));
        verify(attachmentMapper, never()).insert(any(AttachmentDO.class));
    }

    @Test
    void genericUpdateRejectsOnboardingBusinessType() {
        AttachmentSaveReqVO vo = base("x.pdf");
        vo.setId(1L);
        vo.setBusinessType(AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE);
        when(attachmentMapper.selectById(1L)).thenReturn(new AttachmentDO());
        assertThrows(ServiceException.class, () -> attachmentService.updateAttachment(vo));
        verify(attachmentMapper, never()).updateById(any(AttachmentDO.class));
    }

    @Test
    void internalSaveListAllowsOnboardingBusinessType() {
        when(attachmentMapper.selectListByBusiness(
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 100L))
                .thenReturn(List.of());

        AttachmentDO owned = new AttachmentDO();
        owned.setId(5L);
        owned.setBusinessType(AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE);
        owned.setBusinessId(100L);
        owned.setFileName("old.pdf");
        when(attachmentMapper.selectById(5L)).thenReturn(owned);

        AttachmentSaveReqVO keep = base("old.pdf");
        keep.setId(5L);
        AttachmentSaveReqVO neu = base("new.pdf");

        attachmentService.saveAttachmentListInternal(
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 100L, List.of(keep, neu));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(5L, captor.getValue().get(0).getId());
        assertNull(captor.getValue().get(1).getId());
    }

}
