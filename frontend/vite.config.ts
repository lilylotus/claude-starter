import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: {
      // changeOrigin 保持 false（不重写 Host 请求头）：CAS/OAuth2 协议端点未登录时会用
      // Servlet 原生 sendRedirect("/sso/login?...") 发起相对路径重定向，Spring 按请求的
      // Host 头重建成绝对地址返回给浏览器——如果 changeOrigin=true 把 Host 头重写成后端
      // 自己的地址（127.0.0.1:48080），重建出的 Location 就会指向后端而不是本地开发服务器
      // 地址（127.0.0.1:5173），导致浏览器跳过 Vite 直接请求后端的 /sso/login（后端并不
      // 提供这个 SPA 路由），命中 IdentityAuthFilter 返回"未登录，请先登录"而不是渲染
      // 登录页。保持 Host 头透传，让后端按浏览器实际访问的源重建重定向地址。
      '/api': {
        target: 'http://127.0.0.1:48080',
        changeOrigin: false,
      },
    },
  },
})
