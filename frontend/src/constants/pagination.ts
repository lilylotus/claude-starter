// 全项目主列表分页共享的“每页条数”常量：可选项与默认值只在此处维护一份，
// 13 处分页统一 import，避免各处重复写死同一份字面量数组。
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const

export const DEFAULT_PAGE_SIZE = 10
