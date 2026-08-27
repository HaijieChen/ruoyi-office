<script lang="ts" setup>
import type { SystemDeptApi } from '#/api/system/dept';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Input, message, Tree } from 'ant-design-vue';

import { useDeptSelectData } from './dept-select-data';

const props = withDefaults(
  defineProps<{
    /**
     * 是否允许选择公司节点。
     * - false（默认）：编辑档案等场景，公司不可选（须选部门）
     * - true：列表筛选等场景，公司可选（后端按公司展开下属员工）
     */
    allowCompany?: boolean;
  }>(),
  { allowCompany: false },
);

/** 定义组件事件 */
const emit = defineEmits<{
  (
    e: 'select',
    dept: SystemDeptApi.Dept & { companyId?: number; companyName?: string },
  ): void;
}>();

const { treeData, loadTreeData, findCompany } = useDeptSelectData();

const formData = reactive({
  selectedDept: null as
    | null
    | (SystemDeptApi.Dept & { companyId?: number; companyName?: string }),
});

// 树选中的 keys（用于 v-model）
const selectedKeys = ref<number[]>([]);
const searchValue = ref('');
const expandedKeys = ref<number[]>([]);

const modalTitle = computed(() =>
  props.allowCompany ? '选择部门或公司' : '选择部门',
);

type DeptNode = SystemDeptApi.Dept & { children?: DeptNode[] };

function collectIds(nodes: DeptNode[]): number[] {
  const ids: number[] = [];
  for (const node of nodes) {
    if (node.id != null) {
      ids.push(node.id);
    }
    if (node.children?.length) {
      ids.push(...collectIds(node.children));
    }
  }
  return ids;
}

const filteredTree = computed(() => {
  const keyword = searchValue.value.trim().toLowerCase();
  if (!keyword) {
    return treeData.value as DeptNode[];
  }
  const walk = (nodes: DeptNode[]): DeptNode[] => {
    const out: DeptNode[] = [];
    for (const node of nodes) {
      const children = node.children?.length ? walk(node.children) : [];
      if (node.name?.toLowerCase().includes(keyword) || children.length) {
        out.push({ ...node, children });
      }
    }
    return out;
  };
  return walk(treeData.value as DeptNode[]);
});

watch(
  filteredTree,
  (nodes) => {
    if (searchValue.value.trim()) {
      expandedKeys.value = collectIds(nodes);
    }
  },
  { immediate: true },
);

// 同步 selectedDept 和 selectedKeys
watch(
  () => formData.selectedDept,
  (dept) => {
    selectedKeys.value = dept?.id ? [dept.id] : [];
  },
  { immediate: true },
);

/** 模态框实例 */
const [Modal, modalApi] = useVbenModal({
  title: modalTitle.value,
  class: 'w-2/3 max-w-3xl',
  async onConfirm() {
    return handleConfirm();
  },
  async onOpened() {
    searchValue.value = '';
    await loadTreeData();
  },
});

// 允许动态标题
watch(modalTitle, (t) => {
  modalApi.setState({ title: t });
});

/** 确认选择 */
async function handleConfirm() {
  if (!formData.selectedDept) {
    message.error(props.allowCompany ? '请选择部门或公司' : '请选择部门');
    return false;
  }

  // 查找所属公司信息（名称和ID）；若自身即公司则 companyId=自身
  const company = findCompany(formData.selectedDept.id!);
  const deptWithCompany = {
    ...formData.selectedDept,
    companyName: company.name,
    companyId: company.id,
  };

  emit('select', deptWithCompany);
  formData.selectedDept = null;
  selectedKeys.value = [];
  await modalApi.close();
  return true;
}

/** 树节点选择 */
function handleSelect(keys: any[]) {
  if (keys.length === 0) {
    formData.selectedDept = null;
    selectedKeys.value = [];
    return;
  }

  const selectedId = keys[0];
  const selectedNode = findDeptById(treeData.value, selectedId);

  if (selectedNode) {
    // 默认：公司类型不可选；allowCompany=true 时可选
    if (selectedNode.orgType === '1' && !props.allowCompany) {
      message.warning('不能选择公司，请选择部门');
      formData.selectedDept = null;
      selectedKeys.value = [];
      return;
    }

    formData.selectedDept = selectedNode;
    selectedKeys.value = [selectedId];
  }
}

/** 递归查找部门 */
function findDeptById(
  nodes: (SystemDeptApi.Dept & { children?: SystemDeptApi.Dept[] })[],
  id: number,
): null | SystemDeptApi.Dept {
  for (const node of nodes) {
    if (node.id === id) {
      return node;
    }
    if (node.children) {
      const found = findDeptById(node.children, id);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

/** 暴露modal API供外部调用 */
defineExpose({
  modalApi,
});
</script>

<template>
  <Modal>
    <div class="dept-select-container">
      <Input
        v-model:value="searchValue"
        allow-clear
        class="mb-3"
        placeholder="搜索部门或公司"
      />
      <Tree
        v-model:selected-keys="selectedKeys"
        v-model:expanded-keys="expandedKeys"
        :tree-data="filteredTree as any"
        :field-names="{ children: 'children', title: 'name', key: 'id' }"
        :block-node="true"
        :show-line="{ showLeafIcon: false }"
        @select="handleSelect"
      >
        <template #title="{ orgType, name }">
          <span
            :class="{
              'text-gray-400': orgType === '1' && !allowCompany,
            }"
          >
            {{ name }}
            <span
              v-if="orgType === '1'"
              class="ml-2 text-xs"
              :class="allowCompany ? 'text-blue-500' : 'text-gray-400'"
            >
              (公司)
            </span>
          </span>
        </template>
      </Tree>
    </div>
  </Modal>
</template>

<style lang="scss" scoped>
.dept-select-container {
  min-height: 400px;
  max-height: 500px;
  padding: 16px;
  overflow-y: auto;
}
</style>
