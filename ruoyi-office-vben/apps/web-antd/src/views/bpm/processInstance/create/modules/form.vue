<script lang="ts" setup>
import type { Component } from 'vue';

import type { BpmProcessDefinitionApi } from '#/api/bpm/definition';
import type { BpmProcessInstanceApi } from '#/api/bpm/processInstance';

import { computed, nextTick, ref, shallowRef, watch } from 'vue';

import {
  BpmCandidateStrategyEnum,
  BpmFieldPermissionType,
  BpmModelFormType,
  BpmModelType,
  BpmNodeIdEnum,
} from '@vben/constants';
import { useTabs } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';

import formCreate from '@form-create/ant-design-vue';
import {
  Button,
  Card,
  Col,
  Empty,
  message,
  Row,
  Space,
  Spin,
  Select,
  Tabs,
} from 'ant-design-vue';

import {
  getAllowedEmployments,
  getProcessDefinition,
} from '#/api/bpm/definition';
import {
  createProcessInstance,
  getApprovalDetail as getApprovalDetailApi,
} from '#/api/bpm/processInstance';
import {
  decodeFields,
  hydrateRemoteDataSourceRules,
  setConfAndFields2,
} from '#/components/form-create';
import { router } from '#/router';
import ProcessInstanceBpmnViewer from '#/views/bpm/processInstance/detail/modules/bpm-viewer.vue';
import ProcessInstanceSimpleViewer from '#/views/bpm/processInstance/detail/modules/simple-bpm-viewer.vue';
import ProcessInstanceTimeline from '#/views/bpm/processInstance/detail/modules/time-line.vue';

import { resolveCreateShellEmbedLoader } from '../embed-registry';

/** 类型定义 */
interface ProcessFormData {
  rule: any[];
  option: Record<string, any>;
  value: Record<string, any>;
}

interface UserTask {
  id: number | string;
  name: string;
}

defineOptions({ name: 'BpmProcessInstanceCreateForm' });

const props = defineProps({
  selectProcessDefinition: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(['cancel']);
const { closeCurrentTab } = useTabs();

const getTitle = computed(() => {
  return `流程表单 - ${props.selectProcessDefinition.name}`;
});

const detailForm = ref<ProcessFormData>({
  rule: [],
  option: {},
  value: {},
});
const fApi = ref<any>();

const startCompanyDeptId = ref<number | undefined>();
const startDeptId = ref<number | undefined>();
const employmentOptions = ref<{ label: string; value: number }[]>([]);
const allowedEmployments = ref<
  {
    deptId?: number;
    companyDeptId?: number;
    signed?: boolean;
    companyName?: string;
    deptName?: string;
  }[]
>([]);

const startUserSelectTasks = ref<UserTask[]>([]);
const startUserSelectAssignees = ref<Record<string, string[]>>({});
const tempStartUserSelectAssignees = ref<Record<string, string[]>>({});

const bpmnXML = ref<string | undefined>(undefined);
const simpleJson = ref<string | undefined>(undefined);

const activeTab = ref('form');
const activityNodes = ref<BpmProcessInstanceApi.ApprovalNodeInfo[]>([]);
const processInstanceStartLoading = ref(false);

/** NORMAL vs CUSTOM embed vs 业务权限拒绝 */
const shellMode = ref<'embed' | 'normal' | 'unregistered' | 'denied'>('normal');
const EmbedComponent = shallowRef<Component | null>(null);
/** 动态业务表单实例（async SFC expose） */
const embedBodyRef = ref<any>(null);
/** embed 表单是否已完成 reset（可提交） */
const embedReady = ref(false);
const embedLoading = ref(false);
const embedError = ref<null | string>(null);
/** 后端 canStart=false 时的友好空态文案（权威，勿硬编码权限点） */
const startDeniedReason = ref<null | string>(null);
/** 取消过期的 embed 初始化 */
let embedInitGen = 0;
/** 预测请求序号，仅应用最新结果（P2） */
let predictGen = 0;
let predictTimer: ReturnType<typeof setTimeout> | undefined;

const isNormalShell = computed(() => shellMode.value === 'normal');
const isEmbedShell = computed(() => shellMode.value === 'embed');
const isUnregistered = computed(() => shellMode.value === 'unregistered');
const isStartDenied = computed(() => shellMode.value === 'denied');
const canSubmit = computed(() => {
  if (isUnregistered.value || isStartDenied.value) return false;
  if (isEmbedShell.value) return embedReady.value && !embedLoading.value;
  return true;
});

async function loadDiagram(definitionId: string) {
  const processDefinitionDetail: BpmProcessDefinitionApi.ProcessDefinition =
    await getProcessDefinition(definitionId);
  if (processDefinitionDetail) {
    bpmnXML.value = processDefinitionDetail.bpmnXml;
    simpleJson.value = processDefinitionDetail.simpleModel;
  }
}

/** 自选审批人：壳 UI 用 string[]，领域 API 用 number[] */
function buildStartUserSelectAssigneesForDomain(): Record<string, number[]> {
  const out: Record<string, number[]> = {};
  for (const [activityId, ids] of Object.entries(
    startUserSelectAssignees.value || {},
  )) {
    out[activityId] = (ids || []).map(Number).filter((n) => Number.isFinite(n));
  }
  return out;
}

function validateStartUserSelect(): boolean {
  if (startUserSelectTasks.value?.length > 0) {
    for (const userTask of startUserSelectTasks.value) {
      const key = String(userTask.id);
      const assignees = startUserSelectAssignees.value[key];
      if (!Array.isArray(assignees) || assignees.length === 0) {
        message.warning(`请选择${userTask.name}的候选人`);
        return false;
      }
    }
  }
  return true;
}

/** 提交按钮 */
async function submitForm() {
  if (isEmbedShell.value) {
    if (!embedReady.value || !embedBodyRef.value?.submit) {
      message.error(embedError.value || '表单未就绪，请稍候或重试');
      return;
    }
    if (!validateStartUserSelect()) {
      return;
    }
    processInstanceStartLoading.value = true;
    try {
      await embedBodyRef.value.submit({
        startUserSelectAssignees: buildStartUserSelectAssigneesForDomain(),
        startCompanyDeptId: startCompanyDeptId.value,
        startDeptId: startDeptId.value,
      });
      await closeCurrentTab();
      await router.push({ name: 'BpmProcessInstanceMy' });
    } catch {
      // validation / domain error already surfaced
    } finally {
      processInstanceStartLoading.value = false;
    }
    return;
  }

  if (!fApi.value || !props.selectProcessDefinition) {
    return;
  }
  await fApi.value.validate();
  if (!validateStartUserSelect()) {
    return;
  }

  processInstanceStartLoading.value = true;
  try {
    await createProcessInstance({
      processDefinitionId: props.selectProcessDefinition.id,
      variables: {
        ...(detailForm.value.value || {}),
        startCompanyDeptId: startCompanyDeptId.value,
        startDeptId: startDeptId.value,
        startEmploymentLabel: employmentOptions.value.find(
          (o) => o.value === startCompanyDeptId.value,
        )?.label,
      },
      startUserSelectAssignees: startUserSelectAssignees.value,
    });
    message.success('发起流程成功');
    await closeCurrentTab();
    await router.push({ name: 'BpmProcessInstanceMy' });
  } finally {
    processInstanceStartLoading.value = false;
  }
}

function schedulePredict(
  processDefinitionId: string,
  vars: Record<string, unknown>,
) {
  if (predictTimer) clearTimeout(predictTimer);
  predictTimer = setTimeout(() => {
    // 预测前快照已选人，避免 getApprovalDetail 清空后无法恢复
    tempStartUserSelectAssignees.value = {
      ...startUserSelectAssignees.value,
    };
    void getApprovalDetail({
      id: processDefinitionId,
      processVariablesStr: JSON.stringify(vars || {}),
    });
  }, 400);
}

function onEmbedPredictChange(vars: Record<string, unknown>) {
  const id = props.selectProcessDefinition?.id;
  if (!id || !embedReady.value) return;
  schedulePredict(id, vars || {});
}

/**
 * 等待 FormBody 实例暴露 reset（chunk 已 await 后再 mount，通常很快；
 * 用 watch + 长超时错误态，避免固定 1.5s 半初始化）。
 */
function waitForEmbedBody(gen: number): Promise<any> {
  if (embedBodyRef.value?.reset) {
    return Promise.resolve(embedBodyRef.value);
  }
  return new Promise((resolve, reject) => {
    const stop = watch(
      embedBodyRef,
      (v) => {
        if (gen !== embedInitGen) {
          stop();
          resolve(null);
          return;
        }
        if (v?.reset) {
          stop();
          resolve(v);
        }
      },
      { flush: 'post' },
    );
    // 硬超时仅用于失败态；正常路径靠 loader Promise + mount
    window.setTimeout(() => {
      stop();
      if (gen !== embedInitGen) {
        resolve(null);
        return;
      }
      if (embedBodyRef.value?.reset) {
        resolve(embedBodyRef.value);
      } else {
        reject(new Error('业务表单挂载超时'));
      }
    }, 30_000);
  });
}

async function retryEmbedInit() {
  const row = props.selectProcessDefinition;
  if (!row) return;
  await initProcessInfo(row);
}

/** 设置表单信息、获取流程图数据 */
function applyEmploymentSelection(companyDeptId?: number) {
  const hit = allowedEmployments.value.find(
    (e) => e.companyDeptId === companyDeptId,
  );
  startCompanyDeptId.value = hit?.companyDeptId;
  startDeptId.value = hit?.deptId;
}

async function loadEmployments(processDefinitionId?: string) {
  try {
    const list = processDefinitionId
      ? (await getAllowedEmployments(processDefinitionId)) || []
      : [];
    allowedEmployments.value = list.filter(
      (e) => e.companyDeptId != null && e.deptId != null,
    );
    employmentOptions.value = allowedEmployments.value.map((e) => ({
      value: e.companyDeptId!,
      label: `${e.companyName || e.companyDeptId} / ${e.deptName || e.deptId}${e.signed ? '（签约）' : ''}`,
    }));
    const signed = allowedEmployments.value.find((e) => e.signed);
    applyEmploymentSelection(
      signed?.companyDeptId ?? allowedEmployments.value[0]?.companyDeptId,
    );
  } catch {
    allowedEmployments.value = [];
    employmentOptions.value = [];
    startCompanyDeptId.value = undefined;
    startDeptId.value = undefined;
  }
}

async function initProcessInfo(row: any, formVariables?: any) {
  await loadEmployments(row?.id);
  embedInitGen += 1;
  const gen = embedInitGen;
  predictGen += 1;

  startUserSelectTasks.value = [];
  startUserSelectAssignees.value = {};
  tempStartUserSelectAssignees.value = {};
  activityNodes.value = [];
  bpmnXML.value = undefined;
  simpleJson.value = undefined;
  EmbedComponent.value = null;
  embedBodyRef.value = null;
  embedReady.value = false;
  embedLoading.value = false;
  embedError.value = null;
  startDeniedReason.value = null;
  activeTab.value = 'form';

  // 嵌入式业务表单：先用后端 canStart 权威预检（深链/缓存/撤权后直接访问）
  // 列表已隐藏无权限项；此处禁止加载受保护数据，禁止半屏字段与通用 403 toast
  if (resolveCreateShellEmbedLoader(row.key)) {
    const eligibility = await resolveStartEligibility(row);
    if (gen !== embedInitGen) return;
    if (eligibility.canStart === false) {
      shellMode.value = 'denied';
      startDeniedReason.value =
        eligibility.cannotStartReason ||
        '无发起权限，请联系管理员分配对应业务角色';
      return;
    }
  }

  if (row.formType === BpmModelFormType.NORMAL) {
    shellMode.value = 'normal';
    const decodedFields = decodeFields(row.formFields);
    const allowedFields = new Set(
      decodedFields.map((field: any) => field.field).filter(Boolean),
    );

    if (formVariables) {
      for (const key in formVariables) {
        if (!allowedFields.has(key)) {
          delete formVariables[key];
        }
      }
    }

    setConfAndFields2(detailForm, row.formConf, row.formFields, formVariables);

    hydrateRemoteDataSourceRules(
      detailForm.value.rule,
      Number.isInteger(row.formId) && row.formId > 0 && row.id
        ? {
            formId: row.formId,
            processDefinitionId: row.id,
            processDefinitionKey: row.key,
          }
        : undefined,
    );

    detailForm.value.option = {
      ...detailForm.value.option,
      submitBtn: false,
      resetBtn: false,
    };

    await nextTick();
    await getApprovalDetail({
      id: row.id,
      processVariablesStr: JSON.stringify({
        ...(formVariables || {}),
        startCompanyDeptId: startCompanyDeptId.value,
        startDeptId: startDeptId.value,
      }),
    });
    await loadDiagram(row.id);
    return;
  }

  // CUSTOM：壳内嵌注册表（禁止 router.push 业务列表）
  const loader = resolveCreateShellEmbedLoader(row.key);
  if (!loader) {
    shellMode.value = 'unregistered';
    message.error(
      `流程「${row.name || row.key}」未配置发起表单组件，请联系管理员`,
    );
    return;
  }

  shellMode.value = 'embed';
  embedLoading.value = true;
  embedError.value = null;

  try {
    // 1) 先 await 动态 import，chunk 慢也不会假 ready
    const mod = await loader();
    if (gen !== embedInitGen) return;
    const comp = (mod as any)?.default ?? mod;
    EmbedComponent.value = comp as Component;
    await nextTick();
    await nextTick();

    // 2) 等实例 expose reset
    const body = await waitForEmbedBody(gen);
    if (gen !== embedInitGen) return;
    if (!body?.reset) {
      throw new Error('业务表单未就绪');
    }

    // 3) 业务数据初始化
    await body.reset({ mode: 'create' });
    if (gen !== embedInitGen) return;

    embedReady.value = true;
    embedLoading.value = false;

    await getApprovalDetail({
      id: row.id,
      processVariablesStr: JSON.stringify(body.getPredictVariables?.() || {}),
    });
    await loadDiagram(row.id);
  } catch (error: any) {
    if (gen !== embedInitGen) return;
    console.error(error);
    embedReady.value = false;
    embedLoading.value = false;
    // 403：保留空壳，不在这里 message.error（拦截器已 toast R1）
    const bizMsg =
      error?.response?.data?.msg ||
      error?.data?.msg ||
      error?.message ||
      '';
    const isForbidden =
      error?.data?.code === 403 ||
      error?.response?.data?.code === 403 ||
      bizMsg.includes('没有该操作权限') ||
      bizMsg.includes('缺少权限');
    if (isForbidden) {
      shellMode.value = 'denied';
      startDeniedReason.value =
        startDeniedReason.value ||
        '无发起权限，请联系管理员分配对应业务角色';
      embedError.value = null;
      return;
    }
    embedError.value = bizMsg || '加载业务表单失败';
    message.error(embedError.value);
  }
}

/**
 * 解析发起资格：优先列表行上的后端字段；深链时再 get 一次权威元数据。
 * 前端禁止根据权限码自行判断是否可发起。
 */
async function resolveStartEligibility(row: {
  id?: string;
  key?: string;
  canStart?: boolean;
  cannotStartReason?: string;
}): Promise<{ canStart?: boolean; cannotStartReason?: string }> {
  if (row.canStart === false) {
    return {
      canStart: false,
      cannotStartReason: row.cannotStartReason,
    };
  }
  if (row.canStart === true) {
    return { canStart: true };
  }
  // 行上无 canStart（旧缓存/深链）：向后端拉取权威结果
  try {
    const detail = await getProcessDefinition(row.id, row.key);
    if (!detail) {
      return { canStart: true };
    }
    return {
      canStart: detail.canStart,
      cannotStartReason: detail.cannotStartReason,
    };
  } catch {
    // 预检失败不放开嵌入加载；保守拒绝
    return {
      canStart: false,
      cannotStartReason: '无法校验发起权限，请稍后重试或联系管理员',
    };
  }
}

watch(
  () => detailForm.value.value,
  (newValue) => {
    if (!isNormalShell.value) return;
    if (newValue && Object.keys(newValue).length > 0) {
      tempStartUserSelectAssignees.value = {
        ...startUserSelectAssignees.value,
      };
      startUserSelectAssignees.value = {};
      getApprovalDetail({
        id: props.selectProcessDefinition.id,
        processVariablesStr: JSON.stringify({
          ...(newValue || {}),
          startCompanyDeptId: startCompanyDeptId.value,
          startDeptId: startDeptId.value,
        }),
      });
    }
  },
  {
    deep: true,
  },
);

watch(startCompanyDeptId, () => {
  applyEmploymentSelection(startCompanyDeptId.value);
  if (!props.selectProcessDefinition?.id) {
    return;
  }
  getApprovalDetail({
    id: props.selectProcessDefinition.id,
    processVariablesStr: JSON.stringify({
      ...(detailForm.value.value || {}),
      startCompanyDeptId: startCompanyDeptId.value,
      startDeptId: startDeptId.value,
    }),
  });
});

async function getApprovalDetail(row: {
  id: string;
  processVariablesStr: string;
}) {
  const seq = ++predictGen;
  try {
    const data = await getApprovalDetailApi({
      processDefinitionId: row.id,
      activityId: BpmNodeIdEnum.START_USER_NODE_ID,
      processVariablesStr: row.processVariablesStr,
    });
    // P2：乱序响应丢弃
    if (seq !== predictGen) {
      return;
    }
    if (!data) {
      return;
    }
    activityNodes.value = data.activityNodes;

    startUserSelectTasks.value = (data.activityNodes?.filter(
      (node) =>
        BpmCandidateStrategyEnum.START_USER_SELECT === node.candidateStrategy,
    ) || []) as unknown as UserTask[];

    if (startUserSelectTasks.value.length > 0) {
      const nextAssignees: Record<string, string[]> = {
        ...startUserSelectAssignees.value,
      };
      for (const node of startUserSelectTasks.value) {
        const key = String(node.id);
        const tempAssignees = tempStartUserSelectAssignees.value[key];
        const current = startUserSelectAssignees.value[key];
        if (tempAssignees?.length) {
          nextAssignees[key] = tempAssignees;
        } else if (current?.length) {
          nextAssignees[key] = current;
        } else {
          nextAssignees[key] = [];
        }
      }
      startUserSelectAssignees.value = nextAssignees;
    }

    const formFieldsPermission = data.formFieldsPermission;
    if (formFieldsPermission && isNormalShell.value) {
      Object.entries(formFieldsPermission).forEach(([field, permission]) => {
        setFieldPermission(field, permission as string);
      });
    }
  } catch {
    // 预测失败不阻断填表
  }
}

function setFieldPermission(field: string, permission: string) {
  if (permission === BpmFieldPermissionType.READ) {
    fApi.value?.disabled(true, field);
  }
  if (permission === BpmFieldPermissionType.WRITE) {
    fApi.value?.disabled(false, field);
  }
  if (permission === BpmFieldPermissionType.NONE) {
    fApi.value?.hidden(true, field);
  }
}

function handleCancel() {
  emit('cancel');
}

function selectUserConfirm(activityId: string, userList: any[]) {
  if (!activityId || !Array.isArray(userList)) return;
  startUserSelectAssignees.value[activityId] = userList.map((item) =>
    String(item.id),
  );
  // 用户手选后写入 temp，避免随后预测刷新抹掉
  tempStartUserSelectAssignees.value = {
    ...tempStartUserSelectAssignees.value,
    [activityId]: startUserSelectAssignees.value[activityId] || [],
  };
}

defineExpose({ initProcessInfo });
</script>

<template>
  <Card
    :title="getTitle"
    class="h-full overflow-hidden"
    :body-style="{
      height: 'calc(100% - 112px)',
      paddingTop: '12px',
      overflowY: 'auto',
    }"
  >
    <template #extra>
      <Space wrap>
        <Button plain type="default" @click="handleCancel">
          <IconifyIcon icon="lucide:arrow-left" />&nbsp; 返回
        </Button>
      </Space>
    </template>

    <div v-if="isUnregistered" class="py-16">
      <Empty description="该流程未配置发起表单组件，无法在统一发起中提交" />
    </div>

    <div v-else-if="isStartDenied" class="py-16">
      <Empty
        :description="
          startDeniedReason || '无发起权限，请联系管理员分配对应业务角色'
        "
      />
    </div>

    <Tabs
      v-else
      v-model:active-key="activeTab"
      class="flex flex-1 flex-col overflow-hidden"
    >
      <Tabs.TabPane tab="表单填写" key="form">
        <Row :gutter="[48, 16]" class="pt-4">
          <Col
            :xs="24"
            :sm="24"
            :md="18"
            :lg="18"
            :xl="18"
            class="flex-1 overflow-auto"
          >
            <div v-if="employmentOptions.length" class="mb-4 max-w-md">
              <div class="mb-1 text-sm">任职</div>
              <Select
                v-model:value="startCompanyDeptId"
                class="w-full"
                :options="employmentOptions"
                placeholder="默认签约任职，可改选其它任职"
                @change="(v: any) => applyEmploymentSelection(v)"
              />
            </div>
            <form-create
              v-if="isNormalShell"
              :rule="detailForm.rule"
              v-model:api="fApi"
              v-model="detailForm.value"
              :option="detailForm.option"
              @submit="submitForm"
            />
            <template v-else-if="isEmbedShell">
              <div v-if="embedLoading" class="flex justify-center py-16">
                <Spin tip="加载业务表单…" />
              </div>
              <div
                v-else-if="embedError && !embedReady"
                class="py-12 text-center"
              >
                <Empty :description="embedError" />
                <Button type="primary" class="mt-4" @click="retryEmbedInit">
                  重试
                </Button>
              </div>
              <component
                :is="EmbedComponent"
                v-show="EmbedComponent && !embedLoading"
                v-if="EmbedComponent"
                ref="embedBodyRef"
                @predict-change="onEmbedPredictChange"
                @success="() => {}"
              />
            </template>
          </Col>
          <Col :xs="24" :sm="24" :md="6" :lg="6" :xl="6">
            <ProcessInstanceTimeline
              :activity-nodes="activityNodes"
              :show-status-icon="false"
              @select-user-confirm="selectUserConfirm"
            />
          </Col>
        </Row>
      </Tabs.TabPane>
      <Tabs.TabPane
        tab="流程图"
        key="flow"
        class="flex flex-1 overflow-hidden"
        :force-render="true"
      >
        <div class="h-full w-full">
          <ProcessInstanceBpmnViewer
            :bpmn-xml="bpmnXML"
            v-if="BpmModelType.BPMN === selectProcessDefinition.modelType"
          />
          <ProcessInstanceSimpleViewer
            :simple-json="simpleJson"
            v-if="BpmModelType.SIMPLE === selectProcessDefinition.modelType"
          />
        </div>
      </Tabs.TabPane>
    </Tabs>

    <template #actions>
      <template v-if="activeTab === 'form' && !isUnregistered">
        <Space wrap class="flex w-full justify-center">
          <Button
            plain
            type="primary"
            :disabled="!canSubmit"
            :loading="processInstanceStartLoading || embedLoading"
            @click="submitForm"
          >
            <IconifyIcon icon="lucide:check" />
            发起
          </Button>
          <Button plain type="default" @click="handleCancel">
            <IconifyIcon icon="lucide:x" />
            取消
          </Button>
        </Space>
      </template>
    </template>
  </Card>
</template>
