<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, Clock } from '@element-plus/icons-vue'
import type { Item } from '../types/api'
import { money, saleState, dateTime } from '../utils/format'
import { categoryArtwork } from '../utils/categoryArtwork'
import { useClock } from '../composables/useClock'
const props = defineProps<{ item: Item; category?: string }>()
const art = computed(() => categoryArtwork(props.category))
const now = useClock()
</script>
<template>
  <RouterLink :to="`/items/${item.id}`" class="item-card"
    ><div class="item-art" :class="`art-${art.tone}`" :data-category-art="art.key">
      <span class="item-state"><i class="dot"></i>{{ saleState(item, now) }}</span>
      <div class="item-illustration" aria-hidden="true">
        <el-icon><component :is="art.icon" /></el-icon>
      </div>
      <span class="art-caption">{{ category || '社区好物' }} · 分类示意</span>
    </div>
    <div class="item-info">
      <span class="item-category">{{ category || '社区好物' }}</span>
      <h3>{{ item.name }}</h3>
      <p class="item-description">{{ item.description || '发起人暂未填写商品说明' }}</p>
      <div class="item-price-row">
        <span class="price"><small>¥</small>{{ money(item.price) }}</span
        ><span class="muted small">剩余 {{ item.stock }} 件</span>
      </div>
      <div class="item-bottom">
        <span
          ><el-icon><Clock /></el-icon
          >{{ item.endTime ? dateTime(item.endTime).split(' ')[0] + ' 截止' : '长期团购' }}</span
        ><el-icon><ArrowRight /></el-icon>
      </div></div
  ></RouterLink>
</template>
<style scoped>
.item-card {
  display: block;
  border: 1px solid #e5e9e0;
  border-radius: 15px;
  background: #fff;
  overflow: hidden;
  color: var(--ink);
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}
.item-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 14px 28px #314d1910;
}
.item-art {
  height: 200px;
  position: relative;
  overflow: hidden;
  background: #edf0df;
}
.art-1 {
  background: #f5eadd;
}
.art-2 {
  background: #e8efeb;
}
.art-3 {
  background: #eee9df;
}
.item-state {
  position: absolute;
  left: 14px;
  top: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  background: #ffffffd9;
  padding: 6px 9px;
  border-radius: 5px;
  color: #6c805b;
  z-index: 1;
}
.item-illustration {
  position: absolute;
  left: calc(50% - 49px);
  top: 54px;
  width: 98px;
  height: 98px;
  display: grid;
  place-items: center;
  border-radius: 28px;
  transform: rotate(-9deg);
  background: #ffffff80;
  box-shadow: 15px 15px 0 #6b82540b;
}
.item-illustration .el-icon {
  font-size: 62px;
  color: #8da470;
}
.art-1 .el-icon {
  color: #b69061;
}
.art-2 .el-icon {
  color: #7e9f8c;
}
.art-3 .el-icon {
  color: #ad9b7b;
}
.art-caption {
  position: absolute;
  bottom: 13px;
  right: 13px;
  font-size: 8px;
  letter-spacing: 1px;
  color: #899078;
}
.item-info {
  padding: 19px;
}
.item-category {
  font-size: 10px;
  color: #9a9c81;
}
.item-info h3 {
  font-size: 15px;
  margin: 9px 0 8px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.item-description {
  font-size: 11px;
  color: #929b8f;
  height: 20px;
  margin-bottom: 15px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.item-price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.item-price-row .price {
  font-size: 23px;
}
.item-price-row .price small {
  font-size: 13px;
  margin-right: 2px;
}
.item-bottom {
  border-top: 1px solid #eef0e9;
  margin-top: 17px;
  padding-top: 13px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #94a08b;
  font-size: 10px;
}
.item-bottom span {
  display: flex;
  gap: 5px;
  align-items: center;
}
@media (max-width: 600px) {
  .item-art {
    height: 165px;
  }
  .item-illustration {
    top: 42px;
    transform: rotate(-9deg) scale(0.8);
  }
  .item-info {
    padding: 14px;
  }
  .item-price-row {
    flex-wrap: wrap;
  }
  .item-state {
    left: 10px;
    top: 10px;
    font-size: 9px;
  }
  .item-info h3 {
    font-size: 14px;
  }
}
</style>
