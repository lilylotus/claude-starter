<script setup lang="ts">
// 图片展示页：CSS 多列瀑布流 + IntersectionObserver 触底无限加载 + el-image 懒加载/
// 点击查看原图，全部零新增依赖（浏览器原生 API + Element Plus 既有组件能力）。
// 见 openspec/changes/add-image-gallery，design.md Decision 8。
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as imageApi from '@/api/image'
import type { ImageVO } from '@/types/image'
import AuthorizedImage from '@/components/AuthorizedImage.vue'

const FEED_PAGE_SIZE = 20
// 页面实际滚动的容器，与 layout/AppLayout.vue 里包裹 <router-view> 的那层一致，
// AuthorizedImage 的懒加载 IntersectionObserver 监听这个容器而不是 window
const SCROLL_CONTAINER_SELECTOR = '.app-layout__main'

const images = ref<ImageVO[]>([])
const page = ref(0)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(true)

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const result = await imageApi.feedImages({ page: page.value + 1, pageSize: FEED_PAGE_SIZE })
    images.value.push(...result.records)
    page.value = result.page
    total.value = result.total
    hasMore.value = result.records.length > 0 && images.value.length < result.total
  } finally {
    loading.value = false
  }
}

const sentinelRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

function setupObserver() {
  const root = document.querySelector(SCROLL_CONTAINER_SELECTOR)
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) {
        loadMore()
      }
    },
    { root, rootMargin: '0px 0px 200px 0px' },
  )
  if (sentinelRef.value) observer.observe(sentinelRef.value)
}

onMounted(async () => {
  await loadMore()
  await nextTick()
  setupObserver()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div class="image-gallery">
    <div class="image-gallery__waterfall">
      <div v-for="image in images" :key="image.id" class="image-gallery__card">
        <AuthorizedImage
          :id="image.id"
          lazy
          :root="SCROLL_CONTAINER_SELECTOR"
          fit="cover"
          :aspect-ratio="`${image.imageWidth} / ${image.imageHeight}`"
          class="image-gallery__image"
        />
        <div class="image-gallery__caption" :title="image.originalFileName">{{ image.originalFileName }}</div>
      </div>
    </div>

    <div ref="sentinelRef" class="image-gallery__sentinel">
      <span v-if="loading">加载中...</span>
      <span v-else-if="!hasMore && images.length > 0">没有更多图片了</span>
      <span v-else-if="!hasMore && images.length === 0">暂无图片</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.image-gallery {
  padding: 4px;
}

.image-gallery__waterfall {
  column-count: 4;
  column-gap: 16px;

  @media (max-width: 1200px) {
    column-count: 3;
  }

  @media (max-width: 768px) {
    column-count: 2;
  }
}

.image-gallery__card {
  break-inside: avoid;
  margin-bottom: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.image-gallery__image {
  display: block;
  width: 100%;
  height: auto;
  cursor: pointer;
}

.image-gallery__caption {
  padding: 8px 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.image-gallery__sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}
</style>
