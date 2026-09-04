import { describe, expect, it } from 'vitest';

import {
  IM_APPROVAL_TABS,
  isImApprovalShellPath,
  resolveImSilentRedirect,
} from './tabs';

describe('IM_APPROVAL_TABS', () => {
  it('exposes five approval-center entries and no finance/designer menus', () => {
    expect(IM_APPROVAL_TABS.map((tab) => tab.key)).toEqual([
      'todo',
      'create',
      'my',
      'done',
      'copy',
    ]);
    expect(IM_APPROVAL_TABS.map((tab) => tab.label)).toEqual([
      '待办任务',
      '发起流程',
      '我的流程',
      '已办任务',
      '抄送我的',
    ]);
    const paths = IM_APPROVAL_TABS.map((tab) => tab.path).join(' ');
    expect(paths).not.toMatch(/finance|designer|model|manager/);
  });

  it('treats only /im/approval/* as the shell', () => {
    expect(isImApprovalShellPath('/im/approval/todo')).toBe(true);
    expect(isImApprovalShellPath('/finance/payment')).toBe(false);
    expect(isImApprovalShellPath('/bpm/manager/model')).toBe(false);
  });

  it('deep-links a todo document instead of the list', () => {
    expect(
      resolveImSilentRedirect({
        id: '88',
        taskId: '9',
      }),
    ).toBe('/bpm/process-instance/todo-detail?id=88&taskId=9');
    expect(resolveImSilentRedirect({})).toBe('/im/approval/todo');
  });
});
