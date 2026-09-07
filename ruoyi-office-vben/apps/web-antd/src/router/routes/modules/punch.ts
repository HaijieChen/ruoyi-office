import type { RouteRecordRaw } from 'vue-router';

// OA 补卡独立模块：挂在 /bpm/oa/punch，不要再注册第二个 /bpm/oa 父路由
const routes: RouteRecordRaw[] = [
  {
    path: '/bpm/oa/punch',
    name: 'OAPunch',
    meta: {
      title: 'OA补卡',
      hideInMenu: true,
    },
    children: [
      {
        path: '',
        name: 'OAPunchIndex',
        component: () => import('#/views/bpm/oa/punch/index.vue'),
        meta: {
          title: '补卡列表',
          activePath: '/bpm/oa/punch',
        },
      },
      {
        path: 'create',
        name: 'OAPunchCreate',
        component: () => import('#/views/bpm/oa/punch/create.vue'),
        meta: {
          title: '发起补卡',
          activePath: '/bpm/oa/punch',
        },
      },
      {
        path: 'detail',
        name: 'OAPunchDetail',
        component: () => import('#/views/bpm/oa/punch/detail.vue'),
        meta: {
          title: '补卡详情',
          activePath: '/bpm/oa/punch',
        },
      },
    ],
  },
];

export default routes;
