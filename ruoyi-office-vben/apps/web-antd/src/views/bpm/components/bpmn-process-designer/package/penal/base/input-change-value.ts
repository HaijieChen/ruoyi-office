/**
 * Ant Design Vue Input @change 可能传入字符串，也可能传入原生 InputEvent。
 * BPMN 属性必须是字符串；直接写入事件对象会变成 "[object InputEvent]"。
 */
export function resolveInputChangeValue(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }
  if (value != null && typeof value === 'object' && 'target' in (value as object)) {
    const target = (value as { target?: { value?: unknown } }).target;
    if (typeof target?.value === 'string') {
      return target.value;
    }
  }
  return '';
}

/** 把 BPMN 流程名称写回模型，发布时两边才能对上。 */
export function syncProcessNameToModel(
  model: { name?: string } | undefined | null,
  name: string,
): void {
  if (!model || !name) {
    return;
  }
  model.name = name;
}

export function replaceBpmnProcessName(xml: string, name: string): string {
  if (!xml || !name) {
    return xml;
  }
  const escaped = name.replaceAll('&', '&amp;').replaceAll('"', '&quot;');
  return xml.replace(
    /(<bpmn2?:process\b[^>]*\bname=")([^"]*)(")/,
    `$1${escaped}$3`,
  );
}
