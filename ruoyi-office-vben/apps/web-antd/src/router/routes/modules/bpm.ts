import type { RouteRecordRaw } from 'vue-router';

/**
 * BPM 静态辅路由。
 *
 * 产品事实（勿再误判为「错误路径」）：
 * - 「可申请流程」目录页 = views/bpm/processInstance/create/index.vue
 *   拉取已启用流程定义（suspensionState=1），按分类展示，支持搜索后发起。
 * - 系统内置两种模型：BpmModelType.BPMN(10) 与 BpmModelType.SIMPLE(20)；
 *   选中流程后 form.vue 按 modelType 分别渲染 BPMN / Simple 流程图。
 *
 * 路由双入口（同组件）：
 * 1) 静态：/bpm/start-process（本文件，兼容书签与「工作流程」redirect）
 * 2) 后端菜单：/bpm/task/create（system_menu 2720「发起流程」，component 同上）
 *
 * accessMode=backend 时静态路由与菜单路由会合并；start-process 必须挂真实 component，
 * 不能只做 redirect，否则访问 /bpm → /bpm/start-process 会 404，目录页消失。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/bpm',
    name: 'bpm',
    // 工作流程入口 → 可申请流程目录
    redirect: '/bpm/start-process',
    meta: {
      title: '工作流',
      hideInMenu: true,
    },
    children: [
      {
        path: 'start-process',
        component: () => import('#/views/bpm/processInstance/create/index.vue'),
        // 与后端菜单 component_name 可能重名；path 仍独立可用。
        // 若需避免 name 覆盖，可改为 BpmStartProcess，但 path 入口保持不变。
        name: 'BpmProcessInstanceCreate',
        meta: {
          title: '发起流程',
          icon: 'ant-design:plus-circle-outlined',
          keepAlive: true,
        },
      },
      {
        path: 'task',
        name: 'BpmTask',
        meta: {
          title: '任务管理',
          icon: 'ant-design:history-outlined',
        },
        redirect: '/bpm/task/my',
        children: [
          {
            path: 'my',
            name: 'BpmTaskMy',
            component: () => import('#/views/bpm/processInstance/index.vue'),
            meta: {
              title: '我的流程',
            },
          },
        ],
      },
      {
        path: 'process-instance/detail',
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
        path: 'process-instance/todo-detail',
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
        path: 'manager/model/create',
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
        path: 'manager/model/:type/:id',
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
        path: 'manager/definition',
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
        path: 'process-instance/report',
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
    ],
  },
];

export default routes;
