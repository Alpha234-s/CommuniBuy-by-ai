<script setup lang="ts">
import type { OrderLine } from '../types/api'
import { money } from '../utils/format'
defineProps<{ lines: OrderLine[] }>()
</script>
<template>
  <div class="order-lines">
    <div v-for="line in lines" :key="line.itemId" class="order-line">
      <span class="line-art" aria-hidden="true">团</span>
      <div class="line-info">
        <RouterLink :to="`/items/${line.itemId}`">{{
          line.itemName || '历史商品信息未留存'
        }}</RouterLink>
        <p class="muted small">
          {{ line.snapshotSource === 'LEGACY_UNKNOWN' ? '历史快照缺失' : '下单时商品快照' }} · ¥{{
            money(line.price)
          }}
          × {{ line.quantity }}
        </p>
      </div>
      <strong>¥{{ money(line.total) }}</strong>
    </div>
  </div>
</template>
