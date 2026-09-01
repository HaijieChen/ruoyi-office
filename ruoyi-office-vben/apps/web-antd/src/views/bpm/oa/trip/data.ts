import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

import { z } from '#/adapter/form';
import { DictTag } from '#/components/dict-tag';
import { FilePreviewList } from '#/components/upload';
import { getRangePickerDefaultProps } from '#/utils';

const INVALID_RANGE_MESSAGE = '结束时间必须晚于开始时间，且时长须大于 0';

/** hours = minutes/60，HALF_UP 保留 1 位小数；缺一边或非法区间返回 undefined */
export function calcTripHours(
  startTime?: number | string | null,
  endTime?: number | string | null,
): number | undefined {
  if (startTime == null || startTime === '' || endTime == null || endTime === '') {
    return undefined;
  }
  const start = Number(startTime);
  const end = Number(endTime);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
    return undefined;
  }
  const minutes = Math.floor((end - start) / 60_000);
  if (minutes <= 0) {
    return undefined;
  }
  const hours = Math.round((minutes / 60) * 10) / 10;
  return hours > 0 ? hours : undefined;
}

export function isTripRangeInvalid(
  startTime?: number | string | null,
  endTime?: number | string | null,
): boolean {
  if (startTime == null || startTime === '' || endTime == null || endTime === '') {
    return false;
  }
  return calcTripHours(startTime, endTime) == null;
}

/** 新增的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'userNickname',
      label: '申请人',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
    },
    {
      fieldName: 'deptName',
      label: '部门',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
    },
    {
      fieldName: 'type',
      label: '出差类型',
      component: 'Select',
      componentProps: {
        placeholder: '请选择出差类型',
        options: getDictOptions(DICT_TYPE.BPM_OA_TRIP_TYPE, 'number'),
        allowClear: true,
      },
      rules: 'required',
    },
    {
      fieldName: 'startTime',
      label: '开始时间',
      component: 'DatePicker',
      componentProps: {
        placeholder: '请选择开始时间',
        showTime: true,
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
      },
      rules: 'required',
    },
    {
      fieldName: 'endTime',
      label: '结束时间',
      component: 'DatePicker',
      componentProps: {
        placeholder: '请选择结束时间',
        showTime: true,
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
      },
      dependencies: {
        triggerFields: ['startTime', 'endTime'],
        rules: (values) => {
          if (!values.endTime) {
            return 'required';
          }
          if (isTripRangeInvalid(values.startTime, values.endTime)) {
            return z.any().refine(() => false, { message: INVALID_RANGE_MESSAGE });
          }
          return 'required';
        },
      },
      rules: 'required',
    },
    {
      fieldName: 'hours',
      label: '出差时长(小时)',
      component: 'Input',
      componentProps: {
        disabled: true,
        placeholder: '选择起止时间后自动计算',
      },
      dependencies: {
        triggerFields: ['startTime', 'endTime'],
        componentProps: (values) => {
          values.hours = calcTripHours(values.startTime, values.endTime);
          return {
            disabled: true,
            placeholder: '选择起止时间后自动计算',
          };
        },
        rules: (values) => {
          if (isTripRangeInvalid(values.startTime, values.endTime)) {
            return z.any().refine(() => false, { message: INVALID_RANGE_MESSAGE });
          }
          return z.any().optional();
        },
      },
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'bizType',
      label: '出差类型',
      component: 'Select',
      componentProps: {
        placeholder: '请选择出差类型',
        options: getDictOptions(DICT_TYPE.BPM_OA_TRIP_BIZ_TYPE, 'number'),
        allowClear: true,
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
      field: 'status',
      title: '状态',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.BPM_PROCESS_INSTANCE_STATUS },
      },
    },
    {
      field: 'bizType',
      title: '出差类型',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.BPM_OA_TRIP_BIZ_TYPE },
      },
    },
    {
      field: 'reason',
      title: '出差事由',
      minWidth: 160,
    },
    {
      field: 'destination',
      title: '目的地城市',
      minWidth: 140,
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
      title: '时长(小时)',
      minWidth: 100,
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
    { label: '申请人', field: 'userNickname' },
    { label: '部门', field: 'deptName' },
    {
      label: '出差类型',
      field: 'bizType',
      render: (val) =>
        val == null || val === ''
          ? ''
          : h(DictTag, { type: DICT_TYPE.BPM_OA_TRIP_BIZ_TYPE, value: val }),
    },
    { label: '出差事由', field: 'reason' },
    { label: '出发城市', field: 'originCity' },
    { label: '目的地城市', field: 'destination' },
    {
      label: '交通工具',
      field: 'transport',
      render: (val) =>
        val == null || val === ''
          ? ''
          : h(DictTag, { type: DICT_TYPE.BPM_OA_TRIP_TRANSPORT, value: val }),
    },
    { label: '公司/邀请方', field: 'partyName' },
    { label: '地址', field: 'address' },
    { label: '对接人', field: 'contactInfo' },
    {
      label: '机酒预定情况',
      field: 'hotelBooking',
      render: (val) =>
        val == null || val === ''
          ? ''
          : h(DictTag, { type: DICT_TYPE.BPM_OA_TRIP_HOTEL_BOOKING, value: val }),
    },
    {
      label: '是否需要内容产出',
      field: 'needOutput',
      render: (val) =>
        val == null || val === ''
          ? ''
          : h(DictTag, { type: DICT_TYPE.INFRA_BOOLEAN_STRING, value: val }),
    },
    {
      label: '是否有车马费',
      field: 'hasCarriageFee',
      render: (val) =>
        val == null || val === ''
          ? ''
          : h(DictTag, { type: DICT_TYPE.INFRA_BOOLEAN_STRING, value: val }),
    },
    { label: '备注', field: 'remark' },
    { label: '同行人员', field: 'companionNickname' },
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
      label: '出差时长(小时)',
      field: 'hours',
      render: (val) =>
        val === undefined || val === null || val === '' ? '' : `${Number(val).toFixed(1)} 小时`,
    },
    {
      label: '附件',
      field: 'attachmentUrls',
      render: (val) => h(FilePreviewList, { value: val }),
    },
  ];
}
