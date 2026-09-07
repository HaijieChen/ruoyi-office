import { describe, expect, it } from 'vitest';

import { buildSealStartAttachments } from './seal-start-attachments';

describe('buildSealStartAttachments', () => {
  it('omits fileSize instead of claiming an empty file', () => {
    const rows = buildSealStartAttachments([
      '/admin-api/infra/file/1/get/20260907/weixin.jpg',
    ]);
    expect(rows).toHaveLength(1);
    expect(rows[0].fileName).toBe('weixin.jpg');
    expect(rows[0].fileUrl).toBe(
      '/admin-api/infra/file/1/get/20260907/weixin.jpg',
    );
    expect(rows[0].filePath).toBe(
      '/admin-api/infra/file/1/get/20260907/weixin.jpg',
    );
    expect(rows[0]).not.toHaveProperty('fileSize');
    expect(rows[0]).not.toHaveProperty('fileId');
  });
});
