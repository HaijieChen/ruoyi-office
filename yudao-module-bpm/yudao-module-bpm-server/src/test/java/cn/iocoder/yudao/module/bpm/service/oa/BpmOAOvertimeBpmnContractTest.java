package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** U4：加班 BPMN / model / 菜单 SQL 字符串契约 */
class BpmOAOvertimeBpmnContractTest {

    @Test
    void bpmnHasDeptHrGmHolidayAndCopyWangPeng() throws Exception {
        Path xml = findRoot().resolve("sql/mysql/bpmn/oa_overtime.bpmn20.xml");
        assertTrue(Files.exists(xml));
        String text = Files.readString(xml);

        assertTrue(text.contains("id=\"oa_overtime\""));
        assertTrue(text.contains("candidateStrategy=\"38\""));
        assertTrue(text.contains("candidateParam=\"2\""));
        assertTrue(text.contains("candidateStrategy=\"10\""));
        assertTrue(text.contains("candidateParam=\"hr_admin\""));
        assertTrue(text.contains("candidateParam=\"gm\""));
        assertTrue(text.contains("${holiday == true}"));
        assertTrue(text.contains("${holiday == false}"));
        assertTrue(text.contains("${bpmCopyTaskDelegate}"));
        assertTrue(text.contains("<flowable:candidateStrategy>30</flowable:candidateStrategy>"));
        assertTrue(text.contains("<flowable:candidateParam>221</flowable:candidateParam>"));
        assertTrue(text.contains("nickname=王鹏"));
        assertFalse(text.contains("INSERT INTO"));
    }

    @Test
    void modelSqlIsIdempotentFormType20AndFailsWithoutWangPeng() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/bpm_oa_overtime_model.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);

        assertTrue(text.contains("form_type` = 20"));
        assertTrue(text.contains("'/bpm/oa/overtime/create'"));
        assertTrue(text.contains("'/bpm/oa/overtime/detail'"));
        assertTrue(text.contains("LIKE 'oa_overtime%'"));
        assertTrue(text.contains("nickname` = '王鹏'"));
        assertTrue(text.contains("SELECT `id`"));
        assertTrue(text.contains("INTO @oa_overtime_wangpeng_id"));
        assertFalse(text.contains("INSERT INTO `bpm_process_definition"));
        assertFalse(text.contains("INSERT INTO ACT_RE_PROCDEF"));
    }

    @Test
    void menuSqlIsIdempotentUnderBpmOaParentAndGrantsQueryOnly() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/bpm_oa_overtime_menu.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);

        assertTrue(text.contains("WHERE NOT EXISTS"));
        assertTrue(text.contains("bpm/oa/leave/index"));
        assertTrue(text.contains("bpm/oa/overtime/index"));
        assertTrue(text.contains("bpm:oa-overtime:query"));
        assertTrue(text.contains("bpm:oa-overtime:create"));
        assertTrue(text.contains("'hr_admin'"));
        assertTrue(text.contains("'super_admin'"));
        assertFalse(text.contains("finance/"));
        assertFalse(grantBlock(text).contains("bpm:oa-overtime:create"));
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
