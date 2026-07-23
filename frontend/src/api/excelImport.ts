import request from './request'
import type { FormFieldBizType, ImportResult } from '@/types/importFieldConfig'

// Excel 批量导入接口封装（模板下载 + 批量导入），组件/store 不直接调用 axios。

// 按业务对象类型下载 Excel 导入模板时用作浏览器另存为的默认文件名
const TEMPLATE_FILENAME: Record<FormFieldBizType, string> = {
  ORG: '组织导入模板.xlsx',
  USER: '人员导入模板.xlsx',
  POSITION: '任职导入模板.xlsx',
  APP: '应用导入模板.xlsx',
}

// 下载指定业务对象类型的 Excel 导入模板并触发浏览器另存为。该接口返回原始 .xlsx 文件流
// （不是 { code, message, data } 包装结构），必须显式声明 responseType: 'blob'，
// 响应拦截器对这类请求直接透传响应体，不做业务码校验（见 src/api/request.ts）
export async function downloadImportTemplate(bizType: FormFieldBizType): Promise<void> {
  const blob = (await request.get('/excel-import/template', {
    params: { bizType },
    responseType: 'blob',
  })) as unknown as Blob

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = TEMPLATE_FILENAME[bizType] ?? `${bizType}导入模板.xlsx`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

// 按业务对象类型批量导入 Excel 文件：multipart/form-data 上传，bizType 与 file 均作为
// 表单字段提交（后端 @RequestParam bizType 可从 multipart 表单字段解析），
// 返回成功条数与失败明细（行号 + 原因）
export function batchImportExcel(bizType: FormFieldBizType, file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('bizType', bizType)
  formData.append('file', file)
  return request.post('/excel-import/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
