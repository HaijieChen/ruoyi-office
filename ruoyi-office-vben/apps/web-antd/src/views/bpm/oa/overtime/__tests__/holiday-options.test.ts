import { describe, expect, it } from 'vitest';

import { normalizeHolidaySelectOptions } from '../overtime-holiday-options';

describe('normalizeHolidaySelectOptions', () => {
  it('keeps string label/value pairs', () => {
    expect(
      normalizeHolidaySelectOptions([
        { label: '否', value: 'false' },
        { label: '是', value: 'true' },
      ]),
    ).toEqual([
      { label: '否', value: 'false' },
      { label: '是', value: 'true' },
    ]);
  });

  it('stringifies dict values so Select options stay DefaultOptionType', () => {
    expect(
      normalizeHolidaySelectOptions([
        { label: '否', value: false },
        { label: '是', value: 1 },
      ]),
    ).toEqual([
      { label: '否', value: 'false' },
      { label: '是', value: '1' },
    ]);
  });

  it('drops entries missing label or value', () => {
    expect(
      normalizeHolidaySelectOptions([
        { label: '空值' },
        { value: 'true' },
        { label: '是', value: 'true' },
      ]),
    ).toEqual([{ label: '是', value: 'true' }]);
  });
});
