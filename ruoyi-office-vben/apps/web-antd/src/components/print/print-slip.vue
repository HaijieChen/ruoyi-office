<script lang="ts" setup>
import { computed } from 'vue';

import { Button } from 'ant-design-vue';

import { printFormElement } from '#/utils/print-form';

defineOptions({ name: 'FinancePrintSlip' });

export type PrintField = { label: string; span?: 1 | 2; value?: unknown };

const props = withDefaults(
  defineProps<{
    buttonText?: string;
    fields: PrintField[];
    footer?: string;
    lineColumns?: string[];
    lineRows?: unknown[][];
    slipId: string;
    title: string;
  }>(),
  { buttonText: '打印' },
);

const rows = computed(() => {
  const out: PrintField[][] = [];
  let i = 0;
  const list = props.fields || [];
  while (i < list.length) {
    const cur = list[i]!;
    if (cur.span === 2) {
      out.push([cur]);
      i += 1;
      continue;
    }
    const next = list[i + 1];
    if (next && next.span !== 2) {
      out.push([cur, next]);
      i += 2;
    } else {
      out.push([cur]);
      i += 1;
    }
  }
  return out;
});

function cell(v: unknown) {
  if (v == null || v === '') return '-';
  return String(v);
}

function onPrint() {
  printFormElement(document.getElementById(props.slipId), props.title);
}
</script>

<template>
  <div>
    <Button type="primary" class="print:hidden" @click="onPrint">
      {{ buttonText }}
    </Button>
    <div :id="slipId" class="finance-print-slip hidden">
      <h1>{{ title }}</h1>
      <table>
        <tbody>
          <tr v-for="(row, i) in rows" :key="i">
            <template v-if="row.length === 1 && row[0].span === 2">
              <th>{{ row[0].label }}</th>
              <td colspan="3">{{ cell(row[0].value) }}</td>
            </template>
            <template v-else>
              <th>{{ row[0].label }}</th>
              <td>{{ cell(row[0].value) }}</td>
              <th v-if="row[1]">{{ row[1].label }}</th>
              <td v-if="row[1]">{{ cell(row[1].value) }}</td>
              <td v-else colspan="2"></td>
            </template>
          </tr>
        </tbody>
      </table>
      <table v-if="lineColumns?.length" class="mt">
        <thead>
          <tr>
            <th v-for="c in lineColumns" :key="c">{{ c }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(line, ri) in lineRows || []" :key="ri">
            <td v-for="(c, ci) in line" :key="ci">{{ cell(c) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="footer" class="foot">{{ footer }}</div>
    </div>
  </div>
</template>

<style>
.finance-print-slip {
  box-sizing: border-box;
  width: 180mm;
  padding: 8mm;
  color: #000;
  font-size: 13px;
  font-family: SimSun, 'Songti SC', serif;
  background: #fff;
}
.finance-print-slip h1 {
  margin: 0 0 12px;
  font-size: 22px;
  font-weight: 700;
  text-align: center;
}
.finance-print-slip table {
  width: 100%;
  border-collapse: collapse;
}
.finance-print-slip th,
.finance-print-slip td {
  padding: 6px 8px;
  border: 1px solid #000;
  vertical-align: top;
}
.finance-print-slip th {
  width: 18%;
  font-weight: 600;
  background: #f3f3f3;
}
.finance-print-slip table.mt {
  margin-top: 10px;
}
.finance-print-slip .foot {
  margin-top: 10px;
}
</style>
