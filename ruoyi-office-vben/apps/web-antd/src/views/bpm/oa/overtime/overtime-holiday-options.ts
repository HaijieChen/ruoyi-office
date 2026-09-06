export type OvertimeHolidayOption = {
  label: string;
  value: string;
};

/** 字典 value 可能是 boolean|number|string；Select 需要非 boolean 的 option value */
export function normalizeHolidaySelectOptions(
  options: Array<{ label?: unknown; value?: unknown }>,
): OvertimeHolidayOption[] {
  const result: OvertimeHolidayOption[] = [];
  for (const item of options) {
    if (item.label == null || item.value == null) continue;
    result.push({ label: String(item.label), value: String(item.value) });
  }
  return result;
}
