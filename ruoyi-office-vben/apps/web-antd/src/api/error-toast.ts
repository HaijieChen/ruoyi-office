/**
 * 全局错误 toast 是否压掉。
 * hideErrorMessage 仍压非权限门错误；biz 403 且文案为「缺少权限…」时弹出服务端 msg。
 */
export function shouldSuppressErrorToast(error: {
  config?: { hideErrorMessage?: boolean; headers?: Record<string, unknown> };
  data?: { code?: number; msg?: string };
  response?: { data?: { code?: number; msg?: string } };
}): boolean {
  const responseData = error?.response?.data ?? error?.data ?? {};
  const code = error?.data?.code ?? responseData?.code;
  const msg = String(error?.data?.msg ?? responseData?.msg ?? '');
  if (code === 401) {
    return true;
  }
  const hide =
    error?.config?.hideErrorMessage === true ||
    error?.config?.headers?.['X-Hide-Error-Message'] === '1';
  if (hide) {
    return !(code === 403 && msg.includes('缺少权限'));
  }
  return false;
}
