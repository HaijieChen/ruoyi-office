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
 * 通用附件服务：仅归属校验；类型/大小限制由业务方负责（#4）。
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
        when(attachmentMapper.selectListByBusiness("hrm_employee_archive_onboarding", 100L))
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
                attachmentService.saveAttachmentList("hrm_employee_archive_onboarding", 100L, List.of(hijack)));
        verify(attachmentMapper, never()).insertOrUpdate(anyList());
    }

    @Test
    void allowsDocxForNonOnboardingBusiness() {
        // #4：通用附件不得被入职资料策略误伤
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
    void acceptsOwnedIdAndInsertsNew() {
        when(attachmentMapper.selectListByBusiness("hrm_employee_archive_onboarding", 100L))
                .thenReturn(List.of());

        AttachmentDO owned = new AttachmentDO();
        owned.setId(5L);
        owned.setBusinessType("hrm_employee_archive_onboarding");
        owned.setBusinessId(100L);
        owned.setFileName("old.pdf");
        owned.setFilePath("/old.pdf");
        owned.setFileUrl("https://x/old.pdf");
        when(attachmentMapper.selectById(5L)).thenReturn(owned);

        AttachmentSaveReqVO keep = base("old.pdf");
        keep.setId(5L);
        AttachmentSaveReqVO neu = base("new.pdf");

        attachmentService.saveAttachmentList("hrm_employee_archive_onboarding", 100L, List.of(keep, neu));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(5L, captor.getValue().get(0).getId());
        assertNull(captor.getValue().get(1).getId());
    }

}
