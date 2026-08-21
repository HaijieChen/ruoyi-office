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
    void parseGeneratedHssf() throws Exception {
        byte[] bytes;
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            String[] cols = {"序号", "考勤号码", "自定义编号", "姓名", "是否智能排班", "日期", "对应时段",
                    "上班时间", "下班时间", "签到时间", "签退时间", "应到", "实到", "迟到时间", "早退时间",
                    "是否旷工", "加班时间", "工作时间", "例外情况", "应签到", "应签退", "部门", "平日", "周末",
                    "节假日", "出勤时间", "平日加班", "周末加班", "节假日加班"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            for (int i = 0; i < 39; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(3).setCellValue("人" + i);
                row.createCell(5).setCellValue("2026/8/10");
                row.createCell(15).setCellValue("");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            bytes = out.toByteArray();
        }
        PunchXlsParser.ParseResult result = new PunchXlsParser().parse(new ByteArrayInputStream(bytes));
        assertEquals(29, result.headers().size());
        assertTrue(result.headers().contains("姓名"));
        assertEquals(39, result.names().size());
    }
}
