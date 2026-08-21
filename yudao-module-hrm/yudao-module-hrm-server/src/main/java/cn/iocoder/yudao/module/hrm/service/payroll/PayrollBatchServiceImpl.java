package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollBatchDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.PayrollBatchMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.PayrollLineMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Validated
public class PayrollBatchServiceImpl implements PayrollBatchService {

    @Resource
    private PayrollBatchMapper payrollBatchMapper;
    @Resource
    private PayrollLineMapper payrollLineMapper;
    @Resource
    private EmployeeMapper employeeMapper;
    @Resource
    private MinWageService minWageService;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    private final PayrollCalculator calculator = new PayrollCalculator();

    @Override
    public PayrollBatchDO getOrCreate(int yearMonth) {
        PayrollBatchDO existing = findBatch(yearMonth);
        if (existing != null) {
            return existing;
        }
        PayrollBatchDO batch = new PayrollBatchDO();
        batch.setYearMonth(yearMonth);
        batch.setStatus(PayrollBatchRules.DRAFT);
        payrollBatchMapper.insert(batch);
        return batch;
    }

    @Override
    public PayrollBatchDO generate(int yearMonth) {
        PayrollBatchDO batch = getOrCreate(yearMonth);
        if (!PayrollBatchRules.canEdit(batch.getStatus())) {
            throw new IllegalStateException("published batch is locked");
        }
        payrollLineMapper.delete(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
        BigDecimal minWage = minWageService.effectiveOn(yearMonth);
        if (minWage == null) {
            minWage = BigDecimal.ZERO;
        }
        List<EmployeeDO> employees = employeeMapper.selectList(new LambdaQueryWrapperX<EmployeeDO>()
                .notIn(EmployeeDO::getEmployeeStatus, 6, 7));
        for (EmployeeDO emp : employees) {
            BigDecimal wage = emp.getRegularSalary() != null ? emp.getRegularSalary() : emp.getProbationSalary();
            if (wage == null) {
                wage = BigDecimal.ZERO;
            }
            int tenure = tenureYears(emp.getEntryDate(), yearMonth);
            PayrollCalculator.Result calc = calculator.calculate(new PayrollCalculator.Input(
                    wage, new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    wage, wage, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, false, tenure, minWage));
            PayrollLineDO line = new PayrollLineDO();
            line.setBatchId(batch.getId());
            line.setEmployeeId(emp.getId());
            line.setEmployeeName(emp.getName());
            line.setPayable(calc.payable());
            line.setNet(calc.net());
            line.setTax(BigDecimal.ZERO);
            line.setOvertime(BigDecimal.ZERO);
            line.setIdCard(emp.getIdCard());
            line.setBankAccount(emp.getBankAccount());
            line.setSnapshot(false);
            line.setUserId(emp.getUserId());
            line.setSickDays(BigDecimal.ZERO);
            payrollLineMapper.insert(line);
        }
        return batch;
    }

    @Override
    public PunchUploadVO uploadPunch(int yearMonth, InputStream in) throws Exception {
        generate(yearMonth);
        PunchXlsParser.ParseResult parsed = new PunchXlsParser().parse(in);
        List<EmployeeDO> employees = employeeMapper.selectList(new LambdaQueryWrapperX<EmployeeDO>()
                .notIn(EmployeeDO::getEmployeeStatus, 6, 7));
        PayrollPunchApply.Result applied = PayrollPunchApply.apply(
                employees.stream()
                        .map(e -> new PayrollPunchApply.EmployeeName(e.getId(), e.getName()))
                        .toList(),
                parsed);
        Map<Long, EmployeeDO> byId = employees.stream().collect(Collectors.toMap(EmployeeDO::getId, e -> e));
        BigDecimal minWage = minWageService.effectiveOn(yearMonth);
        if (minWage == null) {
            minWage = BigDecimal.ZERO;
        }
        PayrollBatchDO batch = require(yearMonth);
        List<PayrollLineDO> lines = payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
        for (PayrollLineDO line : lines) {
            BigDecimal absence = applied.absenceByEmployeeId().get(line.getEmployeeId());
            if (absence == null) {
                continue;
            }
            EmployeeDO emp = byId.get(line.getEmployeeId());
            if (emp == null) {
                continue;
            }
            BigDecimal wage = emp.getRegularSalary() != null ? emp.getRegularSalary() : emp.getProbationSalary();
            if (wage == null) {
                wage = BigDecimal.ZERO;
            }
            int tenure = tenureYears(emp.getEntryDate(), yearMonth);
            PayrollCalculator.Result calc = calculator.calculate(new PayrollCalculator.Input(
                    wage, new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    wage, wage, line.getTax(), BigDecimal.ZERO, BigDecimal.ZERO, absence,
                    BigDecimal.ZERO, false, tenure, minWage));
            line.setPayable(calc.payable());
            line.setNet(calc.net());
            line.setPunchName(emp.getName());
            payrollLineMapper.updateById(line);
        }
        return new PunchUploadVO(applied.matched(), applied.unmatched());
    }

    @Override
    public PayrollBatchDO publish(int yearMonth) {
        PayrollBatchDO batch = require(yearMonth);
        batch.setStatus(PayrollBatchRules.publish(batch.getStatus()));
        payrollBatchMapper.updateById(batch);
        payrollLineMapper.delete(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, true));
        List<PayrollLineDO> hrLines = payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
        for (PayrollLineDO hr : hrLines) {
            PayrollLineDO snap = new PayrollLineDO();
            snap.setBatchId(hr.getBatchId());
            snap.setEmployeeId(hr.getEmployeeId());
            snap.setEmployeeName(hr.getEmployeeName());
            snap.setPayable(hr.getPayable());
            snap.setNet(hr.getNet());
            snap.setTax(hr.getTax());
            snap.setOvertime(hr.getOvertime());
            snap.setSnapshot(true);
            snap.setUserId(hr.getUserId());
            snap.setSickDays(hr.getSickDays());
            payrollLineMapper.insert(snap);
            if (hr.getUserId() != null) {
                notify(hr.getUserId(), yearMonth);
            }
        }
        return batch;
    }

    @Override
    public PayrollBatchDO withdraw(int yearMonth) {
        PayrollBatchDO batch = require(yearMonth);
        batch.setStatus(PayrollBatchRules.withdraw(batch.getStatus()));
        payrollBatchMapper.updateById(batch);
        payrollLineMapper.delete(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, true));
        return batch;
    }

    @Override
    public List<PayrollLineDO> listHrLines(int yearMonth) {
        PayrollBatchDO batch = findBatch(yearMonth);
        if (batch == null) {
            return List.of();
        }
        return payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
    }

    @Override
    public List<PayrollLineDO> listMyPayslips(Long loginUserId) {
        return payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getSnapshot, true)
                .eq(PayrollLineDO::getUserId, loginUserId)
                .orderByDesc(PayrollLineDO::getId));
    }

    @Override
    public void updateAdjust(Long lineId, BigDecimal tax, BigDecimal overtime) {
        PayrollLineDO line = payrollLineMapper.selectById(lineId);
        if (line == null || Boolean.TRUE.equals(line.getSnapshot())) {
            throw new IllegalStateException("line not editable");
        }
        PayrollBatchDO batch = payrollBatchMapper.selectById(line.getBatchId());
        if (!PayrollBatchRules.canEdit(batch.getStatus())) {
            throw new IllegalStateException("published batch is locked");
        }
        if (tax != null) {
            line.setTax(tax);
        }
        if (overtime != null) {
            line.setOvertime(overtime);
        }
        if (line.getPayable() != null && line.getTax() != null) {
            BigDecimal extra = overtime == null ? BigDecimal.ZERO : overtime;
            line.setNet(line.getPayable().add(extra).subtract(line.getTax()));
        }
        payrollLineMapper.updateById(line);
    }

    private void notify(Long userId, int yearMonth) {
        try {
            NotifySendSingleToUserReqDTO req = new NotifySendSingleToUserReqDTO();
            req.setUserId(userId);
            req.setTemplateCode("hrm_payroll_published");
            Map<String, Object> params = new HashMap<>();
            params.put("yearMonth", yearMonth);
            req.setTemplateParams(params);
            notifyMessageSendApi.sendSingleMessageToAdmin(req);
        } catch (Exception ignored) {
            // template may be missing in early env
        }
    }

    private PayrollBatchDO findBatch(int yearMonth) {
        return payrollBatchMapper.selectOne(new LambdaQueryWrapperX<PayrollBatchDO>()
                .eq(PayrollBatchDO::getYearMonth, yearMonth));
    }

    private PayrollBatchDO require(int yearMonth) {
        PayrollBatchDO batch = findBatch(yearMonth);
        if (batch == null) {
            throw new IllegalStateException("batch not found");
        }
        return batch;
    }

    static int tenureYears(LocalDate entry, int yearMonth) {
        if (entry == null) {
            return 0;
        }
        int year = yearMonth / 100;
        int month = yearMonth % 100;
        LocalDate asOf = LocalDate.of(year, month, 1);
        int years = asOf.getYear() - entry.getYear();
        if (asOf.getMonthValue() < entry.getMonthValue()) {
            years--;
        }
        return Math.max(years, 0);
    }
}
