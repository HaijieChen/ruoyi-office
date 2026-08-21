package cn.iocoder.yudao.module.hrm.service.payroll;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunchXlsParserTest {

    @Test
    void parseGeneratedHssfWithEmployeeNo() throws Exception {
        byte[] bytes;
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            String[] cols = {"工号", "姓名", "日期", "迟到时间", "是否旷工"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            for (int i = 0; i < 3; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue("E0" + i);
                row.createCell(1).setCellValue("人" + i);
                row.createCell(2).setCellValue("2026/8/10");
                row.createCell(4).setCellValue("");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            bytes = out.toByteArray();
        }
        PunchXlsParser.ParseResult result = new PunchXlsParser().parse(new ByteArrayInputStream(bytes));
        assertTrue(result.headers().contains("工号"));
        assertEquals(3, result.names().size());
        assertEquals("E00", result.rows().get(0).employeeNo());
    }
}
