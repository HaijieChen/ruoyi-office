package cn.iocoder.yudao.module.infra.framework.file.core.client.db;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileContentDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileContentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * TC-SIZE-01：DB provider Data truncation 须映射为业务 4xx（ServiceException），非未处理 500。
 */
@ExtendWith(MockitoExtension.class)
class DBFileClientUploadGuardTest {

    @Mock
    private FileContentMapper fileContentMapper;

    @Test
    void upload_dataTruncation_mapsToServiceException4xx() {
        DBFileClientConfig cfg = new DBFileClientConfig();
        cfg.setDomain("http://127.0.0.1:48080");
        DBFileClient client = new DBFileClient(4L, cfg);
        ReflectionTestUtils.setField(client, "fileContentMapper", fileContentMapper);
        ReflectionTestUtils.setField(client, "config", cfg);

        doThrow(new DataIntegrityViolationException(
                "Data truncation: Data too long for column 'content' at row 1"))
                .when(fileContentMapper).insert(any(FileContentDO.class));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.upload(new byte[20 * 1024 * 1024], "hrm-onboarding-private/a.pdf", "application/pdf"));
        assertEquals(1_001_003_003, ex.getCode());
    }

    @Test
    void isDataTruncation_detectsMySqlMessages() {
        assertTrue(DBFileClient.isDataTruncation(
                new RuntimeException("Data truncation: Data too long for column 'content'")));
        assertTrue(DBFileClient.isDataTruncation(
                new RuntimeException(new Exception("Packet for query is too large"))));
        assertFalse(DBFileClient.isDataTruncation(new RuntimeException("connection reset")));
    }

}
