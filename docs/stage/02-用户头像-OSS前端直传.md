# 用户头像：OSS 前端直传（可扩展存储设计）

> 规划日期：2026-01-17

## 目标与结论
- 数据库 `user.user_avatar` 只存「对象 Key（推荐）」或「可访问 URL」，不存二进制。
- 文件不经过后端：前端直传 OSS；后端只做「登录校验 + 生成上传凭证 + 更新头像字段」。
- 当前落地为阿里云 OSS；后续更换存储（S3/MinIO/COS…）只需新增 Provider 实现，不改业务接口。

## 整体架构（前端直传）
1. 浏览器向后端请求上传凭证：`POST /api/user/avatar/upload-token`
2. 后端生成一次性的表单上传参数（Post Policy），限制上传的 `key` 和大小范围
3. 浏览器携带 `formFields + file` 直传到 OSS
4. 上传成功后，浏览器把 `objectKey` 回传给后端：`POST /api/user/avatar`
5. 后端校验 `objectKey` 归属当前用户，并更新 `user.user_avatar`

核心原则：
- OSS 密钥永远不下发给前端（只返回 policy/signature 等临时参数）
- 后端只负责授权与记录，避免带宽浪费

## 对象 Key 规范（推荐）
`avatar/{userId}/{yyyy}/{MM}/{uuid}.{ext}`

示例：
`avatar/1024/2026/01/0c7a2eaa-8f7a-4b5e-acde.jpg`

好处：
- 用户隔离：天然按 `userId` 分目录
- 不覆盖：uuid 保证唯一
- 易做生命周期规则（可选）

## 数据库存储策略（推荐 Key）
推荐（更企业）：
- `user.user_avatar` 存 Key：`avatar/1024/2026/01/xxx.jpg`
- 展示时由系统统一拼接域名：`publicUrl = storage.domain + "/" + key`

本项目已实现：
- 写入时：若传入的是本系统 `storage.domain` 下的 URL，会自动抽取 Key 再入库
- 读取时：`UserVO.userAvatar` 会自动把 Key 转成可访问 URL 返回给前端

相关代码：
- Key/URL 互转：`src/main/java/fun/javierchen/jcaiagentbackend/storage/StorageUrlResolver.java`
- UserVO 输出：`src/main/java/fun/javierchen/jcaiagentbackend/service/impl/UserServiceImpl.java`

## 配置（application.yml）
在 `src/main/resources/application.yml` 新增了 `storage.*`：

- `storage.type`：`oss` / `none`
- `storage.domain`：对外访问域名（可为 OSS 域名或 CDN 域名）
- `storage.oss.endpoint`：OSS endpoint
- `storage.oss.bucket`：Bucket 名
- `storage.oss.access-key-id` / `storage.oss.access-key-secret`：仅后端使用
- `storage.oss.expire-seconds`：上传凭证有效期（秒）
- `storage.oss.max-size-mb`：头像大小限制（MB）

建议用环境变量注入（示例）：
- `JC_STORAGE_DOMAIN`
- `JC_OSS_ENDPOINT`
- `JC_OSS_BUCKET`
- `JC_OSS_ACCESS_KEY_ID`
- `JC_OSS_ACCESS_KEY_SECRET`

## 接口说明
### 1) 获取头像上传凭证
`POST /api/user/avatar/upload-token`

请求：
```json
{ "fileName": "avatar.png" }
```

响应（关键字段）：
- `uploadUrl`：表单上传地址（bucket host）
- `objectKey`：本次上传要使用的对象 Key（上传成功后用于更新头像）
- `formFields`：表单字段（policy/signature 等），前端直传需原样提交
- `fileUrl`：该对象的可访问 URL（用于上传成功后预览）

### 2) 更新当前用户头像
`POST /api/user/avatar`

请求：
```json
{ "avatarKey": "avatar/1024/2026/01/uuid.png" }
```

后端校验：
- 必须登录（session）
- `avatarKey` 必须属于当前用户目录（`avatar/{userId}/` 前缀），防越权

返回：
- `UserVO`（其中 `userAvatar` 已是可访问 URL）

## 前端对接流程（最短路径）
1. 用户登录后，携带 cookie 调用 `POST /api/user/avatar/upload-token`
2. 使用 `fetch + FormData` 直传 OSS：
   - `POST {uploadUrl}`
   - `FormData` 中填入所有 `formFields`
   - `FormData` 的 `file` 填入用户选择的文件
3. OSS 返回成功后，调用 `POST /api/user/avatar`，提交 `objectKey`
4. 用返回的 `UserVO.userAvatar` 更新界面头像

注意：
- 需要在 OSS Bucket 侧配置 CORS，允许你的前端域名对 `uploadUrl` 发起跨域 POST。

## 扩展性设计（更换存储怎么做）
当前存储抽象：
- `AvatarStorageService`：`src/main/java/fun/javierchen/jcaiagentbackend/storage/avatar/AvatarStorageService.java`
- OSS 实现：`src/main/java/fun/javierchen/jcaiagentbackend/storage/oss/OssAvatarStorageService.java`
- 通过 `storage.type` 选择实现（`@ConditionalOnProperty`）

后续更换为 S3/MinIO/COS：
1. 新增一个 `XxxAvatarStorageService implements AvatarStorageService`
2. 使用 `@ConditionalOnProperty(prefix="storage", name="type", havingValue="xxx")`
3. 在 `application.yml` 增加对应配置块
4. 业务 Controller/Service 不需要改

## 安全与成本（当前实现与可增强点）
已做：
- 上传凭证只允许上传到“本次生成的 objectKey”（Exact match）
- 上传大小限制（Policy 中 Content-Length-Range）
- 更新头像时做 Key 前缀校验，防止改他人头像

可选增强（更企业）：
- 使用 STS 临时凭证替代服务端长 AK/SK（接口形态不变，仅替换 OSS Provider 生成逻辑）
- 增加 Content-Type 白名单校验与更严格的 Policy 条件
- Bucket 私有 + 通过 CDN/签名 URL 访问（按你的业务需要选择）

## 常见问题：You have no right to access this object because of bucket acl
原因：
- OSS Bucket/对象为“私有读”，浏览器直接访问 `https://.../avatar/...` 会被拒绝。

两种解决方式（选一种即可）：
1) 公开读（简单）
   - 把 Bucket 设置为 `public-read`（或上传后把对象 ACL 设为 public-read）
   - 继续使用 `storage.domain + key` 方式访问
2) 私有读 + 后端签名 URL（更企业，推荐）
   - 保持 Bucket 私有
   - 配置 `storage.oss.sign-read-url: true`
   - 后端在返回 `UserVO.userAvatar` 时生成临时可访问的签名 URL（有效期 `storage.oss.read-url-expire-seconds`）

注意：
- 如果开启签名 URL，前端不要把 `userAvatar` 当作永久 URL 存起来；需要展示时调用 `GET /api/user/current` 获取最新值。

## 代码入口
- 接口层：`src/main/java/fun/javierchen/jcaiagentbackend/controller/UserAvatarController.java`
- 业务层：`src/main/java/fun/javierchen/jcaiagentbackend/service/UserAvatarService.java`
- 存储配置：`src/main/resources/application.yml`

## 给 Agent 的提示词（用于生成前端对接）
```
你是前端工程师，请对接后端的“用户头像 OSS 前端直传”能力，并提供一个可用的头像上传与更新 UI。
要求：
1) 基于 session cookie 登录态，请求都带上 credentials: 'include'。
2) 调用 POST /api/user/avatar/upload-token 获取 uploadUrl、objectKey、formFields、fileUrl。
3) 使用 fetch + FormData 直传到 uploadUrl：
   - 把 formFields 的每个字段都 append 到 FormData
   - 把 file 作为 FormData 的 file 字段上传
4) OSS 上传成功后，调用 POST /api/user/avatar，提交 { avatarKey: objectKey } 更新头像。
5) 头像展示优先使用后端返回的 UserVO.userAvatar（已是可访问 URL）。
6) 处理常见错误：未登录、上传超限（> 2MB）、跨域失败（提示需要 OSS 配 CORS）、更新越权。
7) UI：选择图片->本地预览->上传进度/Loading->成功后刷新当前用户信息（GET /api/user/current）。
8) Base URL 默认为 http://localhost:8525/api，可配置。
```

```aiignore
你是前端工程师，请对接后端的“用户头像 OSS 前端直传”能力，并提供一个可用的头像上传与更新 UI 在  [ProfilePage.tsx](src/features/auth/ProfilePage.tsx) 
要求：
1) 基于 session cookie 登录态，请求都带上 credentials: 'include'。
2) 调用 POST /api/user/avatar/upload-token 获取 uploadUrl、objectKey、formFields、fileUrl。
3) 使用 fetch + FormData 直传到 uploadUrl：
   - 把 formFields 的每个字段都 append 到 FormData
   - 把 file 作为 FormData 的 file 字段上传
4) OSS 上传成功后，调用 POST /api/user/avatar，提交 { avatarKey: objectKey } 更新头像。
5) 头像展示优先使用后端返回的 UserVO.userAvatar（已是可访问 URL）。
6) 处理常见错误：未登录、上传超限（> 2MB）、跨域失败（提示需要 OSS 配 CORS）、更新越权。
7) UI：选择图片->本地预览->上传进度/Loading->成功后刷新当前用户信息（GET /api/user/current）。
8) Base URL 默认为 http://localhost:8525/api，可配置。
```
