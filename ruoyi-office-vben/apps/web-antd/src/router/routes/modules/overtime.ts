import type { RouteRecordRaw } from 'vue-router';

// OA 加班独立模块：挂在 /bpm/oa/overtime，不要再注册第二个 /bpm/oa 父路由
const routes: RouteRecordRaw[] = [
  {
    path: '/bpm/oa/overtime',
    name: 'OAOvertime',
    meta: {
      title: 'OA加班',
      hideInMenu: true,
    },
    children: [
      {
        path: '',
        name: 'OAOvertimeIndex',
        component: () => import('#/views/bpm/oa/overtime/index.vue'),
        meta: {
          title: '加班列表',
          activePath: '/bpm/oa/overtime',
        },
      },
      {
        path: 'create',
        name: 'OAOvertimeCreate',
        component: () => import('#/views/bpm/oa/overtime/create.vue'),
        meta: {
          title: '发起加班',
          activePath: '/bpm/oa/overtime',
        },
      },
      {
        path: 'detail',
        name: 'OAOvertimeDetail',
        component: () => import('#/views/bpm/oa/overtime/detail.vue'),
        meta: {
          title: '加班详情',
          activePath: '/bpm/oa/overtime',
        },
      },
      {
        path: 'calendar',
        name: 'OAOvertimeCalendar',
        component: () => import('#/views/bpm/oa/overtime-calendar/index.vue'),
        meta: {
          title: '节假日日历',
          activePath: '/bpm/oa/overtime/calendar',
          authority: ['bpm:oa-overtime-calendar:query'],
        },
      },
    ],
  },
];

export default routes;
