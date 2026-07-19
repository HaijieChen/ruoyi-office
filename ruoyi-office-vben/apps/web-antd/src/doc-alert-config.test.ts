import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

describe('web-antd documentation alerts', () => {
  it('disables documentation hints on every page', () => {
    const applicationEnv = readFileSync(
      resolve(process.cwd(), 'apps/web-antd/.env'),
      'utf8',
    );

    expect(applicationEnv).toMatch(/^VITE_APP_DOCALERT_ENABLE=false$/m);
  });
});
