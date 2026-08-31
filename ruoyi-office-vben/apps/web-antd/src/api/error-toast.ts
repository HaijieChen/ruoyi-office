/**
 * 全局错误 toast 是否压掉。
 * hideErrorMessage 仍压非 403；biz code 403（权限门）始终弹出服务端 msg。
 */
export function shouldSuppressErrorToast(error: {
  config?: { hideErrorMessage?: boolean; headers?: Record<string, unknown> };
  data?: { code?: number };
  response?: { data?: { code?: number } };
}): boolean {
  const responseData = error?.response?.data ?? error?.data ?? {};
  const code = error?.data?.code ?? responseData?.code;
  if (code === 401) {
    return true;
  }
  if (code === 403) {
    return false;
  }
  return (
    error?.config?.hideErrorMessage === true ||
    error?.config?.headers?.['X-Hide-Error-Message'] === '1'
  );
}
