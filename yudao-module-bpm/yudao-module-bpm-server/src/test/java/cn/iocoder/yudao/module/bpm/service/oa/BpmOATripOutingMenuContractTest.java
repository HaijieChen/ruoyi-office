package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 出差/外出菜单 SQL、目录 embed、模型 form path 契约；U8 加班/补卡假勤目录嵌入 */
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
    void catalogEmbedsTripAndOutingInStartShell() throws Exception {
        Path fe = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/create/embed-registry.ts");
        String ts = Files.readString(fe);
        assertTrue(ts.contains("oa_business_trip: ()"));
        assertTrue(ts.contains("oa_outing: ()"));
        assertTrue(ts.contains("bpm/oa/trip/modules/form-body.vue"));
        assertTrue(ts.contains("bpm/oa/outing/modules/form-body.vue"));
        assertFalse(ts.contains("oa_business_trip: '/bpm/oa/trip/create'"));
        assertFalse(ts.contains("oa_outing: '/bpm/oa/outing/create'"));

        Path registry = findRoot().resolve(
                "yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmEmbedProcessStartPermissionRegistry.java");
        String java = Files.readString(registry);
        assertFalse(java.contains("oa_business_trip"));
        assertFalse(java.contains("oa_outing"));
    }

    @Test
    void catalogEmbedsOvertimeAndPunchInAttendanceStartShell() throws Exception {
        Path fe = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/create/embed-registry.ts");
        String ts = Files.readString(fe);
        assertTrue(ts.contains("oa_overtime: ()"));
        assertTrue(ts.contains("oa_punch_correction: ()"));
        assertTrue(ts.contains("bpm/oa/overtime/modules/form-body.vue"));
        assertTrue(ts.contains("bpm/oa/punch/modules/form-body.vue"));
        assertFalse(ts.contains("oa_overtime: '/bpm/oa/overtime/create'"));
        assertFalse(ts.contains("oa_punch_correction: '/bpm/oa/punch/create'"));

        Path registry = findRoot().resolve(
                "yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmEmbedProcessStartPermissionRegistry.java");
        String java = Files.readString(registry);
        assertFalse(java.contains("oa_overtime"));
        assertFalse(java.contains("oa_punch_correction"));

        Path catalog = findRoot().resolve("sql/mysql/bpm_process_start_catalog.sql");
        String sql = Files.readString(catalog);
        assertTrue(sql.contains("WHEN 'oa_overtime' THEN 'attendance'"));
        assertTrue(sql.contains("WHEN 'oa_punch_correction' THEN 'attendance'"));
        assertFalse(sql.contains("bpm_model"));
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

    @Test
    void tripDetailUsesApprovalShellAndFilePreview() throws Exception {
        Path constants = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/constants.ts");
        String constantsText = Files.readString(constants);
        assertTrue(constantsText.contains("'/bpm/oa/trip/detail'"),
                "出差 formCustomViewPath 必须进 P-shell 白名单");

        Path data = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/oa/trip/data.ts");
        String dataText = Files.readString(data);
        assertTrue(dataText.contains("FilePreviewList"),
                "详情附件须用 FilePreviewList 预览，不能只拼 URL");
        assertFalse(dataText.contains("val.join('\\n')"));

        Path detail = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/oa/trip/detail.vue");
        String detailText = Files.readString(detail);
        assertTrue(detailText.contains("ApprovalOverviewPanel"),
                "列表详情须展示审批全貌");
        assertTrue(detailText.contains("processInstanceId"));
        assertTrue(detailText.contains("inProcessShell"),
                "流程壳传入 processInstance 时只渲染字段，不包整页");
        int shellIdx = detailText.indexOf("v-if=\"inProcessShell\"");
        int wrapIdx = detailText.indexOf("<ContentWrap");
        assertTrue(shellIdx >= 0 && wrapIdx > shellIdx,
                "壳内字段布局必须排在 ContentWrap 之前，避免盖住审批时间线");

        assertTrue(constantsText.contains("'/bpm/oa/leave/detail'"),
                "请假 formCustomViewPath 必须进 P-shell 白名单");
        assertTrue(constantsText.contains("'/bpm/oa/outing/detail'"),
                "外出 formCustomViewPath 必须进 P-shell 白名单");
        assertShellFieldOnly(
                findRoot().resolve("ruoyi-office-vben/apps/web-antd/src/views/bpm/oa/leave/detail.vue"),
                "请假");
        assertShellFieldOnly(
                findRoot().resolve("ruoyi-office-vben/apps/web-antd/src/views/bpm/oa/outing/detail.vue"),
                "外出");
    }

    private static void assertShellFieldOnly(Path detail, String label) throws Exception {
        String detailText = Files.readString(detail);
        assertTrue(detailText.contains("inProcessShell"),
                label + " 流程壳传入 processInstance 时只渲染字段，不包整页");
        int shellIdx = detailText.indexOf("v-if=\"inProcessShell\"");
        int wrapIdx = detailText.indexOf("<ContentWrap");
        assertTrue(shellIdx >= 0 && wrapIdx > shellIdx,
                label + " 壳内字段布局必须排在 ContentWrap 之前，避免盖住审批时间线");
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
