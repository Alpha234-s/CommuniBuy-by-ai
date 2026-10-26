<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { useTask } from '../composables/useTask'
import { money, orderLabels } from '../utils/format'
import type { Checkout } from '../types/api'
import PageHeading from '../components/PageHeading.vue'
import ErrorNotice from '../components/ErrorNotice.vue'
import OrderLines from '../components/OrderLines.vue'
const route = useRoute()
const task = useTask()
const result = ref<Checkout | null>(null)
async function load() {
  await task.run(async () => {
    result.value = await api.checkoutResult(String(route.params.id))
  })
}
onMounted(load)
</script>
<template>
  <PageHeading
    title="结算批次详情"
    description="已按发起人拆分订单，请分别查看并完成支付。"
    eyebrow="ONE BASKET, MANY GOOD THINGS"
  /><ErrorNotice :message="task.error.value" @retry="load" /><el-skeleton
    v-if="task.busy.value"
    :rows="5"
    class="panel"
    animated
  /><template v-if="result"
    ><section class="panel">
      <p class="muted small">批次 {{ result.id }}</p>
      <div class="summary-row">
        <span>{{ result.orders.length }} 张订单 · 订单创建完成不代表已支付</span
        ><strong class="price"><small>¥</small>{{ money(result.total) }}</strong>
      </div>
    </section>
    <section v-for="entry in result.orders" :key="entry.order.id" class="panel">
      <div class="summary-row">
        <h3>发起人 {{ entry.order.owner }} · 订单 {{ entry.order.id }}</h3>
        <el-tag effect="plain">{{ orderLabels[entry.order.status] }}</el-tag>
      </div>
      <OrderLines :lines="entry.lines" />
      <div class="summary-row total">
        <strong>订单金额 ¥{{ money(entry.order.total) }}</strong
        ><RouterLink :to="`/orders/${entry.order.id}`"
          ><el-button type="primary">查看订单与支付</el-button></RouterLink
        >
      </div>
    </section></template
  >
</template>
