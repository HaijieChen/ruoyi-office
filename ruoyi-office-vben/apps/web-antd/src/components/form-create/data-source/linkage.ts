export interface LinkageNode {
  dependencies?: readonly string[];
  field: string;
}

export type DependencyChangeStrategy =
  | 'clear-and-reload'
  | 'keep-and-revalidate';

export interface DependencyChangeOptions {
  currentValue: unknown;
  multiple?: boolean;
  options?: readonly Record<string, unknown>[];
  outputMappings?: Readonly<Record<string, string>>;
  strategy: DependencyChangeStrategy;
  valueField: string;
}

export interface DependencyChangeResult {
  mappedValues: Record<string, unknown>;
  reload: true;
  value: unknown;
}

const FORBIDDEN_PATH_SEGMENTS = new Set([
  '__proto__',
  'constructor',
  'prototype',
]);
// Keep this byte-for-byte aligned with the backend ASCII field-name contract.
const RECORD_PATH_PATTERN =
  // eslint-disable-next-line regexp/prefer-w, regexp/use-ignore-case
  /^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*$/;

export function readOwnPath(
  record: Readonly<Record<string, unknown>>,
  path: string,
): unknown {
  if (!RECORD_PATH_PATTERN.test(path)) {
    throw new Error(`Invalid record path: ${path}`);
  }
  const segments = path.split('.');
  if (segments.some((segment) => FORBIDDEN_PATH_SEGMENTS.has(segment))) {
    throw new Error(`Invalid record path: ${path}`);
  }

  let value: unknown = record;
  for (const segment of segments) {
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

export function buildDependencyOrder(nodes: readonly LinkageNode[]): string[] {
  const fields = new Set<string>();
  for (const node of nodes) {
    if (fields.has(node.field)) {
      throw new Error(`Duplicate linkage field: ${node.field}`);
    }
    fields.add(node.field);
  }
  for (const node of nodes) {
    for (const dependency of node.dependencies ?? []) {
      fields.add(dependency);
    }
  }

  const fieldOrder = [...fields];
  const orderIndex = new Map(
    fieldOrder.map((field, index) => [field, index] as const),
  );
  const indegree = new Map<string, number>(
    fieldOrder.map((field) => [field, 0]),
  );
  const consumers = new Map(
    fieldOrder.map((field) => [field, new Set<string>()] as const),
  );

  for (const node of nodes) {
    for (const dependency of new Set(node.dependencies)) {
      consumers.get(dependency)?.add(node.field);
      indegree.set(node.field, (indegree.get(node.field) ?? 0) + 1);
    }
  }

  const ready = fieldOrder.filter((field) => indegree.get(field) === 0);
  const result: string[] = [];
  while (ready.length > 0) {
    const field = ready.shift() as string;
    result.push(field);
    for (const consumer of consumers.get(field) ?? []) {
      const nextIndegree = (indegree.get(consumer) ?? 0) - 1;
      indegree.set(consumer, nextIndegree);
      if (nextIndegree === 0) {
        ready.push(consumer);
        ready.sort(
          (left, right) =>
            (orderIndex.get(left) ?? 0) - (orderIndex.get(right) ?? 0),
        );
      }
    }
  }

  if (result.length !== fieldOrder.length) {
    throw new Error('Dependency cycle detected');
  }
  return result;
}

function emptyMappedValues(
  outputMappings: Readonly<Record<string, string>>,
): Record<string, unknown> {
  return Object.fromEntries(
    Object.values(outputMappings).map((targetField) => [
      targetField,
      undefined,
    ]),
  );
}

export function applyDependencyChange({
  currentValue,
  multiple = false,
  options = [],
  outputMappings = {},
  strategy,
  valueField,
}: DependencyChangeOptions): DependencyChangeResult {
  const clearedMappings = emptyMappedValues(outputMappings);
  if (strategy === 'clear-and-reload') {
    return {
      mappedValues: clearedMappings,
      reload: true,
      value: multiple ? [] : undefined,
    };
  }

  let currentValues: unknown[];
  if (multiple) {
    currentValues = Array.isArray(currentValue) ? [...currentValue] : [];
  } else {
    currentValues = [currentValue];
  }
  const selectedRows: Record<string, unknown>[] = [];
  const retainedValues: unknown[] = [];

  for (const value of currentValues) {
    const row = options.find((option) =>
      Object.is(readOwnPath(option, valueField), value),
    );
    if (row) {
      retainedValues.push(value);
      selectedRows.push(row);
    }
  }

  const mappedValues = { ...clearedMappings };
  for (const [sourcePath, targetField] of Object.entries(outputMappings)) {
    if (multiple && selectedRows.length > 0) {
      mappedValues[targetField] = selectedRows.map((row) =>
        readOwnPath(row, sourcePath),
      );
    } else if (selectedRows[0]) {
      mappedValues[targetField] = readOwnPath(selectedRows[0], sourcePath);
    }
  }

  return {
    mappedValues,
    reload: true,
    value: multiple ? retainedValues : retainedValues[0],
  };
}
