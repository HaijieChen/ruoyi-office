/** Parse persisted attachment JSON; legacy empty records have no attachments. */
export function parseAttachmentUrls(raw?: null | string | string[]): string[] {
  if (!raw) return [];
  if (Array.isArray(raw)) {
    return raw.filter((url) => typeof url === 'string' && !!url.trim());
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parseAttachmentUrls(parsed) : [];
  } catch {
    return [];
  }
}
