## Why

`image-captcha` 的生成接口 `GET /api/captcha/image` 目前返回的 `imageBase64` 是**不含**
`data:image/png;base64,` 前缀的纯 base64 字符串（见 `CaptchaImageRenderer#renderToBase64`
的实现与 Javadoc），前端无法把它直接赋给 `<img>` 的 `src`，必须自己拼接前缀。而
`image-captcha` spec 与 `CaptchaImageVO.imageBase64` 字段注释都写明该值应「供前端直接
渲染为 `<img>` 的 `src`」——实现与约定不一致。本次把前缀补上，让返回值真正可直接使用。

## What Changes

- `CaptchaImageRenderer#renderToBase64` 返回值改为带 `data:image/png;base64,` 前缀的
  完整 data URI（当前输出格式固定为 PNG，前缀中的 MIME 类型随 `IMAGE_FORMAT` 常量推导），
  并同步更新方法 Javadoc。
- `CaptchaImageVO.imageBase64` 的字段注释明确其为完整 data URI（可直接作为 `<img src>`）。
- `GET /api/captcha/image` 接口的 `@Operation`/字段 `@Schema` 文档同步说明返回的是完整
  data URI。
- 补充/调整 `ImageCaptchaServiceImplTest` 对 `imageBase64` 的断言：由「非空」加强为
  「以 `data:image/png;base64,` 开头」。
- **BREAKING**（对已按旧格式自行拼接前缀的调用方而言）：返回字符串格式变化。当前前端
  尚未接入该接口（`frontend/` 中无任何 `imageBase64`/`captcha` 引用），无实际调用方受影响。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `image-captcha`: 「图形验证码生成」需求中，返回的图片内容由「base64 编码的图片数据」
  明确为「带 `data:image/<type>;base64,` 前缀的完整 data URI，可直接作为 `<img>` 的
  `src`」。

## Impact

- 后端代码：`backend/src/main/java/cn/nihility/rbac/captcha/support/CaptchaImageRenderer.java`
  （返回值 + Javadoc）、`captcha/dto/CaptchaImageVO.java`（字段注释 + `@Schema`）、
  `captcha/controller/ImageCaptchaController.java`（`@Operation` 描述）。
- 测试：`backend/src/test/java/cn/nihility/rbac/captcha/service/impl/ImageCaptchaServiceImplTest.java`。
- 不新增依赖、不改数据库、不改配置项、不涉及前端改动。
