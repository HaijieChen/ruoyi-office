/** Start/resubmit payload: omit unknown size instead of claiming 0. */

export function buildSealStartAttachments(urls: string[]) {
  return urls.map((url, i) => ({
    fileName: url.split('/').pop() || `file-${i}`,
    filePath: url,
    fileUrl: url,
  }));
}
