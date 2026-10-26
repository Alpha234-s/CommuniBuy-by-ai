<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { useAuth } from '../stores/auth'
import { useTask } from '../composables/useTask'
import { decodeJson, encodeJson } from '../utils/json'
import { getIntent, saveIntent, finishIntent } from '../utils/intent'
import { money, dateTime, validUserId } from '../utils/format'
import { confirmAction } from '../utils/confirm'
import type { Settlement } from '../types/api'
import PageHeading from '../components/PageHeading.vue'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const task = useTask()
const write = useTask()
const user = auth.user!.id
const seller = ref('')
const lookup = ref('')
const result = ref<Settlement | null>(null)
const pending = ref(getIntent(user, 'settlement'))
const pendingSeller = computed(() => {
  if (!pending.value) return ''
  try {
    return (decodeJson(pending.value.payload) as { seller?: string }).seller || user
  } catch {
    return '原请求记录无法读取，请先核对结果'
  }
})
const hasId = computed(() => !!route.params.id)
async function load() {
  await task.run(async () => {
    result.value = await api.settlement(String(route.params.id))
  })
}
async function create() {
  if (
    !pending.value &&
    !(await confirmAction('这会创建内部账务结算批次，不是银行打款。确认继续？'))
  )
    return
  await write.run(async () => {
    if (seller.value && !validUserId(seller.value)) throw new Error('卖家账号格式不正确')
    const intent =
      pending.value ||
      saveIntent(
        user,
        'settlement',
        encodeJson(auth.isAdmin && seller.value ? { seller: seller.value } : {})
      )
    pending.value = intent
    const created = await api.settle(decodeJson(intent.payload) as object, intent.key)
    finishIntent(user, 'settlement')
    pending.value = null
    await router.push(`/settlements/${created.id}`)
  })
}
async function abandon() {
  if (await confirmAction('放弃本地重试不会撤销已经生成的结算批次。确认已核对本次结果？')) {
    finishIntent(user, 'settlement')
    pending.value = null
  }
}
async function find() {
  await write.run(async () => {
    if (!/^[a-f0-9-]{36}$/.test(lookup.value)) throw new Error('请输入正确的结算编号')
    await router.push(`/settlements/${lookup.value}`)
  })
}
onMounted(() => {
  if (hasId.value) void load()
})
</script>
<template>
  <PageHeading
    :title="hasId ? '内部结算详情' : '内部结算'"
    description="核对已完成订单的内部账务，不代表银行打款或提现。"
    eyebrow="SETTLEMENT LEDGER"
  /><ErrorNotice :message="task.error.value" @retry="load" /><ErrorNotice
    :message="write.error.value"
  /><template v-if="!hasId"
    ><div class="split-layout">
      <section class="panel">
        <h2>创建结算批次</h2>
        <p class="muted">纳入符合规则且尚未结算的账目，已结算后的退款可能产生负数调整。</p>
        <el-input
          v-if="auth.isAdmin"
          v-model="seller"
          placeholder="卖家账号（留空结算自己）"
          :disabled="!!pending"
        /><el-alert
          v-if="pending"
          title="存在待确认的结算请求，将使用原请求重试。"
          type="warning"
          :closable="false"
        />
        <p v-if="pending" class="muted small">原请求结算对象：{{ pendingSeller }}</p>
        <el-button
          type="primary"
          style="margin-top: 20px"
          :loading="write.busy.value"
          @click="create"
          >{{ pending ? '重试原结算请求' : '创建内部结算批次' }}</el-button
        ><el-button v-if="pending" text @click="abandon">已核对，放弃此次重试</el-button>
      </section>
      <section class="panel">
        <h2>查看已有批次</h2>
        <p class="muted small">输入之前保存的结算编号。</p>
        <form @submit.prevent="find">
          <el-input v-model="lookup" placeholder="结算编号" aria-label="结算编号" /><el-button
            native-type="submit"
            style="margin-top: 20px"
            >查看批次</el-button
          >
        </form>
      </section>
    </div></template
  >
  <section v-if="result" class="panel">
    <p class="muted small">
      批次 {{ result.id }} · 卖家 {{ result.seller }} · {{ dateTime(result.createTime) }}
    </p>
    <div class="summary-row">
      <h2>本批净额</h2>
      <strong class="price"><small>¥</small>{{ money(result.netAmount) }}</strong>
    </div>
    <el-alert
      v-if="BigInt(result.netAmount) < 0n"
      title="负数净额表示需要扣回的账款，不是新增收入。"
      type="warning"
      :closable="false"
    /><el-table :data="result.entries"
      ><el-table-column label="订单" min-width="150"
        ><template #default="{ row }"
          ><RouterLink :to="`/orders/${row.orderId}`">{{ row.orderId }}</RouterLink></template
        ></el-table-column
      ><el-table-column label="类型" width="150"
        ><template #default="{ row }">{{
          row.kind === 'REFUND' ? '退款扣回' : '销售入账'
        }}</template></el-table-column
      ><el-table-column label="金额" min-width="160"
        ><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column
      ></el-table
    ><RouterLink to="/settlements"
      ><el-button style="margin-top: 22px">返回内部结算</el-button></RouterLink
    >
  </section>
</template>
