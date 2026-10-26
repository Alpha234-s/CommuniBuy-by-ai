<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <div class="panel state-empty">
    <p class="eyebrow">COMMUNIBUY</p>
    <h1>{{ route.path === '/403' ? '此页面需要相应权限' : '没有找到这个页面' }}</h1>
    <p class="muted">
      {{
        route.path === '/403'
          ? '请使用有权限的账号访问。'
          : '地址可能有误，你可以回到首页继续浏览。'
      }}
    </p>
    <RouterLink to="/"><el-button type="primary">返回首页</el-button></RouterLink>
  </div>
</template>
