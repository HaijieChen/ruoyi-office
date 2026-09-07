import { describe, expect, it } from 'vitest';

import { formatFileSize } from './format-file-size';

describe('formatFileSize', () => {
  it('does not render missing size as 0B', () => {
    expect(formatFileSize(null)).toBe('未知');
    expect(formatFileSize(undefined)).toBe('未知');
  });

  it('keeps real byte sizes and treats NaN as unknown', () => {
    expect(formatFileSize(0)).toBe('0B');
    expect(formatFileSize(106878)).toBe('104.4KB');
    expect(formatFileSize(Number.NaN)).toBe('未知');
  });
});
