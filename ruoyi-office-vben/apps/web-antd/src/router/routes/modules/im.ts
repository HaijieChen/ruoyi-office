import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/im/approval/silent-login',
    name: 'ImApprovalSilentLogin',
    component: () => import('#/views/im/approval/silent-login.vue'),
    meta: {
      title: 'IM 免登',
      hideInMenu: true,
      ignoreAccess: true,
    },
  },
  {
    path: '/im/approval',
    name: 'ImApprovalShell',
    component: () => import('#/views/im/approval/layout.vue'),
    redirect: '/im/approval/todo',
    meta: {
      title: '审批中心',
      hideInMenu: true,
      ignoreAccess: true,
    },
    children: [
      {
        path: 'todo',
        name: 'ImApprovalTodo',
        component: () => import('#/views/bpm/task/todo/index.vue'),
        meta: { title: '待办任务', hideInMenu: true, ignoreAccess: true },
      },
      {
        path: 'create',
        name: 'ImApprovalCreate',
        component: () => import('#/views/bpm/processInstance/create/index.vue'),
        meta: { title: '发起流程', hideInMenu: true, ignoreAccess: true },
      },
      {
        path: 'my',
        name: 'ImApprovalMy',
        component: () => import('#/views/bpm/processInstance/index.vue'),
        meta: { title: '我的流程', hideInMenu: true, ignoreAccess: true },
      },
      {
        path: 'done',
        name: 'ImApprovalDone',
        component: () => import('#/views/bpm/task/done/index.vue'),
        meta: { title: '已办任务', hideInMenu: true, ignoreAccess: true },
      },
      {
        path: 'copy',
        name: 'ImApprovalCopy',
        component: () => import('#/views/bpm/task/copy/index.vue'),
        meta: { title: '抄送我的', hideInMenu: true, ignoreAccess: true },
      },
    ],
  },
];

export default routes;
