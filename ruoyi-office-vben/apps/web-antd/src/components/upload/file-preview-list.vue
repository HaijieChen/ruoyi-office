<script lang="ts" setup>
import FileUpload from './file-upload.vue';

defineOptions({ name: 'FilePreviewList' });

const props = defineProps<{
  value?: null | string | string[];
}>();

function parseUrls(raw?: null | string | string[]): string[] {
  if (!raw) return [];
  if (Array.isArray(raw)) {
    return raw.map((s) => String(s || '').trim()).filter(Boolean);
  }
  const text = String(raw).trim();
  if (!text || text === '-') return [];
  try {
    const j = JSON.parse(text);
    if (Array.isArray(j)) {
      return j.map((s) => String(s || '').trim()).filter(Boolean);
    }
  } catch {
    // not json
  }
  return text
    .split(/[,;\n]/)
    .map((s) => s.trim())
    .filter((s) => s && s !== '-');
}

</script>

<template>
  <FileUpload
    v-if="parseUrls(value).length"
    :value="parseUrls(value)"
    :max-number="Math.max(parseUrls(value).length, 1)"
    disabled
  />
  <span v-else>-</span>
</template>
