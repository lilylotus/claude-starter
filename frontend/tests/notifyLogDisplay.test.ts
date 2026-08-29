import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { formatNotifyBiz, formatNotifyDataType } from '../src/views/application/app/notifyLogDisplay.ts'

describe('通知日志展示格式', () => {
  it('将数据类型编码转换为中文并保留未知编码', () => {
    assert.equal(formatNotifyDataType('ORG'), '组织')
    assert.equal(formatNotifyDataType('UNKNOWN'), 'UNKNOWN')
    assert.equal(formatNotifyDataType(null), '-')
  })

  it('组合业务 id 和名称并兼容缺失值', () => {
    assert.equal(formatNotifyBiz(2, '测试组织名称'), '2（测试组织名称）')
    assert.equal(formatNotifyBiz(2, null), '2')
    assert.equal(formatNotifyBiz(null, '测试组织名称'), '-')
  })
})
