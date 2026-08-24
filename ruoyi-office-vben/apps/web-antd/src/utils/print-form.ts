/** 只打印指定表单节点，不带侧栏/顶栏/操作条。Safari 可用。 */
export function printFormElement(el: HTMLElement | null, title = '打印') {
  if (!el) return;
  const iframe = document.createElement('iframe');
  iframe.setAttribute(
    'style',
    'position:fixed;right:0;bottom:0;width:0;height:0;border:0;',
  );
  document.body.appendChild(iframe);
  const doc = iframe.contentDocument;
  if (!doc) {
    iframe.remove();
    return;
  }
  const styles = [...document.querySelectorAll('style,link[rel="stylesheet"]')]
    .map((n) => n.outerHTML)
    .join('\n');
  doc.open();
  doc.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${title}</title>
${styles}
<style>
  html,body{margin:0;padding:12px;background:#fff;color:#000}
  @page{margin:12mm}
</style></head><body>${el.outerHTML}</body></html>`);
  doc.close();
  const win = iframe.contentWindow;
  const cleanup = () => iframe.remove();
  if (!win) {
    cleanup();
    return;
  }
  win.focus();
  win.onafterprint = cleanup;
  win.print();
  setTimeout(cleanup, 2500);
}
