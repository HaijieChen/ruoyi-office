import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

import { DictTag } from '#/components/dict-tag';
import { getRangePickerDefaultProps } from '#/utils';

import { normalizeHolidaySelectOptions } from './overtime-holiday-options';

export {
  calcOvertimeHours,
  combineDateAndTime,
  getOvertimeRangeError,
  previewOvertimeHours,
} from './overtime-hours';

/** 与字典 bpm_oa_overtime_holiday 对齐；字典未加载时作下拉兜底 */
export const OA_OVERTIME_HOLIDAY_DICT = 'bpm_oa_overtime_holiday';

export const OA_OVERTIME_HOLIDAY_OPTIONS = [
  { label: '否', value: 'false' },
  { label: '是', value: 'true' },
];

export type { OvertimeHolidayOption } from './overtime-holiday-options';
export { normalizeHolidaySelectOptions } from './overtime-holiday-options';

export function getOvertimeHolidayOptions() {
  const loaded = getDictOptions(OA_OVERTIME_HOLIDAY_DICT, 'string');
  return normalizeHolidaySelectOptions(
    loaded?.length ? loaded : OA_OVERTIME_HOLIDAY_OPTIONS,
  );
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'reason',
      label: '加班事由',
      component: 'Input',
      componentProps: {
        placeholder: '请输入加班事由',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '审批结果',
      component: 'Select',
      componentProps: {
        placeholder: '请选择审批结果',
        allowClear: true,
        options: getDictOptions(
          DICT_TYPE.BPM_PROCESS_INSTANCE_STATUS,
          'number',
        ),
      },
    },
    {
      fieldName: 'createTime',
      label: '申请时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'id',
      title: '申请编号',
      minWidth: 100,
    },
    {
      field: 'userNickname',
      title: '申请人',
      minWidth: 100,
    },
    {
      field: 'deptName',
      title: '部门',
      minWidth: 120,
    },
    {
      field: 'reason',
      title: '加班事由',
      minWidth: 160,
    },
    {
      field: 'startTime',
      title: '开始时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'endTime',
      title: '结束时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'hours',
      title: '加班时长',
      minWidth: 100,
      formatter: ({ cellValue }) =>
        cellValue === undefined || cellValue === null || cellValue === ''
          ? ''
          : Number(cellValue).toFixed(1),
    },
    {
      field: 'holiday',
      title: '是否法定节假日',
      minWidth: 140,
      cellRender: {
        name: 'CellDict',
        props: { type: OA_OVERTIME_HOLIDAY_DICT },
      },
    },
    {
      field: 'status',
      title: '状态',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.BPM_PROCESS_INSTANCE_STATUS },
      },
    },
    {
      field: 'createTime',
      title: '申请时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 240,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 详情 */
export function useDetailFormSchema(): DescriptionItemSchema[] {
  return [
    {
      label: '申请人',
      field: 'userNickname',
    },
    {
      label: '部门',
      field: 'deptName',
    },
    {
      label: '加班事由',
      field: 'reason',
    },
    {
      label: '开始时间',
      field: 'startTime',
      render: (val) => formatDateTime(val) as string,
    },
    {
      label: '结束时间',
      field: 'endTime',
      render: (val) => formatDateTime(val) as string,
    },
    {
      label: '加班时长(小时)',
      field: 'hours',
      render: (val) =>
        val === undefined || val === null || val === ''
          ? ''
          : `${Number(val).toFixed(1)} 小时`,
    },
    {
      label: '是否法定节假日',
      field: 'holiday',
      render: (val) =>
        h(DictTag, {
          type: OA_OVERTIME_HOLIDAY_DICT,
          value: val,
        }),
    },
    {
      label: '附件',
      field: 'attachmentUrls',
      render: (val) => {
        if (!Array.isArray(val) || val.length === 0) {
          return '';
        }
        return val.join('\n');
      },
    },
  ];
}
