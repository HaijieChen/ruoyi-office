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
    const target = decodeURIComponent(raw || fallback);
    return isLoop(target) ? fallback : target;
  } catch {
    return fallback;
  }
}
