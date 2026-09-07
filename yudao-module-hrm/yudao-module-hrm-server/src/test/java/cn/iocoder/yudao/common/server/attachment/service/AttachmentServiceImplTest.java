package cn.iocoder.yudao.common.server.attachment.service;

import cn.iocoder.yudao.common.server.attachment.controller.vo.AttachmentSaveReqVO;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import cn.iocoder.yudao.common.server.attachment.dal.mysql.AttachmentMapper;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 通用附件：归属校验 + 保留业务类型禁止通用写 + FileDO 权威写入。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @InjectMocks
    private AttachmentServiceImpl attachmentService;

    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private FileAccessApi fileAccessApi;

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

    private FileRespDTO file(long id, String path, String name) {
        FileRespDTO f = new FileRespDTO();
        f.setId(id);
        f.setPath(path);
        f.setName(name);
        f.setSize(2048L);
        f.setType("application/pdf");
        return f;
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
    void internalSaveListAllowsOnboardingWhenFileDoMatchesPrivatePath() {
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
        neu.setFileId(55L);
        neu.setFilePath("hrm-onboarding-private/new.pdf");
        neu.setFileUrl("");
        when(fileAccessApi.getFile(55L)).thenReturn(
                file(55L, "hrm-onboarding-private/new.pdf", "new.pdf"));

        attachmentService.saveAttachmentListInternal(
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 100L, List.of(keep, neu));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(5L, captor.getValue().get(0).getId());
        assertNull(captor.getValue().get(1).getId());
        assertEquals(55L, captor.getValue().get(1).getFileId());
        assertEquals("hrm-onboarding-private/new.pdf", captor.getValue().get(1).getFilePath());
        assertEquals("", captor.getValue().get(1).getFileUrl());
    }

    @Test
    void internalSaveListRejectsPrivateLookingPathWithMismatchedFileId() {
        when(attachmentMapper.selectListByBusiness(
                AttachmentServiceImpl.RESERVED_ENTRY_BILL_BUSINESS_TYPE, 1L))
                .thenReturn(List.of());
        // 反例：私有样式 path + 任意 fileId（FileDO 真实 path 不同）
        AttachmentSaveReqVO forged = base("x.pdf");
        forged.setFileId(777L);
        forged.setFilePath("hrm-onboarding-private/forged.pdf");
        forged.setFileUrl("");
        when(fileAccessApi.getFile(777L)).thenReturn(
                file(777L, "public/real-report.pdf", "real-report.pdf"));

        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentListInternal(
                        AttachmentServiceImpl.RESERVED_ENTRY_BILL_BUSINESS_TYPE, 1L, List.of(forged)));
        verify(attachmentMapper, never()).insertOrUpdate(anyList());
    }

    @Test
    void internalSaveListRejectsForgedReservedWithPublicUrl() {
        when(attachmentMapper.selectListByBusiness(
                AttachmentServiceImpl.RESERVED_ENTRY_BILL_BUSINESS_TYPE, 1L))
                .thenReturn(List.of());
        AttachmentSaveReqVO forged = base("public.pdf");
        forged.setFileId(777L);
        forged.setFilePath("public/report.pdf");
        forged.setFileUrl("https://evil/report.pdf");

        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentListInternal(
                        AttachmentServiceImpl.RESERVED_ENTRY_BILL_BUSINESS_TYPE, 1L, List.of(forged)));
        verify(attachmentMapper, never()).insertOrUpdate(anyList());
        verify(fileAccessApi, never()).getFile(anyLong());
    }

    @Test
    void internalSaveListRejectsReservedNewMissingFileId() {
        when(attachmentMapper.selectListByBusiness(
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 1L))
                .thenReturn(List.of());
        AttachmentSaveReqVO noId = base("x.pdf");
        noId.setFilePath("hrm-onboarding-private/x.pdf");
        noId.setFileUrl("");
        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentListInternal(
                        AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 1L, List.of(noId)));
    }

    @Test
    void transferReservedAllowsHistoricalPublicPathFromSource() {
        AttachmentDO src = new AttachmentDO();
        src.setId(10L);
        src.setBusinessType("201");
        src.setBusinessId(1L);
        src.setFileId(105L);
        src.setFilePath("public/report.pdf");
        src.setFileName("report.pdf");
        src.setFileSize(100L);
        when(attachmentMapper.selectListByBusiness("201", 1L)).thenReturn(List.of(src));
        when(fileAccessApi.getFile(105L)).thenReturn(
                file(105L, "public/report.pdf", "report.pdf"));

        attachmentService.transferReservedAttachmentsFromSource(
                "201", 1L,
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 99L);

        verify(attachmentMapper).deleteByBusiness(
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 99L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        AttachmentDO copy = captor.getValue().get(0);
        assertEquals(105L, copy.getFileId());
        assertEquals("public/report.pdf", copy.getFilePath());
        assertEquals("", copy.getFileUrl());
        assertEquals(AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, copy.getBusinessType());
        assertEquals(99L, copy.getBusinessId());
    }

    @Test
    void genericGetRejectsOnboardingBusinessType() {
        AttachmentDO onboarding = new AttachmentDO();
        onboarding.setId(9L);
        onboarding.setBusinessType(AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE);
        when(attachmentMapper.selectById(9L)).thenReturn(onboarding);
        assertThrows(ServiceException.class, () -> attachmentService.getAttachment(9L));
    }

    @Test
    void genericGetRejectsCaseAliasOnboardingBusinessType() {
        AttachmentDO onboarding = new AttachmentDO();
        onboarding.setId(9L);
        onboarding.setBusinessType("HRM_EMPLOYEE_ARCHIVE_ONBOARDING");
        when(attachmentMapper.selectById(9L)).thenReturn(onboarding);
        assertThrows(ServiceException.class, () -> attachmentService.getAttachment(9L));
    }

    @Test
    void genericListRejectsEntryBillType201() {
        assertThrows(ServiceException.class, () ->
                attachmentService.getAttachmentListByBusiness("201", 1L));
        assertThrows(ServiceException.class, () ->
                attachmentService.getAttachmentListByBusiness(" 201 ", 1L));
        assertThrows(ServiceException.class, () ->
                attachmentService.saveAttachmentList("HRM_EMPLOYEE_ARCHIVE_ONBOARDING", 1L, List.of()));
    }

    @Test
    void genericDeleteRejectsOnboardingBusinessType() {
        AttachmentDO onboarding = new AttachmentDO();
        onboarding.setId(9L);
        onboarding.setBusinessType(AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE);
        when(attachmentMapper.selectById(9L)).thenReturn(onboarding);
        assertThrows(ServiceException.class, () -> attachmentService.deleteAttachment(9L));
        verify(attachmentMapper, never()).deleteById(9L);
    }

    @Test
    void genericListByBusinessRejectsOnboardingType() {
        assertThrows(ServiceException.class, () ->
                attachmentService.getAttachmentListByBusiness(
                        AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 1L));
    }

    @Test
    void internalGetAndListAllowOnboarding() {
        AttachmentDO onboarding = new AttachmentDO();
        onboarding.setId(9L);
        onboarding.setBusinessType(AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE);
        when(attachmentMapper.selectById(9L)).thenReturn(onboarding);
        when(attachmentMapper.selectListByBusiness(
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 1L))
                .thenReturn(List.of(onboarding));

        assertEquals(9L, attachmentService.getAttachmentInternal(9L).getId());
        assertEquals(1, attachmentService.getAttachmentListByBusinessInternal(
                AttachmentServiceImpl.RESERVED_ONBOARDING_BUSINESS_TYPE, 1L).size());
    }

    @Test
    void listFillsMissingSizeFromUniqueFileUrlWithoutDownloading() {
        AttachmentDO row = new AttachmentDO();
        row.setId(27L);
        row.setBusinessType("103");
        row.setBusinessId(19L);
        row.setFileId(null);
        row.setFileUrl("http://files/weixin.jpg");
        row.setFilePath("20260907/weixin.jpg");
        row.setFileSize(0L);
        row.setFileType(null);
        when(attachmentMapper.selectListByBusiness("103", 19L)).thenReturn(List.of(row));
        FileRespDTO meta = file(2493L, "20260907/weixin.jpg", "微信图片.jpg");
        meta.setSize(106878L);
        meta.setType("image/jpeg");
        when(fileAccessApi.getUniqueFileByUrl("http://files/weixin.jpg")).thenReturn(meta);

        List<AttachmentDO> listed = attachmentService.getAttachmentListByBusiness("103", 19L);

        assertEquals(1, listed.size());
        assertEquals(2493L, listed.get(0).getFileId());
        assertEquals(106878L, listed.get(0).getFileSize());
        assertEquals("image/jpeg", listed.get(0).getFileType());
        assertEquals("jpg", listed.get(0).getFileExtension());
        verify(fileAccessApi, never()).getFileContent(anyLong());
        verify(attachmentMapper, never()).insertOrUpdate(anyList());
        verify(attachmentMapper, never()).updateById(any(AttachmentDO.class));
    }

    @Test
    void listLeavesUnknownWhenNoUniqueFileMetadata() {
        AttachmentDO row = new AttachmentDO();
        row.setId(28L);
        row.setBusinessType("103");
        row.setBusinessId(19L);
        row.setFileUrl("http://files/missing.jpg");
        row.setFilePath("missing.jpg");
        row.setFileSize(0L);
        when(attachmentMapper.selectListByBusiness("103", 19L)).thenReturn(List.of(row));
        when(fileAccessApi.getUniqueFileByUrl("http://files/missing.jpg")).thenReturn(null);
        when(fileAccessApi.getUniqueFileByPath("missing.jpg")).thenReturn(null);

        List<AttachmentDO> listed = attachmentService.getAttachmentListByBusiness("103", 19L);

        assertNull(listed.get(0).getFileSize());
        verify(fileAccessApi, never()).getFileContent(anyLong());
    }

    @Test
    void listDoesNotOverwriteAuthoritativeNonZeroSize() {
        AttachmentDO row = new AttachmentDO();
        row.setId(29L);
        row.setBusinessType("103");
        row.setBusinessId(19L);
        row.setFileId(9L);
        row.setFileSize(12L);
        row.setFileType("application/pdf");
        when(attachmentMapper.selectListByBusiness("103", 19L)).thenReturn(List.of(row));

        List<AttachmentDO> listed = attachmentService.getAttachmentListByBusiness("103", 19L);

        assertEquals(12L, listed.get(0).getFileSize());
        verify(fileAccessApi, never()).getUniqueFileByUrl(any());
        verify(fileAccessApi, never()).getUniqueFileByPath(any());
    }

    @Test
    void saveResolvesZeroSizeFromUniqueUrl() {
        when(attachmentMapper.selectListByBusiness("103", 19L)).thenReturn(List.of());
        FileRespDTO meta = file(2493L, "20260907/weixin.jpg", "微信图片.jpg");
        meta.setSize(106878L);
        meta.setType("image/jpeg");
        when(fileAccessApi.getUniqueFileByUrl("http://files/weixin.jpg")).thenReturn(meta);

        AttachmentSaveReqVO req = base("weixin.jpg");
        req.setFileUrl("http://files/weixin.jpg");
        req.setFilePath("http://files/weixin.jpg");
        req.setFileSize(0L);
        req.setFileId(null);
        attachmentService.saveAttachmentList("103", 19L, List.of(req));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        AttachmentDO saved = captor.getValue().get(0);
        assertEquals(2493L, saved.getFileId());
        assertEquals(106878L, saved.getFileSize());
        assertEquals("image/jpeg", saved.getFileType());
        verify(fileAccessApi, never()).getFileContent(anyLong());
    }

    @Test
    void saveResolvesOmittedSizeFromUniqueUrl() {
        when(attachmentMapper.selectListByBusiness("103", 19L)).thenReturn(List.of());
        FileRespDTO meta = file(2493L, "20260907/weixin.jpg", "微信图片.jpg");
        meta.setSize(106878L);
        meta.setType("image/jpeg");
        when(fileAccessApi.getUniqueFileByUrl("/admin-api/infra/file/1/get/20260907/weixin.jpg")).thenReturn(meta);

        AttachmentSaveReqVO req = base("weixin.jpg");
        req.setFileUrl("/admin-api/infra/file/1/get/20260907/weixin.jpg");
        req.setFilePath("/admin-api/infra/file/1/get/20260907/weixin.jpg");
        req.setFileSize(null);
        req.setFileId(null);
        req.setFileType(null);
        req.setFileExtension(null);
        attachmentService.saveAttachmentList("103", 19L, List.of(req));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentMapper).insertOrUpdate(captor.capture());
        AttachmentDO saved = captor.getValue().get(0);
        assertEquals(2493L, saved.getFileId());
        assertEquals(106878L, saved.getFileSize());
        assertEquals("image/jpeg", saved.getFileType());
        assertEquals("jpg", saved.getFileExtension());
        verify(fileAccessApi, never()).getFileContent(anyLong());
    }

}
