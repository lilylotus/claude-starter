// 把后端 "yyyy-MM-dd HH:mm:ss" 格式的时间字符串解析为本地时区的 epoch 毫秒时间戳。
// 直接 `new Date("yyyy-MM-dd HH:mm:ss")` 在部分浏览器上不可靠（非 ISO 8601 格式），
// 这里补一个 'T' 分隔符转成类 ISO 8601 格式后再交给 Date 解析。
export function parseBackendDateTime(value: string | undefined | null): number {
  if (!value) return 0
  const isoLike = value.includes('T') ? value : value.replace(' ', 'T')
  const timestamp = new Date(isoLike).getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
}
