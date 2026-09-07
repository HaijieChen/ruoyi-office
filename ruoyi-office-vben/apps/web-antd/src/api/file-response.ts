import type { AxiosResponse } from 'axios';

/** Decode opted-in file endpoint errors before the authentication interceptor. */
export async function decodeFileResponse(response: AxiosResponse) {
  const config = response.config as typeof response.config & {
    decodeBusinessErrorBlob?: boolean;
  };
  if (!config.decodeBusinessErrorBlob || config.responseType !== 'blob' || !(response.data instanceof Blob)) {
    return response;
  }
  const type = response.data.type || String(response.headers['content-type'] || '');
  if (!type.toLowerCase().includes('json')) return response;
  // An explicitly named download is a file, even when its contents resemble an error.
  if (String(response.headers['content-disposition'] || '').includes('filename')) return response;
  let data;
  try {
    data = JSON.parse(await response.data.text());
  } catch {
    return response;
  }
  if (!data || typeof data.code !== 'number' || data.code === 0 ||
      typeof data.msg !== 'string' || data.data !== null) return response;
  throw Object.assign(new Error(data.msg || '文件读取失败'), {
    config: response.config,
    data,
    response: { ...response, data },
  });
}
