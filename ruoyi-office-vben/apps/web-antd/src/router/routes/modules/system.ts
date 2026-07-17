import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    name: 'Profile',
    path: '/profile',
    component: () => import('#/views/_core/profile/index.vue'),
    meta: {
      icon: 'ant-design:profile-outlined',
      title: $t('ui.widgets.profile'),
      hideInMenu: true,
    },
  },
  {
    path: '/system/notify-message',
    component: () => import('#/views/system/notify/my/index.vue'),
    name: 'MyNotifyMessage',
    meta: {
      title: '我的站内信',
      icon: 'ant-design:message-filled',
      hideInMenu: true,
    },
  },
  {
    path: '/system/dept/org-chart',
    component: () => import('#/views/system/dept/org-chart.vue'),
    name: 'SystemDeptOrgChart',
    meta: {
      title: '组织架构图',
      icon: 'ant-design:apartment-outlined',
      hideInMenu: true,
    },
  },
  {
    path: '/notice/view',
    component: () => import('#/views/notice/view/index.vue'),
    name: 'NoticeView',
    meta: {
      title: '通知公告',
      icon: 'ant-design:notification-outlined',
      hideInMenu: true,
    },
  },
];

export default routes;
