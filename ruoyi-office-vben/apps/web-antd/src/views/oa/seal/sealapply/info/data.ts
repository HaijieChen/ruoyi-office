import type { Ref } from 'vue';

import type { VbenFormSchema } from '#/adapter/form';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { message } from 'ant-design-vue';

/** 新增/修改的表单 */
export function useFormSchema(
  readonly?: Ref<boolean>,
  nodeKeyName?: Ref<string>,
  canReturnEdit?: Ref<boolean>,
): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'billCode',
      label: '单据编号',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'sealName',
      label: '印章',
      rules: 'required',
      component: 'Input',
      componentProps: {
        placeholder: '请输入印章',
      },
    },
    {
      fieldName: 'useType',
      label: '用章类型',
      rules: 'required',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.OA_SEAL_USE_TYPE, 'number'),
        placeholder: '请选择用章类型',
      },
    },
    {
      fieldName: 'useMode',
      label: '用章方式',
      rules: 'required',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.OA_SEAL_USE_MODE, 'number'),
        placeholder: '请选择用章方式',
      },
    },
    {
      fieldName: 'documentTitle',
      label: '文件标题',
      component: 'Input',
      componentProps: {
        placeholder: '请输入文件标题',
      },
    },
    {
      fieldName: 'documentType',
      label: '文件类型',
      component: 'Input',
      componentProps: {
        placeholder: '请输入文件类型',
      },
    },
    {
      fieldName: 'documentCount',
      label: '文件份数',
      component: 'InputNumber',
      componentProps: {
        placeholder: '请输入文件份数',
        min: 1,
      },
    },
    {
      fieldName: 'contractAmount',
      label: '合同金额',
      component: 'InputAmount',
      componentProps: {
        placeholder: '请输入合同金额',
        showUnit: false,
        precision: 2,
      },
      dependencies: {
        triggerFields: ['useType'],
        show: (values) => values.useType === 1, // 仅合同用章时显示
      },
    },
    {
      fieldName: 'contractParty',
      label: '合同对方',
      component: 'Input',
      componentProps: {
        placeholder: '请输入合同对方',
      },
      dependencies: {
        triggerFields: ['useType'],
        show: (values) => values.useType === 1, // 仅合同用章时显示
      },
    },
    {
      fieldName: 'expectedUseTime',
      label: '使用日期',
      rules: 'required',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '请选择使用日期',
      },
      dependencies: {
        triggerFields: ['expectedReturnTime'],
        trigger: (values, formApi) => {
          if (
            values.expectedReturnTime &&
            values.expectedUseTime &&
            values.expectedReturnTime <= values.expectedUseTime
          ) {
            message.error('预计用章时间不能晚于或等于预计归还时间');
            // 立即清空预计归还时间字段
            formApi?.setFieldValue('expectedReturnTime', undefined);
          }
        },
      },
    },
    {
      fieldName: 'expectedReturnTime',
      label: '预计归还日期',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '选填',
      },
      dependencies: {
        triggerFields: ['expectedUseTime'],
        trigger: (values, formApi) => {
          if (
            values.expectedUseTime &&
            values.expectedReturnTime &&
            values.expectedReturnTime <= values.expectedUseTime
          ) {
            message.error('预计归还日期须晚于使用日期');
            formApi?.setFieldValue('expectedReturnTime', undefined);
          }
        },
      },
    },
    {
      fieldName: 'actualReturnTime',
      label: '实际归还时间',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '请选择实际归还时间',
        // 当canReturnEdit为true时，即使在只读模式下也可以编辑
        disabled: () => {
          if (canReturnEdit?.value) {
            return false; // canReturnEdit为true时，不禁用
          }
          return readonly?.value; // 否则跟随readonly状态
        },
      },
      // 当节点名称为"申请人归还印章"时设置为必填
      rules: nodeKeyName?.value === '申请人归还印章' ? 'required' : undefined,
    },
    {
      fieldName: 'isUrgent',
      label: '是否紧急',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        placeholder: '请选择是否紧急',
      },
    },
    {
      fieldName: 'cause',
      label: '用章事由',
      rules: 'required',
      component: 'Textarea',
      formItemClass: 'col-span-full',
      componentProps: {
        placeholder: '请输入用章事由',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      formItemClass: 'col-span-full',
      componentProps: {
        placeholder: '请输入备注',
      },
    },
  ];
}
