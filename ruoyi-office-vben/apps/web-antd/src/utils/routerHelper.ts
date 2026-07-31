import type {
  RouteLocationNormalized,
  RouteRecordNormalized,
} from 'vue-router';

import { defineAsyncComponent } from 'vue';

const modules = import.meta.glob('../views/**/*.{vue,tsx}');

/**
 * 注册一个异步组件
 * @param componentPath 例:/bpm/oa/leave/detail
 */
export function registerComponent(componentPath: string) {
  for (const item in modules) {
    if (item.includes(componentPath)) {
      // 使用异步组件的方式来动态加载组件
      return defineAsyncComponent(modules[item] as any);
    }
  }
}

/**
 * 解析「路由 path + 可选 query」字符串，供 BPM 自定义 create 路径使用。
 * 例：/finance/invoice-application?openCreate=1
 */
export function parsePathWithQuery(raw: string): {
  path: string;
  query: Record<string, string>;
} {
  if (!raw) {
    return { path: '/', query: {} };
  }
  const qIndex = raw.indexOf('?');
  if (qIndex < 0) {
    return { path: raw, query: {} };
  }
  const path = raw.slice(0, qIndex) || '/';
  const query: Record<string, string> = {};
  new URLSearchParams(raw.slice(qIndex + 1)).forEach((value, key) => {
    query[key] = value;
  });
  return { path, query };
}

export const getRawRoute = (
  route: RouteLocationNormalized,
): RouteLocationNormalized => {
  if (!route) return route;
  const { matched, ...opt } = route;
  return {
    ...opt,
    matched: (matched
      ? matched.map((item) => ({
          meta: item.meta,
          name: item.name,
          path: item.path,
        }))
      : undefined) as RouteRecordNormalized[],
  };
};
