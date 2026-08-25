/** 只打印指定表单节点，不带侧栏/顶栏/操作条。Safari 可用。 */
export function printFormElement(el: HTMLElement | null, title = '打印') {
  if (!el) return;
  const clone = el.cloneNode(true) as HTMLElement;
  clone.classList.remove('hidden');
  clone.style.display = 'block';
  clone.querySelectorAll('.hidden').forEach((n) => {
    (n as HTMLElement).classList.remove('hidden');
    (n as HTMLElement).style.display = '';
  });

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
  html,body{margin:0;padding:0;background:#fff;color:#000}
  @page{size:A4;margin:12mm}
  body{page-break-after:avoid}
  .finance-print-slip{box-sizing:border-box;width:180mm;padding:8mm;color:#000;font-size:13px;font-family:SimSun,'Songti SC',serif;background:#fff}
  .finance-print-slip h1{margin:0 0 12px;font-size:22px;font-weight:700;text-align:center}
  .finance-print-slip table{width:100%;border-collapse:collapse}
  .finance-print-slip th,.finance-print-slip td{padding:6px 8px;border:1px solid #000;vertical-align:top}
  .finance-print-slip th{width:18%;font-weight:600;background:#f3f3f3}
  .finance-print-slip table.mt{margin-top:10px}
  .finance-print-slip .foot{margin-top:10px}
</style></head><body>${clone.outerHTML}</body></html>`);
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
