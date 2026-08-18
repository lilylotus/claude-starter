## ADDED Requirements

### Requirement: 可复用的转换脚本执行工具类
系统 SHALL 提供一个位于 `cn.nihility.rbac.common.util` 包下的静态工具类
`ScriptTransformExecutor`，在最小权限的 GraalVM 沙箱内执行一段 JavaScript 转换脚本
（脚本以 `value` 全局变量读入源字段值，脚本最后一个表达式的值作为结果），供后端各处
需要"转换脚本"能力的字段映射功能共同复用，不各自重复实现沙箱执行细节。脚本执行 SHALL
有超时保护（200 毫秒），超时或执行异常时 SHALL 返回 `null` 并记录 WARN 级别日志，不
SHALL 向上抛出异常影响调用方后续处理。

#### Scenario: 脚本正常执行返回结果
- **WHEN** 调用方执行一段合法的转换脚本，源字段值为某个具体值
- **THEN** 返回该脚本最后一个表达式的求值结果

#### Scenario: 脚本执行超时返回 null
- **WHEN** 调用方执行的脚本运行超过 200 毫秒仍未结束
- **THEN** 方法返回 `null`，记录一条 WARN 日志，不向上抛出异常

#### Scenario: 脚本执行异常返回 null
- **WHEN** 调用方执行的脚本在运行期间抛出异常（如访问未定义变量）
- **THEN** 方法返回 `null`，记录一条 WARN 日志，不向上抛出异常
