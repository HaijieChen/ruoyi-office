<script lang="ts" setup>
import type { EmployeeArchiveApi } from '#/api/hrm/employee';
import type { SystemDeptApi } from '#/api/system/dept';

import { computed, h, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions, useTabs } from '@vben/hooks';

import {
  Button,
  Checkbox,
  DatePicker,
  Input,
  message,
  Select,
  Space,
  Table,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { useVbenForm } from '#/adapter/form';
import {
  createEmployeeArchive,
  getEmployeeArchive,
  updateEmployeeArchive,
} from '#/api/hrm/employee';
import { getSimpleDeptList } from '#/api/system/dept';
import { AttachmentList } from '#/components/attachment-list';
import { CardContainer } from '#/components/basic-form';
import { DeptSelectModal } from '#/views/system/dept/components';

import {
  useAvatarFormSchema,
  useBasicFormSchema,
  useWorkFormSchema,
} from './data';
import {
  normalizeContractList,
  validateContractList,
  validateEducationRoles,
  validateSocialSecurity,
} from './roster-rules';

defineOptions({ name: 'HrmEmployeeArchiveInfo' });

const route = useRoute();
const router = useRouter();
const { closeCurrentTab } = useTabs();

const formData = ref<Partial<EmployeeArchiveApi.EmployeeArchive>>({});
const readonly = ref(false);
const loading = ref(false);

// 部门选择弹窗引用
const deptSelectModalRef = ref<InstanceType<typeof DeptSelectModal>>();

// 工作经历列表
const workExperienceList = ref<EmployeeArchiveApi.EmployeeWorkExperience[]>([]);
const employmentList = ref<EmployeeArchiveApi.EmployeeEmployment[]>([]);
const companyOptions = ref<{ label: string; value: number }[]>([]);
// 教育经历列表
const educationList = ref<EmployeeArchiveApi.EmployeeEducation[]>([]);
// 家属信息列表
const familyList = ref<EmployeeArchiveApi.EmployeeFamily[]>([]);
// 合同明细
const contractList = ref<EmployeeArchiveApi.EmployeeContract[]>([]);
// 入职资料（专用 VO：claimToken / 已有 id + downloadPath）
const onboardingAttachments = ref<EmployeeArchiveApi.OnboardingAttachment[]>(
  [],
);
const attachmentListRef = ref<InstanceType<typeof AttachmentList>>();

const educationLevelOptions = getDictOptions(DICT_TYPE.HRM_EDUCATION);
const educationTypeOptions = getDictOptions(DICT_TYPE.HRM_EDUCATION_TYPE);
const contractTypeOptions = getDictOptions(DICT_TYPE.HRM_CONTRACT_TYPE);

const currentContractSummary = computed(() => {
  if (!contractList.value.length) {
    return '暂无合同';
  }
  const current = contractList.value[contractList.value.length - 1]!;
  const start = current.startDate || '-';
  const end = current.endDate || '无固定期限';
  const typeLabel =
    contractTypeOptions.find((o) => o.value === current.contractType)?.label ||
    current.contractType ||
    '-';
  return `当前合同：第${current.sequenceNo || contractList.value.length}次 · ${typeLabel} · ${start} 至 ${end}`;
});

// 工作经历表格列定义
const workExperienceColumns = [
  {
    title: '开始时间',
    dataIndex: 'startTime',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(DatePicker, {
        value: text ? dayjs(text) : null,
        format: 'YYYY-MM-DD',
        placeholder: '请选择开始时间',
        style: { width: '100%' },
        onChange: (date: any) => {
          if (workExperienceList.value[index]) {
            workExperienceList.value[index].startTime = date
              ? dayjs(date).format('YYYY-MM-DD')
              : undefined;
          }
        },
      } as any);
    },
  },
  {
    title: '截止时间',
    dataIndex: 'endTime',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(DatePicker, {
        value: text ? dayjs(text) : null,
        format: 'YYYY-MM-DD',
        placeholder: '请选择截止时间',
        style: { width: '100%' },
        onChange: (date: any) => {
          if (workExperienceList.value[index]) {
            workExperienceList.value[index].endTime = date
              ? dayjs(date).format('YYYY-MM-DD')
              : undefined;
          }
        },
      } as any);
    },
  },
  {
    title: '职务',
    dataIndex: 'jobPosition',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '请输入职务',
        onChange: (e: any) => {
          if (workExperienceList.value[index]) {
            workExperienceList.value[index].jobPosition = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '单位名称',
    dataIndex: 'companyName',
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '请输入单位名称',
        onChange: (e: any) => {
          if (workExperienceList.value[index]) {
            workExperienceList.value[index].companyName = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    customRender: ({ index }: any) => {
      if (readonly.value) return '-';
      return h(
        Button,
        {
          type: 'link',
          size: 'small',
          danger: true,
          onClick: () => handleDeleteWorkExperience(index),
        },
        () => '删除',
      );
    },
  },
];

// 教育经历表格列定义
const educationColumns = [
  {
    title: '开始时间',
    dataIndex: 'startTime',
    width: 140,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(DatePicker, {
        value: text ? dayjs(text) : null,
        format: 'YYYY-MM-DD',
        placeholder: '开始',
        style: { width: '100%' },
        onChange: (date: any) => {
          if (educationList.value[index]) {
            educationList.value[index].startTime = date
              ? dayjs(date).format('YYYY-MM-DD')
              : undefined;
          }
        },
      } as any);
    },
  },
  {
    title: '毕业时间',
    dataIndex: 'endTime',
    width: 140,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(DatePicker, {
        value: text ? dayjs(text) : null,
        format: 'YYYY-MM-DD',
        placeholder: '毕业',
        style: { width: '100%' },
        onChange: (date: any) => {
          if (educationList.value[index]) {
            educationList.value[index].endTime = date
              ? dayjs(date).format('YYYY-MM-DD')
              : undefined;
          }
        },
      } as any);
    },
  },
  {
    title: '学历',
    dataIndex: 'educationLevel',
    width: 120,
    customRender: ({ text, index }: any) => {
      if (readonly.value) {
        return (
          educationLevelOptions.find((o) => o.value === text)?.label ||
          text ||
          '-'
        );
      }
      return h(Select, {
        value: text,
        options: educationLevelOptions,
        placeholder: '学历',
        allowClear: true,
        style: { width: '100%' },
        onChange: (val: any) => {
          if (educationList.value[index]) {
            educationList.value[index].educationLevel = val;
          }
        },
      } as any);
    },
  },
  {
    title: '学历类别',
    dataIndex: 'educationType',
    width: 120,
    customRender: ({ text, index }: any) => {
      if (readonly.value) {
        return (
          educationTypeOptions.find((o) => o.value === text)?.label ||
          text ||
          '-'
        );
      }
      return h(Select, {
        value: text,
        options: educationTypeOptions,
        placeholder: '类别',
        allowClear: true,
        style: { width: '100%' },
        onChange: (val: any) => {
          if (educationList.value[index]) {
            educationList.value[index].educationType = val;
          }
        },
      } as any);
    },
  },
  {
    title: '学位',
    dataIndex: 'degree',
    width: 100,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '学位',
        onChange: (e: any) => {
          if (educationList.value[index]) {
            educationList.value[index].degree = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '专业',
    dataIndex: 'major',
    width: 120,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '专业',
        onChange: (e: any) => {
          if (educationList.value[index]) {
            educationList.value[index].major = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '学校名称',
    dataIndex: 'schoolName',
    width: 140,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '学校',
        onChange: (e: any) => {
          if (educationList.value[index]) {
            educationList.value[index].schoolName = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '第一学历',
    dataIndex: 'firstEducation',
    width: 90,
    customRender: ({ text, index }: any) => {
      return h(Checkbox, {
        checked: !!text,
        disabled: readonly.value,
        onChange: (e: any) => {
          if (educationList.value[index]) {
            educationList.value[index].firstEducation = e.target.checked;
          }
        },
      } as any);
    },
  },
  {
    title: '最高学历',
    dataIndex: 'highestEducation',
    width: 90,
    customRender: ({ text, index }: any) => {
      return h(Checkbox, {
        checked: !!text,
        disabled: readonly.value,
        onChange: (e: any) => {
          if (educationList.value[index]) {
            educationList.value[index].highestEducation = e.target.checked;
          }
        },
      } as any);
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 80,
    customRender: ({ index }: any) => {
      if (readonly.value) return '-';
      return h(
        Button,
        {
          type: 'link',
          size: 'small',
          danger: true,
          onClick: () => handleDeleteEducation(index),
        },
        () => '删除',
      );
    },
  },
];

// 合同明细表格列
const contractColumns = [
  {
    title: '次数',
    dataIndex: 'sequenceNo',
    width: 70,
    customRender: ({ text }: any) => text || '-',
  },
  {
    title: '合同类型',
    dataIndex: 'contractType',
    width: 160,
    customRender: ({ text, index }: any) => {
      if (readonly.value) {
        return (
          contractTypeOptions.find((o) => o.value === text)?.label ||
          text ||
          '-'
        );
      }
      return h(Select, {
        value: text,
        options: contractTypeOptions,
        placeholder: '类型',
        allowClear: true,
        style: { width: '100%' },
        onChange: (val: any) => {
          if (contractList.value[index]) {
            contractList.value[index].contractType = val;
          }
        },
      } as any);
    },
  },
  {
    title: '开始日期',
    dataIndex: 'startDate',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(DatePicker, {
        value: text ? dayjs(text) : null,
        format: 'YYYY-MM-DD',
        style: { width: '100%' },
        onChange: (date: any) => {
          if (contractList.value[index]) {
            contractList.value[index].startDate = date
              ? dayjs(date).format('YYYY-MM-DD')
              : ('' as any);
          }
        },
      } as any);
    },
  },
  {
    title: '结束日期',
    dataIndex: 'endDate',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '无固定期限';
      return h(DatePicker, {
        value: text ? dayjs(text) : null,
        format: 'YYYY-MM-DD',
        style: { width: '100%' },
        onChange: (date: any) => {
          if (contractList.value[index]) {
            contractList.value[index].endDate = date
              ? dayjs(date).format('YYYY-MM-DD')
              : undefined;
          }
        },
      } as any);
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 80,
    customRender: ({ index }: any) => {
      if (readonly.value) return '-';
      return h(
        Button,
        {
          type: 'link',
          size: 'small',
          danger: true,
          onClick: () => handleDeleteContract(index),
        },
        () => '删除',
      );
    },
  },
];

// 家属信息表格列定义
const familyColumns = [
  {
    title: '姓名',
    dataIndex: 'name',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '请输入姓名',
        onChange: (e: any) => {
          if (familyList.value[index]) {
            familyList.value[index].name = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '关系',
    dataIndex: 'relationship',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '请输入关系',
        onChange: (e: any) => {
          if (familyList.value[index]) {
            familyList.value[index].relationship = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '联系电话',
    dataIndex: 'mobile',
    width: 150,
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '请输入联系电话',
        onChange: (e: any) => {
          if (familyList.value[index]) {
            familyList.value[index].mobile = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '工作单位',
    dataIndex: 'workUnit',
    customRender: ({ text, index }: any) => {
      if (readonly.value) return text || '-';
      return h(Input, {
        value: text,
        placeholder: '请输入工作单位',
        onChange: (e: any) => {
          if (familyList.value[index]) {
            familyList.value[index].workUnit = e.target.value;
          }
        },
      } as any);
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    customRender: ({ index }: any) => {
      if (readonly.value) return '-';
      return h(
        Button,
        {
          type: 'link',
          size: 'small',
          danger: true,
          onClick: () => handleDeleteFamily(index),
        },
        () => '删除',
      );
    },
  },
];

// 初始化基本信息表单
const [BasicForm, basicFormApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-1',
    labelWidth: 120,
  },
  wrapperClass: 'grid grid-cols-2 gap-4',
  layout: 'horizontal',
  schema: useBasicFormSchema(!!formData.value.id),
  showDefaultActions: false,
});

// 初始化照片表单
const [AvatarForm, avatarFormApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 80,
  },
  layout: 'vertical',
  schema: useAvatarFormSchema(),
  showDefaultActions: false,
});

// 初始化工作信息表单
const [WorkForm, workFormApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-1',
    labelWidth: 120,
  },
  wrapperClass: 'grid grid-cols-2 gap-4',
  layout: 'horizontal',
  schema: useWorkFormSchema(deptSelectModalRef, readonly),
  showDefaultActions: false,
});

const pageTitle = computed(() => {
  if (readonly.value) {
    return '查看员工档案';
  }
  return formData.value.id ? '编辑员工档案' : '新增员工档案';
});

/** 加载数据 */
async function loadData(newId?: string) {
  const id = newId || (route.query.id as string);
  if (!id) {
    return;
  }

  loading.value = true;
  try {
    const data = await getEmployeeArchive(Number(id));
    formData.value = data;

    // 后端返回的日期已经是 YYYY-MM-DD 格式（LocalDate），直接使用
    await basicFormApi.setValues(data);
    await avatarFormApi.setValues(data);
    await workFormApi.setValues(data);
    employmentList.value = (data.employmentList || []).map((item) => ({
      ...item,
      signed: !!item.signed,
    }));
    if (
      !employmentList.value.length &&
      data.companyId != null
    ) {
      employmentList.value = [
        {
          companyDeptId: data.companyId,
          companyName: data.companyName,
          signed: true,
        },
      ];
    }

    // 加载工作经历
    if (data.workExperienceList) {
      workExperienceList.value = data.workExperienceList.map((item) => ({
        ...item,
        startTime: item.startTime
          ? dayjs(item.startTime).format('YYYY-MM-DD')
          : '',
        endTime: item.endTime ? dayjs(item.endTime).format('YYYY-MM-DD') : '',
        jobPosition: item.jobPosition || '',
        companyName: item.companyName || '',
      }));
    }

    // 加载教育经历
    if (data.educationList) {
      educationList.value = data.educationList.map((item) => ({
        ...item,
        startTime: item.startTime
          ? dayjs(item.startTime).format('YYYY-MM-DD')
          : '',
        endTime: item.endTime ? dayjs(item.endTime).format('YYYY-MM-DD') : '',
        major: item.major || '',
        schoolName: item.schoolName || '',
        firstEducation: !!item.firstEducation,
        highestEducation: !!item.highestEducation,
      }));
    } else {
      educationList.value = [];
    }

    // 加载家属信息
    familyList.value = data.familyList || [];

    // 合同
    contractList.value = (data.contractList || []).map((item, index) => ({
      ...item,
      sequenceNo: (item.sequenceNo || index + 1) as 1 | 2 | 3 | 4,
      startDate: item.startDate
        ? dayjs(item.startDate).format('YYYY-MM-DD')
        : '',
      endDate: item.endDate
        ? dayjs(item.endDate).format('YYYY-MM-DD')
        : undefined,
    }));

    // 入职资料（含鉴权 downloadPath；不暴露 fileId/url）
    onboardingAttachments.value = (data.onboardingAttachments || []).map(
      (item) => ({
        id: item.id,
        claimToken: item.claimToken,
        fileName: item.fileName,
        fileSize: item.fileSize,
        fileExtension: item.fileExtension,
        fileType: item.fileType,
        sortOrder: item.sortOrder,
        remark: item.remark,
        uploadTime: item.uploadTime,
        downloadPath: item.downloadPath,
      }),
    );
  } catch (error) {
    console.error('加载员工档案失败', error);
    message.error('加载员工档案失败');
  } finally {
    loading.value = false;
  }
}

/** 保存 */
async function handleSave() {
  // 验证所有表单
  const basicValid = await basicFormApi.validate();
  const avatarValid = await avatarFormApi.validate();
  const workValid = await workFormApi.validate();

  if (!basicValid.valid || !avatarValid.valid || !workValid.valid) {
    return;
  }

  loading.value = true;
  try {
    // 合并所有表单数据
    const basicValues = await basicFormApi.getValues();
    const avatarValues = await avatarFormApi.getValues();
    const workValues = await workFormApi.getValues();

    const values = {
      ...basicValues,
      ...avatarValues,
      ...workValues,
    } as EmployeeArchiveApi.EmployeeArchive;

    // 新增时，员工工号由后端自动生成，前端不传或传空
    if (!values.id && (!values.employeeNo || values.employeeNo.trim() === '')) {
      values.employeeNo = undefined;
    }

    // 处理日期字段：空值统一转换为 undefined，后端 LocalDate 会自动处理 YYYY-MM-DD 格式
    // 注意：表格中的 onChange 已直接设置为 undefined，这里只处理表单字段可能的空字符串情况
    if (!values.birthday || values.birthday === '') {
      values.birthday = undefined;
    }
    if (!values.entryDate || values.entryDate === '') {
      values.entryDate = undefined;
    }
    if (!values.formalDate || values.formalDate === '') {
      values.formalDate = undefined;
    }

    // 花名册校验
    const ssError = validateSocialSecurity({
      socialSecurityEnabled: values.socialSecurityEnabled as boolean | null,
      socialSecurityStartMonth: values.socialSecurityStartMonth as string,
    });
    if (ssError) {
      message.error(ssError);
      loading.value = false;
      return;
    }
    const eduError = validateEducationRoles(educationList.value);
    if (eduError) {
      message.error(eduError);
      loading.value = false;
      return;
    }

    let normalizedContracts: EmployeeArchiveApi.EmployeeContract[];
    try {
      normalizedContracts = normalizeContractList(contractList.value);
    } catch (e: any) {
      message.error(e?.message || '合同数据不合法');
      loading.value = false;
      return;
    }
    const contractError = validateContractList(normalizedContracts);
    if (contractError) {
      message.error(contractError);
      loading.value = false;
      return;
    }

    // 社保为否时清空参保年月
    if (values.socialSecurityEnabled === false) {
      values.socialSecurityStartMonth = undefined;
    }

    // 表格中的日期字段已在 onChange 中设置为 undefined，直接使用即可
    const signedRows = employmentList.value.filter((r) => r.signed);
    if (!employmentList.value.length || signedRows.length !== 1) {
      message.error('请至少维护一家任职公司，并只标注一家签约公司');
      loading.value = false;
      return;
    }
    values.employmentList = employmentList.value;
    const signed = signedRows[0]!;
    values.companyId = signed.companyDeptId;
    values.companyName = signed.companyName;
    values.workExperienceList = workExperienceList.value;
    values.educationList = educationList.value;
    values.familyList = familyList.value;
    values.contractList = normalizedContracts;
    // 仅提交专用字段：已有 id 或新 claimToken
    values.onboardingAttachments = onboardingAttachments.value.map((a) => ({
      id: a.id,
      claimToken: a.claimToken,
      sortOrder: a.sortOrder,
      remark: a.remark,
    }));

    // 只读派生字段不回写
    delete values.age;
    delete values.companyTenureMonths;
    delete values.marriageChildbearingSummary;
    delete values.contractSignCount;
    delete values.currentContractType;
    delete values.currentContractStartDate;
    delete values.currentContractEndDate;

    if (formData.value.id) {
      values.id = formData.value.id;
      const oldUserGenerated = formData.value.userGenerated;
      await updateEmployeeArchive(values);
      // 保存成功后重新加载数据
      await loadData();
      // 如果已生成用户，提示同步更新
      if (oldUserGenerated) {
        message.success('保存成功，并自动更新用户信息');
      } else {
        message.success('保存成功');
      }
    } else {
      const result = await createEmployeeArchive(values);
      message.success('新增成功');
      // 新增成功后，如果有返回ID，更新路由并加载数据
      if (result && typeof result === 'number') {
        formData.value.id = result;
        await loadData(result);
      }
    }
  } catch (error) {
    console.error('保存失败', error);
    message.error('保存失败');
  } finally {
    loading.value = false;
  }
}

/** 关闭 */
function handleClose() {
  closeCurrentTab();
  router.go(-1);
}

// ========== 工作经历相关操作 ==========
function handleAddWorkExperience() {
  const newItem: EmployeeArchiveApi.EmployeeWorkExperience = {
    startTime: '',
    endTime: '',
    jobPosition: '',
    companyName: '',
  };
  workExperienceList.value.push(newItem);
}

function handleDeleteWorkExperience(index: number) {
  workExperienceList.value.splice(index, 1);
}

// ========== 教育经历相关操作 ==========
function handleAddEducation() {
  const newItem: EmployeeArchiveApi.EmployeeEducation = {
    startTime: '',
    endTime: '',
    major: '',
    schoolName: '',
    firstEducation: false,
    highestEducation: false,
  };
  educationList.value.push(newItem);
}

function handleDeleteEducation(index: number) {
  educationList.value.splice(index, 1);
}

// ========== 合同相关操作 ==========
function handleAddContract() {
  if (contractList.value.length >= 4) {
    message.warning('最多维护四次合同');
    return;
  }
  const next = (contractList.value.length + 1) as 1 | 2 | 3 | 4;
  contractList.value.push({
    sequenceNo: next,
    startDate: '',
    contractType: undefined,
  });
}

function handleDeleteContract(index: number) {
  contractList.value.splice(index, 1);
  contractList.value = normalizeContractList(contractList.value);
}

function handleUploadAttachment() {
  attachmentListRef.value?.handleTriggerUpload?.();
}

// ========== 家属信息相关操作 ==========
function handleAddFamily() {
  const newItem: EmployeeArchiveApi.EmployeeFamily = {
    name: '',
    relationship: '',
    mobile: '',
    workUnit: '',
  };
  familyList.value.push(newItem);
}

function handleDeleteFamily(index: number) {
  familyList.value.splice(index, 1);
}

/** 处理部门选择 */
function handleDeptSelect(
  dept: SystemDeptApi.Dept & { companyId?: number; companyName?: string },
) {
  // 设置部门ID、部门名称、公司ID和公司名称
  workFormApi.setFieldValue('deptId', dept.id);
  workFormApi.setFieldValue('deptName', dept.name);
  workFormApi.setFieldValue('companyId', dept.companyId);
  workFormApi.setFieldValue('companyName', dept.companyName || '');
}

// 监听 readonly 状态变化，更新表单的 disabled 状态
watch(
  readonly,
  (isReadonly) => {
    // 更新基本信息表单
    const basicSchema = useBasicFormSchema(!!formData.value.id);
    const updatedBasicSchema = basicSchema.map((item) => ({
      ...item,
      componentProps: {
        ...item.componentProps,
        disabled: isReadonly || item.fieldName === 'employeeNo', // 员工工号始终禁用
      },
    }));
    basicFormApi.updateSchema(updatedBasicSchema);

    // 更新照片表单
    const avatarSchema = useAvatarFormSchema();
    const updatedAvatarSchema = avatarSchema.map((item) => ({
      ...item,
      componentProps: {
        ...item.componentProps,
        disabled: isReadonly,
      },
    }));
    avatarFormApi.updateSchema(updatedAvatarSchema);

    // 更新工作信息表单
    const workSchema = useWorkFormSchema(deptSelectModalRef, readonly);
    const updatedWorkSchema = workSchema.map((item) => ({
      ...item,
      componentProps: {
        ...item.componentProps,
        disabled: isReadonly,
      },
    }));
    workFormApi.updateSchema(updatedWorkSchema);
  },
  { immediate: true },
);

function handleAddEmployment() {
  employmentList.value.push({
    companyDeptId: undefined as unknown as number,
    signed: employmentList.value.length === 0,
  });
}

function handleDeleteEmployment(index: number) {
  employmentList.value.splice(index, 1);
}

function markSigned(index: number) {
  employmentList.value = employmentList.value.map((row, i) => ({
    ...row,
    signed: i === index,
  }));
}

onMounted(async () => {
  // 判断是否只读
  readonly.value = route.query.readonly === 'true';
  try {
    const list = (await getSimpleDeptList()) || [];
    companyOptions.value = list
      .filter((d: any) => String(d.orgType) === '1')
      .map((d: any) => ({ label: d.name, value: d.id }));
  } catch {
    companyOptions.value = [];
  }

  // 加载数据
  await loadData();
});
</script>

<template>
  <Page :loading="loading" :title="pageTitle" auto-content-height>
    <template #extra>
      <Space>
        <Button @click="handleClose">关闭</Button>
        <Button v-if="!readonly" type="primary" @click="handleSave">
          保存
        </Button>
      </Space>
    </template>

    <!-- 基本信息 -->
    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="基本信息">
        <div class="flex gap-6">
          <!-- 左侧表单区域 -->
          <div class="flex-1">
            <BasicForm />
          </div>
          <!-- 右侧照片区域 -->
          <div class="w-[160px]">
            <AvatarForm />
          </div>
        </div>
      </CardContainer>
    </div>

    <!-- 工作信息 -->
    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="工作信息">
        <WorkForm />
        <!-- 部门选择弹窗 -->
        <DeptSelectModal ref="deptSelectModalRef" @select="handleDeptSelect" />
      </CardContainer>
    </div>

    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="任职公司">
        <template #extra>
          <Button v-if="!readonly" type="primary" @click="handleAddEmployment">
            添加任职
          </Button>
        </template>
        <Table
          :columns="[
            { title: '公司', key: 'company', width: 280 },
            { title: '签约公司', key: 'signed', width: 120 },
            { title: '操作', key: 'action', width: 80 },
          ]"
          :data-source="employmentList"
          :pagination="false"
          row-key="companyDeptId"
          size="small"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'company'">
              <Select
                v-model:value="record.companyDeptId"
                class="w-full"
                :disabled="readonly"
                :options="companyOptions"
                placeholder="选择任职公司"
                @change="
                  (v: any) => {
                    const hit = companyOptions.find((o) => o.value === v);
                    record.companyName = hit?.label;
                  }
                "
              />
            </template>
            <template v-else-if="column.key === 'signed'">
              <Checkbox
                :checked="!!record.signed"
                :disabled="readonly"
                @change="() => markSigned(index)"
              >
                签约
              </Checkbox>
            </template>
            <template v-else-if="column.key === 'action'">
              <Button
                v-if="!readonly"
                type="link"
                danger
                @click="handleDeleteEmployment(index)"
              >
                删除
              </Button>
            </template>
          </template>
        </Table>
      </CardContainer>
    </div>

    <!-- 工作经历 -->
    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="工作经历">
        <template #extra>
          <Button
            v-if="!readonly"
            type="primary"
            @click="handleAddWorkExperience"
          >
            {{ $t('ui.actionTitle.create') }}
          </Button>
        </template>
        <Table
          :columns="workExperienceColumns"
          :data-source="workExperienceList"
          :pagination="false"
          :row-key="(record, index) => record.id || `work_${index}`"
          size="small"
        />
      </CardContainer>
    </div>

    <!-- 教育经历 -->
    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="教育经历">
        <template #extra>
          <Button v-if="!readonly" type="primary" @click="handleAddEducation">
            {{ $t('ui.actionTitle.create') }}
          </Button>
        </template>
        <Table
          :columns="educationColumns"
          :data-source="educationList"
          :pagination="false"
          :row-key="(record, index) => record.id || `edu_${index}`"
          size="small"
          :scroll="{ x: 1200 }"
        />
      </CardContainer>
    </div>

    <!-- 合同信息 -->
    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="合同信息">
        <template #extra>
          <Button v-if="!readonly" type="primary" @click="handleAddContract">
            添加合同
          </Button>
        </template>
        <div class="mb-2 text-sm text-gray-500">
          {{ currentContractSummary }}
        </div>
        <Table
          :columns="contractColumns"
          :data-source="contractList"
          :pagination="false"
          :row-key="(record, index) => record.id || `contract_${index}`"
          size="small"
        />
      </CardContainer>
    </div>

    <!-- 家属信息 -->
    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="家属信息">
        <template #extra>
          <Button v-if="!readonly" type="primary" @click="handleAddFamily">
            {{ $t('ui.actionTitle.create') }}
          </Button>
        </template>
        <Table
          :columns="familyColumns"
          :data-source="familyList"
          :pagination="false"
          :row-key="(record, index) => record.id || `family_${index}`"
          size="small"
        />
      </CardContainer>
    </div>

    <!-- 入职资料 -->
    <div class="mb-4 rounded-lg bg-white p-4 shadow-sm">
      <CardContainer title="入职资料">
        <template #extra>
          <Button
            v-if="!readonly"
            type="primary"
            @click="handleUploadAttachment"
          >
            上传附件
          </Button>
        </template>
        <AttachmentList
          ref="attachmentListRef"
          v-model="onboardingAttachments as any"
          :readonly="readonly"
          accept=".pdf,.jpg,.jpeg,.png,.gif,.bmp,.webp,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.zip,.rar,.7z"
          :max-count="10"
          :max-size="20"
          :hide-upload-button="true"
          :use-file-claim="true"
          :auth-download="true"
        />
      </CardContainer>
    </div>
  </Page>
</template>

<style scoped></style>
