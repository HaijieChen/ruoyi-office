import type { BindingContext } from './expression';

import { resolveBinding } from './expression';
import { readOwnPath } from './linkage';

// Keep this byte-for-byte aligned with the backend ASCII field-name contract.
// eslint-disable-next-line regexp/prefer-w, regexp/use-ignore-case
const SAFE_SCHEMA_NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]{0,62}$/;
const FORBIDDEN_NAMES = new Set(['__proto__', 'constructor', 'prototype']);
const RESERVED_SERVER_PARAMS = new Set([
  'companyId',
  'deptId',
  'tenantId',
  'userId',
]);

/**
 * Validate a search/pagination parameter name against the safe schema-name rule.
 * The name must not be server-reserved.
 */
export function validateSelectorParamName(name: string): boolean {
  return (
    SAFE_SCHEMA_NAME_PATTERN.test(name) &&
    !FORBIDDEN_NAMES.has(name) &&
    !RESERVED_SERVER_PARAMS.has(name)
  );
}

/**
 * Resolve parameter bindings against a binding context to build execute request params.
 * Only declared bindings are sent; no undeclared parameters are added.
 */
export function buildExecuteParams(
  parameterBindings: Readonly<Record<string, string>>,
  context: BindingContext,
): Record<string, unknown> {
  const params: Record<string, unknown> = {};
  for (const [paramName, bindingExpr] of Object.entries(parameterBindings)) {
    params[paramName] = resolveBinding(bindingExpr, context);
  }
  return params;
}

/**
 * Build a label/value snapshot array from selected rows.
 * Only label and value are kept — not the full server row.
 */
export function buildSelectionSnapshot(
  selectedRows: ReadonlyArray<Readonly<Record<string, unknown>>>,
  labelField: string,
  valueField: string,
): Array<{ label: unknown; value: unknown }> {
  return selectedRows.map((row) => ({
    label: readOwnPath(row, labelField),
    value: readOwnPath(row, valueField),
  }));
}
