package cn.iocoder.yudao.module.hrm.service.payroll;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PayrollPunchApply {

    public record EmployeeName(Long id, String name, String employeeNo) {
    }

    public record Result(int matched, List<String> unmatched, Map<Long, BigDecimal> absenceByEmployeeId) {
    }

    public static Result apply(List<EmployeeName> employees, PunchXlsParser.ParseResult punch) {
        Map<String, Long> byNo = new LinkedHashMap<>();
        Map<String, Integer> noCounts = new LinkedHashMap<>();
        Map<String, Long> byName = new LinkedHashMap<>();
        List<String> archiveNames = new ArrayList<>();
        for (EmployeeName emp : employees) {
            if (emp.employeeNo() != null && !emp.employeeNo().isBlank()) {
                String no = emp.employeeNo().trim();
                noCounts.merge(no, 1, Integer::sum);
                byNo.put(no, emp.id());
            }
            if (emp.name() != null) {
                archiveNames.add(emp.name());
                byName.put(emp.name(), emp.id());
            }
        }
        List<String> unmatched = new ArrayList<>();
        Map<Long, BigDecimal> absence = new LinkedHashMap<>();
        int matched = 0;
        Map<Long, List<PunchXlsParser.PunchRow>> rowsByEmp = new LinkedHashMap<>();
        for (PunchXlsParser.PunchRow row : punch.rows()) {
            Long id = resolve(row, byNo, noCounts, archiveNames, byName);
            if (id == null) {
                unmatched.add(label(row));
                continue;
            }
            rowsByEmp.computeIfAbsent(id, k -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<Long, List<PunchXlsParser.PunchRow>> e : rowsByEmp.entrySet()) {
            matched++;
            long abs = e.getValue().stream().filter(PunchXlsParser.PunchRow::absence).count();
            if (abs > 0) {
                absence.put(e.getKey(), BigDecimal.valueOf(abs));
            }
        }
        return new Result(matched, unmatched, absence);
    }

    static Long resolve(PunchXlsParser.PunchRow row, Map<String, Long> byNo, Map<String, Integer> noCounts,
                        List<String> archiveNames, Map<String, Long> byName) {
        if (row.employeeNo() != null && !row.employeeNo().isBlank()) {
            String no = row.employeeNo().trim();
            if (noCounts.getOrDefault(no, 0) == 1) {
                return byNo.get(no);
            }
            return null;
        }
        if (row.name() != null && PayrollBatchRules.uniqueExactName(archiveNames, row.name())) {
            return byName.get(row.name());
        }
        return null;
    }

    static String label(PunchXlsParser.PunchRow row) {
        if (row.employeeNo() != null && !row.employeeNo().isBlank()) {
            return "工号=" + row.employeeNo();
        }
        return "姓名=" + (row.name() == null ? "" : row.name());
    }
}
