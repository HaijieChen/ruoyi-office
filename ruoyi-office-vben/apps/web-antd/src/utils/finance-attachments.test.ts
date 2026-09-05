import { describe, expect, it } from 'vitest';

import { parseAttachmentUrls } from './finance-attachments';

describe('finance attachment round-trip', () => {
  it('hydrates multiple URLs without splitting filenames containing commas', () => {
    const urls = ['https://files/a,b.pdf', 'https://files/合同.docx'];
    expect(parseAttachmentUrls(JSON.stringify(urls))).toEqual(urls);
  });
  it('supports clearing and legacy null records', () => {
    for (const raw of [undefined, null, '', '[]', 'null']) {
      expect(parseAttachmentUrls(raw)).toEqual([]);
    }
  });
  it('rejects malformed persisted data and non-string members', () => {
    expect(parseAttachmentUrls('broken')).toEqual([]);
    expect(parseAttachmentUrls('[null,1,"","https://files/a.pdf"]')).toEqual(['https://files/a.pdf']);
  });
});
