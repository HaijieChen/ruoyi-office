package cn.iocoder.yudao.module.hrm.service.employee;

import cn.iocoder.yudao.module.hrm.HrmServerApplication;
import cn.iocoder.yudao.module.infra.api.file.FileAccessApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.FilePrivateDirs;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #4 部署约束可执行证据：standalone HRM 启动禁止；FileAccessApi 非 RPC。
 */
class DeploymentFormConstraintTest {

    @Test
    void standaloneHrmMainIsForbidden() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> HrmServerApplication.main(new String[0]));
        assertTrue(ex.getMessage().contains("forbidden")
                || ex.getMessage().contains("monolith")
                || ex.getMessage().contains("yudao-server"));
    }

    @Test
    void hrmServerPomForcesSkipRepackage() throws Exception {
        // 从模块源码树读取 pom（测试 classpath 相对路径）
        java.nio.file.Path pom = java.nio.file.Paths.get(
                System.getProperty("user.dir"), "pom.xml");
        if (!java.nio.file.Files.exists(pom)) {
            // surefire 可能在 module 根
            pom = java.nio.file.Paths.get("yudao-module-hrm/yudao-module-hrm-server/pom.xml");
        }
        assertTrue(java.nio.file.Files.exists(pom), "hrm-server pom not found: " + pom);
        String xml = java.nio.file.Files.readString(pom, StandardCharsets.UTF_8);
        assertTrue(xml.contains("<skip>true</skip>"),
                "hrm-server must force spring-boot repackage skip=true");
        assertFalse(xml.contains("<skip>${skip.repackage}</skip>"),
                "must not depend on cloud skip.repackage=false");
    }

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
    }

}
