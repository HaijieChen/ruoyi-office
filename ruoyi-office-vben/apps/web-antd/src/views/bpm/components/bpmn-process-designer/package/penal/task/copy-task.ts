export const COPY_DELEGATE_EXPRESSION = '${bpmCopyTaskDelegate}';

export function createCopyServiceTaskShape(elementFactory: {
  _bpmnFactory?: { create: (type: string, attrs: Record<string, string>) => unknown };
  createShape: (attrs: Record<string, unknown>) => any;
}) {
  const bpmnFactory = elementFactory._bpmnFactory;
  const businessObject = bpmnFactory
    ? bpmnFactory.create('bpmn:ServiceTask', {
        name: '抄送人',
        delegateExpression: COPY_DELEGATE_EXPRESSION,
      })
    : undefined;
  const shape = elementFactory.createShape({
    type: 'bpmn:ServiceTask',
    ...(businessObject ? { businessObject } : {}),
  });
  const bo = shape.businessObject;
  bo.name = '抄送人';
  bo.delegateExpression = COPY_DELEGATE_EXPRESSION;
  return shape;
}

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
