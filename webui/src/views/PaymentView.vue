<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { useAuth } from '../stores/auth'
import { useTask } from '../composables/useTask'
import { confirmAction } from '../utils/confirm'
import { money, dateTime } from '../utils/format'
import type { DetailedOrder, Payment } from '../types/api'
import PageHeading from '../components/PageHeading.vue'
import ErrorNotice from '../components/ErrorNotice.vue'
import { useClock } from '../composables/useClock'
const now = useClock()
const id = String(useRoute().params.id)
const auth = useAuth()
const task = useTask()
const write = useTask()
const payment = ref<Payment | null>(null)
const order = ref<DetailedOrder | null>(null)
const canPay = computed(
  () =>
    payment.value?.channel === 'SIMULATOR' &&
    order.value?.order.user === auth.user?.id &&
    order.value?.order.status === 0 &&
    now.value < order.value.order.expiresAt &&
    ['PENDING', 'FAILED'].includes(payment.value.status)
)
const labels: Record<string, string> = {
  PENDING: '等待支付',
  SUCCEEDED: '支付成功',
  FAILED: '支付失败',
  CANCELLED: '支付已关闭',
}
async function load() {
  await task.run(async () => {
    payment.value = await api.payment(id)
    order.value = await api.order(payment.value.orderId)
  })
}
async function action(name: 'simulate' | 'query') {
  if (
    name === 'simulate' &&
    !(await confirmAction('这是 SIMULATOR 模拟支付，不会扣除真实资金。确认执行模拟付款？'))
  )
    return
  await write.run(async () => {
    payment.value = await api.paymentAction(id, name)
    order.value = await api.order(payment.value.orderId)
  })
}
onMounted(load)
</script>
<template>
  <PageHeading
    title="模拟支付"
    description="本项目当前仅提供隔离的模拟支付，不涉及真实资金。"
    eyebrow="SIMULATOR PAYMENT"
  /><ErrorNotice :message="task.error.value" @retry="load" /><ErrorNotice
    :message="write.error.value"
  />
  <div v-if="payment" class="panel form-width">
    <el-alert
      :title="
        payment.channel === 'SIMULATOR'
          ? 'SIMULATOR 模拟渠道 · 非真实支付'
          : '支付渠道尚未启用，请联系维护者确认演示环境'
      "
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 25px"
    />
    <p class="muted small">支付编号 {{ payment.id }}</p>
    <h2>{{ labels[payment.status] || payment.status }}</h2>
    <p class="price"><small>¥</small>{{ money(payment.amount) }}</p>
    <el-descriptions :column="1"
      ><el-descriptions-item label="订单">{{ payment.orderId }}</el-descriptions-item
      ><el-descriptions-item label="渠道">{{ payment.channel }}</el-descriptions-item
      ><el-descriptions-item label="商户">{{ payment.merchant }}</el-descriptions-item
      ><el-descriptions-item label="更新时间">{{
        dateTime(payment.updateTime)
      }}</el-descriptions-item></el-descriptions
    >
    <div class="inline-actions" style="margin-top: 20px">
      <el-button
        v-if="canPay"
        type="primary"
        :loading="write.busy.value"
        @click="action('simulate')"
        >确认模拟付款</el-button
      ><el-button
        :loading="write.busy.value"
        :disabled="payment.channel !== 'SIMULATOR'"
        @click="action('query')"
        >查询并核对支付结果</el-button
      ><RouterLink :to="`/orders/${payment.orderId}`"><el-button>返回订单</el-button></RouterLink>
    </div>
    <p class="plain-note" style="margin-top: 24px">
      付款结果以支付记录与订单最新状态为准。失败或处理中不代表已完成付款。
    </p>
  </div>
  <el-skeleton v-else-if="task.busy.value" class="panel" :rows="5" animated />
</template>
