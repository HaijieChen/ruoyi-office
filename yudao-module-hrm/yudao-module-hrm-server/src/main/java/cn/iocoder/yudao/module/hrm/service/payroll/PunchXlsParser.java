package cn.iocoder.yudao.module.hrm.service.payroll;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PunchXlsParser {

    public record PunchRow(String name, String date, boolean absence, boolean late) {
    }

    public record ParseResult(List<String> headers, List<PunchRow> rows, Set<String> names) {
    }

    public ParseResult parse(InputStream in) throws Exception {
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            if (header != null) {
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    headers.add(formatter.formatCellValue(header.getCell(c)).trim());
                }
            }
            int nameIdx = indexOf(headers, "姓名");
            int dateIdx = indexOf(headers, "日期");
            int absenceIdx = indexOf(headers, "是否旷工");
            int lateIdx = indexOf(headers, "迟到时间");
            List<PunchRow> rows = new ArrayList<>();
            Set<String> names = new LinkedHashSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String name = cell(formatter, row, nameIdx);
                if (name.isEmpty()) {
                    continue;
                }
                names.add(name);
                String late = cell(formatter, row, lateIdx);
                String absence = cell(formatter, row, absenceIdx);
                rows.add(new PunchRow(
                        name,
                        cell(formatter, row, dateIdx),
                        isTrue(absence),
                        !late.isEmpty()
                ));
            }
            return new ParseResult(headers, rows, names);
        }
    }

    private static int indexOf(List<String> headers, String name) {
        int i = headers.indexOf(name);
        return i < 0 ? -1 : i;
    }

    private static String cell(DataFormatter formatter, Row row, int idx) {
        if (idx < 0) {
            return "";
        }
        Cell cell = row.getCell(idx);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static boolean isTrue(String v) {
        return "TRUE".equalsIgnoreCase(v) || "1".equals(v) || "是".equals(v);
    }
}
