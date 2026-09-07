import type { AxiosResponse } from 'axios';

/** File endpoints can return HTTP 200 with a JSON business error. Decode it
 * before the shared authentication interceptor so refresh/re-login still runs. */
export async function decodeFileResponse(response: AxiosResponse) {
  if (response.config.responseType !== 'blob' || !(response.data instanceof Blob)) {
    return response;
  }
  const type = response.data.type || String(response.headers['content-type'] || '');
  if (!type.toLowerCase().includes('json')) return response;
  const data = JSON.parse(await response.data.text());
  throw Object.assign(new Error(data.msg || '文件读取失败'), {
    config: response.config,
    data,
    response: { ...response, data },
  });
}
