<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../stores/auth'
import { useTask } from '../composables/useTask'
import { confirmAction } from '../utils/confirm'
import { encodeJson, decodeJson } from '../utils/json'
import { finishIntent, getIntent, saveIntent } from '../utils/intent'
import { money, requireText, saleState } from '../utils/format'
import type { Cart, CartLine } from '../types/api'
import ErrorNotice from '../components/ErrorNotice.vue'
import PageHeading from '../components/PageHeading.vue'
const auth = useAuth()
const router = useRouter()
const task = useTask()
const write = useTask()
const user = auth.user!.id
const cart = ref<Cart | null>(null)
const address = ref(auth.user?.address || '')
const pending = ref(getIntent(user, 'checkout'))
const selected = computed(() => cart.value?.lines.filter((l) => l.selected) || [])
const total = computed(() =>
  selected.value.reduce((sum, line) => sum + BigInt(line.price) * BigInt(line.quantity), 0n)
)
const frozen = computed(() =>
  pending.value
    ? (decodeJson(pending.value.payload) as { address: string; expectedTotal: bigint | number })
    : null
)
async function load() {
  await task.run(async () => {
    cart.value = await api.cart()
  })
}
async function change(line: CartLine, quantity: number | undefined, selectedValue: boolean) {
  await write.run(async () => {
    if (quantity === undefined || !Number.isInteger(quantity) || quantity < 1)
      throw new Error('数量须为正整数')
    cart.value = await api.cartChange(line.itemId, quantity, selectedValue)
  })
}
async function remove(line: CartLine) {
  if (await confirmAction(`从购物车移除“${line.name}”？`))
    await write.run(async () => {
      cart.value = await api.cartDelete(line.itemId)
    })
}
async function checkout() {
  await write.run(async () => {
    let intent = pending.value
    if (!intent) {
      if (!cart.value || !selected.value.length) throw new Error('请先勾选要购买的商品')
      requireText(address.value, '收货地址', 255, false)
      if (selected.value.some((l) => saleState(l) !== '正在团购' || l.quantity > l.stock))
        throw new Error('部分商品不可售或库存不足，请核对购物车')
      if (total.value > 9223372036854775807n)
        throw new Error('所选商品金额超出单次结算范围，请减少选中商品后重试')
      intent = saveIntent(
        user,
        'checkout',
        encodeJson({
          address: address.value,
          cartVersion: BigInt(cart.value.version),
          expectedTotal: total.value,
        })
      )
      pending.value = intent
    }
    try {
      const result = await api.checkout(decodeJson(intent.payload) as object, intent.key)
      finishIntent(user, 'checkout')
      pending.value = null
      await router.push(`/checkouts/${result.id}`)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) await load()
      throw e
    }
  })
}
async function abandon() {
  if (
    await confirmAction(
      '请先核对“我的订单”。放弃重试不会取消已经创建的订单，重新结算可能重复购买。确认已核对？'
    )
  ) {
    finishIntent(user, 'checkout')
    pending.value = null
    await load()
  }
}
onMounted(load)
</script>
<template>
  <PageHeading
    title="我的购物车"
    description="挑选喜欢的好物，勾选后一起结算。"
    eyebrow="A BASKET OF GOOD THINGS"
    ><el-button :loading="task.busy.value" :disabled="write.busy.value" @click="load"
      >刷新购物车</el-button
    ></PageHeading
  ><ErrorNotice :message="task.error.value" @retry="load" /><ErrorNotice
    :message="write.error.value"
  /><el-alert
    v-if="pending"
    title="上次结算结果待确认。重试使用原地址、金额和购物车版本，不会改用当前购物车。"
    type="warning"
    :closable="false"
    style="margin-bottom: 22px"
  />
  <div class="split-layout">
    <section class="panel">
      <el-skeleton v-if="task.busy.value && !cart" :rows="5" animated />
      <div v-for="line in cart?.lines || []" :key="line.itemId" class="cart-line">
        <el-checkbox
          :model-value="line.selected"
          :aria-label="`选择${line.name}`"
          :disabled="write.busy.value || task.busy.value || !!pending"
          @change="(value: unknown) => change(line, line.quantity, !!value)"
        />
        <div class="cart-line-info">
          <RouterLink :to="`/items/${line.itemId}`">{{ line.name }}</RouterLink>
          <p class="muted small">
            ¥{{ money(line.price) }} · {{ saleState(line) }} · 剩余 {{ line.stock }} 件
          </p>
          <el-input-number
            :model-value="line.quantity"
            :min="1"
            :max="2147483647"
            :precision="0"
            size="small"
            :disabled="write.busy.value || task.busy.value || !!pending"
            @change="(value: number | undefined) => change(line, value, line.selected)"
          />
        </div>
        <div class="cart-line-total">
          <strong>¥{{ money(BigInt(line.price) * BigInt(line.quantity)) }}</strong
          ><el-button
            link
            :disabled="write.busy.value || task.busy.value || !!pending"
            @click="remove(line)"
            >移除</el-button
          >
        </div>
      </div>
      <el-empty
        v-if="cart && !cart.lines.length && !pending"
        description="购物车还是空的，去发现一点好物吧"
        ><RouterLink to="/"><el-button>发现团购</el-button></RouterLink></el-empty
      >
      <p v-if="cart && !cart.lines.length && pending" class="plain-note">
        当前购物车已无商品，请使用右侧原请求继续核对上次结算结果。
      </p>
    </section>
    <section class="panel checkout-summary">
      <h2>{{ pending ? '原结算请求' : '确认结算' }}</h2>
      <p class="muted small">提交后将按发起人拆成独立订单，各订单分别支付。</p>
      <div class="summary-row">
        <span>当前选中</span><span>{{ selected.length }} 种商品</span>
      </div>
      <el-form label-position="top" @submit.prevent="checkout"
        ><el-form-item label="收货地址"
          ><el-input
            v-if="frozen"
            :model-value="frozen.address"
            type="textarea"
            :rows="3"
            disabled /><el-input
            v-else
            v-model="address"
            :maxlength="255"
            type="textarea"
            :rows="3"
            placeholder="确认本次订单的收货地址"
        /></el-form-item>
        <div class="summary-row total">
          <span>{{ pending ? '原请求确认金额' : '选中商品合计' }}</span
          ><strong class="price"
            ><small>¥</small>{{ money(frozen ? frozen.expectedTotal : total) }}</strong
          >
        </div>
        <el-button
          type="primary"
          size="large"
          native-type="submit"
          style="width: 100%; margin-top: 18px"
          :loading="write.busy.value"
          :disabled="task.busy.value || (!pending && !selected.length)"
          >{{ pending ? '使用原请求重试结算' : '确认并创建订单' }}</el-button
        ><el-button v-if="pending" text style="margin-top: 15px" @click="abandon"
          >已核对订单，放弃此次重试</el-button
        ></el-form
      >
      <p class="muted small" style="margin-top: 20px">
        创建订单不代表已付款，最终金额由服务端核定。
      </p>
    </section>
  </div>
</template>
<style scoped>
.cart-line {
  display: flex;
  gap: 15px;
  align-items: center;
  padding: 23px 0;
  border-bottom: 1px solid var(--line);
}
.cart-line:first-child {
  padding-top: 3px;
}
.cart-line:last-child {
  border-bottom: 0;
}
.cart-line-info {
  flex: 1;
}
.cart-line-info > a {
  font-size: 15px;
}
.cart-line-info p {
  margin: 7px 0 12px;
}
.cart-line-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 15px;
  font-size: 14px;
}
.checkout-summary {
  align-self: start;
}
@media (max-width: 450px) {
  .cart-line {
    gap: 10px;
  }
  .cart-line-total {
    font-size: 12px;
  }
}
</style>
