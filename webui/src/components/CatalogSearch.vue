<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
const route = useRoute()
const router = useRouter()
const text = ref('')
watch(
  () => route.query.q,
  (value) => {
    text.value = typeof value === 'string' ? value : ''
  },
  { immediate: true }
)
async function search() {
  const q = text.value.trim()
  await router.push({ path: '/', query: q ? { q } : {} })
}
</script>

<template>
  <form class="catalog-search" role="search" aria-label="搜索社区团购平台" @submit.prevent="search">
    <el-input
      v-model="text"
      placeholder="搜索社区好物、团购名称"
      aria-label="搜索团购"
      clearable
      size="large"
      :maxlength="128"
    >
      <template #prefix
        ><el-icon><Search /></el-icon
      ></template>
    </el-input>
    <el-button type="primary" native-type="submit" size="large">搜索</el-button>
  </form>
</template>

<style scoped>
.catalog-search {
  display: flex;
  min-width: 0;
  width: 100%;
  height: 46px;
  border: 2px solid var(--green);
  border-radius: var(--radius-small);
  background: #fff;
  overflow: hidden;
}
.catalog-search:focus-within {
  outline: 3px solid #b5cebd;
  outline-offset: 3px;
}
.catalog-search :deep(.el-input__wrapper) {
  box-shadow: none;
  padding: 0 14px;
  background: transparent;
}
.catalog-search :deep(.el-input__inner) {
  height: 42px;
  font-size: 14px;
}
.catalog-search :deep(.el-input__icon) {
  font-size: 18px;
}
.catalog-search :deep(.el-button) {
  border: 0;
  margin: 0;
  height: 42px;
  min-width: 88px;
  border-radius: 0;
  font-size: 15px;
}
.catalog-search :deep(.el-button:focus-visible) {
  outline-offset: -4px;
}
@media (max-width: 767px) {
  .catalog-search {
    height: 46px;
  }
  .catalog-search :deep(.el-button) {
    min-width: 72px;
  }
}
</style>
