import { onMounted, ref } from 'vue';

import { buildUUID } from '@vben/utils';

import { getDataSourceSimpleList } from '#/api/bpm/form-data-source';
import {
  localeProps,
  makeRequiredRule,
} from '#/components/form-create/helpers';

/**
 * 数据源选择器设计器规则
 * 添加 "数据源选择器" 到系统字段菜单
 */
export function useRemoteDataSourceRule() {
  const label = '数据源选择器';
  const name = 'RemoteDataSourceSelect';
  const dataSourceOptions = ref<{ label: string; value: string }[]>([]);

  onMounted(async () => {
    try {
      const list = await getDataSourceSimpleList();
      dataSourceOptions.value =
        list?.map((item) => ({
          label: `${item.name} (${item.code})`,
          value: item.code,
        })) ?? [];
    } catch {
      // 设计器中加载失败不阻塞
    }
  });

  return {
    icon: 'icon-json',
    label,
    name,
    rule() {
      return {
        type: name,
        field: buildUUID(),
        title: label,
        info: '',
        $required: false,
        props: {
          dataSourceCode: '',
          labelField: 'name',
          valueField: 'id',
          multiple: false,
        },
      };
    },
    props(_: any, { t }: any) {
      return localeProps(t, `${name}.props`, [
        makeRequiredRule(),
        {
          type: 'select',
          field: 'dataSourceCode',
          title: '数据源标识',
          value: '',
          options: dataSourceOptions.value,
        },
        {
          type: 'input',
          field: 'labelField',
          title: '显示字段',
          value: 'name',
          props: { placeholder: 'name' },
        },
        {
          type: 'input',
          field: 'valueField',
          title: '值字段',
          value: 'id',
          props: { placeholder: 'id' },
        },
        {
          type: 'TableOptions',
          field: 'parameterBindings',
          title: '请求参数绑定',
          value: {},
          info: '参数名对应 FORM/USER/PROCESS 的安全字段表达式',
          props: {
            valueType: 'object',
            column: [
              { label: '数据源参数名', key: 'label' },
              { label: '绑定表达式', key: 'value' },
            ],
          },
        },
        {
          type: 'TableOptions',
          field: 'outputMappings',
          title: '结果字段联动',
          value: {},
          info: '将结果字段安全映射到当前表单的普通字段',
          props: {
            valueType: 'object',
            column: [
              { label: '结果字段路径', key: 'label' },
              { label: '目标表单字段', key: 'value' },
            ],
          },
        },
        {
          type: 'TableOptions',
          field: 'dependencies',
          title: '依赖字段',
          value: [],
          info: '任一依赖字段变化时重新加载数据源',
          props: {
            valueType: 'string',
            column: [{ label: '表单字段名', key: 'value' }],
          },
        },
        {
          type: 'switch',
          field: 'multiple',
          title: '是否多选',
          value: false,
        },
        {
          type: 'input',
          field: 'searchParamName',
          title: '搜索参数名',
          info: '配置后启用搜索，参数名须为数据源定义的参数',
          props: { placeholder: '如 keyword，留空禁用搜索' },
        },
        {
          type: 'switch',
          field: 'pageable',
          title: '启用分页',
          value: false,
        },
        {
          type: 'input',
          field: 'pageNoParamName',
          title: '页码参数名',
          info: '须为数据源定义的参数',
          props: { placeholder: '如 pageNo' },
        },
        {
          type: 'input',
          field: 'pageSizeParamName',
          title: '每页条数参数名',
          info: '须为数据源定义的参数',
          props: { placeholder: '如 pageSize' },
        },
        {
          type: 'inputNumber',
          field: 'pageSize',
          title: '每页条数',
          value: 20,
          props: { min: 1, max: 200 },
        },
        {
          type: 'input',
          field: 'snapshotField',
          title: '快照存储字段',
          info: '历史记录中用于还原显示标签的字段名',
          props: { placeholder: '留空则不存储快照' },
        },
        {
          type: 'select',
          field: 'onDependencyChange',
          title: '依赖变更策略',
          value: 'clear-and-reload',
          options: [
            { label: '清空并重新加载', value: 'clear-and-reload' },
            { label: '保留有效值并重新加载', value: 'keep-and-revalidate' },
          ],
        },
      ]);
    },
  };
}
