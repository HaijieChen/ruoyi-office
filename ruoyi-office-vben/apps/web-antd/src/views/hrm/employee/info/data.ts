import type { VbenFormSchema } from '@vben/common-ui';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { z } from '#/adapter/form';

/**
 * 基本信息表单配置
 */
export function useBasicFormSchema(_isEdit?: boolean): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '姓名',
      component: 'Input',
      componentProps: {
        placeholder: '请输入姓名',
      },
      rules: 'required',
    },
    {
      fieldName: 'employeeNo',
      label: '员工工号',
      component: 'Input',
      help: '系统自动生成，无需手动输入',
      componentProps: {
        placeholder: '系统自动生成',
        readonly: true,
        disabled: true,
      },
    },
    {
      fieldName: 'sex',
      label: '性别',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.SYSTEM_USER_SEX, 'number'),
      },
      rules: 'required',
    },
    {
      fieldName: 'birthday',
      label: '出生日期',
      component: 'DatePicker',
      componentProps: {
        placeholder: '请选择出生日期',
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
      },
    },
    {
      fieldName: 'bloodType',
      label: '血型',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.HRM_BLOOD_TYPE),
      },
    },
    {
      fieldName: 'education',
      label: '文化程度',
      component: 'Select',
      componentProps: {
        placeholder: '请选择文化程度',
        options: getDictOptions(DICT_TYPE.HRM_EDUCATION),
      },
    },
    {
      fieldName: 'nation',
      label: '民族',
      component: 'Input',
      componentProps: {
        placeholder: '请输入民族',
      },
    },
    {
      fieldName: 'politicalStatus',
      label: '政治面貌',
      component: 'Select',
      componentProps: {
        placeholder: '请选择政治面貌',
        options: getDictOptions(DICT_TYPE.HRM_POLITICAL_STATUS),
      },
    },
    {
      fieldName: 'maritalStatus',
      label: '婚姻状况',
      component: 'Select',
      componentProps: {
        placeholder: '请选择婚姻状况',
        options: getDictOptions(DICT_TYPE.HRM_MARITAL_STATUS),
      },
    },
    {
    {
      fieldName: 'householdType',
      label: '户籍性质',
      component: 'Select',
      componentProps: {
        placeholder: '请选择户籍性质',
        options: getDictOptions(DICT_TYPE.HRM_HOUSEHOLD_TYPE),
        allowClear: true,
      },
    },
    {
      fieldName: 'age',
      label: '年龄',
      component: 'Input',
      componentProps: {
        placeholder: '自动计算',
        readonly: true,
        disabled: true,
      },
    },
    {
      fieldName: 'jobTitle',
      label: '职称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入职称',
      },
    },
    {
      fieldName: 'nativePlace',
      label: '籍贯',
      component: 'Input',
      componentProps: {
        placeholder: '请输入籍贯',
      },
    },
    {
      fieldName: 'height',
      label: '身高(cm)',
      component: 'InputNumber',
      componentProps: {
        placeholder: '请输入身高',
        min: 0,
        max: 300,
        precision: 2,
      },
    },
    {
      fieldName: 'weight',
      label: '体重(kg)',
      component: 'InputNumber',
      componentProps: {
        placeholder: '请输入体重',
        min: 0,
        max: 500,
        precision: 2,
      },
    },
    {
      fieldName: 'idCard',
      label: '身份证号码',
      rules: z
        .string()
        .min(1, '身份证号不能为空')
        .regex(
          /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9X]$/i,
          '请输入18位有效身份证号',
        ),
      component: 'Input',
      componentProps: {
        placeholder: '请输入身份证号码',
      },
    },
    {
      fieldName: 'mobile',
      label: '手机号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入手机号',
        maxLength: 11,
      },
      rules: z
        .string()
        .min(1, '手机号不能为空')
        .regex(/^1[3-9]\d{9}$/, '请输入正确的手机号码'),
    },
    {
      fieldName: 'email',
      label: '邮箱',
      component: 'Input',
      componentProps: {
        placeholder: '请输入邮箱',
      },
      rules: z
        .string()
        .optional()
        .refine(
          (val) => !val || /^[^\s@]+@[^\s@][^\s.@]*\.[^\s@]+$/.test(val),
          '请输入正确的邮箱地址',
        ),
    },
    {
      fieldName: 'householdAddress',
      label: '户籍所在地',
      component: 'Input',
      componentProps: {
        placeholder: '请输入户籍所在地',
      },
    },
    {
      fieldName: 'currentAddress',
      label: '现居住地址',
      component: 'Input',
      componentProps: {
        placeholder: '请输入现居住地址',
      },
    },
    {
      fieldName: 'emergencyContact',
      label: '紧急联系人',
      component: 'Input',
      componentProps: {
        placeholder: '请输入紧急联系人',
      },
    },
    {
      fieldName: 'emergencyRelationship',
      label: '紧急联系人关系',
      component: 'Input',
      componentProps: {
        placeholder: '请输入关系，如配偶/父母',
      },
    },
    {
      fieldName: 'emergencyPhone',
      label: '联系电话',
      component: 'Input',
      componentProps: {
        placeholder: '请输入联系电话',
      },
    },
  ];
}

/**
 * 照片表单配置
 */
export function useAvatarFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'avatar',
      component: 'ImageUpload',
      componentProps: {
        contentText: '上传员工照片',
        showDescription: false,
        maxNumber: 1,
      },
    },
  ];
}

/**
 * 工作信息表单配置
 */
export function useWorkFormSchema(
  deptSelectModalRef?: any,
  readonly?: any,
): VbenFormSchema[] {
  return [
    {
      fieldName: 'bankName',
      label: '工资开户行',
      component: 'Input',
      componentProps: {
        placeholder: '请输入工资开户行',
      },
    },
    {
      fieldName: 'bankAccount',
      label: '工资卡账户',
      component: 'Input',
      componentProps: {
        placeholder: '请输入工资卡账户',
      },
    },
    {
      fieldName: 'jobPost',
      label: '职位',
      component: 'Input',
      componentProps: {
        placeholder: '请输入职位',
      },
    },
    {
      fieldName: 'jobPosition',
      label: '职务',
      component: 'Select',
      componentProps: {
        placeholder: '请选择职务',
        options: getDictOptions(DICT_TYPE.HRM_JOB_POSITION),
      },
    },
    {
      fieldName: 'employeeStatus',
      label: '员工类型',
      component: 'Select',
      componentProps: {
        placeholder: '请选择员工类型',
        options: getDictOptions(DICT_TYPE.HRM_EMPLOYEE_STATUS, 'number'),
      },
      rules: 'required',
    },
    {
      fieldName: 'employmentForm',
      label: '用工形式',
      component: 'Select',
      componentProps: {
        placeholder: '请选择用工形式',
        options: getDictOptions(DICT_TYPE.HRM_EMPLOYMENT_FORM),
        allowClear: true,
      },
    },
    {
      fieldName: 'deptName',
      label: '所属部门',
      component: 'HelpInput',
      componentProps: {
        placeholder: '请选择所属部门',
        bind: {
          readonly,
          onClick: () => {
            if (!readonly?.value && deptSelectModalRef?.value) {
              deptSelectModalRef.value.modalApi.open();
            }
          },
        },
        onClick: () => {
          if (!readonly?.value && deptSelectModalRef?.value) {
            deptSelectModalRef.value.modalApi.open();
          }
        },
      },
      // 导入写库后编辑保存亦须有有效部门展示名（与 deptId 同步）
      rules: 'required',
    },
    {
      fieldName: 'deptId',
      label: '部门ID',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'companyId',
      label: '公司ID',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'companyName',
      label: '所属公司',
      component: 'Input',
      componentProps: {
        placeholder: '所属公司',
        readonly: true,
        disabled: true,
      },
    },
    {
      fieldName: 'entryDate',
      label: '入职日期',
      component: 'DatePicker',
      componentProps: {
        placeholder: '请选择入职日期',
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
      },
    },
    {
      fieldName: 'formalDate',
      label: '转正日期',
      component: 'DatePicker',
      componentProps: {
        placeholder: '请选择转正日期',
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
      },
    },
    {
      fieldName: 'companyTenureMonths',
      label: '司龄（月）',
      component: 'Input',
      componentProps: {
        placeholder: '自动计算',
        readonly: true,
        disabled: true,
      },
    },
    {
      fieldName: 'socialSecurityEnabled',
      label: '是否缴纳社保',
      component: 'Select',
      componentProps: {
        placeholder: '请选择（可留空）',
        allowClear: true,
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
      },
    },
    {
      fieldName: 'housingFundEnabled',
      label: '是否缴纳公积金',
      component: 'Select',
      componentProps: {
        placeholder: '请选择（可留空）',
        allowClear: true,
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
      },
    },
    {
      fieldName: 'socialSecurityStartMonth',
      label: '参保年月',
      component: 'DatePicker',
      componentProps: {
        placeholder: '请选择参保年月',
        picker: 'month',
        format: 'YYYY-MM',
        valueFormat: 'YYYY-MM',
      },
    },
    {
      fieldName: 'probationSalary',
      label: '试用期薪资',
      component: 'InputNumber',
      componentProps: {
        placeholder: '请输入试用期薪资',
        min: 0,
        precision: 2,
        style: { width: '100%' },
      },
    },
    {
      fieldName: 'regularSalary',
      label: '转正薪资',
      component: 'InputNumber',
      componentProps: {
        placeholder: '请输入转正薪资',
        min: 0,
        precision: 2,
        style: { width: '100%' },
      },
    },
    {
      fieldName: 'recruitmentChannel',
      label: '招聘渠道',
      component: 'Input',
      componentProps: {
        placeholder: '请输入招聘渠道',
      },
    },
    {
      fieldName: 'interviewerName',
      label: '面试人',
      component: 'Input',
      componentProps: {
        placeholder: '请输入面试人',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Input',
      formItemClass: 'col-span-2',
      componentProps: {
        placeholder: '请输入备注',
      },
    },
  ];
}
