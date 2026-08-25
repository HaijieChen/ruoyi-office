/** 员工档案派生字段：年龄（周岁）、司龄（完整月数），与后端 Period.between 口径一致。 */

function parseYmd(value?: string): Date | undefined {
  if (!value || typeof value !== 'string') {
    return undefined;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (!match) {
    return undefined;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(year, month - 1, day);
  if (
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day
  ) {
    return undefined;
  }
  return date;
}

function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** 18 位身份证号解析出生日期 YYYY-MM-DD */
export function parseBirthdayFromIdCard(idCard?: string): string | undefined {
  const raw = (idCard || '').trim().toUpperCase();
  if (
    !/^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9X]$/.test(
      raw,
    )
  ) {
    return undefined;
  }
  const date = `${raw.slice(6, 10)}-${raw.slice(10, 12)}-${raw.slice(12, 14)}`;
  return parseYmd(date) ? date : undefined;
}

/** 周岁，未到生日则减 1 */
export function calcAgeYears(
  birthday?: string,
  today: Date = new Date(),
): number | undefined {
  const birth = parseYmd(birthday);
  if (!birth) {
    return undefined;
  }
  const now = startOfLocalDay(today);
  let years = now.getFullYear() - birth.getFullYear();
  const beforeBirthday =
    now.getMonth() < birth.getMonth() ||
    (now.getMonth() === birth.getMonth() && now.getDate() < birth.getDate());
  if (beforeBirthday) {
    years -= 1;
  }
  return years < 0 ? undefined : years;
}

/** 完整月数（不含不足一月的天数），与后端 companyTenureMonths 一致 */
export function calcTenureMonths(
  entryDate?: string,
  today: Date = new Date(),
): number | undefined {
  const entry = parseYmd(entryDate);
  if (!entry) {
    return undefined;
  }
  const now = startOfLocalDay(today);
  let months =
    (now.getFullYear() - entry.getFullYear()) * 12 +
    (now.getMonth() - entry.getMonth());
  if (now.getDate() < entry.getDate()) {
    months -= 1;
  }
  return months < 0 ? 0 : months;
}

export function formatDerivedNumber(value?: number): string | undefined {
  return value == null ? undefined : String(value);
}
