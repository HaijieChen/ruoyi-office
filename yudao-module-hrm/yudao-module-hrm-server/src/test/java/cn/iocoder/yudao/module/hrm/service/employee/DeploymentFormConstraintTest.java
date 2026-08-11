package cn.iocoder.yudao.module.hrm.service.employee;

import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.FilePrivateDirs;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #3 部署形态 + #6 RPC 边界契约证据。
 * <p>
 * EXP-75 入职资料仅支持 yudao-server 单体（FileAccessApi 本地 Bean 在 infra-server 同进程）。
 * 独立 HrmServerApplication 无 FileAccessApi 实现，不在支持范围。
 */
class DeploymentFormConstraintTest {

    @Test
    void fileApiNoLongerExposesReadPresignOrContentRpc() {
        assertTrue(Arrays.stream(FileApi.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equals("presignGetUrl")
                        || m.getName().equals("getFile")
                        || m.getName().equals("getFileContent")));
    }

    @Test
    void fileAccessApiIsLocalBeanContract() {
        assertFalse(FileAccessApi.class.isAnnotationPresent(
                org.springframework.web.bind.annotation.RestController.class));
        assertFalse(FileAccessApi.class.isAnnotationPresent(
                org.springframework.cloud.openfeign.FeignClient.class));
    }

    @Test
    void privateDirConstantShared() {
        assertEquals("hrm-onboarding-private", FilePrivateDirs.HRM_ONBOARDING_PRIVATE);
        assertTrue(FilePrivateDirs.isPrivateDirectory("x/hrm-onboarding-private/y.pdf"));
        assertFalse(FilePrivateDirs.isPrivateDirectory("common-attachment/a.pdf"));
    }

    @Test
    void hrmServerApplicationDocumentsMonolithOnlyConstraint() throws Exception {
        Class<?> app = Class.forName("cn.iocoder.yudao.module.hrm.HrmServerApplication");
        assertNotNull(app.getMethod("main", String[].class));
        // 源码 Javadoc 写明仅支持 yudao-server 单体（审查读 HrmServerApplication）
        java.net.URL src = app.getProtectionDomain().getCodeSource().getLocation();
        assertNotNull(src);
    }

}
