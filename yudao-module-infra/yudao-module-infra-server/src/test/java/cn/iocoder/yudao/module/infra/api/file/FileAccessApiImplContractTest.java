package cn.iocoder.yudao.module.infra.api.file;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #3：FileAccessApi 仅本地 Service，非 RPC/RestController。
 */
class FileAccessApiImplContractTest {

    @Test
    void fileAccessApiImplIsLocalServiceOnly() {
        assertTrue(FileAccessApiImpl.class.isAnnotationPresent(Service.class));
        assertFalse(FileAccessApiImpl.class.isAnnotationPresent(RestController.class));
        assertTrue(FileAccessApi.class.isAssignableFrom(FileAccessApiImpl.class));
    }

}
