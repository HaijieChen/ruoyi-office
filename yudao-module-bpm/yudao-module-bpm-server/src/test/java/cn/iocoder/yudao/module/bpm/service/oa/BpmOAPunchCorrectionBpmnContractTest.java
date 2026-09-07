package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** U5：补卡 BPMN / model / 菜单 SQL 字符串契约 */
class BpmOAPunchCorrectionBpmnContractTest {

    @Test
    void bpmnHasDeptThenHrOrSignWithoutGatewayOrCopy() throws Exception {
        Path xml = findRoot().resolve("sql/mysql/bpmn/oa_punch_correction.bpmn20.xml");
        assertTrue(Files.exists(xml));
        String text = Files.readString(xml);

        assertTrue(text.contains("id=\"oa_punch_correction\""));
        assertTrue(text.contains("candidateStrategy=\"38\""));
        assertTrue(text.contains("candidateParam=\"2\""));
        assertTrue(text.contains("candidateStrategy=\"10\""));
        assertTrue(text.contains("candidateParam=\"hr_admin\""));
        assertTrue(text.contains("<flowable:approveMethod>3</flowable:approveMethod>"));
        assertTrue(text.contains("sourceRef=\"taskDeptLeaderMulti\" targetRef=\"taskHr\""));
        assertTrue(text.contains("sourceRef=\"taskHr\" targetRef=\"endEvent\""));
        assertFalse(text.contains("exclusiveGateway"));
        assertFalse(text.contains("bpmCopyTaskDelegate"));
        assertFalse(text.contains("candidateParam=\"gm\""));
        assertFalse(text.contains("INSERT INTO"));
    }

    @Test
    void modelSqlIsIdempotentFormType20() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/bpm_oa_punch_correction_model.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);

        assertTrue(text.contains("form_type` = 20"));
        assertTrue(text.contains("'/bpm/oa/punch/create'"));
        assertTrue(text.contains("'/bpm/oa/punch/detail'"));
        assertTrue(text.contains("LIKE 'oa_punch_correction%'"));
        assertTrue(text.contains("`KEY_` = 'oa_punch_correction'"));
        assertFalse(text.contains("INSERT INTO `bpm_process_definition"));
        assertFalse(text.contains("INSERT INTO ACT_RE_PROCDEF"));
    }

    @Test
    void menuSqlIsIdempotentUnderBpmOaParentAndGrantsQueryOnly() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/bpm_oa_punch_correction_menu.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);

        assertTrue(text.contains("WHERE NOT EXISTS"));
        assertTrue(text.contains("bpm/oa/leave/index"));
        assertTrue(text.contains("bpm/oa/punch/index"));
        assertTrue(text.contains("bpm:oa-punch-correction:query"));
        assertTrue(text.contains("bpm:oa-punch-correction:create"));
        assertTrue(text.contains("'hr_admin'"));
        assertTrue(text.contains("'super_admin'"));
        assertFalse(text.contains("finance/"));
        assertFalse(grantBlock(text).contains("bpm:oa-punch-correction:create"));
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
