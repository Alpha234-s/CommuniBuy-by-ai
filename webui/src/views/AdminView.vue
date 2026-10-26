<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { useAuth } from '../stores/auth'
import { useTask } from '../composables/useTask'
import {
  money,
  dateTime,
  orderLabels,
  pageCount,
  requireText,
  validUserId,
  validatePassword,
} from '../utils/format'
import { confirmAction } from '../utils/confirm'
import type { Audit, Category, Item, Order, Page, Stats, User } from '../types/api'
import PageHeading from '../components/PageHeading.vue'
import ErrorNotice from '../components/ErrorNotice.vue'
const StatsChart = defineAsyncComponent(() => import('../components/StatsChart.vue'))
const route = useRoute()
const auth = useAuth()
const task = useTask()
const write = useTask()
const section = computed(() => String(route.params.section || 'overview'))
const titles: Record<string, string> = {
  overview: '平台概览',
  users: '用户管理',
  categories: '分类管理',
  items: '全部团购',
  orders: '全部订单',
  audit: '操作审计',
}
const page = ref(1)
const total = ref('0')
const stats = ref<Stats | null>(null)
const keyword = ref('')
const status = ref('')
const users = ref<User[]>([])
const categories = ref<Category[]>([])
const items = ref<Item[]>([])
const orders = ref<Order[]>([])
const audits = ref<Audit[]>([])
const categoryName = ref('')
const newUser = reactive({ id: '', password: '' })
function collect<T>(result: Page<T>, setter: (rows: T[]) => void) {
  setter(result.content)
  total.value = result.count
}
async function load() {
  await task.run(async () => {
    const params = {
      page: page.value - 1,
      size: 10,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
    }
    switch (section.value) {
      case 'overview':
        stats.value = await api.stats()
        break
      case 'users': {
        const result = await api.users({ page: params.page, size: 10 })
        users.value = await Promise.all(result.content.map((id) => api.user(id)))
        total.value = result.count
        break
      }
      case 'categories':
        collect(await api.categories({ page: params.page, size: 10 }), (rows) => {
          categories.value = rows
        })
        break
      case 'items':
        collect(await api.items(params), (rows) => {
          items.value = rows
        })
        break
      case 'orders':
        collect(
          await api.orders({ page: params.page, size: 10, status: params.status }),
          (rows) => {
            orders.value = rows
          }
        )
        break
      case 'audit':
        collect(await api.audit(page.value - 1), (rows) => {
          audits.value = rows
        })
        break
      default:
        throw new Error('未找到此管理页面')
    }
  })
}
function search() {
  page.value = 1
  void load()
}
async function changeRole(user: User, role: number) {
  if (user.id === auth.user?.id) return
  if (
    !(await confirmAction(
      `确认将 ${user.id} 设为${role === -1 ? '封禁' : role === 1 ? '管理员' : '普通用户'}？该账号的旧会话会失效。`
    ))
  )
    return
  await write.run(async () => {
    await api.role(user.id, role)
    await load()
  }, '用户角色已更新')
}
async function createUser() {
  await write.run(async () => {
    if (!validUserId(newUser.id)) throw new Error('账号格式不正确')
    const issue = validatePassword(newUser.password)
    if (issue) throw new Error(issue)
    await api.createUser(newUser.id, newUser.password)
    newUser.id = ''
    newUser.password = ''
    await load()
  }, '普通用户已创建')
}
async function createCategory() {
  await write.run(async () => {
    requireText(categoryName.value, '分类名称', 64, false)
    await api.createCategory(categoryName.value)
    categoryName.value = ''
    await load()
  }, '分类已创建')
}
async function toggle(item: Item) {
  if (await confirmAction(item.status === 0 ? '确认下架？已有订单不会自动取消。' : '确认上架？'))
    await write.run(async () => {
      await api.editItem(item.id, { status: item.status === 0 ? -1 : 0 })
      await load()
    }, '销售状态已更新')
}
onMounted(load)
</script>
<template>
  <PageHeading
    :title="titles[section] || '管理后台'"
    description="按权限管理平台数据，操作结果以服务端最新记录为准。"
    eyebrow="COMMUNIBUY ADMINISTRATION"
    ><el-button :loading="task.busy.value" :disabled="write.busy.value" @click="load"
      >刷新数据</el-button
    ></PageHeading
  ><ErrorNotice :message="task.error.value" @retry="load" /><ErrorNotice
    :message="write.error.value"
  /><el-skeleton
    v-if="task.busy.value && section === 'overview'"
    :rows="7"
    animated
    class="panel"
  />
  <template v-if="section === 'overview' && stats"
    ><div class="stats-grid">
      <article
        v-for="metric in [
          { label: '用户数量', value: stats.userCount },
          { label: '团购数量', value: stats.itemCount },
          { label: '订单数量', value: stats.orderCount },
          { label: '进行中团购', value: stats.ongoingItemCount },
        ]"
        :key="metric.label"
        class="panel metric"
      >
        <p>{{ metric.label }}</p>
        <strong>{{ BigInt(metric.value).toLocaleString('zh-CN') }}</strong>
      </article>
    </div>
    <div class="split-layout">
      <section class="panel">
        <h2>平台对象数量</h2>
        <p class="muted small">当前累计数量，不代表按日增长趋势。</p>
        <StatsChart :stats="stats" />
      </section>
      <section class="panel">
        <h2>资金统计</h2>
        <div
          v-for="metric in [
            { label: '当前已付有效订单额', value: stats.totalRevenue },
            { label: '累计成功支付', value: stats.grossPaidAmount },
            { label: '累计成功退款', value: stats.refundAmount },
            { label: '支付净额', value: stats.netPaidAmount },
          ]"
          :key="metric.label"
          class="summary-row"
        >
          <span>{{ metric.label }}</span
          ><strong>¥{{ money(metric.value) }}</strong>
        </div>
        <p class="plain-note">
          以上不代表利润或抽佣。缺支付凭据的历史已付订单：{{ stats.unreconciledPaidOrderCount }}
          张。
        </p>
      </section>
    </div></template
  >
  <section v-if="section !== 'overview'" class="panel" v-loading="task.busy.value">
    <template v-if="section === 'users'"
      ><form class="toolbar" @submit.prevent="createUser">
        <el-input
          v-model="newUser.id"
          placeholder="新用户账号"
          aria-label="新用户账号"
          :maxlength="32"
        /><el-input
          v-model="newUser.password"
          type="password"
          show-password
          placeholder="初始密码"
          aria-label="初始密码"
          autocomplete="new-password"
          :maxlength="72"
        /><el-button type="primary" native-type="submit" :loading="write.busy.value"
          >创建普通用户</el-button
        >
      </form>
      <el-table :data="users"
        ><el-table-column prop="id" label="账号" min-width="150" /><el-table-column
          prop="nickname"
          label="昵称"
          min-width="150" /><el-table-column label="身份 / 状态" min-width="190"
          ><template #default="{ row }"
            ><el-select
              :model-value="row.role"
              :disabled="row.id === auth.user?.id || write.busy.value"
              @change="(value: unknown) => changeRole(row, Number(value))"
              ><el-option label="普通用户" :value="0" /><el-option
                label="管理员"
                :value="1" /><el-option
                label="封禁"
                :value="-1" /></el-select></template></el-table-column></el-table
    ></template>
    <template v-if="section === 'categories'"
      ><form class="toolbar" @submit.prevent="createCategory">
        <el-input
          v-model="categoryName"
          placeholder="新的商品分类名称"
          aria-label="分类名称"
          :maxlength="64"
        /><el-button type="primary" native-type="submit" :loading="write.busy.value"
          >新建分类</el-button
        >
      </form>
      <el-table :data="categories"
        ><el-table-column prop="id" label="分类编号" width="160" /><el-table-column
          prop="name"
          label="分类名称" /></el-table
    ></template>
    <template v-if="section === 'items'"
      ><form class="toolbar" @submit.prevent="search">
        <el-input
          v-model="keyword"
          placeholder="搜索团购名称或说明"
          aria-label="搜索全部团购"
        /><el-button native-type="submit" :disabled="task.busy.value">搜索</el-button>
      </form>
      <el-table :data="items"
        ><el-table-column prop="name" label="名称" min-width="190" /><el-table-column
          prop="owner"
          label="发起人"
          width="130"
        /><el-table-column label="价格" width="150"
          ><template #default="{ row }">¥{{ money(row.price) }}</template></el-table-column
        ><el-table-column prop="stock" label="库存" width="100" /><el-table-column
          label="操作"
          width="180"
          ><template #default="{ row }"
            ><RouterLink :to="`/seller/items/${row.id}/edit`"
              ><el-button link type="primary">编辑</el-button></RouterLink
            ><el-button link :disabled="write.busy.value" @click="toggle(row)">{{
              row.status === 0 ? '下架' : '上架'
            }}</el-button></template
          ></el-table-column
        ></el-table
      ></template
    >
    <template v-if="section === 'orders'"
      ><div class="toolbar">
        <el-select
          v-model="status"
          placeholder="全部状态"
          clearable
          :disabled="task.busy.value"
          @change="search"
          ><el-option
            v-for="(label, value) in orderLabels"
            :key="value"
            :label="label"
            :value="value"
        /></el-select>
      </div>
      <el-table :data="orders"
        ><el-table-column label="订单" min-width="150"
          ><template #default="{ row }"
            ><RouterLink :to="`/orders/${row.id}`">{{ row.id }} →</RouterLink></template
          ></el-table-column
        ><el-table-column prop="user" label="买家" width="130" /><el-table-column
          prop="owner"
          label="发起人"
          width="130"
        /><el-table-column label="总额" min-width="150"
          ><template #default="{ row }">¥{{ money(row.total) }}</template></el-table-column
        ><el-table-column label="状态" width="110"
          ><template #default="{ row }">{{ orderLabels[row.status] }}</template></el-table-column
        ></el-table
      ></template
    >
    <template v-if="section === 'audit'"
      ><p class="muted small">操作者与资源标识已脱敏，排查时可使用 traceId。</p>
      <el-table :data="audits"
        ><el-table-column label="时间" min-width="180"
          ><template #default="{ row }">{{ dateTime(row.createTime) }}</template></el-table-column
        ><el-table-column prop="action" label="动作" min-width="140" /><el-table-column
          prop="outcome"
          label="结果"
          width="100" /><el-table-column
          prop="httpStatus"
          label="HTTP"
          width="80" /><el-table-column
          prop="traceId"
          label="Trace ID"
          min-width="230"
          show-overflow-tooltip /><el-table-column
          prop="actorHash"
          label="操作者哈希"
          min-width="160"
          show-overflow-tooltip /></el-table
    ></template>
    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="section === 'audit' ? 20 : 10"
        :total="pageCount(total)"
        :disabled="task.busy.value"
        layout="prev,pager,next"
        @current-change="load"
      />
    </div>
  </section>
</template>
<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}
.metric p {
  font-size: 12px;
  color: #8e9a83;
  margin-bottom: 10px;
}
.metric strong {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -1px;
}
.metric {
  padding: 23px;
}
@media (max-width: 700px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
  .metric {
    padding: 18px;
  }
  .metric strong {
    font-size: 25px;
  }
}
</style>
