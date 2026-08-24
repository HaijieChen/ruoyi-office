package cn.iocoder.yudao.module.hrm.service.payroll;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeductXlsParserTest {

    @Test
    void parseTaxSocialHousingAndSkipBlank() throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("工号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("个人所得税");
            header.createCell(3).setCellValue("社保扣除");
            header.createCell(4).setCellValue("公积金扣除");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("PX01");
            row.createCell(1).setCellValue("验收全勤");
            row.createCell(2).setCellValue(120);
            row.createCell(3).setCellValue(900);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            List<DeductXlsParser.DeductRow> rows = new DeductXlsParser().parse(new ByteArrayInputStream(out.toByteArray()));
            assertEquals(1, rows.size());
            assertEquals("PX01", rows.get(0).employeeNo());
            assertEquals(new BigDecimal("120"), rows.get(0).tax());
            assertEquals(new BigDecimal("900"), rows.get(0).social());
            assertNull(rows.get(0).housing());
        }
    }
}
