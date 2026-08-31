import { describe, expect, it } from 'vitest';

import {
  replaceBpmnProcessName,
  resolveInputChangeValue,
  syncProcessNameToModel,
} from './input-change-value';

describe('resolveInputChangeValue', () => {
  it('keeps a plain string for BPMN process name', () => {
    expect(resolveInputChangeValue('薪资付款申请')).toBe('薪资付款申请');
  });

  it('unwraps Ant Design Vue InputEvent instead of writing [object InputEvent]', () => {
    const event = {
      target: { value: '薪资付款申请' },
      toString() {
        return '[object InputEvent]';
      },
    };
    expect(resolveInputChangeValue(event)).toBe('薪资付款申请');
    expect(String(event)).toBe('[object InputEvent]');
  });

  it('returns empty string for empty or unknown payloads', () => {
    expect(resolveInputChangeValue('')).toBe('');
    expect(resolveInputChangeValue(undefined)).toBe('');
    expect(resolveInputChangeValue({ target: {} })).toBe('');
  });
});

describe('syncProcessNameToModel', () => {
  it('writes BPMN process name back so publish expected name matches', () => {
    const model = { name: '薪资付款申请' };
    syncProcessNameToModel(model, '薪资付款申请1');
    expect(model.name).toBe('薪资付款申请1');
  });

  it('does not throw when model is missing', () => {
    expect(() =>
      syncProcessNameToModel(undefined, '薪资付款申请1'),
    ).not.toThrow();
  });

  it('keeps the previous model name when the next name is empty', () => {
    const model = { name: '薪资付款申请' };
    syncProcessNameToModel(model, '');
    expect(model.name).toBe('薪资付款申请');
  });
});

describe('replaceBpmnProcessName', () => {
  it('updates the process name attribute in BPMN xml', () => {
    const xml =
      '<bpmn2:process id="finance_salary_payment_apply" name="薪资付款申请" isExecutable="true">';
    expect(replaceBpmnProcessName(xml, '薪资付款申请1')).toContain(
      'name="薪资付款申请1"',
    );
  });
});
