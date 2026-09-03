import { beforeEach, describe, expect, it, vi } from 'vitest';

const renderAsync = vi.fn();

vi.mock('docx-preview', () => ({
  renderAsync: (...args: unknown[]) => renderAsync(...args),
}));

import {
  destroyDocxPreview,
  guessPreviewKind,
  renderDocxPreview,
  resolvePreviewKind,
  sniffPreviewKind,
} from './file-preview';

describe('guessPreviewKind', () => {
  it('returns docx for .docx names', () => {
    expect(guessPreviewKind('合同草稿.docx')).toBe('docx');
  });

  it('returns docx for URL paths ending in .docx', () => {
    expect(
      guessPreviewKind('https://minio.example/bucket/a/draft.docx?token=1'),
    ).toBe('docx');
  });

  it('is case-insensitive for DOCX', () => {
    expect(guessPreviewKind('FILE.DOCX')).toBe('docx');
  });

  it('does not treat .doc as docx', () => {
    expect(guessPreviewKind('legacy.doc')).toBeNull();
  });

  it('still returns pdf for .pdf', () => {
    expect(guessPreviewKind('a.pdf')).toBe('pdf');
  });
});

describe('resolvePreviewKind', () => {
  it('uses origin file name when the stored URL has no extension', () => {
    expect(
      resolvePreviewKind({
        name: 'a1b2c3',
        url: 'https://minio.example/bucket/a1b2c3',
        originName: '报销说明.docx',
      }),
    ).toBe('docx');
  });

  it('uses wordprocessingml MIME when names have no extension', () => {
    expect(
      resolvePreviewKind({
        name: 'a1b2c3',
        url: 'https://minio.example/bucket/a1b2c3',
        type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      }),
    ).toBe('docx');
  });
});

describe('sniffPreviewKind', () => {
  it('detects docx from a zip that contains word/', async () => {
    const bytes = new TextEncoder().encode('PK\x03\x04xxxxword/document.xml');
    await expect(sniffPreviewKind(new Blob([bytes]))).resolves.toBe('docx');
  });
});

describe('renderDocxPreview', () => {
  beforeEach(() => {
    renderAsync.mockReset();
  });

  it('renders a blob into the container', async () => {
    renderAsync.mockImplementation(async (_data: unknown, container: HTMLElement) => {
      container.innerHTML = '<p>hello</p>';
    });
    const container = document.createElement('div');
    await renderDocxPreview(new Blob(['pk'], { type: 'application/octet-stream' }), container);
    expect(renderAsync).toHaveBeenCalledOnce();
    expect(container.innerHTML).toContain('hello');
  });

  it('rejects when renderAsync throws', async () => {
    renderAsync.mockRejectedValue(new Error('bad zip'));
    const container = document.createElement('div');
    await expect(
      renderDocxPreview(new Blob(['x']), container),
    ).rejects.toThrow();
  });

  it('rejects when render finishes with an empty container', async () => {
    renderAsync.mockResolvedValue(undefined);
    const container = document.createElement('div');
    await expect(
      renderDocxPreview(new Blob(['x']), container),
    ).rejects.toThrow();
  });
});

describe('destroyDocxPreview', () => {
  it('clears the container', () => {
    const container = document.createElement('div');
    container.innerHTML = '<p>keep</p>';
    destroyDocxPreview(container);
    expect(container.innerHTML).toBe('');
  });
});
