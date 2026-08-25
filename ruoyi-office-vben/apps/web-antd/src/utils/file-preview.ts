import { isTenantEnable, useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

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

export function guessPreviewKind(nameOrUrl: string): 'image' | 'pdf' | null {
  const ext = guessFileExt(nameOrUrl);
  if (ext === 'pdf') return 'pdf';
  if (MIME_BY_EXT[ext]?.startsWith('image/')) return 'image';
  return null;
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
  try {
    const blob = await fetchPreviewBlob(url);
    const obj = URL.createObjectURL(blob);
    window.open(obj, '_blank');
  } catch (error) {
    if (/^https?:\/\//i.test(url)) {
      window.open(url, '_blank');
      return;
    }
    throw error;
  }
}
