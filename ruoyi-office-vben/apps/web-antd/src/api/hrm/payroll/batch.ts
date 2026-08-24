import { requestClient } from '#/api/request';

export namespace PayrollBatchApi {
  export interface Batch {
    id?: number;
    yearMonth: number;
    status: string;
  }
  export interface Line {
    id?: number;
    yearMonth?: number;
    companyName?: string;
    deptName?: string;
    jobPost?: string;
    employeeName?: string;
    entryDate?: string;
    wage?: number;
    socialBase?: number;
    housingBase?: number;
    fullAttendanceBonus?: number;
    housingSubsidy?: number;
    performance?: number;
    bonus?: number;
    subsidy?: number;
    holidayOvertimeDays?: number;
    holidayOvertimePay?: number;
    weekdayOvertimePay?: number;
    sickDays?: number;
    sickRate?: number;
    sickPay?: number;
    personalAbsenceDays?: number;
    personalLeavePay?: number;
    tripSubsidy?: number;
    otherAdjust?: number;
    payable?: number;
    socialDeduct?: number;
    housingDeduct?: number;
    tax?: number;
    overtime?: number;
    net?: number;
    bankAccount?: string;
    bankName?: string;
    mobile?: string;
    idCard?: string;
    snapshot?: boolean;
  }
}

export function getPayrollBatch(yearMonth: number) {
  return requestClient.get<PayrollBatchApi.Batch>('/hrm/payroll-batch/get', {
    params: { yearMonth },
  });
}

export function generatePayrollBatch(yearMonth: number) {
  return requestClient.post<PayrollBatchApi.Batch>('/hrm/payroll-batch/generate', null, {
    params: { yearMonth },
  });
}

export function publishPayrollBatch(yearMonth: number) {
  return requestClient.post<PayrollBatchApi.Batch>('/hrm/payroll-batch/publish', null, {
    params: { yearMonth },
  });
}

export function withdrawPayrollBatch(yearMonth: number) {
  return requestClient.post<PayrollBatchApi.Batch>('/hrm/payroll-batch/withdraw', null, {
    params: { yearMonth },
  });
}

export function listPayrollLines(yearMonth: number) {
  return requestClient.get<PayrollBatchApi.Line[]>('/hrm/payroll-batch/lines', {
    params: { yearMonth },
  });
}

export function exportPayrollBatch(yearMonth: number) {
  return requestClient.download('/hrm/payroll-batch/export', { params: { yearMonth } });
}

export function downloadPunchTemplate() {
  return requestClient.download('/hrm/payroll-batch/punch-template');
}

export function uploadPayrollPunch(yearMonth: number, file: File) {
  const data = new FormData();
  data.append('file', file);
  return requestClient.post<{ matched: number; unmatched: string[] }>(
    '/hrm/payroll-batch/upload-punch',
    data,
    { params: { yearMonth } },
  );
}

export function listMyPayslips() {
  return requestClient.get<PayrollBatchApi.Line[]>('/hrm/payroll-payslip/list');
}

export function adjustPayrollLine(lineId: number, patch: Partial<PayrollBatchApi.Line>) {
  return requestClient.put('/hrm/payroll-batch/adjust', patch, { params: { lineId } });
}
