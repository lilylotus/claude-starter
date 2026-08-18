## ADDED Requirements

### Requirement: 全局字符串 Redis 工具类
系统 SHALL 提供一个全局静态工具类（`RedisUtils`），封装基于 `StringRedisTemplate` 的
字符串 key 读写、Hash 字段读写、key 存在性判断、过期时间设置与删除操作，供业务代码
直接调用，无需各自注入 `StringRedisTemplate`。

#### Scenario: 读写字符串 key
- **WHEN** 业务代码调用 `RedisUtils.set(key, value, timeout, unit)` 写入一个字符串值
  并设置过期时间
- **THEN** 之后调用 `RedisUtils.get(key)` 能读取到该值，超过过期时间后读取返回空

#### Scenario: 读写 Hash 字段
- **WHEN** 业务代码调用 `RedisUtils.putHash`/`putHashField` 写入一个 Hash 的多个/单个字段
- **THEN** 调用 `RedisUtils.hashEntries`/`getHashObject` 能读取到已写入的字段值

### Requirement: 基于 JSON 字符串中转的对象存取
`RedisUtils` SHALL 提供对象存取方法（`setObject`/`getObject`/`putHashObject`/
`getHashObject`），写入前把对象序列化为 JSON 字符串、读取后按目标类型反序列化，
供业务代码直接存取对象实例而不必手动调用 JSON 工具类。

#### Scenario: 存取对象实例
- **WHEN** 业务代码调用 `RedisUtils.setObject(key, payload, timeout, unit)` 写入一个
  对象实例
- **THEN** 调用 `RedisUtils.getObject(key, PayloadClass.class)` 能读取到反序列化后的
  同等对象

### Requirement: 基于专用对象 Redis 模板的对象存取
系统 SHALL 提供一个基于专用 `objectRedisTemplate`（`RedisTemplate<String, Object>`，
value/hashValue 用 `Jackson2JsonRedisSerializer` 包装）的对象存取工具类
（`RedisObjectUtils`），使 `opsForValue()`/`opsForHash()` 可以直接 put/get 对象实例，
序列化在 Redis 客户端层面完成，不需要调用方手动转 JSON 字符串。

#### Scenario: 直接存取对象实例
- **WHEN** 业务代码调用 `RedisObjectUtils.set(key, value)` 写入一个对象实例
- **THEN** 调用 `RedisObjectUtils.get(key, ValueClass.class)` 能读取到转换后的同等对象，
  底层未经过手动 JSON 字符串序列化

#### Scenario: 读取原始值并按需转换类型
- **WHEN** 业务代码调用 `RedisObjectUtils.get(key)` 读取一个已写入的复杂对象
- **THEN** 返回值为反序列化后的原始 `LinkedHashMap`（Jackson 处理未知目标类型的标准
  行为），业务代码可再调用 `RedisObjectUtils.get(key, ValueClass.class)` 转换为目标类型

### Requirement: 未初始化时明确报错
`RedisUtils`/`RedisObjectUtils` 依赖的底层 Redis 模板 SHALL 由 Spring 容器在启动阶段
注入；容器尚未完成注入时调用工具类方法 SHALL 抛出 `IllegalStateException`，而不是
返回 `null` 或抛出难以定位根因的 `NullPointerException`。

#### Scenario: Spring 容器未启动时调用工具类方法
- **WHEN** `RedisUtils`/`RedisObjectUtils` 尚未完成初始化（底层 Redis 模板未注入）时，
  调用其读写方法
- **THEN** 方法抛出 `IllegalStateException`，异常信息提示容器尚未启动完成初始化
