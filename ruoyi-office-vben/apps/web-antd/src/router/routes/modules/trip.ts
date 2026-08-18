import type { RouteRecordRaw } from 'vue-router';

// OA 出差独立模块：挂在 /bpm/oa/trip，不要再注册第二个 /bpm/oa 父路由
const routes: RouteRecordRaw[] = [
  {
    path: '/bpm/oa/trip',
    name: 'OATrip',
    meta: {
      title: 'OA出差',
      hideInMenu: true,
    },
    children: [
      {
        path: '',
        name: 'OATripIndex',
        component: () => import('#/views/bpm/oa/trip/index.vue'),
        meta: {
          title: '出差列表',
          activePath: '/bpm/oa/trip',
        },
      },
      {
        path: 'create',
        name: 'OATripCreate',
        component: () => import('#/views/bpm/oa/trip/create.vue'),
        meta: {
          title: '创建出差',
          activePath: '/bpm/oa/trip',
        },
      },
      {
        path: 'detail',
        name: 'OATripDetail',
        component: () => import('#/views/bpm/oa/trip/detail.vue'),
        meta: {
          title: '出差详情',
          activePath: '/bpm/oa/trip',
        },
      },
    ],
  },
];

export default routes;
