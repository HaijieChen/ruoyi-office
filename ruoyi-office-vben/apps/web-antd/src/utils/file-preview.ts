import { isTenantEnable, useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { resolveRequestTenantId } from '#/constants/tenant';

/** 走后端 /infra/file/preview，从存储器读字节，不直连 MinIO */
export async function previewAuthUrl(url: string) {
  const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
  const accessStore = useAccessStore();
  const base = (apiURL || '').replace(//$/, '');
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
  const blob = await res.blob();
  const obj = URL.createObjectURL(blob);
  window.open(obj, '_blank');
}
