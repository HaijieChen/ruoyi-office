export type ImApprovalTabKey = 'copy' | 'create' | 'done' | 'my' | 'todo';

export interface ImApprovalTab {
  key: ImApprovalTabKey;
  label: string;
  path: string;
}

/** IM 工作台审批中心五个入口。不含财务台账、流程设计器。 */
export const IM_APPROVAL_TABS: ImApprovalTab[] = [
  { key: 'todo', label: '待办任务', path: '/im/approval/todo' },
  { key: 'create', label: '发起流程', path: '/im/approval/create' },
  { key: 'my', label: '我的流程', path: '/im/approval/my' },
  { key: 'done', label: '已办任务', path: '/im/approval/done' },
  { key: 'copy', label: '抄送我的', path: '/im/approval/copy' },
];

export function isImApprovalShellPath(path: string | undefined): boolean {
  return Boolean(path?.startsWith('/im/approval'));
}

export function resolveImSilentRedirect(
  query: Record<string, string | undefined>,
): string {
  const id = query.id;
  if (!id) {
    return '/im/approval/todo';
  }
  const params = new URLSearchParams({ id });
  if (query.taskId) {
    params.set('taskId', query.taskId);
  }
  return `/bpm/process-instance/todo-detail?${params.toString()}`;
}
