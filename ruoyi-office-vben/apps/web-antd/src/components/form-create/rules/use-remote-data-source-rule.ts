import type { Ref } from 'vue';

import { buildUUID } from '@vben/utils';

import {
  localeProps,
  makeRequiredRule,
} from '#/components/form-create/helpers';

/**
 * 数据源选择器设计器规则
 * 添加 "数据源选择器" 到系统字段菜单
 */
export function useRemoteDataSourceRule(designer: Ref) {
  const label = '数据源选择器';
  const name = 'RemoteDataSourceSelect';

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
    props(_: any, { api, t }: any) {
      return localeProps(t, `${name}.props`, [
        makeRequiredRule(),
        {
          type: 'RemoteDataSourceConfigEditor',
          field: 'dataSourceCode',
          title: '数据源配置',
          value: '',
          props: {
            getActiveRule: () => api.activeRule,
            getFormRules: () => designer.value?.getRule?.() ?? [],
          },
        },
      ]);
    },
  };
}
