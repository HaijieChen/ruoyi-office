<script lang="ts" setup>
import type { BpmCategoryApi } from '#/api/bpm/category';
import type { BpmProcessDefinitionApi } from '#/api/bpm/definition';

import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Card,
  Col,
  InputSearch,
  message,
  Row,
  Space,
  Tooltip,
} from 'ant-design-vue';

import { getCategorySimpleList } from '#/api/bpm/category';
import {
  getProcessDefinition,
  getProcessDefinitionList,
} from '#/api/bpm/definition';
import { getProcessInstance } from '#/api/bpm/processInstance';

import { resolveCreateShellRedirectPath } from './embed-registry';
import ProcessDefinitionDetail from './modules/form.vue';

defineOptions({ name: 'BpmProcessInstanceCreate' });

const route = useRoute();
const router = useRouter();

const loading = ref(true); // 加载中

function queryProcessInstanceId(): string {
  const raw = route.query.processInstanceId;
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value == null ? '' : String(value);
}

const categoryList: any = ref([]); // 分类的列表
const activeCategory = ref(''); // 当前选中的分类

const searchName = ref(''); // 当前搜索关键字
const processDefinitionList = ref<BpmProcessDefinitionApi.ProcessDefinition[]>(
  [],
); // 流程定义的列表
const filteredProcessDefinitionList = ref<
  BpmProcessDefinitionApi.ProcessDefinition[]
>([]); // 用于存储搜索过滤后的流程定义

const selectProcessDefinition = ref(); // 选择的流程定义
const processDefinitionDetailRef = ref();

/** 查询列表 */
async function getList() {
  loading.value = true;
  try {
    // 1.1 所有流程分类数据
    await loadCategoryList();
    // 1.2 所有流程定义数据
    await loadProcessDefinitionList();

    // 2. 如果 processInstanceId 非空，说明是重新发起 / 再提一单
    const processInstanceId = queryProcessInstanceId();
    if (processInstanceId.length > 0) {
      const processInstance = await getProcessInstance(processInstanceId);
      if (!processInstance) {
        message.error('重新发起流程失败，原因：流程实例不存在');
        return;
      }
      let processDefinition = processDefinitionList.value.find(
        (item: any) => item.key === processInstance.processDefinition?.key,
      );
      // 列表因 canStart 已隐藏：深链/撤权后仍需友好拒绝，不加载业务表单
      if (!processDefinition) {
        const key = processInstance.processDefinition?.key;
        const id = processInstance.processDefinition?.id;
        try {
          processDefinition = await getProcessDefinition(id, key);
        } catch {
          processDefinition = undefined;
        }
      }
      if (!processDefinition) {
        message.error('重新发起流程失败，原因：流程定义不存在');
        return;
      }
      selectProcessDefinition.value = undefined;
      await nextTick();
      await handleSelect(
        processDefinition,
        processInstance.formVariables,
        processInstance.businessKey,
      );
    }
  } finally {
    loading.value = false;
  }
}

/** 获取所有流程分类数据 */
async function loadCategoryList() {
  try {
    categoryList.value = (await getCategorySimpleList()) || [];
  } catch {
    categoryList.value = [];
  }
}

/** 获取所有流程定义数据 */
async function loadProcessDefinitionList() {
  // 流程定义：后端已按可见性/发起范围过滤，这里不再按 canStart 二次隐藏
  try {
    const list = await getProcessDefinitionList({
      suspensionState: 1,
    });
    processDefinitionList.value = list || [];
  } catch {
    processDefinitionList.value = [];
    message.error('加载可发起流程失败');
  }
  handleQuery();
}

/** 搜索流程 */
function handleQuery() {
  if (searchName.value.trim()) {
    // 如果有搜索关键字，进行过滤
    filteredProcessDefinitionList.value = processDefinitionList.value.filter(
      (definition: any) =>
        definition.name.toLowerCase().includes(searchName.value.toLowerCase()),
    );
    // 如果有匹配，切换到第一个包含匹配结果的分类
    activeCategory.value = availableCategories.value[0]?.name;
  } else {
    // 如果没有搜索关键字，恢复所有数据
    filteredProcessDefinitionList.value = processDefinitionList.value;
    // 恢复到第一个可用分类
    if (availableCategories.value.length > 0) {
      activeCategory.value = availableCategories.value[0].code;
    }
  }
}

function matchesModelCategory(
  item: BpmProcessDefinitionApi.ProcessDefinition,
  category: BpmCategoryApi.Category,
) {
  if (item.categoryName && item.categoryName === category.name) {
    return true;
  }
  return Boolean(item.category && item.category === category.code);
}

/** 与流程模型页相同：按分类名称/编码归组，缺分类进未分类，不并进默认分类 */
const processDefinitionGroup = computed(() => {
  const list = filteredProcessDefinitionList.value || [];
  const orderedGroup: Record<
    string,
    BpmProcessDefinitionApi.ProcessDefinition[]
  > = {};
  if (!list.length) {
    return orderedGroup;
  }
  const used = new Set<string>();
  (categoryList.value || []).forEach((category: BpmCategoryApi.Category) => {
    const items = list.filter((item) => matchesModelCategory(item, category));
    if (items.length) {
      orderedGroup[category.code] = items;
      items.forEach((item) => used.add(item.id));
    }
  });
  const rest = list.filter((item) => !used.has(item.id));
  if (rest.length) {
    orderedGroup.uncategorized = rest;
  }
  return orderedGroup;
});

/**
 * 处理选择流程：留在 BPM 壳内，form 按 embed-registry 挂 FormBody。
 */
async function handleSelect(
  row: BpmProcessDefinitionApi.ProcessDefinition,
  formVariables?: any,
  businessKey?: string,
) {
  const redirectPath = resolveCreateShellRedirectPath(row.key);
  if (redirectPath) {
    // 无 create 时后端已从列表剔除；仍防御 canStart=false
    if (row.canStart === false) {
      message.warning(
        row.cannotStartReason || '无发起权限，请联系管理员分配对应业务角色',
      );
      return;
    }
    await router.push({
      path: redirectPath,
      query: { openCreate: '1' },
    });
    return;
  }
  selectProcessDefinition.value = row;
  await nextTick();
  processDefinitionDetailRef.value?.initProcessInfo(
    row,
    formVariables,
    businessKey,
  );
}

/** 过滤出有流程的分类列表。目的：只展示有流程的分类 */
const availableCategories = computed(() => {
  const grouped = processDefinitionGroup.value || {};
  const codes = Object.keys(grouped);
  if (!codes.length) {
    return [];
  }
  const fromDict = (categoryList.value || []).filter(
    (category: BpmCategoryApi.Category) => codes.includes(category.code),
  );
  const known = new Set(fromDict.map((c: BpmCategoryApi.Category) => c.code));
  const extras = codes
    .filter((code) => !known.has(code))
    .map((code) => {
      const sample = grouped[code]?.[0];
      return {
        code,
        name:
          sample?.categoryName ||
          (code === 'uncategorized' ? '未分类' : code),
      };
    });
  return [...fromDict, ...extras];
});

/** 监听可用分类变化，自动设置正确的活动分类 */
watch(
  availableCategories,
  (newCategories) => {
    if (newCategories.length > 0) {
      // 如果当前活动分类不在可用分类中，切换到第一个可用分类
      const currentCategoryExists = newCategories.some(
        (category: BpmCategoryApi.Category) =>
          category.code === activeCategory.value,
      );
      if (!currentCategoryExists) {
        activeCategory.value = newCategories[0].code;
      }
    }
  },
  { immediate: true },
);

/** 初始化；keepAlive 下再提一单只改 query，必须 watch / onActivated */
onMounted(() => {
  getList();
});
onActivated(() => {
  if (queryProcessInstanceId()) {
    void getList();
  }
});
watch(
  () => queryProcessInstanceId(),
  (id, prev) => {
    if (id && id !== prev) {
      void getList();
    }
  },
);
</script>

<template>
  <Page auto-content-height>
    <!-- TODO @jason：这里交互，可以做成类似 vue3 + element-plus 那个一样，滚动切换分类哈？对标钉钉、飞书哈； -->
    <!-- 第一步，通过流程定义的列表，选择对应的流程 -->
    <template v-if="!selectProcessDefinition">
      <Card
        class="h-full"
        title="全部流程"
        :class="{
          'process-definition-container': filteredProcessDefinitionList?.length,
        }"
        :loading="loading"
      >
        <template #extra>
          <div class="flex h-full items-center justify-center">
            <InputSearch
              v-model:value="searchName"
              class="!w-50%"
              placeholder="请输入流程名称检索"
              allow-clear
              @input="handleQuery"
              @clear="handleQuery"
            />
          </div>
        </template>

        <div v-if="filteredProcessDefinitionList?.length">
          <div
            v-for="category in availableCategories"
            :key="category.code"
            class="mb-6"
          >
            <div class="mb-3 text-sm text-gray-500">{{ category.name }}</div>
            <Row :gutter="[16, 16]" :wrap="true">
              <Col
                v-for="definition in processDefinitionGroup[category.code]"
                :key="definition.id"
                :xs="24"
                :sm="12"
                :md="8"
                :lg="6"
                :xl="5"
                @click="handleSelect(definition)"
              >
                <Card
                  hoverable
                  class="w-full cursor-pointer"
                  :class="{
                    'animate-bounce-once !bg-[rgb(63_115_247_/_10%)]':
                      searchName.trim().length > 0,
                  }"
                  :body-style="{
                    width: '100%',
                    padding: '16px',
                  }"
                >
                  <div class="flex items-center">
                    <img
                      v-if="definition.icon"
                      :src="definition.icon"
                      class="size-10 rounded object-contain"
                      alt="流程图标"
                    />
                    <div
                      v-else
                      class="flex size-10 flex-shrink-0 items-center justify-center rounded bg-primary"
                    >
                      <span class="text-xs text-white">
                        {{ definition.name?.slice(0, 2) }}
                      </span>
                    </div>
                    <span class="ml-3 flex-1 truncate text-base">
                      <Tooltip
                        placement="topLeft"
                        :title="`${definition.description || definition.name}`"
                      >
                        {{ definition.name }}
                      </Tooltip>
                    </span>
                  </div>
                </Card>
              </Col>
            </Row>
          </div>
        </div>
        <div v-else class="!py-48 text-center">
          <Space direction="vertical" size="large">
            <span class="text-gray-500">没有找到搜索结果</span>
          </Space>
        </div>
      </Card>
    </template>

    <!-- 第二步，填写表单，进行流程的提交 -->
    <ProcessDefinitionDetail
      v-else
      ref="processDefinitionDetailRef"
      :select-process-definition="selectProcessDefinition"
      @cancel="selectProcessDefinition = undefined"
    />
  </Page>
</template>

<style lang="scss" scoped>
@keyframes bounce {
  0%,
  50% {
    transform: translateY(-5px);
  }

  100% {
    transform: translateY(0);
  }
}

.animate-bounce-once {
  animation: bounce 0.5s ease;
}
</style>
