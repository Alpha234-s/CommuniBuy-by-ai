<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, ArrowRight, ShoppingBag, Box, CircleCheck, Plus } from '@element-plus/icons-vue'
import { api } from '../api'
import { errorText, useTask } from '../composables/useTask'
import { useCategories } from '../composables/useCategories'
import type { Item } from '../types/api'
import { pageCount } from '../utils/format'
import ItemCard from '../components/ItemCard.vue'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute()
const router = useRouter()
const query = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const keyword = ref(query.value)
const category = ref('')
const page = ref(1)
const items = ref<Item[]>([])
const total = ref('0')
const busy = ref(false)
const error = ref('')
const { categories, more, loadMore } = useCategories()
const categoryTask = useTask()
let sequence = 0
async function load() {
  const current = ++sequence
  busy.value = true
  error.value = ''
  try {
    const result = await api.items({
      page: page.value - 1,
      size: 8,
      status: 0,
      keyword: query.value || undefined,
      category: category.value || undefined,
    })
    if (current === sequence) {
      items.value = result.content
      total.value = result.count
    }
  } catch (e) {
    if (current === sequence) error.value = errorText(e)
  } finally {
    if (current === sequence) busy.value = false
  }
}
function search() {
  const next = keyword.value.trim()
  if (next !== query.value) {
    void router.push({ path: '/', query: next ? { q: next } : {} })
  } else if (page.value !== 1) page.value = 1
  else void load()
}
function selectCategory(id: string) {
  category.value = id
  page.value = 1
}
watch(query, () => {
  keyword.value = query.value
  page.value = 1
})
watch([page, category, query], () => {
  void load()
})
onMounted(() => {
  void load()
  void categoryTask.run(loadMore)
})
</script>

<template>
  <section class="home-hero">
    <div class="hero-copy">
      <p class="eyebrow">A LITTLE CLOSER, A LITTLE BETTER.</p>
      <span class="badge"><span class="dot"></span> 好物相聚 · 社区共享</span>
      <h1>一起买好物，<br />把生活过得<span>更有滋味。</span></h1>
      <p>邻居发起的好团购，值得一起分享。<br />在熟悉的社区里，遇见一点新鲜。</p>
      <a href="#discover" class="hero-link"
        >发现身边好团购 <el-icon><ArrowRight /></el-icon
      ></a>
    </div>
    <div class="hero-scene" aria-hidden="true">
      <span class="scene-ring"></span>
      <div class="scene-leaf"></div>
      <div class="scene-box">
        <el-icon><Box /></el-icon><span>GOOD THINGS</span>
      </div>
      <div class="scene-bag">
        <el-icon><ShoppingBag /></el-icon><strong>together<br />is better.</strong
        ><span>COMMUNIBUY</span>
      </div>
      <div class="scene-sticker">让好生活<br />更近一点 ↗</div>
      <span class="scene-caption">SHARED BY YOUR COMMUNITY</span>
    </div>
  </section>
  <div class="value-strip">
    <span
      ><el-icon><CircleCheck /></el-icon> 邻居发起，随时发现</span
    ><span
      ><el-icon><CircleCheck /></el-icon> 团购进度，清晰可见</span
    ><span
      ><el-icon><CircleCheck /></el-icon> 一份好物，一起分享</span
    ><RouterLink to="/seller/items/new"
      >我也想发起 <el-icon><Plus /></el-icon
    ></RouterLink>
  </div>
  <section id="discover" class="discover">
    <div class="discover-heading">
      <div>
        <p class="eyebrow">DISCOVER GOOD THINGS</p>
        <div class="discover-title-row">
          <h2>
            发现好团购 <span v-if="!busy && !error">{{ total }} 个团购</span>
          </h2>
          <RouterLink to="/seller/items/new" class="discover-publish">
            <el-button type="primary" size="large" :icon="Plus">发起团购</el-button>
          </RouterLink>
        </div>
      </div>
      <form
        class="home-search"
        role="search"
        aria-label="搜索社区团购平台"
        @submit.prevent="search"
      >
        <el-input
          v-model="keyword"
          placeholder="搜索团购名称或说明"
          aria-label="搜索团购"
          clearable
          size="large"
          @clear="search"
          ><template #prefix
            ><el-icon><Search /></el-icon></template></el-input
        ><el-button type="primary" native-type="submit" size="large">搜索</el-button>
      </form>
    </div>
    <div class="category-tabs" aria-label="团购分类">
      <button :class="{ active: !category }" @click="selectCategory('')">全部好物</button
      ><button
        v-for="c in categories"
        :key="c.id"
        :class="{ active: category === c.id }"
        @click="selectCategory(c.id)"
      >
        {{ c.name }}</button
      ><button v-if="more" :disabled="categoryTask.busy.value" @click="categoryTask.run(loadMore)">
        更多分类 +
      </button>
    </div>
    <ErrorNotice :message="categoryTask.error.value" @retry="categoryTask.run(loadMore)" />
    <ErrorNotice :message="error" @retry="load" />
    <el-skeleton v-if="busy" :rows="7" animated class="panel" />
    <div v-else-if="!error && items.length" class="product-grid">
      <ItemCard
        v-for="item in items"
        :key="item.id"
        :item="item"
        :category="categories.find((c) => c.id === item.categoryId)?.name"
      />
    </div>
    <el-empty
      v-else-if="!error"
      :description="
        query || category
          ? '没有找到匹配的团购，试试其他条件'
          : '还没有上架的团购，来发起第一份好物吧'
      "
      ><RouterLink v-if="query" to="/"><el-button type="primary">清空搜索</el-button></RouterLink
      ><el-button v-else-if="category" type="primary" @click="selectCategory('')"
        >查看全部好物</el-button
      ></el-empty
    >
    <div v-if="!error && total !== '0'" class="pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="8"
        :total="pageCount(total)"
        background
        layout="prev, pager, next"
      />
    </div>
  </section>
  <section class="host-banner">
    <div>
      <span class="eyebrow">SHARE SOMETHING GOOD</span>
      <h2>你发现的好物，也值得让邻居知道。</h2>
      <p>从发起一份团购开始，把好生活分享给身边的邻居。</p>
    </div>
    <RouterLink to="/seller/items/new"
      ><el-button plain size="large"
        >发起我的团购 <el-icon><ArrowRight /></el-icon></el-button
    ></RouterLink>
  </section>
  <p class="home-footnote">商品图形为统一占位示意，具体商品以发起人文字说明为准。</p>
</template>

<style scoped>
.home-hero {
  min-height: 355px;
  background: #edf0e1;
  border-radius: 20px;
  display: flex;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
  padding: 38px 45px;
}
.hero-copy {
  position: relative;
  z-index: 1;
}
.hero-copy > .eyebrow {
  font-size: 9px;
  color: #96a17f;
  margin-bottom: 18px;
}
.hero-copy .badge {
  background: transparent;
  border-color: #d3ddc4;
  font-size: 10px;
  padding: 4px 9px;
}
.hero-copy h1 {
  font-size: 38px;
  line-height: 1.5;
  margin: 15px 0 12px;
  letter-spacing: -1.2px;
}
.hero-copy h1 span {
  color: #7e9453;
}
.hero-copy > p:not(.eyebrow) {
  font-size: 12px;
  color: #889677;
  line-height: 1.9;
  margin-bottom: 18px;
}
.hero-link {
  display: inline-flex;
  align-items: center;
  gap: 28px;
  font-size: 12px;
  font-weight: 600;
}
.hero-scene {
  position: relative;
  min-width: 390px;
  width: 43%;
  align-self: stretch;
}
.scene-ring {
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: #e2e7cd;
  left: 22px;
  top: -5px;
}
.scene-box {
  position: absolute;
  width: 132px;
  height: 130px;
  background: #d8c3a0;
  border-radius: 5px 5px 12px 12px;
  left: 7px;
  top: 128px;
  transform: rotate(-10deg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 13px;
  box-shadow: 10px 15px 0 #a6b08415;
}
.scene-box .el-icon {
  font-size: 53px;
  color: #a58b60;
}
.scene-box span {
  font-size: 7px;
  letter-spacing: 2px;
  color: #8c7853;
}
.scene-bag {
  position: absolute;
  left: 132px;
  top: 55px;
  background: #7d9561;
  border-radius: 9px 9px 21px 7px;
  width: 162px;
  height: 207px;
  transform: rotate(11deg);
  padding: 25px;
  color: #ecf0dc;
  box-shadow: 13px 15px 0 #708c5210;
}
.scene-bag:before {
  content: '';
  position: absolute;
  top: -39px;
  left: 42px;
  width: 75px;
  height: 53px;
  border: 7px solid #768b5e;
  border-bottom: 0;
  border-radius: 40px 40px 0 0;
}
.scene-bag > .el-icon {
  font-size: 31px;
}
.scene-bag strong {
  font-family: Georgia, serif;
  font-size: 28px;
  line-height: 1.05;
  display: block;
  margin: 10px 0 20px;
}
.scene-bag > span {
  font-size: 6px;
  letter-spacing: 2px;
}
.scene-leaf {
  position: absolute;
  left: 260px;
  top: 10px;
  width: 62px;
  height: 108px;
  background: #abb887;
  border-radius: 90% 0;
  transform: rotate(18deg);
}
.scene-sticker {
  position: absolute;
  right: 10px;
  bottom: 38px;
  background: #fafbf0;
  border: 1px solid #dde2ce;
  padding: 13px 20px;
  font-size: 12px;
  line-height: 1.7;
  transform: rotate(-8deg);
  color: #7c8c65;
  border-radius: 5px;
}
.scene-caption {
  position: absolute;
  right: 0;
  top: 1px;
  font-size: 7px;
  color: #9da88e;
  letter-spacing: 2px;
  writing-mode: vertical-rl;
}
.value-strip {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 5px 26px;
  color: #8d9883;
  font-size: 11px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 38px;
}
.value-strip span,
.value-strip a {
  display: flex;
  gap: 9px;
  align-items: center;
}
.value-strip a {
  color: #59764b;
}
.discover {
  scroll-margin-top: 20px;
}
.discover-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
  gap: 25px;
}
.discover-heading .eyebrow {
  font-size: 9px;
  margin-bottom: 10px;
}
.discover-heading h2 {
  margin: 0;
  font-size: 24px;
}
.discover-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 18px;
}
.discover-publish {
  display: inline-flex;
  flex-shrink: 0;
}
.discover-heading h2 span {
  font-size: 11px;
  font-weight: 400;
  color: #9ba28f;
  margin-left: 15px;
}
.home-search {
  display: flex;
  gap: 10px;
  max-width: 410px;
  width: 43%;
}
.category-tabs {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 10px;
  margin-bottom: 13px;
}
.category-tabs button {
  white-space: nowrap;
  border: 1px solid #e5e8df;
  border-radius: 8px;
  background: #fff;
  padding: 9px 20px;
  font-size: 12px;
  color: #87927c;
}
.category-tabs button.active {
  background: #28664c;
  color: #fff;
  border-color: #28664c;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 22px;
}
.host-banner {
  border: 1px solid #e1e6d8;
  border-radius: 15px;
  background: #f1f3e9;
  margin-top: 45px;
  padding: 28px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.host-banner h2 {
  font-size: 20px;
  margin: 10px 0;
}
.host-banner .eyebrow {
  font-size: 8px;
}
.host-banner p {
  color: #8c967e;
  font-size: 12px;
  margin: 0;
}
.host-banner .el-button {
  background: transparent;
  border-color: #c4d1b5;
  color: #5d784c;
}
.host-banner .el-icon {
  margin-left: 12px;
}
.home-footnote {
  text-align: right;
  color: #a4ac9c;
  font-size: 9px;
  margin-top: 17px;
}
@media (max-width: 1050px) {
  .home-hero {
    padding: 30px;
  }
  .hero-copy h1 {
    font-size: 30px;
  }
  .hero-scene {
    transform: scale(0.85);
    transform-origin: right center;
    min-width: 310px;
  }
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
  }
  .value-strip {
    gap: 10px;
  }
  .value-strip > span {
    font-size: 10px;
  }
}
@media (max-width: 700px) {
  .home-hero {
    min-height: 290px;
  }
  .hero-copy h1 {
    font-size: 29px;
  }
  .hero-scene {
    display: none;
  }
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .value-strip {
    flex-wrap: wrap;
    padding: 20px 0;
    gap: 13px;
    margin-bottom: 28px;
  }
  .value-strip > span:last-of-type {
    display: none;
  }
  .discover-heading {
    flex-direction: column;
    align-items: stretch;
    gap: 18px;
  }
  .home-search {
    width: 100%;
    max-width: none;
  }
  .category-tabs button {
    padding: 8px 15px;
  }
  .host-banner {
    flex-direction: column;
    align-items: flex-start;
    padding: 25px;
  }
  .host-banner h2 {
    font-size: 18px;
  }
  .home-hero .eyebrow {
    font-size: 7px;
  }
  .discover-heading h2 {
    font-size: 22px;
  }
}
@media (max-width: 370px) {
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
