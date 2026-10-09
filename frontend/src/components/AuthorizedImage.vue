<script setup lang="ts">
// 认证图片：图片管理缩略图、图片展示瀑布流共用。原图内容接口受 IdentityAuthFilter
// 保护，不能直接把 URL 当 <img src> 用（浏览器原生请求不会带 identity-token/menu 头），
// 必须经过 axios 以 responseType: 'blob' 取回内容，再转成本地 blob URL 展示
// （add-image-gallery change design.md Decision 5 修正说明）。
// lazy=false（管理页，单页最多十几行）时挂载即请求；lazy=true（展示页瀑布流）时用
// IntersectionObserver 监听，进入视口才请求，与"分页加载数据"是两件独立的事。
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchImageRaw } from '@/api/image'

const props = withDefaults(
  defineProps<{
    id: number
    lazy?: boolean
    root?: string
    fit?: 'cover' | 'contain' | 'fill' | 'none' | 'scale-down'
    aspectRatio?: string
  }>(),
  { lazy: true, root: undefined, fit: 'cover', aspectRatio: undefined },
)

const containerRef = ref<HTMLElement | null>(null)
const src = ref<string>()
const loading = ref(false)
const failed = ref(false)

let objectUrl: string | undefined
let observer: IntersectionObserver | null = null

async function load() {
  if (src.value || loading.value) return
  loading.value = true
  failed.value = false
  try {
    const blob = await fetchImageRaw(props.id)
    objectUrl = URL.createObjectURL(blob)
    src.value = objectUrl
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function disconnectObserver() {
  observer?.disconnect()
  observer = null
}

function setupObserver() {
  disconnectObserver()
  if (!containerRef.value) return
  const root = props.root ? document.querySelector(props.root) : null
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) {
        load()
        disconnectObserver()
      }
    },
    { root, rootMargin: '200px' },
  )
  observer.observe(containerRef.value)
}

onMounted(() => {
  if (props.lazy) {
    setupObserver()
  } else {
    load()
  }
})

// id 变化时（如列表翻页后组件被复用）重新加载，释放旧的 blob URL
watch(
  () => props.id,
  () => {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl)
      objectUrl = undefined
    }
    src.value = undefined
    failed.value = false
    if (props.lazy) {
      setupObserver()
    } else {
      load()
    }
  },
)

onBeforeUnmount(() => {
  disconnectObserver()
  if (objectUrl) URL.revokeObjectURL(objectUrl)
})
</script>

<template>
  <div
    ref="containerRef"
    class="authorized-image"
    :style="aspectRatio ? { aspectRatio } : undefined"
  >
    <el-image v-if="src" :src="src" :fit="fit" :preview-src-list="[src]" preview-teleported class="authorized-image__el">
      <template #error>
        <div class="authorized-image__placeholder">加载失败</div>
      </template>
    </el-image>
    <div v-else class="authorized-image__placeholder">{{ failed ? '加载失败' : '加载中...' }}</div>
  </div>
</template>

<style scoped lang="scss">
.authorized-image {
  width: 100%;
  height: 100%;
  position: relative;
}

.authorized-image__el {
  display: block;
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.authorized-image__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 48px;
  color: var(--color-text-tertiary);
  font-size: 12px;
  background: var(--color-canvas);
}
</style>
