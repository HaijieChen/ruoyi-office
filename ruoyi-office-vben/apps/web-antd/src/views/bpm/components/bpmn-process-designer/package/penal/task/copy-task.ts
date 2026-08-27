export const COPY_DELEGATE_EXPRESSION = '${bpmCopyTaskDelegate}';

export function isCopyServiceTask(businessObject?: {
  $type?: string;
  delegateExpression?: string;
  implementation?: string;
  $attrs?: Record<string, string>;
} | null) {
  if (!businessObject) {
    return false;
  }
  const expr =
    businessObject.delegateExpression ||
    businessObject.implementation ||
    businessObject.$attrs?.['flowable:delegateExpression'] ||
    '';
  return String(expr).includes('bpmCopyTaskDelegate');
}
