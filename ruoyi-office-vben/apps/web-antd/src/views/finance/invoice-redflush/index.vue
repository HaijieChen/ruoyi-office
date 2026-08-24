<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import FormBody from './modules/form-body.vue';

defineOptions({ name: 'FinanceInvoiceRedflush' });
</script>

<template>
  <Page auto-content-height>
    <div class="mx-auto max-w-2xl p-4">
      <h1 class="mb-4 text-lg font-medium">开票申请-红冲</h1>
      <FormBody />
    </div>
  </Page>
</template>
