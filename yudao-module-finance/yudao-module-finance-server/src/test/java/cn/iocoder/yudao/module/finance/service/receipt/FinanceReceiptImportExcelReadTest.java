package cn.iocoder.yudao.module.finance.service.receipt;

import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归：含 yyyy-MM-dd 文本日期的 xlsx 应能整表读入，不抛 ExcelDataConvertException。
 */
class FinanceReceiptImportExcelReadTest {

    @TempDir
    Path tempDir;

    @Test
    void readShouldAcceptDateOnlyTextAndNumericExcelDates() throws Exception {
        Path file = tempDir.resolve("bank-receipt-import.xlsx");
        // 用 VO 写出：一行日期时间文本、一行日期-only 文本（模拟手工改单元格）
        List<FinanceReceiptImportExcelVO> seed = List.of(
                FinanceReceiptImportExcelVO.builder()
                        .entityCompanyName("示例主体公司")
                        .bankAccount("文枢建行颛桥支行")
                        .transactionDate("2026-05-14 00:00:00")
                        .payerName("北京开心袋鼠软件有限公司")
                        .payerAccount("6222000033334444")
                        .transactionAmount(new BigDecimal("1206.90"))
                        .summary("demo")
                        .bankSerialNo("BANK-SERIAL-DEMO-023")
                        .build(),
                FinanceReceiptImportExcelVO.builder()
                        .entityCompanyName("示例主体公司")
                        .bankAccount("文枢建行颛桥支行")
                        .transactionDate("2026-05-25")
                        .payerName("安徽梦帆网络科技有限公司")
                        .payerAccount("6222000033334444")
                        .transactionAmount(new BigDecimal("23185.50"))
                        .summary("demo text date")
                        .bankSerialNo("BANK-SERIAL-DEMO-024")
                        .build()
        );
        FastExcelFactory.write(file.toFile(), FinanceReceiptImportExcelVO.class)
                .sheet("银行到款")
                .doWrite(seed);

        // 再读回
        List<FinanceReceiptImportExcelVO> rows;
        try (InputStream in = Files.newInputStream(file)) {
            rows = FastExcelFactory.read(in, FinanceReceiptImportExcelVO.class, null)
                    .autoCloseStream(false)
                    .doReadAllSync();
        }

        assertEquals(2, rows.size());
        assertEquals("2026-05-14 00:00:00", rows.get(0).getTransactionDate());
        assertEquals("2026-05-25", rows.get(1).getTransactionDate());
        assertEquals(LocalDateTime.of(2026, 5, 14, 0, 0, 0),
                FinanceReceiptImportDateParser.tryParse(rows.get(0).getTransactionDate()));
        assertEquals(LocalDateTime.of(2026, 5, 25, 0, 0, 0),
                FinanceReceiptImportDateParser.tryParse(rows.get(1).getTransactionDate()));
    }

    @Test
    void readProblemFileIfPresentShouldNotThrowOnDateOnlyCells() throws Exception {
        Path problem = Path.of(
                "/Users/chenhaijie/Library/Containers/com.tencent.WeWorkMac/Data/Documents/Profiles/"
                        + "5C3683E05E0A5A2922F52602F85C9EA0/Caches/Files/2026-07/"
                        + "719d09beb7d773519a1db75f2c6eefb7/银行到款导入模板.xls");
        if (!Files.isRegularFile(problem)) {
            return; // 环境无该文件则跳过
        }
        List<FinanceReceiptImportExcelVO> rows;
        try (InputStream in = Files.newInputStream(problem)) {
            rows = FastExcelFactory.read(in, FinanceReceiptImportExcelVO.class, null)
                    .autoCloseStream(false)
                    .doReadAllSync();
        }
        assertEquals(32, rows.size());
        // 第 25 行（index 23）原为 yyyy-MM-dd 文本
        assertEquals("2026-05-25", rows.get(23).getTransactionDate());
        assertNotNull(FinanceReceiptImportDateParser.tryParse(rows.get(23).getTransactionDate()));
        // 数值日期行应被 converter 规范为 yyyy-MM-dd HH:mm:ss
        assertNotNull(FinanceReceiptImportDateParser.tryParse(rows.get(0).getTransactionDate()));
        assertTrue(rows.stream().allMatch(r -> FinanceReceiptImportDateParser.tryParse(r.getTransactionDate()) != null));
    }
}
