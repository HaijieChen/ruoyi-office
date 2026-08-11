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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @InjectMocks
    private AttachmentServiceImpl attachmentService;

    @Mock
    private AttachmentMapper attachmentMapper;

    private AttachmentSaveReqVO validPdf(String name) {
        AttachmentSaveReqVO vo = new AttachmentSaveReqVO();
        vo.setFileName(name);
        vo.setFilePath("/files/" + name);
        vo.setFileUrl("https://files.example.com/" + name);
        vo.setFileSize(1024L);
        vo.setFileExtension("pdf");
        vo.setFileType("application/pdf");
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
        foreign.setBusinessType("201"); // 入职单
        foreign.setBusinessId(7L);
        foreign.setFileName("a.pdf");
        foreign.setFilePath("/a.pdf");
        foreign.setFileUrl("https://x/a.pdf");
        when(attachmentMapper.selectById(99L)).thenReturn(foreign);

        AttachmentSaveReqVO hijack = validPdf("a.pdf");
        hijack.setId(99L);

        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList("hrm_employee_archive_onboarding", 100L, List.of(hijack)));
        verify(attachmentMapper, never()).insertOrUpdate(anyList());
    }

    @Test
    void rejectsMoreThanTenAttachments() {
        when(attachmentMapper.selectListByBusiness(anyString(), anyLong())).thenReturn(List.of());
        List<AttachmentSaveReqVO> list = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            list.add(validPdf("f" + i + ".pdf"));
        }
        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList("hrm_employee_archive_onboarding", 1L, list));
    }

    @Test
    void rejectsOversizedAndIllegalTypeAndBlobUrl() {
        when(attachmentMapper.selectListByBusiness(anyString(), anyLong())).thenReturn(List.of());

        AttachmentSaveReqVO huge = validPdf("big.pdf");
        huge.setFileSize(21L * 1024 * 1024);
        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList("t", 1L, List.of(huge)));

        AttachmentSaveReqVO exe = validPdf("a.exe");
        exe.setFileExtension("exe");
        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList("t", 1L, List.of(exe)));

        AttachmentSaveReqVO blob = validPdf("a.pdf");
        blob.setFileUrl("blob:http://local/uuid");
        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList("t", 1L, List.of(blob)));
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

        AttachmentSaveReqVO keep = validPdf("old.pdf");
        keep.setId(5L);
        AttachmentSaveReqVO neu = validPdf("new.pdf");

        attachmentService.saveAttachmentList("hrm_employee_archive_onboarding", 100L, List.of(keep, neu));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(5L, captor.getValue().get(0).getId());
        assertNull(captor.getValue().get(1).getId());
        assertEquals("hrm_employee_archive_onboarding", captor.getValue().get(1).getBusinessType());
        assertEquals(100L, captor.getValue().get(1).getBusinessId());
    }

}
