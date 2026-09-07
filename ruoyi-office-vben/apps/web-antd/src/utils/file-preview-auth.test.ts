import { beforeEach, expect, it, vi } from 'vitest';
const state = vi.hoisted(() => ({ responses: [] as Blob[], refreshes: 0, logouts: 0, calls: 0, failRefresh: false }));
vi.mock('docx-preview', () => ({ renderAsync: vi.fn() }));
vi.mock('@vben/locales', () => ({ $t: (s: string) => s }));
vi.mock('#/api/request', async () => {
 const { RequestClient } = await import('../../../../packages/effects/request/src/request-client/request-client');
 const { defaultResponseInterceptor, authenticateResponseInterceptor } = await import('../../../../packages/effects/request/src/request-client/preset-interceptors');
 const { decodeFileResponse } = await import('../api/file-response');
 const client = new RequestClient({ responseReturn: 'data', adapter: async config => {
  state.calls++;
  return { data: state.responses.shift(), status: 200, statusText: 'OK', headers: {}, config };
 }});
 client.addResponseInterceptor({fulfilled: decodeFileResponse});
 client.addResponseInterceptor(defaultResponseInterceptor({codeField:'code', dataField:'data', successCode:0}));
 client.addResponseInterceptor(authenticateResponseInterceptor({client, enableRefreshToken:true,
  formatToken: token => `Bearer ${token}`,
  doRefreshToken: async () => { state.refreshes++; if(state.failRefresh) throw new Error('登录失效'); return 'renewed'; },
  doReAuthenticate: async () => {state.logouts++;},
 }));
 return { requestClient: client };
});
import { fetchPreviewBlob } from './file-preview';
const error = (code: number) => new Blob([JSON.stringify({code,msg:'账号未登录',data:null})],{type:'application/json'});
beforeEach(() => Object.assign(state, {responses:[],refreshes:0,logouts:0,calls:0,failRefresh:false}));
it('refreshes HTTP200 business401 and returns PDF bytes', async () => {
 state.responses=[error(401),new Blob(['%PDF-1.3'],{type:'application/pdf'})];
 expect(await (await fetchPreviewBlob('https://example.test/a.pdf')).text()).toBe('%PDF-1.3');
 expect(state.refreshes).toBe(1);
});
it('does not present business403 as a file',async()=>{
 state.responses=[error(403)];
 await expect(fetchPreviewBlob('https://example.test/a.pdf')).rejects.toMatchObject({code:403});
 expect(state.refreshes).toBe(0);
});
it('requests login when refresh fails',async()=>{
 state.responses=[error(401)];state.failRefresh=true;
 await expect(fetchPreviewBlob('https://example.test/a.pdf')).rejects.toThrow('登录失效');
 expect(state.logouts).toBe(1);
});
it('does not loop when renewed credentials also fail',async()=>{
 state.responses=[error(401),error(401)];
 await expect(fetchPreviewBlob('https://example.test/a.pdf')).rejects.toBeDefined();
 expect(state.calls).toBe(2);expect(state.refreshes).toBe(1);
});
it.each(['application/pdf','image/png','application/vnd.openxmlformats-officedocument.wordprocessingml.document'])('preserves %s bytes',async type=>{
 state.responses=[new Blob(['bytes'],{type})];
 const blob=await fetchPreviewBlob('https://example.test/file');
 expect(await blob.text()).toBe('bytes');expect(blob.type).toBe(type);
});

it.each(['{"hello":"world"}', '{"code":0,"msg":"ok","data":null}', 'invalid JSON', '{"code":401,"description":"example"}'])('preserves JSON file content: %s', async content => {
 state.responses=[new Blob([content],{type:'application/json'})];
 expect(await (await fetchPreviewBlob('https://example.test/a.json')).text()).toBe(content);
 expect(state.refreshes).toBe(0);
});
it('does not decode ordinary JSON downloads without explicit opt-in', async () => {
 const { decodeFileResponse } = await import('../api/file-response');
 const response = {config:{responseType:'blob'},data:error(401),headers:{}};
 expect(await decodeFileResponse(response as any)).toBe(response);
});
it('preserves explicitly named JSON downloads even when they resemble business errors', async () => {
 const { decodeFileResponse } = await import('../api/file-response');
 const response = {config:{responseType:'blob',decodeBusinessErrorBlob:true},data:error(401),headers:{'content-disposition':'attachment; filename="example.json"'}};
 expect(await decodeFileResponse(response as any)).toBe(response);
});
