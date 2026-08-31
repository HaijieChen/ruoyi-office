import { describe, expect, it } from 'vitest';

import { resolveInputChangeValue } from './input-change-value';

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
