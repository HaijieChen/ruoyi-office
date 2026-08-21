package cn.iocoder.yudao.module.hrm.service.payroll;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PayrollPunchApply {

    public record EmployeeName(Long id, String name) {
    }

    public record Result(int matched, List<String> unmatched, Map<Long, BigDecimal> absenceByEmployeeId) {
    }

    public static Result apply(List<EmployeeName> employees, PunchXlsParser.ParseResult punch) {
        Map<String, Long> unique = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (EmployeeName emp : employees) {
            if (emp.name() == null) {
                continue;
            }
            counts.merge(emp.name(), 1, Integer::sum);
            unique.put(emp.name(), emp.id());
        }
        List<String> unmatched = new ArrayList<>();
        Map<Long, BigDecimal> absence = new HashMap<>();
        int matched = 0;
        List<String> archiveNames = employees.stream().map(EmployeeName::name).toList();
        for (String name : punch.names()) {
            if (!PayrollBatchRules.uniqueExactName(archiveNames, name)) {
                unmatched.add(name);
                continue;
            }
            Long id = unique.get(name);
            matched++;
            long abs = punch.rows().stream()
                    .filter(r -> name.equals(r.name()) && r.absence())
                    .count();
            if (abs > 0) {
                absence.put(id, BigDecimal.valueOf(abs));
            }
        }
        return new Result(matched, unmatched, absence);
    }
}
