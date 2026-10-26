<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { useTask } from '../composables/useTask'
import { useCategories } from '../composables/useCategories'
import { priceToCents, requireText } from '../utils/format'
import { numericId } from '../utils/json'
import { ApiError } from '../api/http'
import type { Item, ItemInput } from '../types/api'
import ErrorNotice from '../components/ErrorNotice.vue'
import PageHeading from '../components/PageHeading.vue'
const route = useRoute()
const router = useRouter()
const task = useTask()
const write = useTask()
const categoryTask = useTask()
const id = ref(route.params.id ? String(route.params.id) : '')
const original = ref<Item | null>(null)
const editing = computed(() => !!id.value)
const stockConflict = ref(false)
const { categories, more, loadMore } = useCategories()
const form = reactive({
  name: '',
  description: '',
  price: '',
  stock: 0,
  status: 0,
  start: null as Date | null,
  end: null as Date | null,
  category: '',
})
async function load() {
  await task.run(async () => {
    numericId(id.value)
    const item = await api.item(id.value)
    original.value = item
    Object.assign(form, {
      name: item.name,
      description: item.description || '',
      price:
        (BigInt(item.price) / 100n).toString() +
        '.' +
        (BigInt(item.price) % 100n).toString().padStart(2, '0'),
      stock: item.stock,
      status: item.status,
      start: new Date(item.startTime * 1000),
      end: item.endTime == null ? null : new Date(item.endTime * 1000),
      category: item.categoryId || '',
    })
    stockConflict.value = false
  })
}
async function submit() {
  await write.run(async () => {
    requireText(form.name, '团购名称', 128)
    if (form.description.length > 10000 || /[<>]/.test(form.description))
      throw new Error('说明最多 10000 字，且不能含尖括号')
    if (!Number.isInteger(form.stock) || form.stock < 0 || form.stock > 2147483647)
      throw new Error('库存须为有效的非负整数')
    if (stockConflict.value) throw new Error('请先重新读取最新商品，再核对库存后提交')
    const data: ItemInput = {
      name: form.name,
      description: form.description,
      price: priceToCents(form.price),
      stock: form.stock,
      status: form.status,
    }
    if (form.start) data.startTime = Math.floor(form.start.getTime() / 1000)
    if (form.end) data.endTime = Math.floor(form.end.getTime() / 1000)
    else if (editing.value) data.endTime = -1
    if (
      data.endTime !== undefined &&
      data.endTime >= 0 &&
      data.endTime <= (data.startTime ?? Math.floor(Date.now() / 1000))
    )
      throw new Error('截止时间应晚于开始时间')
    if (original.value) data.version = BigInt(original.value.version)
    let saved: Item
    try {
      saved = editing.value ? await api.editItem(id.value, data) : await api.createItem(data)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) stockConflict.value = true
      throw e
    }
    const wasNew = !editing.value
    id.value = saved.id
    original.value = saved
    if (form.category && form.category !== saved.categoryId) {
      try {
        await api.assignCategory(saved.id, numericId(form.category))
      } catch (e) {
        if (wasNew) {
          await router.replace({
            path: `/seller/items/${saved.id}/edit`,
            query: { categoryFailed: '1' },
          })
          return
        }
        throw new Error(
          `商品已保存，但分类设置失败。请在此商品编辑页重试分类，避免重复创建。${e instanceof Error ? e.message : ''}`
        )
      }
    }
    await router.push('/seller/items')
  }, '团购已保存')
}
onMounted(() => {
  if (editing.value) void load()
  void categoryTask.run(loadMore)
})
</script>
<template>
  <RouterLink to="/seller/items" class="back-link">← 返回我的团购</RouterLink
  ><PageHeading
    :title="editing ? '编辑团购' : '发起一份好团购'"
    description="填写真实商品信息，让邻居了解你分享的好物。"
  />
  <div class="panel form-width">
    <el-alert
      v-if="route.query.categoryFailed"
      title="商品已创建成功，但分类设置未完成。请重新选择分类后保存，无需再创建商品。"
      type="warning"
      :closable="false"
      style="margin-bottom: 20px"
    /><ErrorNotice :message="task.error.value" @retry="load" /><ErrorNotice
      :message="write.error.value"
    /><el-alert
      v-if="stockConflict"
      title="商品版本发生变化，重新读取会覆盖当前表单，请核对最新库存后再保存。"
      type="warning"
      :closable="false"
      ><el-button @click="load">重新读取商品</el-button></el-alert
    ><el-form
      label-position="top"
      :disabled="write.busy.value || task.busy.value || (editing && !original)"
      @submit.prevent="submit"
      ><el-form-item label="团购名称"
        ><el-input
          v-model="form.name"
          :maxlength="128"
          placeholder="给好物起一个清楚的名字" /></el-form-item
      ><el-form-item label="商品说明"
        ><el-input
          v-model="form.description"
          type="textarea"
          :rows="5"
          :maxlength="10000"
          show-word-limit
          placeholder="规格、内容、注意事项等，请用纯文本说明" /></el-form-item
      ><el-form-item label="单价（元）"
        ><el-input
          v-model="form.price"
          inputmode="decimal"
          placeholder="例如 12.50，免费商品填写 0" /></el-form-item
      ><el-form-item label="剩余可售库存"
        ><el-input-number
          v-model="form.stock"
          :min="0"
          :max="2147483647"
          :precision="0" /></el-form-item
      ><el-form-item label="销售状态"
        ><el-radio-group v-model="form.status"
          ><el-radio :value="0">上架</el-radio><el-radio :value="-1">下架</el-radio></el-radio-group
        ></el-form-item
      ><el-form-item
        :label="editing ? '开始时间（留空保留原值）' : '开始时间（留空为服务端当前时间）'"
        ><el-date-picker
          v-model="form.start"
          type="datetime"
          placeholder="选择开始时间" /></el-form-item
      ><el-form-item label="截止时间（可不限）"
        ><el-date-picker
          v-model="form.end"
          type="datetime"
          clearable
          placeholder="不限时间" /></el-form-item
      ><el-form-item label="商品分类"
        ><el-select
          v-model="form.category"
          placeholder="可先不设置"
          :clearable="!original?.categoryId"
          ><el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id" /></el-select
        ><el-button v-if="more" text @click="categoryTask.run(loadMore)">加载更多分类</el-button
        ><ErrorNotice :message="categoryTask.error.value" @retry="categoryTask.run(loadMore)"
      /></el-form-item>
      <p class="plain-note">
        库存是当前剩余可售数量。编辑时会携带读取到的版本；发生冲突后，需要你重新核对。
      </p>
      <el-button
        type="primary"
        size="large"
        native-type="submit"
        :loading="write.busy.value"
        :disabled="stockConflict"
        >保存团购</el-button
      ></el-form
    >
  </div>
</template>
