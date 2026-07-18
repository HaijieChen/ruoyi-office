/** 数据字典 Select 选择器组件 Props 类型 */
export interface DictSelectProps {
  dictType: string; // 字典类型
  valueType?: 'bool' | 'int' | 'str'; // 字典值类型
  selectType?: 'checkbox' | 'radio' | 'select'; // 选择器类型，下拉框 select、多选框 checkbox、单选框 radio
  formCreateInject?: any;
}

/** 左侧拖拽按钮 */
export interface MenuItem {
  label: string;
  name: string;
  icon: string;
}

/** 左侧拖拽按钮分类 */
export interface Menu {
  title: string;
  name: string;
  list: MenuItem[];
}

/** 通用 API 下拉组件 Props 类型 */
export interface ApiSelectProps {
  name: string; // 组件名称
  labelField?: string; // 选项标签
  valueField?: string; // 选项的值
  url?: string; // url 接口
  isDict?: boolean; // 是否字典选择器
}

/** 选择组件规则配置类型 */
export interface SelectRuleOption {
  label: string; // label 名称
  name: string; // 组件名称
  icon: string; // 组件图标
  props?: any[]; // 组件规则
  event?: any[]; // 事件配置
}

interface RemoteDataSourceRuntimeContextBase {
  formId: number;
  processDefinitionKey?: string;
  processInstanceId?: string;
}

/** 数据源选择器运行时上下文 — 从 BPM 流程页面注入，不进入表单定义。 */
export type RemoteDataSourceRuntimeContext =
  RemoteDataSourceRuntimeContextBase &
    (
      | { processDefinitionId: string; taskId?: never }
      | { processDefinitionId?: never; taskId: string }
    );

/** 数据源选择器组件 Props 类型 */
export interface RemoteDataSourceSelectProps {
  /** 数据源标识 */
  dataSourceCode: string;
  /** 依赖字段列表 */
  dependencies?: string[];
  /** form-create 注入 */
  formCreateInject?: any;
  /** 显示字段 */
  labelField?: string;
  /** 是否多选 */
  multiple?: boolean;
  /** 当前值 */
  modelValue?: unknown;
  /** 依赖变更策略 */
  onDependencyChange?: 'clear-and-reload' | 'keep-and-revalidate';
  /** 输出映射 { sourcePath → targetField } */
  outputMappings?: Record<string, string>;
  /** 分页 — 是否启用 */
  pageable?: boolean;
  /** 分页 — 每页条数上限 */
  pageSize?: number;
  /** 分页 — 页码参数名 */
  pageNoParamName?: string;
  /** 分页 — 每页条数参数名 */
  pageSizeParamName?: string;
  /** 参数绑定 { serverParamName → bindingExpression } */
  parameterBindings?: Record<string, string>;
  /** 运行时上下文 — 由 hydration 注入，不可序列化 */
  runtimeContext?: RemoteDataSourceRuntimeContext;
  /** 搜索 — 搜索参数名 */
  searchParamName?: string;
  /** 快照存储字段 */
  snapshotField?: string;
  /** 值字段 */
  valueField?: string;
}
