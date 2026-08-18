import type { RouteRecordRaw } from 'vue-router';

// OA 外出独立模块：挂在 /bpm/oa/outing，不要再注册第二个 /bpm/oa 父路由
const routes: RouteRecordRaw[] = [
  {
    path: '/bpm/oa/outing',
    name: 'OAOuting',
    meta: {
      title: 'OA外出',
      hideInMenu: true,
    },
    children: [
      {
        path: '',
        name: 'OAOutingIndex',
        component: () => import('#/views/bpm/oa/outing/index.vue'),
        meta: {
          title: '外出列表',
          activePath: '/bpm/oa/outing',
        },
      },
      {
        path: 'create',
        name: 'OAOutingCreate',
        component: () => import('#/views/bpm/oa/outing/create.vue'),
        meta: {
          title: '发起外出',
          activePath: '/bpm/oa/outing',
        },
      },
      {
        path: 'detail',
        name: 'OAOutingDetail',
        component: () => import('#/views/bpm/oa/outing/detail.vue'),
        meta: {
          title: '外出详情',
          activePath: '/bpm/oa/outing',
        },
      },
    ],
  },
];

export default routes;
