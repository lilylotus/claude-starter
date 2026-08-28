import request from './request'
import type { FormFieldBizType } from '@/types/importFieldConfig'

// Excel 导出接口封装（按业务对象类型导出当前管辖范围内的数据），组件/store 不直接调用 axios。
// 与 api/excelImport.ts 的"下载导入模板"函数同构：GET 返回原始 .xlsx 文件流
// （不是 { code, message, data } 包装结构），必须显式声明 responseType: 'blob'，
// 响应拦截器对这类请求直接透传响应体，不做业务码校验（见 src/api/request.ts）。

// 按业务对象类型导出 Excel 时用作浏览器另存为的默认文件名
const EXPORT_FILENAME: Record<FormFieldBizType, string> = {
  ORG: '组织导出.xlsx',
  USER: '人员导出.xlsx',
  POSITION: '任职导出.xlsx',
  APP: '应用导出.xlsx',
}

// 按业务对象类型导出 Excel 并触发浏览器另存为：导出内容按当前登录用户的管辖组织范围
// 收窄（人员导出不做组织范围收紧，与 GET /api/users 现状保持一致），列的选取与顺序
// 取自该 bizType 下状态为启用、且"是否导出"为真的表单字段定义
export async function exportExcel(bizType: FormFieldBizType): Promise<void> {
  const blob = (await request.get('/excel-export/download', {
    params: { bizType },
    responseType: 'blob',
  })) as unknown as Blob

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = EXPORT_FILENAME[bizType] ?? `${bizType}导出.xlsx`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
