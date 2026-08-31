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
