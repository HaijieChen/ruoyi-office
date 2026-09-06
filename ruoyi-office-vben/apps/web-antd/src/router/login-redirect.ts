/** Resolve stale home preferences without sending an authenticated user back to login. */
export function resolveLoginRedirect(
  raw: string,
  home: string,
  login: string,
  current: string,
): string {
  const isLoop = (target: string) => {
    const path = target.split(/[?#]/)[0];
    return (
      !path || path === '/workspace' || path === login || target === current
    );
  };
  const fallback = isLoop(home) ? '/home' : home;
  try {
    const target = decodeRedirectOnce(raw || fallback);
    return isLoop(target) ? fallback : target;
  } catch {
    return fallback;
  }
}

/**
 * Vue-router already decodes query values once. A second decodeURIComponent
 * turns acc=%252F into acc=%2F (URLSearchParams then yields "/").
 * Percent-encoded blobs (no leading slash) still get exactly one decode.
 */
export function decodeRedirectOnce(raw: string): string {
  if (
    raw.startsWith('/') ||
    raw.startsWith('http://') ||
    raw.startsWith('https://') ||
    raw.startsWith('//')
  ) {
    return raw;
  }
  return decodeURIComponent(raw);
}

export function routePathOnly(fullPath: string): string {
  return (fullPath || '').split(/[?#]/)[0];
}

export function isLoginRoute(fullPath: string, loginPath: string): boolean {
  return routePathOnly(fullPath) === loginPath;
}

/**
 * Value stored in login `?redirect=`. Vue-router encodes query values, so the
 * caller must pass an already-encodeURIComponent'd string (or we encode here)
 * and resolveLoginRedirect later decodes exactly once.
 *
 * Login self-paths return undefined so a late 401 cannot nest
 * `/auth/login?redirect=...` into another redirect and drop the business target.
 */
export function encodeLoginRedirectParam(
  fullPath: string,
  loginPath: string,
): string | undefined {
  if (!fullPath || isLoginRoute(fullPath, loginPath)) {
    return undefined;
  }
  return encodeURIComponent(fullPath);
}

/** Hash-router fullPath (`#/bpm/foo?acc=1` → `/bpm/foo?acc=1`). */
export function hashRouterFullPath(hash: string): string {
  if (!hash) {
    return '';
  }
  return hash.startsWith('#') ? hash.slice(1) : hash;
}
