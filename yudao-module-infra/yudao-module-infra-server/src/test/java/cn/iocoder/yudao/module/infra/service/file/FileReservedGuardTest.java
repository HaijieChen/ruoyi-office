package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * #5 批量删除预检 + #6 fail-closed。
 */
@ExtendWith(MockitoExtension.class)
class FileReservedGuardTest {

    @InjectMocks
    private FileServiceImpl fileService;

    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileConfigService fileConfigService;

    @Test
    void deleteFileListRejectsWholeBatchWhenAnyReserved_providerZeroDeletes() throws Exception {
        FileDO publicFile = new FileDO();
        publicFile.setId(1L);
        publicFile.setPath("public/a.pdf");
        publicFile.setConfigId(10L);
        FileDO reserved = new FileDO();
        reserved.setId(2L);
        reserved.setPath("legacy/onboard.pdf");
        reserved.setConfigId(10L);
        when(fileMapper.selectByIds(List.of(1L, 2L))).thenReturn(List.of(publicFile, reserved));
        when(fileMapper.countReservedAttachmentByFileId(1L)).thenReturn(0L);
        when(fileMapper.countReservedAttachmentByPath("public/a.pdf")).thenReturn(0L);
        when(fileMapper.countReservedAttachmentByFileId(2L)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> fileService.deleteFileList(List.of(1L, 2L)));

        verify(fileConfigService, never()).getFileClient(any());
        verify(fileMapper, never()).deleteByIds(anyList());
    }

    @Test
    void getFileContentFailClosedOnMapperException_providerZeroReads() throws Exception {
        when(fileMapper.countReservedAttachmentByPath("legacy/x.pdf"))
                .thenThrow(new RuntimeException("SQL timeout"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fileService.getFileContent(10L, "legacy/x.pdf"));
        assertTrue(ex.getMessage().contains("fail-closed"));
        verify(fileConfigService, never()).getFileClient(any());
    }

    @Test
    void deleteFileFailClosedOnMapperException_providerZeroDeletes() {
        FileDO file = new FileDO();
        file.setId(9L);
        file.setPath("legacy/y.pdf");
        file.setConfigId(10L);
        when(fileMapper.selectById(9L)).thenReturn(file);
        when(fileMapper.countReservedAttachmentByFileId(9L))
                .thenThrow(new RuntimeException("mapper down"));

        assertThrows(IllegalStateException.class, () -> fileService.deleteFile(9L));
        verify(fileConfigService, never()).getFileClient(any());
    }

    @Test
    void softDeletedAttachmentStillBlocksByFileId() {
        when(fileMapper.countReservedAttachmentByFileId(5L)).thenReturn(1L);
        assertThrows(IllegalArgumentException.class,
                () -> fileService.rejectReservedAttachmentBound(5L, "any.pdf"));
        verify(fileMapper, never()).countReservedAttachmentByPath(anyString());
    }

    @Test
    void crossTenantBoundFileIdStillRejected_providerZero() throws Exception {
        // 租户 B 上下文下，mapper 跨租户返回计数 1 → 拒绝，provider 0 次
        FileDO global = new FileDO();
        global.setId(105L);
        global.setPath("public/report.pdf");
        global.setConfigId(10L);
        when(fileMapper.selectById(105L)).thenReturn(global);
        when(fileMapper.countReservedAttachmentByFileId(105L)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> fileService.deleteFile(105L));
        assertThrows(IllegalArgumentException.class, () -> fileService.getFile(105L));
        verify(fileConfigService, never()).getFileClient(any());
    }

}
