<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ShoppingCart } from '@element-plus/icons-vue'
import { api } from '../api'
import { useAuth } from '../stores/auth'
import { useTask } from '../composables/useTask'
import { useClock } from '../composables/useClock'
import { categoryArtwork } from '../utils/categoryArtwork'
import { useCategories } from '../composables/useCategories'
import { dateTime, money, pageCount, requireText, saleState } from '../utils/format'
import { decodeJson, encodeJson, numericId } from '../utils/json'
import { finishIntent, getIntent, saveIntent } from '../utils/intent'
import { confirmAction } from '../utils/confirm'
import type { Item, Page, Review } from '../types/api'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const now = useClock()
const id = String(route.params.id)
const user = auth.user!.id
const scope = `buy:${id}`
const item = ref<Item | null>(null)
const { categories, more, loadMore } = useCategories()
const categoryTask = useTask()
const categoryName = computed(
  () => categories.value.find((c) => c.id === item.value?.categoryId)?.name
)
const art = computed(() => categoryArtwork(categoryName.value))
const quantity = ref(1)
const address = ref(auth.user?.address || '')
const pending = ref(getIntent(user, scope))
const task = useTask()
const write = useTask()
const reviewTask = useTask()
const reviews = ref<Page<Review> | null>(null)
const reviewPage = ref(1)
const state = computed(() => (item.value ? saleState(item.value, now.value) : ''))
const own = computed(() => item.value?.owner === user)
const canBuy = computed(() => !!item.value && !own.value && state.value === '正在团购')
async function load() {
  await task.run(async () => {
    numericId(id)
    item.value = await api.item(id)
    void loadCategory()
  })
}
async function loadCategory() {
  if (item.value?.categoryId == null) return
  await categoryTask.run(async () => {
    while (more.value && !categoryName.value) {
      const previous = categories.value.length
      await loadMore()
      if (categories.value.length === previous) break
    }
  })
}
async function loadReviews() {
  await reviewTask.run(async () => {
    reviews.value = await api.reviews(id, reviewPage.value - 1)
  })
}
async function buy() {
  await write.run(async () => {
    let intent = pending.value
    if (!intent) {
      requireText(address.value, '收货地址', 255, false)
      if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > 2147483647)
        throw new Error('请输入有效整数数量')
      intent = saveIntent(
        user,
        scope,
        encodeJson({ itemId: numericId(id), quantity: quantity.value, address: address.value })
      )
      pending.value = intent
    }
    const order = await api.buy(decodeJson(intent.payload) as object, intent.key)
    finishIntent(user, scope)
    pending.value = null
    await router.push(`/orders/${order.id}`)
  })
}
async function addCart() {
  await write.run(async () => {
    const cart = await api.cart()
    const line = cart.lines.find((l) => l.itemId === id)
    const target = (line?.quantity || 0) + quantity.value
    if (!Number.isInteger(target) || target < 1 || target > 2147483647)
      throw new Error('商品数量超出范围')
    await api.cartChange(id, target, line?.selected ?? true)
  }, '已加入购物车')
}
async function abandon() {
  if (
    await confirmAction(
      '请先到“我的订单”确认上次是否已生成订单。放弃重试不会取消服务端订单；再次购买可能产生另一张订单。确认已核对？'
    )
  ) {
    finishIntent(user, scope)
    pending.value = null
  }
}
onMounted(() => {
  if (pending.value) {
    try {
      const original = decodeJson(pending.value.payload) as { quantity: number; address: string }
      quantity.value = original.quantity
      address.value = original.address
    } catch {
      write.error.value = '本机原请求记录无法读取，请先从我的订单核对，避免重复购买'
    }
  }
  void load()
  void loadReviews()
})
</script>
<template>
  <RouterLink to="/" class="back-link">← 返回发现团购</RouterLink>
  <ErrorNotice :message="task.error.value" @retry="load" />
  <ErrorNotice :message="categoryTask.error.value" @retry="loadCategory" />
  <el-skeleton v-if="task.busy.value" :rows="8" animated class="panel" />
  <template v-else-if="item">
    <div class="split-layout">
      <div class="detail-art" :data-category-art="art.key">
        <el-icon><component :is="art.icon" /></el-icon><strong>GOOD THINGS,<br />TOGETHER.</strong
        ><span>{{ categoryName || '社区好物' }} · 分类图形示意，请以文字说明为准</span>
      </div>
      <div class="panel purchase-panel">
        <div class="inline-actions">
          <span class="badge">{{ state }}</span
          ><span class="small muted">发起人 {{ item.owner }}</span>
        </div>
        <h1>{{ item.name }}</h1>
        <p class="price"><small>¥</small>{{ money(item.price) }}</p>
        <p class="muted small">剩余 {{ item.stock }} 件 · 已发货 {{ item.shipmentCount }} 次</p>
        <div class="plain-note">
          开始：{{ dateTime(item.startTime) }}<br />截止：{{
            item.endTime ? dateTime(item.endTime) : '不限时间'
          }}
        </div>
        <ErrorNotice :message="write.error.value" />
        <el-alert
          v-if="pending"
          title="有一次下单结果尚待确认。重试会沿用原数量、地址和请求编号。"
          type="warning"
          :closable="false"
          class="pending-alert"
        />
        <template v-if="own"
          ><p class="muted small">这是你发起的团购，无法购买自己的商品。</p>
          <RouterLink :to="`/seller/items/${id}/edit`"
            ><el-button type="primary">编辑团购</el-button></RouterLink
          ></template
        >
        <el-form v-else label-position="top" @submit.prevent="buy" :disabled="write.busy.value"
          ><el-form-item label="购买数量"
            ><el-input-number
              v-model="quantity"
              :min="1"
              :max="Math.max(1, item.stock)"
              :precision="0"
              :disabled="!!pending || !canBuy" /></el-form-item
          ><el-form-item label="本次订单收货地址"
            ><el-input
              v-model="address"
              type="textarea"
              :rows="2"
              :maxlength="255"
              placeholder="请填写完整收货地址"
              :disabled="!!pending"
          /></el-form-item>
          <div class="inline-actions">
            <el-button
              type="primary"
              size="large"
              native-type="submit"
              :loading="write.busy.value"
              :disabled="!pending && !canBuy"
              >{{ pending ? '使用原请求重试' : '立即购买' }}</el-button
            ><el-button
              :icon="ShoppingCart"
              size="large"
              :disabled="!canBuy || !!pending"
              @click="addCart"
              >加入购物车</el-button
            >
          </div>
          <el-button v-if="pending" text @click="abandon"
            >已核对订单，放弃此次重试</el-button
          ></el-form
        >
      </div>
    </div>
    <section class="panel">
      <h2>团购说明</h2>
      <p class="body-text">{{ item.description || '发起人暂未填写详细说明。' }}</p>
    </section>
    <section class="panel">
      <h2>用户评价</h2>
      <ErrorNotice :message="reviewTask.error.value" @retry="loadReviews" /><el-skeleton
        v-if="reviewTask.busy.value"
        :rows="3"
        animated
      /><template v-else-if="reviews?.content.length"
        ><article v-for="review in reviews.content" :key="review.id" class="review">
          <div class="summary-row">
            <strong>{{ review.user }}</strong
            ><el-rate :model-value="review.rating" disabled />
          </div>
          <p>{{ review.comment }}</p>
          <span class="muted small">{{ dateTime(review.createTime) }}</span>
        </article>
        <div class="pagination">
          <el-pagination
            v-model:current-page="reviewPage"
            :page-size="10"
            :total="pageCount(reviews.count)"
            layout="prev,pager,next"
            @current-change="loadReviews"
          /></div></template
      ><el-empty
        v-else-if="!reviewTask.error.value"
        description="还没有评价，完成订单后可以分享你的体验"
        :image-size="65"
      />
    </section>
  </template>
</template>
<style scoped>
.detail-art {
  min-height: 430px;
  border-radius: 17px;
  background: #eaf0e0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #7f9869;
  position: relative;
  margin-bottom: 22px;
}
.detail-art > .el-icon {
  font-size: 130px;
  transform: rotate(-12deg);
  margin-bottom: 25px;
}
.detail-art strong {
  font-family: Georgia, serif;
  font-size: 28px;
  letter-spacing: 3px;
  text-align: center;
  line-height: 1.2;
}
.detail-art > span {
  position: absolute;
  bottom: 18px;
  font-size: 10px;
  color: #99a58c;
}
.purchase-panel h1 {
  font-size: 27px;
  margin-top: 23px;
}
.purchase-panel .price {
  font-size: 37px;
  margin: 15px 0;
}
.purchase-panel .plain-note {
  margin-bottom: 25px;
}
.pending-alert {
  margin-bottom: 20px;
}
.review {
  border-top: 1px solid var(--line);
  padding: 16px 0;
}
.review p {
  font-size: 14px;
  white-space: pre-wrap;
}
@media (max-width: 1000px) {
  .detail-art {
    min-height: 280px;
  }
  .detail-art > .el-icon {
    font-size: 85px;
  }
  .detail-art strong {
    font-size: 22px;
  }
}
</style>
