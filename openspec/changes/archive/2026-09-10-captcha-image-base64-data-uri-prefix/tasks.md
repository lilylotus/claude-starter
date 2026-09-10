## 1. 渲染器返回值

- [x] 1.1 `CaptchaImageRenderer#toBase64` 返回值改为
      `"data:image/" + IMAGE_FORMAT + ";base64," + Base64.getEncoder().encodeToString(...)`，
      使 `renderToBase64` 输出完整 data URI。
- [x] 1.2 更新 `renderToBase64` 与 `toBase64` 的 Javadoc（含类注释里「输出为 base64 字符串」
      的表述），改为「输出带 `data:image/<type>;base64,` 前缀的完整 data URI」。

## 2. DTO 与接口文档

- [x] 2.1 `CaptchaImageVO.imageBase64` 字段注释改为「带 `data:image/png;base64,` 前缀的完整
      data URI，前端可直接作为 `<img>` 的 `src`」，并补充 `@Schema(description=..., example=...)`
      （类与 `captchaId` 字段一并补 `@Schema`）。
- [x] 2.2 `ImageCaptchaController#generate` 的 `@Operation` 描述补充「图片字段为完整 data URI」。

## 3. 测试

- [x] 3.1 `ImageCaptchaServiceImplTest#generate_shouldStoreDefaultLengthCharacterAnswer`：把对
      `vo.getImageBase64()` 的「非空」断言加强为 `startsWith("data:image/png;base64,")`，覆盖
      spec 新增 Scenario「返回的图片内容为可直接渲染的 data URI」。

## 4. 收尾

- [x] 4.1 `cd backend && ./gradlew test --tests "cn.nihility.rbac.captcha.*"` 确认验证码相关
      测试全部通过：BUILD SUCCESSFUL，`ImageCaptchaServiceImplTest` 全部用例通过。
- [x] 4.2 实现完成后按 `openspec-doc-sync` 约定对照真实 diff 校对本 change 的
      proposal/design/tasks 与实际实现一致：改动落在
      `CaptchaImageRenderer`（`toBase64` 拼前缀 + 3 处 Javadoc）、`CaptchaImageVO`
      （`@Schema` + 字段注释）、`ImageCaptchaController`（`@Operation` 描述）、
      `ImageCaptchaServiceImplTest`（1 处断言加强），与三份文档一致，无出入。
