import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DescriptionItemSchema } from '#/components/description';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { formatDate, formatDateTime } from '@vben/utils';

import { getRangePickerDefaultProps } from '#/utils';

export {
  combinePunchDateAndTime,
  remainingLabel,
  shouldFetchRemaining,
  toPunchDateStr,
} from './punch-remaining';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'reason',
      label: '补卡事由',
      component: 'Input',
      componentProps: {
        placeholder: '请输入补卡事由',
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
      field: 'punchDate',
      title: '补卡日期',
      minWidth: 120,
      formatter: ({ cellValue }) => (cellValue ? formatDate(cellValue) : ''),
    },
    {
      field: 'punchTime',
      title: '补卡时刻',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'reason',
      title: '补卡事由',
      minWidth: 160,
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
      label: '补卡日期',
      field: 'punchDate',
      render: (val) => (val ? (formatDate(val) as string) : ''),
    },
    {
      label: '补卡时刻',
      field: 'punchTime',
      render: (val) => formatDateTime(val) as string,
    },
    {
      label: '补卡事由',
      field: 'reason',
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
