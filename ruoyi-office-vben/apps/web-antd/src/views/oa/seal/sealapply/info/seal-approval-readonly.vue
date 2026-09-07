<script lang="ts" setup>
import type { SealApprovalField } from './seal-approval-readonly';

defineProps<{
  fields: SealApprovalField[];
}>();
</script>

<template>
  <dl class="seal-readonly-grid">
    <div
      v-for="field in fields"
      :key="field.key"
      class="seal-readonly-item"
      :class="{ 'seal-readonly-item--full': field.fullWidth }"
      :data-field="field.key"
    >
      <dt>{{ field.label }}</dt>
      <dd :class="{ 'seal-readonly-empty': field.empty }">
        {{ field.empty ? '—' : field.value }}
      </dd>
    </div>
  </dl>
</template>

<style scoped>
.seal-readonly-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px 24px;
}
.seal-readonly-item {
  min-width: 0;
}
.seal-readonly-item--full {
  grid-column: 1 / -1;
}
.seal-readonly-item dt {
  color: rgb(0 0 0 / 45%);
  font-size: 13px;
  line-height: 22px;
}
.seal-readonly-item dd {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  word-break: break-word;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.seal-readonly-empty {
  color: rgb(0 0 0 / 25%);
}
</style>
