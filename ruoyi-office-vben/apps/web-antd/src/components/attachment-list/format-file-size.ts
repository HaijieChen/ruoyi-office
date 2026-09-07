export function formatFileSize(size: number | null | undefined): string {
  if (size == null || Number.isNaN(Number(size))) return '未知';
  if (size < 1024) return `${size}B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`;
  return `${(size / (1024 * 1024)).toFixed(1)}MB`;
}
