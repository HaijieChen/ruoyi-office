import { isTenantEnable, useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { renderAsync } from 'docx-preview';

import { resolveRequestTenantId } from '#/constants/tenant';

const MIME_BY_EXT: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  gif: 'image/gif',
  webp: 'image/webp',
  bmp: 'image/bmp',
  svg: 'image/svg+xml',
  pdf: 'application/pdf',
};

export function guessFileExt(nameOrUrl: string) {
  const raw = String(nameOrUrl || '').split('?')[0] || '';
  const base = raw.slice(Math.max(0, raw.lastIndexOf('/') + 1));
  const dot = base.lastIndexOf('.');
  return dot >= 0 ? base.slice(dot + 1).toLowerCase() : '';
}

export type PreviewKind = 'image' | 'pdf' | 'docx';

export function guessPreviewKind(nameOrUrl: string): PreviewKind | null {
  const ext = guessFileExt(nameOrUrl);
  if (ext === 'pdf') return 'pdf';
  if (ext === 'docx') return 'docx';
  if (MIME_BY_EXT[ext]?.startsWith('image/')) return 'image';
  return null;
}

export function resolvePreviewKind(input: {
  name?: string;
  originName?: string;
  type?: string;
  url?: string;
}): PreviewKind | null {
  for (const n of [input.originName, input.name, input.url]) {
    const kind = n ? guessPreviewKind(n) : null;
    if (kind) return kind;
  }
  const type = String(input.type || '').toLowerCase();
  if (type.includes('wordprocessingml')) return 'docx';
  if (type === 'application/pdf') return 'pdf';
  if (type.startsWith('image/')) return 'image';
  return null;
}

export async function sniffPreviewKind(blob: Blob): Promise<PreviewKind | null> {
  const fromType = resolvePreviewKind({ type: blob.type });
  if (fromType) return fromType;
  const head = new Uint8Array(await blob.slice(0, 4096).arrayBuffer());
  if (head[0] === 0x25 && head[1] === 0x50 && head[2] === 0x44 && head[3] === 0x46) {
    return 'pdf';
  }
  if (head[0] === 0xff && head[1] === 0xd8) return 'image';
  if (head[0] === 0x89 && head[1] === 0x50) return 'image';
  if (head[0] === 0x50 && head[1] === 0x4b) {
    const text = new TextDecoder('latin1').decode(head);
    if (text.includes('word/')) return 'docx';
  }
  return null;
}

export async function renderDocxPreview(blob: Blob, container: HTMLElement) {
  container.innerHTML = '';
  await renderAsync(await blob.arrayBuffer(), container);
  if (!container.innerHTML.trim()) {
    throw new Error('docx preview produced no content');
  }
}

export function destroyDocxPreview(container?: HTMLElement | null) {
  if (container) container.innerHTML = '';
}

export function blobWithGuessedType(blob: Blob, nameOrUrl: string) {
  const mime = MIME_BY_EXT[guessFileExt(nameOrUrl)];
  if (!mime || blob.type === mime) return blob;
  if (blob.type && blob.type !== 'application/octet-stream') return blob;
  return new Blob([blob], { type: mime });
}

/** 走后端 /infra/file/preview，从存储器读字节，不直连 MinIO */
export async function fetchPreviewBlob(url: string): Promise<Blob> {
  const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
  const accessStore = useAccessStore();
  const base = (apiURL || '').endsWith('/') ? (apiURL || '').slice(0, -1) : (apiURL || '');
  const preview = base + '/infra/file/preview?url=' + encodeURIComponent(url);
  const headers: Record<string, string> = {};
  if (accessStore.accessToken) {
    headers.Authorization = 'Bearer ' + accessStore.accessToken;
  }
  if (isTenantEnable()) {
    headers['tenant-id'] = String(resolveRequestTenantId(accessStore.tenantId));
  }
  const res = await fetch(preview, { method: 'GET', headers, credentials: 'include' });
  if (!res.ok) {
    throw new Error('预览失败 HTTP ' + res.status);
  }
  return blobWithGuessedType(await res.blob(), url);
}

export async function previewAuthUrl(url: string) {
  const blob = await fetchPreviewBlob(url);
  const obj = URL.createObjectURL(blob);
  window.open(obj, '_blank');
}

export async function downloadAuthFile(url: string, fileName?: string) {
  const blob = await fetchPreviewBlob(url);
  const obj = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = obj;
  const path = String(url).split('?')[0] || '';
  a.download =
    fileName || path.slice(Math.max(0, path.lastIndexOf('/') + 1)) || 'file';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(obj), 1000);
}
