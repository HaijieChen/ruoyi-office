export interface BindingContext {
  FORM: Readonly<Record<string, unknown>>;
  PROCESS: Readonly<{
    definitionKey?: string;
    instanceId?: string;
  }>;
  USER: Readonly<{
    companyId?: number;
    deptId?: number;
    id?: number;
  }>;
}

const PATH_PATTERN = /^(FORM|PROCESS|USER)(?:\.[A-Za-z_][A-Za-z0-9_]*)+$/;
const FORBIDDEN_SEGMENTS = new Set(['__proto__', 'constructor', 'prototype']);
const USER_FIELDS = new Set(['companyId', 'deptId', 'id']);
const PROCESS_FIELDS = new Set(['definitionKey', 'instanceId']);

function invalidBinding(binding: string): Error {
  return new Error(`Invalid binding expression: ${binding}`);
}

export function resolveBinding(
  binding: string,
  context: BindingContext,
): unknown {
  if (!PATH_PATTERN.test(binding)) {
    throw invalidBinding(binding);
  }

  const segments = binding.split('.');
  if (segments.some((segment) => FORBIDDEN_SEGMENTS.has(segment))) {
    throw invalidBinding(binding);
  }

  const [root, ...path] = segments;
  if (
    (root === 'USER' &&
      (path.length !== 1 || !USER_FIELDS.has(path[0] as string))) ||
    (root === 'PROCESS' &&
      (path.length !== 1 || !PROCESS_FIELDS.has(path[0] as string)))
  ) {
    throw invalidBinding(binding);
  }

  let value: unknown = context[root as keyof BindingContext];
  for (const segment of path) {
    if (
      value === null ||
      typeof value !== 'object' ||
      !Object.prototype.hasOwnProperty.call(value, segment)
    ) {
      return undefined;
    }
    value = (value as Record<string, unknown>)[segment];
  }
  return value;
}
