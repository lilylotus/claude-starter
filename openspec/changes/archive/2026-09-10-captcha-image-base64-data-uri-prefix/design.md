## Context

见 proposal.md - Why。现状：`CaptchaImageRenderer#renderToBase64` 用
`Base64.getEncoder().encodeToString(...)` 输出纯 base64；`IMAGE_FORMAT` 常量固定为
`"png"`，`ImageIO.write` 也按此格式写出。`ImageCaptchaServiceImpl#generate` 把该字符串
原样塞进 `CaptchaImageVO.imageBase64`，controller 直接返回。前端尚无调用方。

## Goals / Non-Goals

- Goal：让 `imageBase64` 成为可直接用作 `<img src>` 的完整 data URI，实现与 spec/字段
  注释一致。
- Non-Goal：不做多图片格式支持、不引入图片格式配置项、不改验证码内容生成与校验逻辑、
  不动 Redis key/过期策略、不接入任何登录接口。

## Decisions

### Decision 1: 前缀在 `CaptchaImageRenderer` 内拼接，而非在 service / controller / 前端

- 选型：在 `renderToBase64` 的 `toBase64(...)` 返回处直接拼 `"data:image/" + IMAGE_FORMAT
  + ";base64," + encoded`。
- 理由：图片格式（PNG）是渲染器的内部知识，MIME 类型应与之同源推导，避免在上层硬编码
  `image/png` 导致格式改动时前缀不同步。方法名 `renderToBase64` 语义仍成立（data URI 的
  主体就是 base64），只更新 Javadoc 说明包含前缀即可。
- 备选：① 在 service 层拼 —— 上层需重复知道图片格式，被否。② 让前端拼 —— 与 spec
  「可直接渲染」的约定冲突，被否。

### Decision 2: 测试断言加强为前缀匹配

`ImageCaptchaServiceImplTest#generate_shouldStoreDefaultLengthCharacterAnswer` 里
`assertThat(vo.getImageBase64()).isNotBlank()` 改为
`assertThat(vo.getImageBase64()).startsWith("data:image/png;base64,")`，锁定返回格式契约。

## Risks / Trade-offs

- [已按旧格式（纯 base64）自行拼前缀的调用方会得到 `data:image/png;base64,data:image/...`
  的双前缀] → 当前无任何调用方（前端未接入、后端仅 controller 透传），影响为零；变更已在
  proposal 标注 BREAKING，接入方按新契约实现即可。
- [data URI 前缀使响应体略微增大（约 22 字节）] → 可忽略。

## Migration Plan

无数据迁移。随后端发版生效；回滚即还原 `renderToBase64` 与相关注释/断言。
