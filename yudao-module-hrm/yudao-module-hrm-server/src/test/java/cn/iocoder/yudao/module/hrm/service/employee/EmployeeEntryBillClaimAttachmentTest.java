package cn.iocoder.yudao.module.hrm.service.employee;

import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.OnboardingAttachmentSaveReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.OnboardingFileClaimDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.OnboardingFileClaimMapper;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * H3：入职单 201 附件必须 claim 权威写入；伪造 fileId 路径被拒绝。
 */
@ExtendWith(MockitoExtension.class)
class EmployeeEntryBillClaimAttachmentTest {

    @InjectMocks
    private EmployeeEntryBillServiceImpl entryBillService;

    @Mock
    private AttachmentService attachmentService;
    @Mock
    private FileAccessApi fileAccessApi;
    @Mock
    private OnboardingFileClaimMapper onboardingFileClaimMapper;

    private FileRespDTO privatePdf(long id) {
        FileRespDTO f = new FileRespDTO();
        f.setId(id);
        f.setName("id_scan.pdf");
        f.setPath("hrm-onboarding-private/id_scan.pdf");
        f.setType("application/pdf");
        f.setSize(2048L);
        return f;
    }

    @Test
    void saveEntryBillAttachments_consumesClaimAndDerivesIdentity() {
        OnboardingAttachmentSaveReqVO req = new OnboardingAttachmentSaveReqVO();
        req.setClaimToken("tok-entry");
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-entry"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(88L), any(LocalDateTime.class)))
                .thenReturn(1);
        OnboardingFileClaimDO claim = OnboardingFileClaimDO.builder()
                .claimToken("tok-entry").fileId(55L).uploaderUserId(7L)
                .purpose(OnboardingFileClaimDO.PURPOSE)
                .expireTime(LocalDateTime.now().plusHours(1)).build();
        when(onboardingFileClaimMapper.selectByClaimToken("tok-entry")).thenReturn(claim);
        when(fileAccessApi.getFile(55L)).thenReturn(privatePdf(55L));

        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            entryBillService.saveEntryBillAttachments(88L, List.of(req));
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentSaveReqVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentService).saveAttachmentListInternal(eq("201"), eq(88L), captor.capture());
        AttachmentSaveReqVO saved = captor.getValue().get(0);
        assertEquals(55L, saved.getFileId());
        assertEquals("hrm-onboarding-private/id_scan.pdf", saved.getFilePath());
        assertEquals("", saved.getFileUrl());
        assertEquals("id_scan.pdf", saved.getFileName());
    }

    @Test
    void saveEntryBillAttachments_rejectsForgedWithoutClaim() {
        OnboardingAttachmentSaveReqVO fake = new OnboardingAttachmentSaveReqVO();
        // 无 id、无 claimToken → 伪造
        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class,
                    () -> entryBillService.saveEntryBillAttachments(1L, List.of(fake)));
        }
        verify(attachmentService, never()).saveAttachmentListInternal(anyString(), anyLong(), anyList());
        verify(onboardingFileClaimMapper, never()).consumeIfOpen(any(), any(), any(), any(), any());
    }

    @Test
    void saveEntryBillAttachments_rejectsOtherUploaderClaim() {
        OnboardingAttachmentSaveReqVO req = new OnboardingAttachmentSaveReqVO();
        req.setClaimToken("tok-other");
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-other"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(0);
        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class,
                    () -> entryBillService.saveEntryBillAttachments(1L, List.of(req)));
        }
        verify(attachmentService, never()).saveAttachmentListInternal(anyString(), anyLong(), anyList());
    }

    @Test
    void saveEntryBillAttachments_rejectsPublicPathFromClaimFile() {
        OnboardingAttachmentSaveReqVO req = new OnboardingAttachmentSaveReqVO();
        req.setClaimToken("tok-pub");
        when(onboardingFileClaimMapper.consumeIfOpen(eq("tok-pub"), eq(7L),
                eq(OnboardingFileClaimDO.PURPOSE), eq(1L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(onboardingFileClaimMapper.selectByClaimToken("tok-pub"))
                .thenReturn(OnboardingFileClaimDO.builder()
                        .claimToken("tok-pub").fileId(9L).uploaderUserId(7L)
                        .purpose(OnboardingFileClaimDO.PURPOSE)
                        .expireTime(LocalDateTime.now().plusHours(1)).build());
        FileRespDTO pub = privatePdf(9L);
        pub.setPath("public/global.pdf");
        when(fileAccessApi.getFile(9L)).thenReturn(pub);
        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertThrows(ServiceException.class,
                    () -> entryBillService.saveEntryBillAttachments(1L, List.of(req)));
        }
        verify(attachmentService, never()).saveAttachmentListInternal(anyString(), anyLong(), anyList());
    }

}
