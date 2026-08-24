package cn.iocoder.yudao.module.hrm.service.payroll;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DeductXlsParser {

    public record DeductRow(String employeeNo, String name, BigDecimal tax, BigDecimal social, BigDecimal housing) {
    }

    public List<DeductRow> parse(InputStream in) throws Exception {
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
            int noIdx = first(headers, "工号", "员工编号");
            int nameIdx = headers.indexOf("姓名");
            int taxIdx = first(headers, "个人所得税", "个税");
            int socialIdx = first(headers, "社保扣除", "社保");
            int housingIdx = first(headers, "公积金扣除", "公积金");
            List<DeductRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String no = cell(formatter, row, noIdx);
                String name = cell(formatter, row, nameIdx);
                if (no.isEmpty() && name.isEmpty()) {
                    continue;
                }
                rows.add(new DeductRow(no, name, decimal(formatter, row, taxIdx),
                        decimal(formatter, row, socialIdx), decimal(formatter, row, housingIdx)));
            }
            return rows;
        }
    }

    private static int first(List<String> headers, String... names) {
        for (String name : names) {
            int i = headers.indexOf(name);
            if (i >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static String cell(DataFormatter formatter, Row row, int idx) {
        if (idx < 0) {
            return "";
        }
        Cell cell = row.getCell(idx);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static BigDecimal decimal(DataFormatter formatter, Row row, int idx) {
        String text = cell(formatter, row, idx).replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
