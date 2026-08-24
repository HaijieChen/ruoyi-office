import { afterEach, describe, expect, it } from 'vitest';

import { resetPreferences, updatePreferences } from '../src';
import { usePreferences } from '../src/use-preferences';

describe('usePreferences layout flags', () => {
  afterEach(() => {
    resetPreferences();
  });

  it('keeps mixed-nav flags on desktop so header/sidebar split still works', () => {
    updatePreferences({
      app: { isMobile: false, layout: 'mixed-nav' },
    });

    const { isMixedNav, isSideNav, layout } = usePreferences();

    expect(layout.value).toBe('mixed-nav');
    expect(isMixedNav.value).toBe(true);
    expect(isSideNav.value).toBe(false);
  });

  it('does not keep mixed-nav split flags on mobile so the drawer shows the full menu tree', () => {
    updatePreferences({
      app: { isMobile: true, layout: 'mixed-nav' },
    });

    const { isMixedNav, isSideNav, layout } = usePreferences();

    expect(layout.value).toBe('sidebar-nav');
    expect(isMixedNav.value).toBe(false);
    expect(isSideNav.value).toBe(true);
  });

  it('clears header-mixed-nav split flags on mobile so needSplit cannot stay true', () => {
    updatePreferences({
      app: { isMobile: true, layout: 'header-mixed-nav' },
    });

    const { isHeaderMixedNav, isMixedNav, isSideNav, layout } =
      usePreferences();

    expect(layout.value).toBe('sidebar-nav');
    expect(isHeaderMixedNav.value).toBe(false);
    expect(isMixedNav.value).toBe(false);
    expect(isSideNav.value).toBe(true);
  });
});
