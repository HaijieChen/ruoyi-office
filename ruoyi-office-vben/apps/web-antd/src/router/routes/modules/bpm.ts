import type { RouteRecordRaw } from 'vue-router';

/**
 * BPM 静态辅路由（仅菜单未覆盖的叶子页）。
 *
 * 产品：
 * - 可申请流程目录 = processInstance/create（BPMN + Simple）
 * - 后端菜单正式入口：/bpm/task/create（system_menu 2720）
 * - 静态兼容入口：/bpm/start-process（同组件）
 *
 * 重要：不要再注册 path='/bpm' 的父路由去包一棵不完整子树。
 * accessMode=backend 下静态路由与菜单路由合并时，若静态先注册不完整的 /bpm，
 * 会遮住菜单下的 /bpm/task/create、/todo、/manager/model 等 → 大面积 404。
 * 因此本文件只挂绝对路径叶子，让菜单树完整拥有 /bpm 子树。
 */
const routes: RouteRecordRaw[] = [
  // 可申请流程目录（兼容书签 / 文档中的 start-process）
  {
    path: '/bpm/start-process',
    name: 'BpmStartProcess',
    component: () => import('#/views/bpm/processInstance/create/index.vue'),
    meta: {
      title: '发起流程',
      icon: 'ant-design:plus-circle-outlined',
      keepAlive: true,
      hideInMenu: true,
    },
  },
  {
    path: '/bpm/process-instance/detail',
    component: () => import('#/views/bpm/processInstance/detail/index.vue'),
    name: 'BpmProcessInstanceDetail',
    meta: {
      title: '流程详情',
      activePath: '/bpm/task/my',
      icon: 'ant-design:history-outlined',
      keepAlive: false,
      hideInMenu: true,
    },
    props: (route) => {
      return {
        id: route.query.id,
        taskId: route.query.taskId,
        activityId: route.query.activityId,
      };
    },
  },
  {
    path: '/bpm/process-instance/todo-detail',
    component: () => import('#/views/bpm/processInstance/detail/index.vue'),
    name: 'BpmProcessInstanceTodoDetail',
    meta: {
      title: '待办详情',
      activePath: '/bpm/task/todo',
      icon: 'ant-design:history-outlined',
      keepAlive: false,
      hideInMenu: true,
    },
    props: (route) => {
      return {
        id: route.query.id,
        taskId: route.query.taskId,
        activityId: route.query.activityId,
      };
    },
  },
  {
    path: '/bpm/manager/form/edit',
    name: 'BpmFormEditor',
    component: () => import('#/views/bpm/form/designer/index.vue'),
    meta: {
      title: '编辑流程表单',
      activePath: '/bpm/manager/form',
      hideInMenu: true,
    },
    props: (route) => {
      return {
        id: route.query.id,
        type: route.query.type,
        copyId: route.query.copyId,
      };
    },
  },
  {
    path: '/bpm/manager/model/create',
    component: () => import('#/views/bpm/model/form/index.vue'),
    name: 'BpmModelCreate',
    meta: {
      title: '创建流程',
      activePath: '/bpm/manager/model',
      icon: 'carbon:flow-connection',
      hideInMenu: true,
      keepAlive: true,
    },
  },
  {
    path: '/bpm/manager/model/:type/:id',
    component: () => import('#/views/bpm/model/form/index.vue'),
    name: 'BpmModelUpdate',
    meta: {
      title: '修改流程',
      activePath: '/bpm/manager/model',
      icon: 'carbon:flow-connection',
      hideInMenu: true,
      keepAlive: true,
    },
  },
  {
    path: '/bpm/manager/definition',
    component: () => import('#/views/bpm/model/definition/index.vue'),
    name: 'BpmProcessDefinition',
    meta: {
      title: '流程定义',
      activePath: '/bpm/manager/model',
      icon: 'carbon:flow-modeler',
      hideInMenu: true,
      keepAlive: true,
    },
  },
  {
    path: '/bpm/process-instance/report',
    component: () => import('#/views/bpm/processInstance/report/index.vue'),
    name: 'BpmProcessInstanceReport',
    meta: {
      title: '数据报表',
      activePath: '/bpm/manager/model',
      icon: 'carbon:data-2',
      hideInMenu: true,
      keepAlive: true,
    },
  },
];

export default routes;
