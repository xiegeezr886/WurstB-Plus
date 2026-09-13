# 网易云音乐数据层：Twilight Echo 参考规范与本工程差距分析

> 目标：为「把现有网易云音乐数据层对齐参考项目 Twilight Echo」提供数据层规范与差距分析。
>
> **本文只描述数据层（登录 / cookie / 请求 / 端点 / 音质 / 歌词取数 / 缓存）。**
> 按用户明确要求：**歌词界面与歌词渲染继续使用本工程现有的「类苹果歌词」（AMLL / Apple Music-like Lyrics）实现，不改用参考项目那套。** 因此第 3.4 节的歌词链路以本工程现状为准，目标是保证**逐字（YRC）歌词 + 翻译 + 音译**字段与解析的完整性；参考项目的 TTML 歌词与桌面歌词只作为「可选来源 / 不做」列入差距表（第 3.6 节），**不进入第一阶段必做清单**。
>
> 所有结论均标注来源文件与行号。凡代码中未读到者，一律在第 6 节「未读到 / 待确认」中显式列出，不做推测。

---

## 0. 依据素材（实际读到的文件）

### 0.1 参考项目 Twilight Echo（下载至 `D:\WurstB\_te_ref\`）

| 文件 | 大小 | 作用 |
| --- | --- | --- |
| `src/src/main/ncm/api.ts` | 12038 B / 344 行 | **核心**：本地 NCM 服务启停、`requestNcmApi`、官方登录窗口、cookie 采集、IPC |
| `resources__plugins__ncm-provider__index.mjs` | 89382 B / 2555 行 | **核心**：内置网易云 provider 插件，全部上游端点调用与缓存/回退逻辑 |
| `src__main__cache__ncmCache.ts` | 10533 B / 294 行 | 音频整文件磁盘缓存（LRU / 原子落盘 / 容量清理） |
| `src__main__ncm__cloudTransfer.ts` | 20469 B | 音乐云盘上传/下载（NOS 直传、进度 IPC） |
| `src__main__ncm__serverBinding.ts` | 938 B | 本地服务端口监听等待 |
| `src__main__plugins__providerRouting.ts` | 13481 B / 399 行 | provider 方法级超时与健康度统计 |
| `src__main__plugins__settingsStore.ts` | 4314 B / 125 行 | 插件设置持久化（cookie 落盘位置） |
| `src__main__security__secureStorage.ts` | 5180 B | 敏感值加密（Electron `safeStorage`）+ 日志脱敏 |
| `src__renderer__src__stores__useProviderStore.ts` | 7405 B / 246 行 | renderer 侧 provider 调用入口 |
| `src/src/renderer/src/components/LoginPage.vue` | 64922 B | 登录界面（QR / 官方窗口 / 扫码状态码） |
| `docs__PLUGIN_README.md` | 25785 B | 插件契约导读 |
| `scripts__verify-ncm-patch.cjs` | 577 B | npm 包补丁校验脚本 |
| `package.json` | 31577 B | 依赖版本、脚本 |
| `ROOT__README.md` | 13658 B | 可用性/合规声明 |

### 0.2 本工程（只读，未做任何修改）

`src/main/java/net/wurstclient/music/`、`src/main/java/net/wurstclient/clickgui2/music/`、`MusicPlayerHack.java`、`WurstClient.java`（仅取 `getWurstFolder()` 定义）。

---

## 1. 参考实现架构

### 1.1 调用栈分层

参考实现**不直连官方 API**，而是**在本地拉起一个 Node 版 NeteaseCloudMusicApiEnhanced 服务**，再由主进程以 `fetch` 访问 `http://127.0.0.1:<port>`。完整链路（每一跳均在代码中确认）：

```
Vue renderer
  └─ useProviderStore.ts: callProvider()                     [useProviderStore.ts:153-163]
       window.api.providers.call(providerId, method, args)
     └─ preload（暴露 window.api.providers.*）                [useProviderStore.ts:106,158]
          └─ main: plugin host + providerRouting
               ├─ 方法级超时：默认 15s / 中 30s / 慢 120s      [providerRouting.ts:9-53]
               └─ provider 插件（运行时沙箱）
                    resources/plugins/ncm-provider/index.mjs
                    context.twilight.internal.ncm                [index.mjs:54]
                    ├─ ncmApi.request(path, cookie, opts)        [index.mjs:439]
                    ├─ ncmApi.getCachedSong(songId)              [index.mjs:1768]
                    ├─ ncmApi.cacheSong(songId, url, fileName)   [index.mjs:1800]
                    └─ ncmApi.officialLogin()                    [index.mjs:1060]
                         └─ main: src/main/ncm/api.ts
                              ├─ ipcMain.handle('ncm:request')            [api.ts:263-266]
                              ├─ ipcMain.handle('ncm:getPort')            [api.ts:239-244]
                              ├─ ipcMain.handle('ncm:getCachedSong')      [api.ts:246-249]
                              └─ ipcMain.handle('ncm:cacheSong')          [api.ts:251-261]
                                   └─ requestNcmApi() → fetch             [api.ts:71-141]
                                        http://127.0.0.1:<port><path>
                                        └─ serveNcmApi（@neteasecloudmusicapienhanced/api）
                                             └─ 官方 music.163.com（加密在 npm 包内）
```

**关键点**：renderer 侧**不直接访问网易云**，也没有任何 `127.0.0.1` / `ncm:` 硬编码（对 `PlayerBar.vue` 检索 `ncm:|getPort|127\.0\.0\.1|localPort` **零命中**）；加密（weapi/eapi 签名）**完全由 npm 包承担**，参考代码里没有任何 AES/RSA 实现。

### 1.2 本地服务的启动与端口

来源：`api.ts:19-34, 305-344`；`serverBinding.ts`。

- 监听地址固定 `NCM_API_HOST = '127.0.0.1'`（`api.ts:19`）。
- **随机端口**：`NCM_API_EPHEMERAL_PORT = '0'`，注释明确说明「上游把数字 0 当缺省值（`options.port || 3000`），所以用字符串 `'0'` 请求由 OS 分配端口」（`api.ts:22-24`）。
- 启动参数：`serveNcmApi({ port: '0', host: '127.0.0.1', checkVersion: false })`（`api.ts:328-332`）——**关闭版本检查**。
- 启动前在 `os.tmpdir()/anonymous_token` 写一个空文件（`api.ts:323-326`），满足上游匿名 token 读取。
- `ensureNcmServer()` 单例：`runtime.ncmServer` / `runtime.ncmServerPromise` 去重，避免并发重复起服务（`api.ts:305-320`）。
- 起服失败时 `requestNcmApi` **不抛异常**，返回 `{ code: -1, message }`（`api.ts:85-89`）。
- 请求侧使用 undici 长连接池：`connections: 8, keepAliveTimeout: 60s, keepAliveMaxTimeout: 120s`（`api.ts:26-30`）。

### 1.3 Cookie：来源、透传与持久化

#### 三种获取方式

**(A) 扫码登录**（主路径，`authType: 'qr'`，`index.mjs:68`）

| 步骤 | 端点 | 读到的字段 |
| --- | --- | --- |
| 取 unikey | `/login/qr/key` | `code === 200 && data.data.unikey`（`index.mjs:1125-1128`） |
| 取二维码图 | `/login/qr/create?key=<k>&platform=web&qrimg=true&ua=pc` | `data.data.qrimg`，非 `data:` 前缀则补 `data:image/png;base64,`（`index.mjs:1130-1139`） |
| 轮询 | `/login/qr/check?key=<k>&ua=pc` | `code`；`502` 时降级重试 `&noCookie=true`（`index.mjs:1150-1156`）；`803 && data.cookie` → `saveCookie`（`index.mjs:1166-1168`） |

状态码映射（`ui.qrStatusCodes`，`index.mjs:70`）：`waiting 801 / scanned 802 / expired 800 / success 803`。

**登录窗口用的 UA 参数**：QR 路径统一追加 `ua=pc`（`withQrLoginParams`，`index.mjs:161-163`）；其余非 `/song/url*`、非 `/login/*` 路径追加 `ua=<PC UA 字符串>`（`shouldUsePcUa`，`index.mjs:165-168`；UA 常量 `index.mjs:6-7`）。

**(B) 官方网页登录窗口**（`openOfficialLogin`，`api.ts:143-235`）

- 用**内存分区** `twilight-ncm-login-<ts>`（刻意不带 `persist:` 前缀），注释明确「登录 Cookie 只存活于登录窗口会话期间，不落盘到 userData/Partitions」（`api.ts:154-158`）。
- `BrowserWindow` 参数：920×680、`nodeIntegration: false`、`contextIsolation: true`、`sandbox: true`（`api.ts:163-178`）。
- 加载 `https://music.163.com/#/login`（`api.ts:231`）。
- 采集：`ses.cookies.get({ domain: '.music.163.com' })`，白名单 `MUSIC_U / __csrf / NMTID / MUSIC_A`，以 `;` 拼接（`api.ts:143-151`）。
- 触发时机：`cookies.on('changed')` 每次变更即检查，含 `MUSIC_U=` 即完成（`api.ts:200-211`）。
- **超时 180s**（`NCM_OFFICIAL_LOGIN_TIMEOUT_MS`，`api.ts:20`）；窗口被关闭 → reject「已取消网易云官方登录」（`api.ts:212-218`）。
- 跳转白名单：仅 `^https?://([^/]+\.)?music\.163\.com/` 允许在窗口内导航，其余安全外链交给系统浏览器，否则 deny（`api.ts:219-228`）。

**(C) 账号密码类**：`/login/cellphone`（密码或验证码两种）、`/login`（邮箱），成功判定 `code === 200 && data.cookie.includes('MUSIC_U=')`（`assertSuccessfulLoginResponse`，`index.mjs:450-456`）。

#### 透传

- 插件侧 `getCookie()` 从插件设置读 key `'cookie'`（`COOKIE_KEY`，`index.mjs:5`；`index.mjs:366-378`）。
- `request()` 把 cookie 作为**普通参数**下传（`index.mjs:436-448`）→ `ncmApi.request(path, cookie, ...)` → IPC → `requestNcmApi(path, cookie)`。
- 主进程把 cookie 规范化后写入 `Cookie` 请求头：按 `;` 拆、trim、过滤空项、以 `; ` 重新拼接（`api.ts:96-103`）；长度上限 16 KB（`MAX_NCM_COOKIE_LENGTH`，`api.ts:32`）。
- 三种鉴权等级（`index.mjs:458-512`）：
  - `requestAuthed` — 无 cookie 直接抛「请先登录网易云音乐」；
  - `requestOptionalAuth` — 带上 cookie 但不要求（匿名可用的读接口）；
  - `requestAuthedRead` / `requestOptionalAuthRead` — 前者加瞬时错误重试。

#### 持久化

- 落盘位置：插件 `storagePath/settings.json`，key 为 `cookie`（`settingsStore.ts:45-47, 24-37`）。
- **加密存储**：`isSensitiveStorageKey('cookie')` 命中敏感键正则（含 `cookie|token|session|auth|password|secret|...`，`secureStorage.ts:21-25`），写入时经 `protectJsonValue` → Electron `safeStorage.encryptString()` → base64 信封 `{ciphertext: ...}`（`secureStorage.ts:39-45`）；读取时校验 `safeStorage.isEncryptionAvailable()` 后解密（`secureStorage.ts:66-68`）。**Windows 上即 DPAPI（用户级）**。
- 兼容迁移：读到明文敏感键会自动重写为加密信封（`settingsStore.ts:64-73`）。
- 登录态校验：`/login/status?timestamp=`，失败即 `saveCookie('')` 并清缓存（`index.mjs:1011-1040`）。
- `logout()` **不调用**远端 `/logout`，只清本地 cookie + `resetCaches()`（`index.mjs:1054-1057`）。

### 1.4 请求层：规范化、超时、重试、限流、幂等

| 机制 | 实现 | 来源 |
| --- | --- | --- |
| 路径规范化 | 必须 `/` 开头；禁 `//` 前缀；禁 `\`；用 `new URL(path, 'http://127.0.0.1')` 校验 origin 必须等于 `http://127.0.0.1`；长度上限 4096 | `api.ts:269-285, 31` |
| 请求超时 | **25s**（`NCM_API_REQUEST_TIMEOUT_MS`），`AbortController` + 定时器，并转发调用方 signal | `api.ts:21, 108-112, 137-140` |
| 登录超时 | **180s** | `api.ts:20` |
| 响应解析 | 先 `res.text()` 再 `JSON.parse`；解析失败抛「returned invalid JSON (HTTP <s>, <ct>)」 | `api.ts:119-125` |
| 失败语义 | 任何异常 → `console.error` + 返回 `{code:-1, message}`（**不抛出**）；插件侧再把 `code === -1` 转成异常 | `api.ts:126-136`；`index.mjs:444-446` |
| 时间戳注入 | 仅对 `/login`、`/login/`、`/playlist/create`、`/playlist/delete`、`/playlist/tracks`、`/like`、`/follow` 追加 `timestamp=Date.now()` | `api.ts:43-57, 90-94` |
| 幂等键 | 写入类请求可带 `X-Twilight-Idempotency-Key`，格式 `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$` | `api.ts:35, 104-107` |
| 全局令牌桶 | 突发容量 5、回灌 ~5 req/s，**所有上游请求统一出口**；含系统时钟回拨保护 | `index.mjs:386-434` |
| 读接口重试 | `requestAuthedRead` / `requestOptionalAuthRead`，默认 2 次（歌词 3 次），仅对瞬时错误重试，退避 `250ms × attempt`；瞬时判定正则含 `Unexpected/JSON/timeout/socket/network/fetch failed/502/503/504` 等 | `index.mjs:468-512` |
| 写入幂等记录 | 内存 + 持久化双层，TTL 5 min，内存 256 条 / 持久化 128 条；同 key 不同 payload 直接报错 | `index.mjs:21-25, 233-260` |
| 回退阶梯退避 | 音质回退步间 `min(500, 250×attempt)` ms | `index.mjs:1787-1789` |

### 1.5 错误与风控策略

- **风控识别**：正则 `/安全风险|设备环境异常|操作已拦截|高频|风控|ip 高频|IP 高频/i`（`index.mjs:175-177`）。命中后：
  - 文案分级：`安全风险|设备环境异常|操作已拦截` → 提示「请停止频繁重试，切换网络/设备或按官方提示 24 小时后再试」；其余 → 「请等待几分钟后再试」（`index.mjs:179-186`）。
  - **音质回退阶梯立即终止**（`break`），并且连灰色解锁接口也不再尝试，避免延长风控窗口（`index.mjs:1808-1816, 1833-1837`）。
- **登录态相关码**：`TRANSIENT_LOGIN_ERROR_CODES = {301, 502, 503, 460}`（`index.mjs:8`），QR 检查命中即返回 `retryAfterSeconds`：503/460 → 180s，其余 → 120s（`index.mjs:1158-1163`）。
- **码位文案**（`describeApiError`，`index.mjs:187-193`）：`301` 登录态失效/接口缓存了未登录结果；`400` 参数无效；`502` 二维码状态检查失败；`503` 高频/风控；`460` 网络环境受限。
- **歌词接口成功判定**：`assertLyricsEndpointSuccess` 仅在 `code` 存在且 ≠ 200 时抛错（`index.mjs:2045-2052`）。
- **播放地址成功判定**：优先用条目级 `streamItem.code`（存在且 ≠ 200 才判失败），顶层 `code` 只在条目级缺失时才参与判定；URL 需为 `http(s)://` 或以 `//` 开头（补 `https:`）（`index.mjs:1700-1730`）。
- 主进程日志一律经 `redactSensitiveText` 脱敏（含 `password|captcha|token|cookie|csrf|secret|api_key|...` 的 query/键值对替换，`secureStorage.ts:102-114`）。

### 1.6 缓存

**磁盘音频缓存**（`ncmCache.ts`，全文件级）

- 目录：`<musicCacheRoot>/ncm-cache`；`musicCacheRoot` 默认 `userData/music-cache`，可被设置 `musicCachePath` 覆盖（`ncmCache.ts:30-40`）。
- 命中：`getCachedNcmSong(songId)` 走**内存索引快照**（避免每次播放整目录 `readdirSync`），命中后 `utimesSync` 刷新 mtime 实现 **LRU**（`ncmCache.ts:68-107`）。
- 写入：先写 `<target>.<uuid>.part` → 完成后原子 `rename`（`ncmCache.ts:218-231`）。
- 超时 **45s**，从「实际启动下载」起算（`ncmCache.ts:199`）。
- **延迟 30s 启动**下载：绝大多数跳歌发生在前 30s，被切走的歌完全不产生流量（`ncmCache.ts:152-153, 196-198`）。
- 新解析到达即 abort 所有进行中的下载（含同 songId 的旧 URL 重解析），保证 ≤1 个活跃下载（`ncmCache.ts:186-191, 238-248`）。
- 清理：按容量上限 `NCM_CACHE_MAX_BYTES` + 孤儿 `.part`，失败只告警不阻断（`ncmCache.ts:121-147`）。
- 扩展名推断优先级：文件名后缀 → Content-Type（flac/wav/aac/m4a/ogg）→ URL 路径后缀 → 兜底 `.mp3`（`ncmCache.ts:42-66`）。
- 下载请求头带浏览器 UA + `Referer: https://music.163.com/`，注释说明「网易云 CDN 边缘会拒绝裸 Node fetch」（`ncmCache.ts:201-212`）。
- URL 安全校验：拒绝 `http(s)` 以外协议、带用户名/密码、`localhost`/`0.0.0.0`/`::1`、私网 IPv4（`ncmCache.ts:256-294`）。

**内存缓存**（`index.mjs`）

| 缓存 | 容量/TTL | 来源 |
| --- | --- | --- |
| 歌词 | 200 条 LRU（Map 重插） | `index.mjs:47, 1046-1052` |
| 播放流地址 | TTL **20 min**（CDN 签发地址会过期，过期强制重解析） | `index.mjs:17-20, 1753-1779` |
| 个人资料 | TTL 90s | `index.mjs:46, 520-531` |
| 喜欢歌曲 ID 列表 | TTL 60s，失败回退 15s | `index.mjs:39-40, 1436-1476` |
| 歌单曲目 / 歌单广场目录 | 无 TTL，`force` 或 `resetCaches()` 清 | `index.mjs:16, 32, 209-231` |
| 私人 FM 已见歌曲集合 | 会话级去重（目标 30 首，最多 10 批兜底） | `index.mjs:28-29, 41, 1858-1889` |
| 写入幂等记录 | TTL 5 min | `index.mjs:21-25` |

**云盘传输**（`cloudTransfer.ts`）：独立于播放缓存，走 NOS 直传/直下，最多 20 个文件、音频扩展名白名单、进度经 `NCM_CLOUD_TRANSFER_PROGRESS_CHANNEL` 上报 IPC。

---

## 2. 端点清单

来源全部为 `resources/plugins/ncm-provider/index.mjs`。**我对该文件做了一次路径令牌全量提取，得到 57 个形如 `/xxx` 的令牌，剔除 6 个非端点（`/api/playlist/list` 与 `/api/playlist/highquality/list` 仅出现在注释 `index.mjs:777`；`/i`、`/music`、`/Unexpected` 为 URL/正则片段；`/login/` 为 `startsWith` 判断片段 `index.mjs:167`）后，实际被调用的上游路径为 51 条。**

「需登录」列：✅ = 走 `requestAuthed`（无 cookie 直接失败）；⭕ = `requestOptionalAuth`（带 cookie 但匿名可用）；❌ = 显式不传 cookie。

### 2.1 登录与账号（7 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 1 | `/login/qr/key` | 取二维码 unikey | — | `code`, `data.unikey` | ❌ |
| 2 | `/login/qr/create` | 生成二维码图 | `key`, `platform=web`, `qrimg=true`, `ua=pc` | `code`, `data.qrimg` | ❌ |
| 3 | `/login/qr/check` | 轮询扫码状态 | `key`, `ua=pc`；`code==502` 时加 `noCookie=true` | `code`, `cookie`, `message`/`msg` | ❌（803 时返回 cookie） |
| 4 | `/login/status` | 校验登录态 / 取资料 | `timestamp` | `data.code` 或 `code`, `data.profile.{userId,nickname,avatarUrl,signature}` | ⭕（显式传 cookie） |
| 5 | `/login/cellphone` | 手机号登录 | `phone`, `countrycode`；+`password` 或 +`captcha` | `code`, `cookie`（须含 `MUSIC_U=`） | ❌（返回 cookie） |
| 6 | `/login` | 邮箱登录 | `email`, `password` | `code`, `cookie` | ❌（返回 cookie） |
| 7 | `/captcha/sent` | 发短信验证码 | `phone`, `ctcode`（默认 `86`，`^[0-9]{1,6}$`） | `code`, `message` | ❌ |

> 注：`logout` provider 方法**不调用**远端 `/logout`，仅清本地（`index.mjs:1054-1057`）。全量令牌提取也未发现 `/logout` 调用点。

### 2.2 用户（5 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 8 | `/user/detail` | 补全签名/关注数 | `uid` | `profile.signature`, `profile.follows`, `profile.followeds`（及 `userPoint.signature`、`data.profile.*` 深路径） | ✅ |
| 9 | `/user/playlist` | 用户歌单（含「我喜欢的音乐」） | `uid`, `limit=1000` | `playlist[]`：`id`, `name`, `coverImgUrl`, `trackCount`, `userId`/`creator.userId`, 喜欢歌单标记；`playlists` / `data.playlist` / `data.playlists` 深路径 | ✅ |
| 10 | `/user/followeds` | 粉丝列表 | `uid`, `limit`, `offset` | 列表项 | ✅ |
| 11 | `/user/record` | 听歌排行 | `uid`, `type` | 列表项 | ✅ |
| 12 | `/record/recent/song` | 最近播放 | `limit` | `resourceId`, `playTime`, `resourceType`, `data`（歌曲字段） | ✅ |

### 2.3 歌曲与播放地址（4 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 13 | `/song/url/v1` | **官方播放地址（主路径，按音质等级）** | `id`, `level`（阶梯，见下）, `encodeType=flac` | `data[0].url`, `data[0].code`, `data[0].msg`, 顶层 `code`；音频元信息 `data[0].{type\|encodeType\|format, br\|bitrate, sr\|sampleRate, size}` | ✅ |
| 14 | `/song/url` | 兼容回退（经典码率参数） | `id`, `br` ∈ `999000` / `320000` / `128000` | 同上 | ✅ |
| 15 | `/song/url/match` | 灰色歌曲解锁 | `id` | `code`, `data`（字符串 URL）或 `data.url` 或 `proxyUrl` | ✅ |
| 16 | `/song/detail` | 批量歌曲详情 | `ids`（逗号分隔） | `songs[]` / `data.songs[]` / `result.songs[]` 等深路径；`playlist.tracks`、`hotSongs` 兜底 | ⭕ |

**音质回退阶梯**（`NCM_PLAYBACK_QUALITY_FALLBACKS`，`index.mjs:9-15`）：

```
auto     → hires → lossless → exhigh → standard
hires    → hires → lossless → exhigh → standard
lossless → lossless → exhigh → standard
exhigh   → exhigh → standard
standard → standard
```

请求顺序 = `level` 阶梯（`/song/url/v1`，`encodeType=flac`）**拼接**经典码率阶梯（`/song/url` 的 `999000 → 320000 → 128000`）（`index.mjs:1681-1694`）。全部失败后再试一次 `/song/url/match`。未知/非法 `quality` 一律归一到 `auto`（`index.mjs:1673-1675`）。

**批量详情分片**：`/song/detail` 每片 100 个 id，片间并发 3（`DETAIL_REQUEST_CONCURRENCY`），单片失败且长度 > 25 时二分递归，否则跳过该片（`index.mjs:48, 1202-1234`）。

### 2.4 歌词（2 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 17 | `/lyric/new` | **新歌词（主路径，含逐字）** | `id` | `yrc.lyric`, `lrc.lyric`, `tlyric.lyric`；`code` | ⭕（重试 3 次） |
| 18 | `/lyric` | 旧歌词回退 | `id` | `lrc.lyric`, `tlyric.lyric` | ⭕（重试 2 次） |

**⚠️ 关键规范事实**：参考实现取字段的统一助手是 `extractLyricText(data, key) => data[key]?.lyric || data.data?.[key]?.lyric || null`（`index.mjs:2022-2024`），而 `getLyrics` 只调用了 `extractLyricText(data, 'yrc' | 'lrc' | 'tlyric')` 三个键（`index.mjs:2085-2087`）。**参考实现完全没有读 `romalrc`（音译）与 `ytlrc`（逐字翻译）** —— 我对其余已下载的参考文件也做了 `romalrc|ytlrc` 全量检索，**零命中**。参考的实现取向是「`lyrics = yrc || lrc`，`wordLyrics = yrc || null`，`translatedLyrics = tlyric`」，并在 `/lyric/new` 失败时整体退回旧接口（此时 `wordLyrics = null`）。
>
> 也就是说：**在「逐字 + 翻译 + 音译」这个目标上，本工程现有实现（读 `romalrc`）比参考实现更完整**；对齐时我们应保留自己的字段集，只借鉴参考的接口选择（`/lyric/new` 优先 + 旧接口回退）与缓存策略。

### 2.5 搜索（1 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 19 | `/cloudsearch` | 歌曲 / 歌单 / 歌手搜索 | `keywords`, `type` ∈ `1`(歌曲)/`1000`(歌单)/`100`(歌手), `limit`, `offset` | `result.songs` / `result.playlists` / `result.artists`；`result.songCount` / `playlistCount` / `artistCount`；`data.result.*` 深路径 | 歌曲 ⭕；歌单/歌手 ✅ |

### 2.6 歌单（10 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 20 | `/playlist/detail` | 歌单详情（含 id 列表） | `id` | `playlist.trackIds`（喜欢列表 id 序列） | ✅ |
| 21 | `/playlist/track/all` | 分页取歌单曲目 | `id`, `limit`（≤1000）, `offset` | 歌曲列表深路径 | ⭕ |
| 22 | `/playlist/catlist` | 歌单分类目录 | — | `categories`（id→名）, `sub[]`：`category`, `name`, `hot` | ⭕（重试 3 次） |
| 23 | `/playlist/hot` | 热门标签 | — | `tags[].name`（`data.tags` 深路径） | ⭕ |
| 24 | `/top/playlist` | 分类歌单广场 | `cat`（默认「全部」）, `order` ∈ `hot`/`new`, `limit`(1–100, 默认 30), `offset` | 歌单列表；`total`, `more` | ⭕（重试 3 次） |
| 25 | `/top/playlist/highquality` | 精品歌单 | `cat`, `limit`, `before`（可选） | 歌单列表 | ⭕ |
| 26 | `/playlist/create` | 新建歌单 | `name`, `privacy`, `timestamp` | 新歌单 id | ✅ |
| 27 | `/playlist/delete` | 删除歌单 | `id`, `timestamp` | 结果码 | ✅ |
| 28 | `/playlist/subscribe` | 取消收藏歌单 | `t=2`, `id`, `timestamp` | 结果码 | ✅ |
| 29 | `/playlist/tracks` | 增/删歌单曲目 | `op` ∈ `add`/`del`, `pid`, `tracks`, `timestamp` | 结果码 | ✅ |

**分页上限**：单歌单最多 `MAX_PLAYLIST_TRACKS = 5000` 首、每页 `PLAYLIST_TRACK_PAGE_SIZE = 1000`；优先走 `/playlist/track/all`，失败则退回 `/playlist/detail`（`index.mjs:26-27, 1236-1259`）。
**喜欢歌单 id 获取双路**：先 `/user/playlist` 找喜欢歌单 → `/playlist/detail` 取 `trackIds`；失败退回 `/likelist?uid=`（`index.mjs:1440-1476`）。

### 2.7 推荐与私人 FM（5 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 30 | `/recommend/songs` | **每日推荐歌曲** | — | `data.dailySongs` 或 `dailySongs`；兜底歌曲列表深路径 | ✅ |
| 31 | `/recommend/resource` | 每日推荐歌单 | — | `recommend[]`：`id`, `name`, `picUrl`/`coverImgUrl`, `trackCount`；`data` 兜底 | ✅ |
| 32 | `/personal/fm/mode` | 私人漫游（新接口） | `mode=DEFAULT`, `limit=30` | `data[]` / `result[]` / 歌曲列表深路径 | ✅ |
| 33 | `/personal_fm` | 经典私人 FM（回退） | — | 同上 | ✅ |
| 34 | `/playmode/intelligence/list` | 心动模式 / 智能播放 | `id`(songId), `pid`(playlistId), `sid`(起播 id，默认同 `id`), `count`（默认 20，上限 50） | 条目为推荐包装 `{id, alg, recommended, songInfo}`，歌曲元数据在 `item.songInfo`；`data.data` / `data.songs` / `data.songList` 等深路径 | ✅ |

**私人漫游策略**：先试 `/personal/fm/mode`，失败则循环 `/personal_fm` 最多 10 批，按 songId 去重，凑满 30 首（`index.mjs:28-29, 1871-1891`）。
**私人雷达**：无独立端点，固定歌单 id `3136952023` 走 `fetchPlaylistTracks`（`index.mjs:30, 1893-1895`）。

### 2.8 喜欢 / 关注 / 订阅（5 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 35 | `/like` | 红心 / 取消红心 | `id`(songId), `like`(`true`/`false`), `timestamp` | 结果码 | ✅ |
| 36 | `/likelist` | 喜欢歌曲 id 全量列表 | `uid` | `ids[]` | ✅ |
| 37 | `/follow` | 关注 / 取关用户 | `id`, `t` ∈ `1`/`0`（`timestamp` 由主进程注入） | 结果码 | ✅ |
| 38 | `/artist/sub` | 订阅 / 取关歌手 | `id`, `t` ∈ `1`/`0` | 结果码 | ✅ |
| 39 | `/artist/sublist` | 订阅歌手列表 | `limit`, `offset` | 列表项 | ✅ |

### 2.9 歌手与专辑（8 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 40 | `/artist/songs` | 歌手热门歌曲（主） | `id`, `order=hot`, `limit=100`, `offset` | 歌曲列表深路径 | ✅ |
| 41 | `/artist/top/song` | 歌手热门（回退 1） | `id` | 歌曲列表深路径 | ✅ |
| 42 | `/artists` | 歌手详情+热门（回退 2） | `id` | `artist.hotSongs` / `hotSongs` | ✅ |
| 43 | `/artist/album` | 歌手专辑 | `id`, `limit=100`, `offset` | `hotAlbums[]` / `albums[]` | ✅ |
| 44 | `/artist/desc` | 歌手简介 | `id` | `briefDesc`, `data.briefDesc`, `introduction[0].txt` | ✅ |
| 45 | `/artist/detail` | 歌手详情 | `id` | 详情字段 | ✅ |
| 46 | `/artist/detail/dynamic` | 关注状态 | `id` | `followed` / `isSub` | ✅ |
| 47 | `/album` | 专辑详情 + 曲目 | `id` | 歌曲列表深路径、`album.*` | ✅ |

### 2.10 云盘（4 条）

| # | 路径 | 用途 | 关键参数 | 返回中被用到的字段 | 需登录 |
| --- | --- | --- | --- | --- | --- |
| 48 | `/user/cloud` | 云盘歌曲分页 | `limit`(≤200, 默认 50), `offset` | `data[]`, `count`, `hasMore`；条目 `songId`/`id`, `simpleSong`/`song`, `fileName`, `fileSize`, `bitrate`, `addTime` | ✅ |
| 49 | `/cloud/upload/token` | 申请上传令牌 | `md5`, `fileSize`, `filename`, `bitrate` | 上传令牌 | ✅ |
| 50 | `/cloud/upload/complete` | 完成上传 | 令牌相关参数 | 结果码 | ✅ |
| 51 | `/song/cloud/download` | 云盘歌曲下载地址 | `id` | 响应中的 http(s) URL | ✅ |

### 2.11 常见猜测但**未在参考代码中出现**的端点

以下路径在本工程/其他项目里常见，但在我读到的参考文件中**没有**调用点：`/song/lyric`（参考使用 `/lyric` 与 `/lyric/new`）、`/login/qrcode/unikey`、`/login/qrcode/client/login`（参考使用 `/login/qr/key`、`/login/qr/check`，属 APIEnhanced 风格）、`/song/enhance/player/url`、`/song/enhance/download/url`、`/scrobble`、`/daily_signin`、`/msg/*`、`/comment/*`、`/mv/*`、`/dj/*`、`/satellite/*`、`/batch`。

---

## 3. 本工程现状

全部来源：`src/main/java/net/wurstclient/music/NeteaseCloudApi.java`（697 行）、`NeteaseMusicPlayer.java`（606 行）、`MusicAccountManager.java`（236 行）、`LyricParser.java`（445 行）、`clickgui2/music/*`。

### 3.1 现有端点（13 条，全部直连 `https://music.163.com`）

`ORIGIN = "https://music.163.com"`（`NeteaseCloudApi.java:41`）。**没有本地服务、没有第三方 API 库**，加密只在登录一处自己实现。

| # | 路径 | 本工程方法 | 行号 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | `/api/search/get/web?csrf_token=&s=&type=1&offset=0&total=true&limit=` | `search()` | 80-83 | limit 夹取 1–50 |
| 2 | `/api/discovery/new/songs?areaId=0&limit=` | `topNewSongs()` | 133-135 | **「新歌速递」，不是每日推荐** |
| 3 | `/api/playlist/list?cat=全部&order=hot&offset=0&total=true&limit=` | `recommendedPlaylists()` | 152-154 | limit 夹取 1–20 |
| 4 | `/api/v3/playlist/detail?id=&n=100000&s=0` | `playlistSongs()` | 168-169 | 取 `playlist.trackIds` |
| 5 | `/api/song/detail/?ids=[...]` | `playlistSongs()` | 184-185 | ids 为 URL 编码的 JSON 数组 |
| 6 | `/api/user/playlist?uid=&limit=1&timestamp=` | `likedSongs()` | 199-201 | 只取第 0 个歌单（喜欢歌单） |
| 7 | `/api/song/enhance/player/url?id=&ids=[..]&br=320000` | `resolve()` | 214-216 | **固定 320 kbps** |
| 8 | `/api/song/lyric/v1?id=&lv=-1&kv=-1&tv=-1&rv=-1&yv=-1` | `lyrics()` | 234-235 | 主歌词接口 |
| 9 | `/api/song/lyric?id=&lv=-1&kv=-1&tv=-1` | `lyrics()` 回退 | 239-240 | 注释：旧接口不返回 `romalrc` |
| 10 | `/api/sms/captcha/sent`（POST form: `cellphone`, `ctcode`） | `sendCaptcha()` | 255-256 | |
| 11 | `/weapi/login/cellphone`（POST，weapi 加密） | `loginWithCaptcha()` | 273 | AES-CBC 双层 + RSA `encSecKey` |
| 12 | `/api/login/qrcode/unikey?type=1&timestamp=` | `beginQrLogin()` | 315-316 | 读 `unikey`，要求 `code == 200` |
| 13 | `/api/login/qrcode/client/login?type=1&key=&timestamp=` | `checkQrLogin()` | 328-330 | 码 `800/801/802/803` 映射到 `QrStatus` |
| 14 | `/api/nuser/account/get?timestamp=` | `refreshUserProfile()` | 362-363 | 读 `profile.{userId,nickname,avatarUrl}` |

（表中 14 行含 1 条回退，实际不同路径 13 条。）

### 3.2 登录方式

三段式，全部在 `NeteaseCloudApi` + `clickgui2/music/LoginPage.java` 内：

| 方式 | 实现 | 证据 |
| --- | --- | --- |
| 扫码登录 | `/api/login/qrcode/unikey` 取 key → 二维码内容为站点地址 `https://music.163.com/login?codekey=<key>` → 轮询 `/api/login/qrcode/client/login` | `NeteaseCloudApi.java:312-355`；`LoginPage.java:617, 648` |
| 手机号 + 短信验证码 | `/api/sms/captcha/sent` → `/weapi/login/cellphone` | `NeteaseCloudApi.java:248-290`；`LoginPage.java:666-693` |
| Cookie 导入 | `loginWithCookie()` 过滤后校验 `MUSIC_U`/`MUSIC_A_T` | `NeteaseCloudApi.java:292-310`；`LoginPage.java:558` |

**没有**：官方网页登录窗口、手机号+密码、邮箱+密码。

**登录态采集与判定**：`acceptLoginCookie()` 优先读响应体 `cookie` 字段，为空则拼 `Set-Cookie` 头（`NeteaseCloudApi.java:516-527`）；`refreshUserProfile()` 以 `profile.userId > 0` 为成功判据（`362-374`）。

**Cookie 白名单**：`SESSION_COOKIE = "(?:^|[;,]\\s*)(MUSIC_U|MUSIC_A_T|MUSIC_R_T|__csrf)=([^;,\\s]+)"`（`52-53`）——比参考多了 `MUSIC_A_T`/`MUSIC_R_T`，少了 `NMTID`/`MUSIC_A`。

**Cookie 持久化：明文。** `wurst/netease_cookie.txt`（`WurstClient.getWurstFolder()` = `.minecraft/wurst`，见 `WurstClient.java` 的 `createWurstFolder()`），`loadCookie()` / `saveCookie()` 直接 `Files.readString` / `writeString`（`542-568`），**没有任何加密或权限处理**。
另有 `MusicAccountManager` 把 QQ/酷狗 cookie 明文写入 `wurst/music-accounts.json`（`MusicAccountManager.java:183-211`）；注意它对网易云**显式拒绝**导入，提示「请使用网易云登录面板」（`27-31, 164-166`）。

**请求头**：`User-Agent: Mozilla/5.0 WurstBPlus/1.6 NeteaseMusic`、`Referer: https://music.163.com/`、`Origin: https://music.163.com`（`499-507`）。
**超时**：连接 10s（`58`），单请求 30s（`502`）。
**Cookie 容器**：`CookieManager(null, ACCEPT_ALL)` 挂在 `HttpClient` 上（`55-60`），但鉴权请求实际是手工加 `Cookie` 头（`504-505`）。

### 3.3 音频 URL 与音质参数

- 端点：`/api/song/enhance/player/url`（**不是** `/song/url/v1`），参数 `id`、`ids`（JSON 数组 URL 编码）、`br=320000`（`NeteaseCloudApi.java:212-216`）。
- 固定 320 kbps，**没有** `level` 概念、没有 Hi-Res / 无损选项、没有码率回退阶梯、没有 `/song/url/match` 灰色解锁、没有 20 分钟流地址缓存。
- 空 URL → 抛 `IOException("该歌曲受版权或会员限制，当前账号无法播放")`（`222-223`）；`data` 空 → `IOException("网易云没有返回播放地址")`（`218-219`）。
- 返回结构 `SongResource(URI uri, long size, String type)`（`677-678`）：读 `data[0].url`、`size`、`type`。
- 落盘：`NeteaseMusicPlayer.load()` 取 `resource.type()` 作为扩展名（非法字符剔除，默认 `mp3`），目标 `wurst/music-cache/<songId>.<ext>`，文件不存在或大小为 0 才下载（`190-195`）。
- 下载：先写 `<name>.part` → 校验 `size == 0 || (resource.size() > 0 && size < resource.size()/2)` 判为不完整 → 原子 `ATOMIC_MOVE`（失败退回普通 `move`）（`NeteaseCloudApi.java:398-427`）。
- **下载无超时控制、无并发取消、无延迟启动、无容量清理**（见 3.5）。
- 播放器：`com.goxr3plus.streamplayer.StreamPlayer`，**全文件下载后本地播放**（`NeteaseMusicPlayer.java:194-207`）；`seekTo` 按字节比例 seek（`370-395`）。

### 3.4 歌词来源与解析（按范围修正：保留本工程 AMLL 链路）

**取数**（`NeteaseCloudApi.java:228-246`）：

1. 主接口 `/api/song/lyric/v1?id=<id>&lv=-1&kv=-1&tv=-1&rv=-1&yv=-1`；
2. 抛 `IOException` 时回退 `/api/song/lyric?id=<id>&lv=-1&kv=-1&tv=-1`（注释明确「旧接口不返回 `romalrc`，音译行会缺失，其余照常」）；
3. 组装：`LyricParser.parseBest(string(object(root,"yrc"),"lyric"), string(object(root,"lrc"),"lyric"), string(object(root,"tlyric"),"lyric"), string(object(root,"romalrc"),"lyric"))`。

**逐字歌词所需的返回字段（本工程实际读取，以代码为准）**：

| 字段路径 | 含义 | 本工程是否读取 | 证据 |
| --- | --- | --- | --- |
| `yrc.lyric` | **逐字歌词（YRC）**，行头 `[start,duration]`、词 `(start,duration,0)词` | ✅ 主来源 | `NeteaseCloudApi.java:242`；`LyricParser.java:150-196` |
| `lrc.lyric` | 标准 LRC（YRC 缺失时回退） | ✅ | `NeteaseCloudApi.java:243` |
| `tlyric.lyric` | 翻译歌词 | ✅ | `NeteaseCloudApi.java:244` |
| `romalrc.lyric` | **音译歌词** | ✅（参考项目**未**读） | `NeteaseCloudApi.java:245` |
| `ytlrc.lyric` | 逐字翻译（逐字 LRC 的翻译） | ❌ **未读取** | 全库检索无命中 |
| `klyric.lyric` | 卡拉OK 歌词（逐字） | ❌ 未读取 | 全库检索无命中 |

> 请求参数中的 `yv=-1`（逐字）与 `rv=-1`（音译）是「返回该字段」的开关，与上表的 `yrc` / `romalrc` 对应。**`ytlrc` 需要什么参数我没有在代码或参考中读到**（`yv` 是否同时返回 `ytlrc` 无法确认）——列为待确认项（第 6 节）。

**解析**（`LyricParser.java`，目标是喂给 `net.wurstclient.music.apple.AppleLyricPlayer`，即 AMLL 风格）：

- 三条输入通道，优先级：`parseYrc(yrc)` → 空则 `parse(lrc)`（`50-51`）。
- YRC 支持两种形态：JSON（对象/数组，键尝试 `lyric`/`lrc`/`yrc`/`data`，行字段 `startTime|start|t|time`，词字段 `words|c|chars` + `text|c|word` + `duration|d|dur` + `endTime|end`）与文本形式（`parseYrcJson` `71-143`；`parseYrcText` `150-196`）。
- 文本 YRC 正则：行 `^\[(\d+),(\d+)\]`（`22-23`）、词 `^(.*?)\((\d+),(\d+),0\)`（`24-25`）；首尾为括号的行标记为背景人声（`background`），并剥离括号（`185-222`）。
- 增强 LRC 逐字回退：`<(\d+),(\d+)(?:,\d+)?>`（`26-27`，`parseEnhancedBody` `321-360`）。
- 翻译 / 音译按**时间戳就近匹配**，容差 `SUB_LINE_MATCH_MS = 800`，游标单向推进（`224-275`）。
- 输出模型：`LyricLine(timeMs, text, words, endMs, translation, background)` + `LyricWord(text, startMs, endMs)`（`LyricLine.java` / `LyricWord.java`）。
- 二分查找当前行 `findCurrentIndex`（`384-402`）。
- 播放器侧每曲可调歌词偏移 ±10s，范围夹取（`NeteaseMusicPlayer.java:443-468`）。
- 歌词加载失败**静默置空**，不影响播放（`241-259`）。

**AMLL 渲染层**：`src/main/java/net/wurstclient/music/apple/`（14 个文件，`AppleLyricPlayer.java` 53591 B、`AppleLayout.java`、`AppleTimeline.java`、`AmlOptimize.java`、`AmlMask.java`、`Spring.java`、`LyricWordSplitter.java` 等）。**本次不改动。**

### 3.5 缓存与存储

| 项 | 本工程 | 证据 |
| --- | --- | --- |
| 音频缓存目录 | `wurst/music-cache/<songId>.<ext>` | `NeteaseMusicPlayer.java:261-264, 193` |
| 缓存判定 | 仅 `Files.isRegularFile && Files.size > 0` | `194` |
| 容量上限 / LRU | **无**（全库检索 `prune|evict|MAX_CACHE` 零命中） | — |
| 原子落盘 | 有（`.part` + `ATOMIC_MOVE`） | `NeteaseCloudApi.java:398-427` |
| 下载超时 | **无** | — |
| 并发下载取消 | 有 requestId 世代号丢弃**结果**，但下载仍继续跑完 | `NeteaseMusicPlayer.java:178, 196-197` |
| 歌词缓存 | **无**（每次播放都重新请求） | `210-211` |
| 播放地址缓存 | **无** | — |
| 用户资料缓存 | 无 TTL，仅内存字段 | `63, 357-374` |
| 图片缓存 | `NeteaseImageCache`（clickgui2 层） | `NeteaseImageCache.java` |
| 请求限流 | **无** | — |
| 重试 | **无** | — |
| 幂等键 | **无** | — |

### 3.6 差距对比表

「参考有 / 我们有 / 我们缺」。标注 **[P1]** = 第一阶段最小可用范围内。

| 能力 | 参考有 | 我们有 | 我们缺 |
| --- | --- | --- | --- |
| 数据通道 | 本地 Node 服务（`serveNcmApi`，随机端口）+ REST | 直连 `music.163.com` 网页接口 | **可配置的外部/本地服务地址**；上游变动隔离层 |
| 扫码登录 | `/login/qr/key`+`/qr/create`+`/qr/check`，含 `qrimg` | `/api/login/qrcode/unikey` + `/client/login`（返回 `codekey` 链接，需自行生成二维码） | 服务端直出 `qrimg`；`502/460/503` 分级重试与 `retryAfterSeconds` **[P1]** |
| 官方网页登录窗口 | 有（内存分区、180s 超时、cookie 白名单采集） | 无 | 整块能力（Electron `BrowserWindow` 在 MC 内无直接等价物，需改设计） |
| 手机号+密码 / 邮箱登录 | 有 | 无（仅短信验证码） | 可选 |
| Cookie 加密存储 | `safeStorage`（Windows DPAPI）+ 敏感键自动加密迁移 | **明文** `wurst/netease_cookie.txt` | **静态加密**；复用本工程已有的 `Encryption`（alt 用 `alts.encrypted_json`） |
| Cookie 字段集 | `MUSIC_U/__csrf/NMTID/MUSIC_A` | `MUSIC_U/MUSIC_A_T/MUSIC_R_T/__csrf` | 字段集不一致，Cookie 互导时需归一 |
| 登录态校验 | `/login/status`，失败即清 cookie | `/api/nuser/account/get`，失败即清 | 等价（无需改） |
| 搜索 | `/cloudsearch`（歌曲/歌单/歌手三型） | `/api/search/get/web`（**仅歌曲**） | **歌单搜索、歌手搜索** |
| 歌曲详情 | `/song/detail?ids=`（分片 100、并发 3、失败二分） | `/api/song/detail/?ids=`（一次全量、无分片） | 分片/并发/降级 **[P1]** |
| 播放地址 | `/song/url/v1?level&encodeType=flac` 5 档阶梯 + `/song/url` 3 档码率回退 + `/song/url/match` 灰色解锁 + 20min URL 缓存 | `/api/song/enhance/player/url?br=320000` 单档 | **音质等级选择、码率回退阶梯、灰色解锁、URL TTL 缓存**；`br` 固定 320k **[P1]** |
| 每日推荐 | `/recommend/songs` | **无**（`topNewSongs` 是「新歌速递」） | **整块能力** **[P1]** |
| 每日推荐歌单 | `/recommend/resource` | `/api/playlist/list?cat=全部&order=hot`（热门榜，非个性化） | 个性化推荐 |
| 私人 FM | `/personal/fm/mode` + `/personal_fm` 回退 + 去重凑 30 首 | **无** | **整块能力** |
| 心动模式 | `/playmode/intelligence/list` | 无 | 可选 |
| 我喜欢 | `/user/playlist` → `/playlist/detail` 取 trackIds，失败退 `/likelist` | `/api/user/playlist?limit=1` → 取第 0 个歌单 → `/api/v3/playlist/detail` | **`/likelist` 兜底** **[P1]** |
| 红心/取消红心 | `/like?id&like` | **无**（只能读不能写） | **写入能力** **[P1]** |
| 歌单读取 | `/playlist/track/all` 分页（≤5000）+ `/playlist/detail` 兜底 | `/api/v3/playlist/detail?n=100000` 一次拉全部 trackIds | 分页与兜底（当前一次拉全量，大歌单开销高） |
| 歌单写入 | 创建/删除/收藏/增删曲目（`/playlist/create|delete|subscribe|tracks`） | 无 | 可选（写入需幂等键） |
| 歌单广场 / 分类 | `/top/playlist`、`/top/playlist/highquality`、`/playlist/catlist`、`/playlist/hot` | `/api/playlist/list`（单分类「全部」） | 分类/标签/精品 |
| 歌词接口 | `/lyric/new` 优先（重试 3）+ `/lyric` 回退（重试 2）+ 200 条 LRU | `/api/song/lyric/v1`（`rv=-1&yv=-1`）+ `/api/song/lyric` 回退 | **歌词缓存**；接口选择可对齐 |
| 歌词字段 | `yrc`/`lrc`/`tlyric`（**无 `romalrc`**） | `yrc`/`lrc`/`tlyric`/**`romalrc`** | `ytlrc`（逐字翻译）、`klyric`；**本工程字段集已优于参考** |
| 逐字 + 翻译 + 音译 渲染 | `getLyrics` 三字段 + 参考自有渲染 | YRC 逐字 + 增强 LRC + 800ms 容差配对 + **AMLL 渲染** | 无损（按范围修正保留本工程实现） |
| TTML 歌词 | 参考支持（**未在本轮下载文件中读到解析实现**） | 无 | **列为可选来源 / 不做**，不进第一阶段 |
| 桌面歌词 | 参考有（README 截图） | 无 | **不做** |
| 头像/关注数/签名 | `/user/detail` 补全 | 仅 `nickname`/`avatarUrl` | 签名、关注数 |
| 歌手/专辑页 | 8 个端点 | 无 | 可选 |
| 听歌排行 / 最近播放 | `/user/record`、`/record/recent/song` | 无 | 可选 |
| 云盘 | 4 个端点 + NOS 直传/直下 + 进度上报 | 无 | 可选 |
| 磁盘缓存治理 | 容量上限 + mtime LRU + 孤儿 `.part` 清理 + 30s 延迟启动 + 新解析抢占取消 + 45s 超时 | `.part` + 原子 rename | **容量上限、LRU、延迟启动、抢占取消、下载超时** |
| 前端请求限流 | 令牌桶 5 突发 / 5 rps | 无 | **限流**（直接关系风控/封号风险） |
| 读接口重试 | 瞬时错误退避重试 2–3 次 | 无 | **重试** |
| 风控识别与熔断 | 正则识别 + 终止回退阶梯 + 分级文案 | 无（仅透传上游 msg） | **风控识别与熔断** |
| 写入幂等 | `X-Twilight-Idempotency-Key` + 结果持久化（TTL 5min） | 无 | 幂等键（写入能力落地时必需） |
| 日志脱敏 | `redactSensitiveText` | 无（当前未打印 cookie，风险较低） | 脱敏工具（防御性） |
| provider 健康度 | 方法级成功率/最近错误，30s 轮询 | 无 | 可选（本工程无插件体系） |
| 版权/会员提示 | 透传上游 `msg`，回退全失败返回 `null` | `IOException("该歌曲受版权或会员限制…")` | 等价（我们的文案更明确） |

---

## 4. 对齐方案

### 4.1 方案 A：支持用户自建 / 外部 NeteaseCloudMusicApiEnhanced 服务

**做法**：本工程**不内置** Node 服务，只做一个 REST 客户端 + cookie 管理。

- 新增设置项：服务基址（默认 `http://127.0.0.1:3000`，允许自定义 host/port），并提供连通性探测（`/login/status`）与「未启动服务」的引导文案。
- 新增 `NeteaseApiEnhancedClient`，实现与 `MusicPlatform` 平行的扩展接口（见 4.4），通过 `HttpClient` 访问 `<base><path>`，`Cookie` 头由本工程统一管理。
- 保留现有 `NeteaseCloudApi`（直连）作为**离线后备**：设置里可选「数据源 = 本地服务 / 官方直连（旧）」，默认优先本地服务、不可用时回退直连。
- 端点、参数、音质阶梯、字段读取全部照第 2 节移植；歌词按第 3.4 节的本工程字段集（保留 `romalrc`）。

**优点**
1. **最贴近参考**：第 2 节的 51 条路径可以逐条对照移植，语义、参数、错误码都有现成实现做参照，猜测成本最低。
2. **加密与上游变动被隔离在服务侧**：`/song/url/v1`、`/recommend/songs`、`/personal_fm`、`/likelist`、`/cloudsearch`、`/song/url/match` 这些**我们完全没有、且网页 weapi 路径拿不到**的能力直接可用，不必自己实现 eapi 签名。
3. **工作量小**：纯 Java REST 客户端 + 设置 + cookie 复用，无加密、无设备指纹、无 Node 打包。
4. **法律/ToS 面更干净**：我们**不分发**API 实现，只作为客户端连用户自己起的服务；参考项目正是这个形态。
5. **可增量**：第一阶段只接 6 个能力，后续按需扩端点，不会因为上游改动而改 Java 加密代码。

**缺点 / 代价**
1. **依赖用户自启服务**：必须引导安装 Node + `@neteasecloudmusicapienhanced/api`，玩家门槛明显升高；需要把「服务未运行」做成可诊断、可自愈的状态。
2. 多一层进程与 IPC（http 回环），首次请求有冷启动延迟；参考用随机端口 + 长连接池缓解，我们固定端口则需要处理端口占用。
3. 打包无法「开箱即用」。
4. 本工程是 Minecraft 客户端，**无法照搬参考的 Electron 官方登录窗口**；官方窗口登录能力需另想办法（见 4.4 备注）。

### 4.2 方案 B：在 Java 内直连官方接口并自行实现加密/签名

**做法**：不依赖外部服务，自己实现 weapi/eapi 的加密与签名，逐端点补齐 `/song/url/v1`、`/recommend/songs`、`/personal_fm`、`/likelist`、`/cloudsearch`、`/playlist/track/all`、`/like` 等。

**已知基础**：本工程**已经**实现了 weapi 加密（`NeteaseCloudApi.java:458-481`：`AES/CBC/PKCS5Padding` 双层 + `WEAPI_NONCE = "0CoJUm6Qyw8W8jud"` + `WEAPI_IV = "0102030405060708"` + RSA `modPow(0x10001, WEAPI_MODULUS)` 生成 `encSecKey`），并用它登录成功（`/weapi/login/cellphone`）。

**优点**
1. 无外部依赖，开箱即用。
2. 无跨进程开销，冷启动更快。
3. 完全可控，便于做请求限流/缓存/幂等。

**缺点 / 风险**
1. **易失效**：`/api/song/enhance/player/url`、`/api/search/get/web`、`/api/discovery/new/songs` 都属网页端历史接口，官方随时可能下线或改结构；更关键的是 `/recommend/songs`、`/personal_fm`、`/likelist`、`/cloudsearch` 这几个能力**在网页 weapi 路径上不存在或需 eapi 设备签名**，等于要我们从零复刻 APIEnhanced 的核心工作。
2. **工作量大**：eapi 需要 `AES-ECB` 摘要 + `MD5` 校验 + 浏览器/客户端设备指纹（`os`/`appver`/`deviceId`/`requestId` 等）会话协商，且签名细节不在本轮已下载素材中，**我没有读到任何 eapi 实现可参照**。
3. **风控与封号风险更高**：自研签名的指纹更容易被判为异常设备，参考代码里大量风控文案（`安全风险`/`设备环境异常`/`操作已拦截`/`高频`，`index.mjs:175-177`）正说明这条路的现实摩擦。
4. **法律与 ToS 风险**：自行实现绕过客户端签名的私有接口、自建灰色歌曲解锁（`/song/url/match`）在服务条款与版权层面都更敏感；同时我们无法像方案 A 那样把实现责任留在用户自建的上游服务里。
5. 加密常量与算法一旦被官方调整，需要我们自己跟进，维护成本长期存在。

### 4.3 推荐：**方案 A**（并把现有直连实现降级为后备）

**理由（3–5 句）**

1. 第一阶段要的 6 个能力里，**每日推荐、私人 FM、我喜欢（likelist）、红心写入、音质等级、歌单搜索** 有一半以上在现有 weapi 路径上拿不到，方案 B 等于要重写 APIEnhanced 的核心；而方案 A 只要照第 2 节的 51 条路径做 REST 客户端即可。
2. 参考项目已经把「本地服务 + cookie 透传 + 音质阶梯 + 风控熔断」这套语义完整跑通，方案 A 能直接复用它的规范，把不确定性和返工降到最低。
3. 方案 A 的工作量集中在设置页、HTTP 客户端、错误映射和 UI 引导上，**不需要碰加密与设备指纹**，风险面最小。
4. 方案 A 还能顺带解决「上游接口变更」这个长期痛点：变动被关在用户自己升级的 npm 包里，我们不跟随。
5. 代价（用户需自启服务）可以通过「保留现有直连通道作为后备 + 明确的引导与连通性诊断」显著缓解；而方案 B 的代价（易失效、工作量大、ToS 与风控风险）无法用工程手段消除。

**补充立场**：不建议在第一阶段实现方案 B 的 eapi 签名；也不建议把 Node 服务**打包进本工程分发**（那会把方案 A 的合规优势抵消掉）。

### 4.4 兼容层接口草案（仅示意，本任务不创建 Java 文件）

设置项（沿用本工程既有设置体系）：

```
music.dataSource        = LOCAL_SERVICE | DIRECT_LEGACY   // 默认 LOCAL_SERVICE
music.localServiceUrl   = http://127.0.0.1:3000            // 用户可改
music.playbackQuality   = auto | hires | lossless | exhigh | standard
music.cacheMaxBytes     = <默认值，见 P3>
```

扩展平台接口（在现有 `MusicPlatform` 之外新增，避免破坏既有实现）：

```java
public interface NeteaseEnhancedApi
{
    // P0：连通性
    boolean probe() throws IOException, InterruptedException;

    // P1：最小可用
    QrSession beginQr() throws IOException, InterruptedException;          // /login/qr/key + /login/qr/create
    QrState  pollQr(String key) throws IOException, InterruptedException;  // /login/qr/check → 800/801/802/803
    LoginState checkLogin() throws IOException, InterruptedException;       // /login/status
    void logoutLocal();

    List<NeteaseSong> searchSongs(String kw, int limit, int offset);        // /cloudsearch?type=1
    List<NeteasePlaylist> searchPlaylists(String kw, int limit, int offset); // /cloudsearch?type=1000

    SongResource resolve(NeteaseSong song, String quality);                 // /song/url/v1 阶梯 + /song/url 回退
    List<LyricLine> lyrics(long songId);                                    // /lyric/new → yrc/lrc/tlyric/romalrc

    List<NeteaseSong> dailyRecommendations();                               // /recommend/songs
    List<NeteaseSong> likedSongs(int offset, int limit);                    // /user/playlist → /playlist/detail，失败退 /likelist
    boolean setLiked(long songId, boolean liked);                           // /like（写入，需幂等键）
}
```

**关键实现约定（必须照抄参考语义，否则行为会不一致）**

- 请求统一出口，出口处做：路径规范化 → cookie 注入 → 限流令牌 → 超时 → 风控检测。
- 失败不抛给 UI 原始异常，统一映射为「可读中文 + 是否可重试 + 建议等待秒数」。
- 播放地址解析顺序固定为：`/song/url/v1` 按 `level` 阶梯 → `/song/url` 按 `br` 阶梯 → 全失败才试 `/song/url/match`；**风控命中立即 break，不再尝试任何后续阶梯**。
- 读接口瞬时错误退避重试（`250ms × attempt`），写接口**不自动重试**，改用幂等键。
- 磁盘缓存：30s 延迟启动 + 新解析抢占取消 + 45s 超时 + 容量上限 LRU 清理。

**关于官方登录窗口的备注**：参考的官方窗口依赖 Electron `BrowserWindow` + `session.fromPartition`。本工程是 Forge/Mojmap 客户端，**在 MC 内没有等价物**（`Screen` 不是浏览器）。可选替代：(a) 不做，仅保留扫码登录 + cookie 导入（推荐，第一阶段）；(b) 用系统默认浏览器打开登录页并让用户手工回贴 cookie（体验差）；(c) 内嵌 JavaFX WebView（引入新依赖与渲染线程风险，不建议）。**本轮不深挖该方案，列为待决策。**

### 4.5 分阶段落地清单

#### P0 — 基础管道（最小可用前置，约 0.5–1 天）

- [ ] 设置项：`music.dataSource`、`music.localServiceUrl`、`music.playbackQuality`。
- [ ] `NeteaseServiceClient`：单出口 HTTP GET、路径规范化（禁 `//` 前缀与 `\`）、cookie 注入、25s 超时、`{code:-1,message}` 风格失败封装。
- [ ] 连通性探测 `/login/status` + 设置页状态显示（未启动 / 版本不符 / 正常）。
- [ ] 全局限流令牌桶（突发 5、回灌 5 req/s）+ 读接口瞬时错误退避重试（2 次，`250ms × attempt`）。
- [ ] Cookie 存储抽象：`NeteaseCloudApi` 与本地服务通道**共用同一份 cookie**，仍写 `netease_cookie.txt`（加密留到 P3）。

#### P1 — 最小可用：扫码登录 + 搜索 + 歌曲 URL + 歌词 + 每日推荐 + 我喜欢（约 3–5 天）

- [ ] **扫码登录**：`/login/qr/key` → `/login/qr/create?platform=web&qrimg=true&ua=pc`（直接用服务端返回的 `qrimg`，省掉本工程自绘二维码）→ 轮询 `/login/qr/check?key=&ua=pc`；`502` 降级 `&noCookie=true`；`800/801/802/803` 映射到现有 `QrStatus`；`301/502/503/460` 给出等待秒数。
- [ ] **登录态**：`/login/status` 校验并刷新 `NeteaseUserProfile`。
- [ ] **搜索**：`/cloudsearch?keywords=&type=1&limit=&offset=`（歌曲，匿名可用）；顺带接 `type=1000` 歌单搜索（`SearchPage` 可分页签）。
- [ ] **歌曲详情**：`/song/detail?ids=`（分片 100、并发 3、失败对 >25 的片二分递归）。
- [ ] **歌曲 URL 与音质**：`/song/url/v1?id=&level=&encodeType=flac` 阶梯 + `/song/url?id=&br=999000|320000|128000` 回退；设置项驱动 `level`；解析 `data[0].url/code/size/type/br/sr`；保留现有 `SongResource` 契约以便复用下载与播放。
- [ ] **歌词（按范围修正，保留 AMLL 链路）**：`/lyric/new?id=`（重试 3）→ 失败退 `/lyric?id=`（重试 2）；**读 `yrc.lyric` / `lrc.lyric` / `tlyric.lyric` / `romalrc.lyric` 四个字段**（参考只读前三个，我们**不照抄这个缺失**，音译必须保留），继续交给现有 `LyricParser.parseBest(...)` 与 `AppleLyricPlayer`。
- [ ] **每日推荐**：`/recommend/songs` → `data.dailySongs`（`HomePage` 新增「每日推荐」区，与现有「新歌速递」并存）。
- [ ] **我喜欢**：`/user/playlist?uid=&limit=1000` 定位喜欢歌单 → `/playlist/detail?id=` 取 `trackIds`，失败退 `/likelist?uid=`；分页 → `/song/detail`。
- [ ] **红心写入**：`/like?id=&like=`，带幂等键，失败不自动重试，成功后本地状态回填。
- [ ] 歌词内存 LRU 缓存（200 条）+ 播放地址 TTL 缓存（20 分钟）。

#### P2 — 补齐发现与歌单（约 2–3 天）

- [ ] 私人 FM：`/personal/fm/mode?mode=DEFAULT&limit=30` → 失败退 `/personal_fm` 循环（最多 10 批、songId 去重、凑 30 首）。
- [ ] 每日推荐歌单：`/recommend/resource`。
- [ ] 歌单广场与分类：`/playlist/catlist`、`/playlist/hot`、`/top/playlist`、`/top/playlist/highquality`。
- [ ] 歌单曲目分页化：改走 `/playlist/track/all?id=&limit=1000&offset=`（上限 5000），失败退现有 `/playlist/detail`。
- [ ] 用户资料补全：`/user/detail`（签名、关注/粉丝数）。

#### P3 — 健壮性与安全（约 2–3 天）

- [ ] **Cookie 静态加密**：复用本工程已有的 `Encryption` / `Encryption.chooseEncryptionFolder()`（`WurstClient` 已用它保护 `alts.encrypted_json`），把 `netease_cookie.txt` 迁移为加密文件；`music-accounts.json` 一并处理。
- [ ] 磁盘缓存治理：容量上限 + mtime LRU + 孤儿 `.part` 清理；下载延迟启动 30s、新解析抢占取消、45s 超时。
- [ ] 风控熔断：识别 `安全风险|设备环境异常|操作已拦截|高频|风控` 文案，终止回退阶梯并给出分级提示。
- [ ] 日志脱敏工具（cookie/token/csrf 不出现在日志）。
- [ ] 昵称/头像等字段的宽松解析（沿用现有 `string()` / `number()` / `objects()` 防御式访问器，`NeteaseCloudApi.java:611-672`）。

#### P4 — 可选 / 明确不做

| 项 | 决策 |
| --- | --- |
| 歌单写入（创建/删除/收藏/增删曲目） | 可选，需先落幂等键 |
| 歌手页 / 专辑页（8 端点） | 可选 |
| 听歌排行、最近播放 | 可选 |
| 云盘上传下载（NOS 直传） | 可选，工作量大 |
| 心动模式 `/playmode/intelligence/list` | 可选 |
| 订阅歌手 `/artist/sub|sublist`、关注用户 `/follow` | 可选 |
| 手机号+密码 / 邮箱登录 | 可选 |
| 官方网页登录窗口 | **待决策**（MC 内无 BrowserWindow 等价物，见 4.4 备注）；第一阶段不做 |
| **TTML 歌词** | **不做**（按范围修正：歌词继续用本工程 AMLL 实现；TTML 仅登记为可选来源） |
| **桌面歌词** | **不做**（按范围修正） |
| 方案 B 的 eapi 自研签名 | **不做**（除非日后方案 A 的可用性被判定为不可接受） |

---

## 5. 风险与合规

### 5.1 账号凭据存储

| 项 | 现状 | 风险 | 建议 |
| --- | --- | --- | --- |
| 网易云 cookie | **明文** `wurst/netease_cookie.txt`（`NeteaseCloudApi.java:565-568`） | 同机任意进程/任何能读 `.minecraft` 的程序可直接盗用会话；误传日志/截图即泄漏 | P3 用本工程既有 `Encryption` 加密；文件权限收紧；启动时校验并迁移旧明文 |
| QQ/酷狗 cookie | 明文 `wurst/music-accounts.json`（`MusicAccountManager.java:183-211`） | 同上 | 同上（该文件已存 `cookie` 明文字段） |
| 参考做法对照 | 插件 `settings.json` + Electron `safeStorage`（Windows→DPAPI），敏感键自动加密迁移（`settingsStore.ts:64-73, 101-113`；`secureStorage.ts:21-45`） | — | 对齐目标：**静态加密 + 敏感键自动迁移** |
| 日志 | 本工程当前**未打印** cookie（我检索了 `music/` 下所有 `System.out`/日志调用，未发现 cookie 输出） | 低 | 补 `redactSensitiveText` 等价工具做防御 |
| 登录窗口会话 | 参考用**内存分区**（不写 `userData/Partitions`），登录后即销毁（`api.ts:154-158`） | — | 本工程无该能力；若日后引入内嵌浏览器，必须照此处理，禁止持久化分区 |

**结论：本工程目前是「明文存储」，与参考的「OS 级加密存储」存在实质差距，且这是可在本工程内独立解决、无需外部依赖的一项，建议优先级不低于功能对齐。**

### 5.2 服务条款与版权

参考 README 明确声明（`ROOT__README.md:192`，原文）：

> 内置网易云音乐与第三方音源均依赖对应平台服务，**可用内容受账号权限、所在地区和平台策略影响**。扩展由各自作者维护，可先检查登录状态并更新扩展；仍有问题时，向对应扩展作者反馈。**使用账号和内容时，请遵守对应服务条款。**

同文件 `:242` 声明项目采用 Apache License 2.0，且「相关商标归其权利人所有，Twilight Echo 与这些服务不存在官方隶属或背书关系」。

落地注意点：

1. **内容可用性无保证**：`/song/url/v1` 与 `/song/url` 全部回退仍可能拿不到 URL（VIP / 版权 / 地区限制）。参考的做法是逐级回退后返回 `null` 并把上游 `msg` 透传给 UI（`index.mjs:1700-1710, 1833-1838`）；本工程当前抛「该歌曲受版权或会员限制，当前账号无法播放」（`NeteaseCloudApi.java:222-223`），文案已足够明确，**应保留这一语义**，并在 UI 上区分「需 VIP / 该地区不可用 / 需登录」三类。
2. **灰色歌曲解锁（`/song/url/match`）风险显著更高**：它把受限曲目路由到**非官方来源**。参考把它放在官方回退全部失败之后（`index.mjs:1816-1831`），且风控命中时连它也不试。**建议第一阶段不启用**；若启用，必须默认关闭、由用户显式开启，并在 UI 明示来源非官方。
3. **本地整文件缓存等于下载受版权保护的音频**：本工程当前把整首下载到 `wurst/music-cache/` 且**无容量上限**（3.5 节），既是版权面的灰色地带，也是磁盘占用问题。建议：定位为「播放缓存」而非「下载库」、加上容量上限与 LRU（P3），必要时在文档/UI 声明用户自行承担使用责任。
4. **不要随本工程分发 API 实现**：方案 A 的合规优势正来自「用户自己安装并运行上游服务」。打包 Node 服务进发行版会把风险拉回方案 B 的水平。
5. **不要自研 eapi 签名绕过客户端校验**（方案 B）：既触碰绕过技术措施的问题，也更容易触发账号风控。
6. **服务条款遵守**：参考 README 的表述可直接沿用——账号与内容的使用由用户自行遵守对应平台条款；本工程与网易云无隶属或背书关系。

### 5.3 技术与风控风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| **风控触发 / 账号受限** | 本工程**没有任何限流与重试退避**（3.5 节），连续操作会高频打上游；参考专门做了令牌桶与风控熔断，并给出「24 小时后再试」文案（`index.mjs:175-193`） | P0 令牌桶 + P3 风控熔断 |
| 上游接口漂移 | 现有直连的 `/api/song/enhance/player/url`、`/api/search/get/web`、`/api/discovery/new/songs` 均为网页历史接口 | 方案 A 把漂移隔离到服务侧；保留直连后备但默认不优先 |
| 本地服务未启动 / 端口占用 | 依赖用户环境，失败面大 | P0 连通性探测与明确引导；支持自定义 host/port；参考用随机端口（我们固定端口需处理占用） |
| 无缓存导致流量与延迟 | 每次播放都重新解析 URL + 重新下载（3.5） | P1 URL TTL 缓存 + 歌词 LRU；P3 磁盘缓存治理 |
| 端口/SSRF 面 | 若允许自定义服务地址，需防「把请求打到任意内网地址」 | 限制为 loopback 或显式白名单；参考的缓存层已示范私网地址拒绝逻辑（`ncmCache.ts:256-294`），可借鉴 |
| 歌词字段不完整 | `ytlrc`（逐字翻译）、`klyric` 未读取 | 第 6 节列为待实测确认 |

---

## 6. 未读到 / 待确认

严格区分「我读到了什么」与「我不知道什么」：

**未读到的能力（本轮素材中无实现可参照）**

1. **TTML 歌词的解析实现**：参考 README 截图与描述提到歌词能力，但我下载的文件里**没有** TTML 解析器；`index.mjs` 只做 `yrc/lrc/tlyric` 字段提取。TTML 相关结论仅来自任务描述，**未经代码确认**。
2. **桌面歌词实现**：同上，未读到任何实现文件（README 截图存在，代码未下载）。
3. **eapi 加密/签名实现**：参考项目把加密完全交给 npm 包，**代码中没有任何 eapi 签名可参照**；本工程也只在 `NeteaseCloudApi` 里实现了 weapi（登录用）。因此方案 B 的「自行实现加密/签名」具体算法细节，**我无法从本轮素材给出**。
4. **`plugin.json` manifest 原文**：仅从 `docs__PLUGIN_README.md` 读到它是 `com.twilightecho.provider.ncm`、位于 `resources/plugins/ncm-provider/`，**manifest 文件本身未下载**。
5. **preload 脚本本体**：`window.api.providers.*` 的暴露实现（preload 文件）**未下载**；该跳是从 `useProviderStore.ts:106, 158` 的调用点推断的。
6. **`context.twilight.internal.ncm` 的完整契约**：仅从 `index.mjs` 的调用点确认存在 `request(path, cookie, {signal, idempotencyKey})`、`getCachedSong(songId)`、`cacheSong(songId, url, fileName)`、`officialLogin()` 四个成员；**其类型定义文件未下载**。
7. **`ncmCachePrune.ts` / `ncmCacheIndex.ts` 的具体算法**：只从 `ncmCache.ts` 的 import 与调用确认存在容量上限 `NCM_CACHE_MAX_BYTES`、LRU 计划 `planNcmCachePrune`、索引构建 `buildNcmCacheIndexFromNames`；**上限的具体数值与淘汰公式未读到**。
8. **`cloudTransferHelpers.ts`**：云盘传输的 URL 校验/令牌细节未下载，仅有 `cloudTransfer.ts` 的 import 列表与常量。
9. **`providerWriteIdempotency.ts`（shared 层）**：已下载但本轮未逐行核对；幂等语义主要依据 `index.mjs:21-25, 233-260` 的实现读取。
10. **`verify:ncm-patch` 对应的补丁本体**：只读到校验脚本 `verify-ncm-patch.cjs`（断言 `login_qr_check` 在请求失败时返回 `{status:200, body:{code:-1, msg}}`），**实际 patch 文件（如 `patches/*.patch`）未下载**，因此不知道它还改了哪些行为。

**待实测确认**

11. **`/lyric/new` 是否返回 `romalrc`（音译）与 `ytlrc`（逐字翻译）**：参考**未读** `romalrc`，所以无法从参考确认该字段在新接口下的存在性；本工程读的是官方 `/api/song/lyric/v1?...&rv=-1&yv=-1`，同样**不能推出** `/lyric/new` 的返回集。**需要实际起一个本地服务打一次请求确认**（这是第一阶段歌词部分唯一的实测前置）。
12. **`/lyric/new` 与 `/api/song/lyric/v1` 的字段差异**：`extractLyricText` 兼容 `data[key].lyric` 与 `data.data[key].lyric` 两种嵌套，说明上游存在两种包装；本工程只解了顶层。是否需要在客户端同时兼容两层，待实测。
13. **版本号一致性**：`package.json` 中 `"version": "1.2.0"`（`package.json:3`），而下载用的 jsDelivr 标签是 `1.2.1`（GitHub 标签 `v1.2.1`）。我按 1.2.1 内容分析，**两者是否存在数据层差异未核对**。依赖版本确认为 `"@neteasecloudmusicapienhanced/api": "^4.35.1"`（`package.json:110`），与本工程无对应依赖。
14. **`/song/url/v1` 各 `level` 的实际可用性与返回码差异**：参考的阶梯表是它的经验值，**我没有实际调用验证**每个 level 对普通账号的返回。
15. **本工程 `NeteaseCloudApi` 各端点是否仍全部有效**（例如 `/api/v3/playlist/detail?n=100000`、`/api/discovery/new/songs`）：本轮为纯静态阅读，**未做任何实际请求验证**。
16. **本工程 `clickgui2/music/*` 的完整 UI 能力边界**：我只核对了各页面调用播放器方法的调用点（`HomePage` / `SearchPage` / `LikedPage` / `LoginPage` / `MusicContext`），**未逐行读完 `MusicRegion.java`、`PlayerDetailOverlay.java`、`BottomPlayerBar.java`**，因此 UI 侧改造工作量的估算为粗估。

**环境限制说明**：本次任务按要求**未运行任何 gradle 命令**，也未修改/新建任何 `.java` 文件；仅新建了本 markdown。所有对参考项目的网络访问均为 `Invoke-WebRequest` 下载静态文件（jsDelivr），未执行任何网易云接口请求。

---

## 附录 A：证据索引

| 结论 | 来源 |
| --- | --- |
| 本地服务随机端口、`checkVersion:false`、`anonymous_token` | `api.ts:19-34, 323-344` |
| 25s 请求超时 / 180s 登录超时 | `api.ts:20-21, 112, 207-209` |
| 路径规范化与长度上限 | `api.ts:31-32, 269-285` |
| 时间戳注入路径白名单 | `api.ts:43-57, 90-94` |
| 幂等键头与格式 | `api.ts:35, 104-107` |
| 官方登录窗口（内存分区、cookie 白名单、跳转白名单） | `api.ts:143-235` |
| 失败的 `{code:-1}` 语义 | `api.ts:126-141` |
| 长连接池参数 | `api.ts:26-30` |
| provider 注册与能力声明 | `index.mjs:60-137` |
| 令牌桶（5 突发 / 5 rps） | `index.mjs:386-434` |
| 鉴权三等级与读重试 | `index.mjs:458-512` |
| 风控识别与文案分级 | `index.mjs:175-193` |
| QR 登录三端点与状态码 | `index.mjs:1125-1173`；`ui.qrStatusCodes` `index.mjs:70` |
| 帐密/邮箱登录与成功判定 | `index.mjs:450-456, 1088-1123` |
| 登录态校验与缓存 | `index.mjs:1011-1044` |
| 音质阶梯表与请求路径拼接 | `index.mjs:9-15, 1673-1698` |
| 播放地址回退 + 风控熔断 + 缓存 | `index.mjs:1753-1839` |
| 歌词接口选择与字段提取 | `index.mjs:2022-2099` |
| 每日推荐 / 推荐歌单 / 私人漫游 | `index.mjs:1841-1850, 1871-1915` |
| 心动模式参数 | `index.mjs:1377-1406` |
| 喜欢列表双路 + `/likelist` | `index.mjs:1440-1499` |
| 歌曲详情分片（100/并发 3/二分） | `index.mjs:48, 1202-1234` |
| 歌单分页上限（5000/1000） | `index.mjs:26-27, 1236-1259` |
| 云盘端与字段 | `index.mjs:1501-1663` |
| 用户/歌手/专辑/记录端点 | `index.mjs:982-994, 2161-2303, 2518-2549` |
| 磁盘缓存（30s 延迟 / 45s 超时 / 抢占 / LRU / 清理 / 扩展名 / UA） | `ncmCache.ts:42-66, 68-107, 121-147, 152-254` |
| 私网地址拒绝 | `ncmCache.ts:256-294` |
| cookie 落盘位置与加密 | `settingsStore.ts:24-47, 101-113`；`secureStorage.ts:21-45, 66-68` |
| 日志脱敏正则 | `secureStorage.ts:102-114` |
| provider 方法级超时 | `providerRouting.ts:9-53` |
| renderer 调用入口 | `useProviderStore.ts:106, 153-163` |
| 登录界面调用点 | `LoginPage.vue:347, 475` |
| npm 依赖版本 | `package.json:110` |
| 补丁校验语义 | `verify-ncm-patch.cjs:4-17` |
| 可用性/条款声明 | `ROOT__README.md:192, 242` |
| 本工程端点全集 | `NeteaseCloudApi.java:80-240, 255-363` |
| 本工程音质固定 320k | `NeteaseCloudApi.java:212-226` |
| 本工程歌词字段（含 `romalrc`） | `NeteaseCloudApi.java:228-246` |
| 本工程 YRC 解析与翻译/音译配对 | `LyricParser.java:20-27, 47-55, 150-275` |
| 本工程 cookie 明文路径 | `NeteaseCloudApi.java:542-568` |
| 本工程缓存目录与无治理 | `NeteaseMusicPlayer.java:190-195, 261-264` |
| 本工程 alt 加密基础设施（可复用） | `WurstClient.java` 的 `alts.encrypted_json` + `Encryption.chooseEncryptionFolder()` |
