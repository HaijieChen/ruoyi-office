package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** U7：出差/外出菜单 SQL、目录 redirect、模型 form path 契约 */
class BpmOATripOutingMenuContractTest {

    @Test
    void menuSqlIsIdempotentUnderBpmOaParentAndGrantsQueryOnly() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/bpm_oa_trip_outing_menu.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);

        assertTrue(text.contains("WHERE NOT EXISTS"));
        assertTrue(text.contains("bpm/oa/leave/index"));
        assertTrue(text.contains("bpm/oa/trip/index"));
        assertTrue(text.contains("bpm/oa/outing/index"));
        assertFalse(text.contains("finance/"));
        assertFalse(text.contains("'finance'"));

        assertTrue(text.contains("bpm:oa-trip:query"));
        assertTrue(text.contains("bpm:oa-trip:create"));
        assertTrue(text.contains("bpm:oa-outing:query"));
        assertTrue(text.contains("bpm:oa-outing:create"));

        assertTrue(text.contains("'hr_admin'"));
        assertTrue(text.contains("'super_admin'"));
        assertTrue(text.contains("'bpm:oa-trip:query', 'bpm:oa-outing:query'"));
        assertFalse(grantBlock(text).contains("bpm:oa-trip:create"));
        assertFalse(grantBlock(text).contains("bpm:oa-outing:create"));

        Path mirrored = findRoot().resolve("ruoyi-office-vben/sql/mysql/bpm_oa_trip_outing_menu.sql");
        assertTrue(Files.exists(mirrored));
        assertEquals(text, Files.readString(mirrored));
    }

    @Test
    void catalogRedirectsTripAndOutingAndDoesNotEmbed() throws Exception {
        Path fe = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/create/embed-registry.ts");
        String ts = Files.readString(fe);
        assertTrue(ts.contains("oa_business_trip: '/bpm/oa/trip/create'"));
        assertTrue(ts.contains("oa_outing: '/bpm/oa/outing/create'"));
        assertFalse(ts.contains("oa_business_trip: ()"));
        assertFalse(ts.contains("oa_outing: ()"));

        Path registry = findRoot().resolve(
                "yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmEmbedProcessStartPermissionRegistry.java");
        String java = Files.readString(registry);
        assertFalse(java.contains("oa_business_trip"));
        assertFalse(java.contains("oa_outing"));
    }

    @Test
    void modelSqlAlreadyHasFormPaths() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/bpm_oa_trip_outing_model.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);
        assertTrue(text.contains("'/bpm/oa/trip/create'"));
        assertTrue(text.contains("'/bpm/oa/trip/detail'"));
        assertTrue(text.contains("'/bpm/oa/outing/create'"));
        assertTrue(text.contains("'/bpm/oa/outing/detail'"));
    }

    private static String grantBlock(String text) {
        int idx = text.indexOf("INSERT INTO `system_role_menu`");
        assertTrue(idx >= 0);
        return text.substring(idx);
    }

    private static Path findRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("repo root not found");
        }
        return current;
    }
}
