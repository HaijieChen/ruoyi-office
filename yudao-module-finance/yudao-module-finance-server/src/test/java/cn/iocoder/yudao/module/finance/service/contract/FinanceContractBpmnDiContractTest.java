package cn.iocoder.yudao.module.finance.service.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CS-T3：BPMN 种子必须含 DI，且 process key / needMail / end delegate 约定存在。
 */
class FinanceContractBpmnDiContractTest {

    @Test
    void bpmnSeedMustContainDiagramDiAndProcessKey() throws Exception {
        Path root = findRepositoryRoot();
        Path bpmn = root.resolve("sql/mysql/bpmn/finance_contract_sign.bpmn20.xml");
        assertTrue(Files.exists(bpmn), "BPMN seed must exist");
        String xml = Files.readString(bpmn);
        String lower = xml.toLowerCase(Locale.ROOT);

        assertTrue(xml.contains("id=\"finance_contract_sign\"") || xml.contains("id='finance_contract_sign'"));
        assertTrue(lower.contains("bpmndiagram"), "must include BPMNDiagram for designer");
        assertTrue(lower.contains("bpmnshape") && lower.contains("bpmnedge"));
        assertTrue(xml.contains("needMail"));
        assertTrue(xml.contains("taskSeal") && xml.contains("taskArchive") && xml.contains("taskMail"));
        assertTrue(xml.contains("financeContractApprovalOutcomeDelegate"));
        assertTrue(xml.contains("gatewayMail"));
    }

    @Test
    void diValidationSqlShouldExist() throws Exception {
        Path sql = findRepositoryRoot().resolve("sql/mysql/finance_contract_bpmn_diagram_di.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);
        assertTrue(text.contains("finance_contract_sign"));
        assertTrue(text.contains("BPMNDiagram"));
    }

    private static Path findRepositoryRoot() {
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
