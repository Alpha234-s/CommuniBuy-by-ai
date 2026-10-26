<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useAuth } from '../stores/auth'
import { useTask } from '../composables/useTask'
import { requireText, validatePassword } from '../utils/format'
import { confirmAction } from '../utils/confirm'
import ErrorNotice from '../components/ErrorNotice.vue'
import PageHeading from '../components/PageHeading.vue'
const auth = useAuth()
const router = useRouter()
const task = useTask()
const passwordTask = useTask()
const form = reactive({
  nickname: auth.user?.nickname || '',
  description: auth.user?.description || '',
  address: auth.user?.address || '',
})
const passwords = reactive({ old: '', next: '', confirm: '' })
async function save() {
  await task.run(async () => {
    const changes: Record<string, string> = {}
    if (form.nickname !== (auth.user?.nickname || '')) {
      requireText(form.nickname, '昵称', 64)
      changes.nickname = form.nickname
    }
    if (form.description !== (auth.user?.description || '')) {
      if (form.description.length > 255 || /[<>]/.test(form.description))
        throw new Error('个人说明最多 255 字，不能含尖括号')
      changes.description = form.description
    }
    if (form.address !== (auth.user?.address || '')) {
      requireText(form.address, '地址', 255, false)
      changes.address = form.address
    }
    if (!Object.keys(changes).length) throw new Error('请先修改需要更新的资料')
    auth.user = await api.profile(auth.user!.id, changes)
  }, '个人资料已保存')
}
async function reset() {
  await passwordTask.run(async () => {
    const issue = validatePassword(passwords.old, false) || validatePassword(passwords.next)
    if (issue) throw new Error(issue)
    if (passwords.next !== passwords.confirm) throw new Error('两次新密码不一致')
    if (!(await confirmAction('改密后所有旧会话立即失效，需要重新登录。确认修改？'))) return
    await api.reset(passwords.old, passwords.next)
    passwords.old = ''
    passwords.next = ''
    passwords.confirm = ''
    auth.clear(true)
    await router.push('/login')
  })
}
</script>
<template>
  <PageHeading
    title="个人中心"
    description="完善资料，让社区里的每一份好物顺利抵达。"
    eyebrow="YOUR NEIGHBORHOOD PROFILE"
  />
  <div class="split-layout">
    <section class="panel">
      <h2>个人资料</h2>
      <p class="muted small">
        账号 {{ auth.user?.id }} · {{ auth.isAdmin ? '管理员' : '普通用户' }}
      </p>
      <ErrorNotice :message="task.error.value" /><el-form
        label-position="top"
        :disabled="task.busy.value"
        @submit.prevent="save"
        ><el-form-item label="昵称"
          ><el-input
            v-model="form.nickname"
            :maxlength="64"
            placeholder="让邻居认识你" /></el-form-item
        ><el-form-item label="个人说明"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :maxlength="255" /></el-form-item
        ><el-form-item label="默认收货地址"
          ><el-input
            v-model="form.address"
            type="textarea"
            :rows="3"
            :maxlength="255"
            placeholder="每次下单仍可确认或修改本次订单地址"
        /></el-form-item>
        <p class="plain-note">修改默认地址不会改变已有订单的收货地址。</p>
        <el-button native-type="submit" type="primary" :loading="task.busy.value"
          >保存资料</el-button
        ></el-form
      >
    </section>
    <section class="panel">
      <h2>账号安全</h2>
      <ErrorNotice :message="passwordTask.error.value" /><el-form
        label-position="top"
        :disabled="passwordTask.busy.value"
        @submit.prevent="reset"
        ><el-form-item label="当前密码"
          ><el-input
            v-model="passwords.old"
            type="password"
            show-password
            autocomplete="current-password"
            :maxlength="72" /></el-form-item
        ><el-form-item label="新密码"
          ><el-input
            v-model="passwords.next"
            type="password"
            show-password
            autocomplete="new-password"
            :maxlength="72" /></el-form-item
        ><el-form-item label="确认新密码"
          ><el-input
            v-model="passwords.confirm"
            type="password"
            show-password
            autocomplete="new-password"
            :maxlength="72"
        /></el-form-item>
        <p class="muted small">
          新密码 8～72 字符且不超过 72 个 UTF-8 字节。修改后所有设备需要重新登录。
        </p>
        <el-button native-type="submit" :loading="passwordTask.busy.value"
          >修改密码</el-button
        ></el-form
      >
    </section>
  </div>
</template>
