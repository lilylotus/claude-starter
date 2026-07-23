// element-plus 的中文语言包只发布了 .mjs 文件，没有附带 .d.ts 声明，
// 直接 import 会被 vue-tsc 报 TS7016（隐式 any）。这里补一个最小的环境声明，
// 使 main.ts 里 `import zhCn from 'element-plus/dist/locale/zh-cn.mjs'` 能通过类型检查。
declare module 'element-plus/dist/locale/zh-cn.mjs' {
  import type { Language } from 'element-plus/es/locale'

  const zhCn: Language
  export default zhCn
}
