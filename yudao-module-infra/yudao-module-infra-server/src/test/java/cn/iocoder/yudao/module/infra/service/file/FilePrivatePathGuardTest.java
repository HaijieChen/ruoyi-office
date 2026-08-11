package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * #1：最终 path 含私有前缀（含 name 注入）必须拒绝。
 */
@ExtendWith(MockitoExtension.class)
class FilePrivatePathGuardTest {

    @InjectMocks
    private FileServiceImpl fileService;

    @Mock
    private FileConfigService fileConfigService;
    @Mock
    private cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper fileMapper;

    @BeforeEach
    void flags() {
        FileServiceImpl.PATH_PREFIX_DATE_ENABLE = true;
        FileServiceImpl.PATH_SUFFIX_TIMESTAMP_ENABLE = true;
    }

    @Test
    void presignRejectsNameInjectedPrivatePrefixWithoutExtension() {
        // name=hrm-onboarding-private/evil 无扩展名 → 最终 path 含私有目录
        assertThrows(IllegalArgumentException.class, () ->
                fileService.presignPutUrl("hrm-onboarding-private/evil", null));
        verify(fileConfigService, never()).getMasterFileClient();
    }

    @Test
    void createFileReturnRejectsNameInjectedPrivatePrefix() {
        assertThrows(IllegalArgumentException.class, () ->
                fileService.createFileReturn(new byte[]{1}, "hrm-onboarding-private/scan", null, "application/pdf"));
    }

    @Test
    void createFileReturnAllowPrivateSucceedsForOnboardingDir() throws Exception {
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.getId()).thenReturn(1L);
        when(client.upload(any(), anyString(), anyString())).thenReturn("http://x/private");
        var created = fileService.createFileReturnAllowPrivate(
                new byte[]{1, 2}, "scan.pdf", "hrm-onboarding-private", "application/pdf");
        assertNotNull(created);
        verify(fileMapper).insert(argThat((cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO f) ->
                f.getPath() != null && f.getPath().contains("hrm-onboarding-private")));
    }

    @Test
    void generatePathWithPrivateNameIsDetectedByReject() {
        String path = fileService.generateUploadPath("hrm-onboarding-private/evil", null);
        assertTrue(path.contains("hrm-onboarding-private"));
        assertThrows(IllegalArgumentException.class,
                () -> fileService.rejectPrivateStoragePath(path, false));
        assertDoesNotThrow(() -> fileService.rejectPrivateStoragePath(path, true));
    }

}
