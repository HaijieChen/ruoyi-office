/** 按申请起止时间展示经过时长，不用于审批天数或假期余额扣减。 */
export function formatLeaveDuration(startTime: unknown, endTime: unknown): string {
  const start = toTimestamp(startTime);
  const end = toTimestamp(endTime);
  if (start === undefined || end === undefined || end < start) return '—';

  const seconds = Math.floor((end - start) / 1000);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = seconds % 60;
  const parts = [`${hours} 小时`];
  if (minutes) parts.push(`${minutes} 分钟`);
  if (remainder) parts.push(`${remainder} 秒`);
  return parts.join(' ');
}

function toTimestamp(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  let timestamp: number;
  if (Array.isArray(value) && value.length >= 3) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value;
    timestamp = new Date(year, month - 1, day, hour, minute, second).getTime();
  } else if (typeof value === 'number') {
    timestamp = value > 0 && value < 1e12 ? value * 1000 : value;
  } else if (typeof value === 'string') {
    timestamp = /^\d{10,13}$/.test(value)
      ? Number(value) * (value.length === 10 ? 1000 : 1)
      : new Date(value.replace(' ', 'T')).getTime();
  } else {
    return undefined;
  }
  return Number.isFinite(timestamp) ? timestamp : undefined;
}
