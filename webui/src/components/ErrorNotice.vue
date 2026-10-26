<script setup lang="ts">
defineProps<{ message: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <el-alert
    v-if="message"
    :title="message"
    type="error"
    show-icon
    :closable="false"
    class="error-notice"
    ><template v-if="$attrs.onRetry" #default
      ><el-button size="small" @click="$emit('retry')">重新加载</el-button></template
    ></el-alert
  >
</template>
