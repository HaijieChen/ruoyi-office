<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';

import { Button } from 'ant-design-vue';

import FormBody from './modules/form-body.vue';

defineOptions({ name: 'FinanceExpenseReimbursementCreate' });

const { closeCurrentTab } = useTabs();
const bodyRef = ref<InstanceType<typeof FormBody>>();

async function onSubmit() {
  await bodyRef.value?.submit();
  closeCurrentTab();
}
</script>

<template>
  <Page auto-content-height>
    <div class="mx-auto max-w-3xl p-4">
      <FormBody ref="bodyRef" @success="closeCurrentTab()" />
      <div class="mt-4 text-right">
        <Button type="primary" :loading="bodyRef?.submitting" @click="onSubmit">
          提交
        </Button>
      </div>
    </div>
  </Page>
</template>
