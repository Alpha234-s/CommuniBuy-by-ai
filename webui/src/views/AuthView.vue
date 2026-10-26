<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ShoppingBag, Check, CircleCheck } from '@element-plus/icons-vue'
import { useAuth } from '../stores/auth'
import { api } from '../api'
import { useTask } from '../composables/useTask'
import { safeReturnPath, validUserId, validatePassword } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const register = computed(() => route.path === '/register')
const form = reactive({ user: '', password: '', confirm: '' })
const { busy, error, run } = useTask()
async function submit() {
  await run(async () => {
    if (!validUserId(form.user))
      throw new Error('账号以字母或下划线开头，包含 1～32 位字母、数字或下划线')
    const passwordError = validatePassword(form.password, register.value)
    if (passwordError) throw new Error(passwordError)
    if (register.value) {
      if (form.password !== form.confirm) throw new Error('两次输入的密码不一致')
      await api.register(form.user, form.password)
      form.password = ''
      form.confirm = ''
      await router.push({
        path: '/login',
        query: { registered: '1', return: safeReturnPath(route.query.return) },
      })
    } else {
      await auth.login(form.user, form.password)
      form.password = ''
      await router.push(safeReturnPath(route.query.return))
    }
  })
}
</script>

<template>
  <div class="auth-layout">
    <section class="auth-story">
      <p class="eyebrow">GOOD THINGS, TOGETHER.</p>
      <h1>把社区好物，<br />买成日常的<span>小确幸。</span></h1>
      <p class="story-description">
        发现社区里的团购，和邻居一起挑选。<br />从一份心意开始，让社区生活更有滋味。
      </p>
      <div class="story-art" aria-hidden="true">
        <div class="art-orbit"></div>
        <div class="paper-leaf leaf-one"></div>
        <div class="paper-leaf leaf-two"></div>
        <div class="bag-handle"></div>
        <div class="paper-bag">
          <el-icon><ShoppingBag /></el-icon><span>好物 · 一起分享</span
          ><strong>communi<br />buy.</strong>
        </div>
        <div class="art-label"><span class="dot"></span> 相聚，从好物开始</div>
        <div class="art-tag">WITH<br />CLASSMATES ↗</div>
      </div>
      <div class="story-points">
        <span
          ><el-icon><CircleCheck /></el-icon> 邻居发起</span
        ><span
          ><el-icon><CircleCheck /></el-icon> 订单可查</span
        ><span
          ><el-icon><CircleCheck /></el-icon> 一起分享</span
        >
      </div>
    </section>
    <section class="auth-card">
      <span class="badge">WELCOME TO COMMUNIBUY</span>
      <h2>{{ register ? '开启你的社区团购平台' : '欢迎回来' }}</h2>
      <p class="muted auth-subtitle">
        {{ register ? '创建账号，发现身边值得一起买的好物。' : '登录后，发现正在发生的好团购。' }}
      </p>
      <el-alert
        v-if="route.query.registered"
        title="注册成功，请使用新账号登录"
        type="success"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="route.query.expired"
        title="登录已失效，请重新登录后核对原订单状态"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="route.query.offline"
        title="暂时无法验证登录，请检查服务连接后重试"
        type="warning"
        :closable="false"
        show-icon
      />
      <ErrorNotice :message="error" />
      <el-form label-position="top" @submit.prevent="submit" :disabled="busy">
        <el-form-item label="账号"
          ><el-input
            v-model="form.user"
            name="username"
            autocomplete="username"
            size="large"
            placeholder="请输入你的账号"
            :maxlength="32"
        /></el-form-item>
        <el-form-item label="密码"
          ><el-input
            v-model="form.password"
            name="password"
            :autocomplete="register ? 'new-password' : 'current-password'"
            type="password"
            show-password
            size="large"
            :maxlength="72"
            placeholder="请输入密码"
        /></el-form-item>
        <el-form-item v-if="register" label="确认密码"
          ><el-input
            v-model="form.confirm"
            name="confirm-password"
            autocomplete="new-password"
            type="password"
            show-password
            size="large"
            :maxlength="72"
            placeholder="请再输入一次密码"
        /></el-form-item>
        <p v-if="register" class="muted small">新密码为 8～72 字符，UTF-8 长度不超过 72 字节。</p>
        <el-button
          class="auth-submit"
          type="primary"
          native-type="submit"
          size="large"
          :loading="busy"
          >{{ register ? '创建账号' : '登录，发现好团购' }}<el-icon><ArrowRight /></el-icon
        ></el-button>
      </el-form>
      <p class="auth-switch">
        {{ register ? '已经有账号？' : '还没有账号？' }}
        <RouterLink
          :to="{
            path: register ? '/login' : '/register',
            query: { return: safeReturnPath(route.query.return) },
          }"
          >{{ register ? '前往登录' : '立即注册' }} ↗</RouterLink
        >
      </p>
      <div class="auth-footnote">
        <el-icon><Check /></el-icon> 你的资料仅用于账号与订单服务
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(350px, 0.9fr);
  align-items: center;
  gap: 90px;
}
.auth-story h1 {
  font-size: 44px;
  line-height: 1.55;
  letter-spacing: -1.8px;
  font-weight: 650;
  margin-bottom: 20px;
}
.auth-story h1 span {
  color: #73904a;
}
.story-description {
  font-size: 14px;
  color: #7c8976;
  line-height: 2;
}
.story-art {
  position: relative;
  height: 248px;
  max-width: 480px;
  margin-top: 24px;
}
.art-orbit {
  position: absolute;
  width: 240px;
  height: 205px;
  border-radius: 50%;
  left: 65px;
  top: 15px;
  background: #eef0dc;
}
.paper-bag {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  left: 118px;
  top: 52px;
  width: 166px;
  height: 164px;
  background: #d1b882;
  transform: rotate(-9deg);
  border-radius: 3px 3px 22px 12px;
  padding: 20px 20px;
  color: #50613a;
  box-shadow: 14px 18px 0 #dacda34d;
}
.paper-bag > .el-icon {
  font-size: 24px;
}
.paper-bag span {
  display: block;
  font-size: 9px;
  margin: 3px 0 9px;
  letter-spacing: 1px;
}
.paper-bag strong {
  display: block;
  width: 100%;
  font-family: Georgia, serif;
  font-size: 27px;
  line-height: 0.95;
  letter-spacing: -1px;
}
.bag-handle {
  position: absolute;
  width: 78px;
  height: 70px;
  left: 159px;
  top: 8px;
  border: 7px solid #c0a46c;
  border-radius: 50% 50% 0 0;
  transform: rotate(-9deg);
}
.paper-leaf {
  position: absolute;
  background: #7f9565;
  border-radius: 5% 85% 5% 85%;
  width: 75px;
  height: 135px;
  left: 272px;
  top: 0;
  transform: rotate(28deg);
}
.leaf-two {
  width: 60px;
  height: 105px;
  left: 70px;
  top: 54px;
  transform: rotate(-35deg);
  background: #afbd8b;
}
.art-label {
  position: absolute;
  right: 24px;
  bottom: 45px;
  display: flex;
  gap: 9px;
  align-items: center;
  background: white;
  border: 1px solid #e0e5d8;
  padding: 11px 14px;
  font-size: 11px;
  transform: rotate(5deg);
  border-radius: 7px;
}
.art-tag {
  position: absolute;
  right: 53px;
  top: 20px;
  font-size: 10px;
  letter-spacing: 2px;
  line-height: 1.7;
  color: #84926e;
}
.story-points {
  display: flex;
  gap: 27px;
  font-size: 11px;
  color: #7b8b71;
}
.story-points span {
  display: flex;
  align-items: center;
  gap: 7px;
}
.auth-card {
  background: #fff;
  border: 1px solid #e4e8df;
  padding: 36px;
  border-radius: 20px;
  box-shadow: 0 16px 55px #3e5a2610;
}
.auth-card > .badge {
  font-size: 9px;
  letter-spacing: 1px;
  margin-bottom: 23px;
}
.auth-card h2 {
  font-size: 27px;
  margin-bottom: 10px;
}
.auth-subtitle {
  font-size: 12px;
  margin-bottom: 27px;
}
.auth-card .el-alert {
  margin-bottom: 18px;
}
.auth-submit {
  width: 100%;
  margin-top: 9px;
  display: flex;
  justify-content: space-between;
  padding: 0 18px;
}
.auth-switch {
  text-align: center;
  font-size: 12px;
  color: #97a08f;
  margin: 22px 0 24px;
}
.auth-switch a {
  font-weight: 600;
  margin-left: 5px;
}
.auth-footnote {
  border-top: 1px solid var(--line);
  padding-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  color: #9aa391;
}
@media (max-width: 1000px) {
  .auth-layout {
    gap: 45px;
    grid-template-columns: 1fr 1fr;
  }
  .auth-story h1 {
    font-size: 33px;
  }
  .story-art {
    transform: scale(0.8);
    transform-origin: left center;
  }
  .story-points {
    gap: 14px;
  }
  .auth-card {
    padding: 28px;
  }
}
@media (max-width: 700px) {
  .auth-layout {
    grid-template-columns: 1fr;
    gap: 28px;
  }
  .auth-story h1 {
    font-size: 30px;
  }
  .auth-story {
    padding: 6px 10px;
  }
  .auth-story .eyebrow {
    font-size: 9px;
  }
  .story-description {
    margin-bottom: 0;
  }
  .story-art,
  .story-points {
    display: none;
  }
  .auth-card {
    padding: 28px 22px;
  }
  .auth-story h1 br {
    display: none;
  }
  .auth-card h2 {
    font-size: 24px;
  }
}
</style>
