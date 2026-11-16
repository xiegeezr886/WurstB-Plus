# Moon 风格 Logo HUD

参考本地 `moon-rebase` 提交 `55cd694e898025531de94e61e169ba6eb221f9f3` 的 `WatermarkHudRenderer.renderLogo()`，新增独立 `LogoHUD` 模块，位于 HUD 分类。

- 使用 Moon LOGO 模式的无背景图片布局，默认位置 `(4, 4)`、宽度 68 GUI 单位，支持 HUD 编辑器拖动、缩放和开关。
- 图标复用开始菜单的用户提供图片，按原始 3:2 比例显示；没有复制 Moon 品牌图标或引入文字信息卡。
- 开始菜单 Logo 辉光由 HUD Settings → Logo glow 控制，现默认开启。Logo HUD 在自身设置里保留独立「Logo 辉光」复选框，默认关闭，只影响游戏内 HUD；两者互不联动。轮廓辉光复用预生成图片，无额外实时模糊。
- HUD 和 PlayerESP / MobESP 的「Moon 辉光」默认关闭。
- 旧文字 Logo 模块保留；新模块独立保存位置与启用状态。已有 HUD Settings → Logo glow 用户设置仍优先于新的默认值；可在 HUD Settings 中开启开始菜单 Logo 辉光。

源码位于 `hud2/mods/MoonLogoMod.java` 和 `gui/title/ClientLogo.java`。上游采用 Apache-2.0，许可随包保留在 `META-INF/licenses/moon-glow/`。

## 验证（2026-11-16）

完整构建和 141 项测试通过。Java 17 / Fabric 1.20.1 的隔离测试实例已验证开始菜单 Logo 绘制、Logo HUD 显示、位置移动、1×/1.5× 缩放及 HUD 独立辉光开关；HUD、PlayerESP、MobESP 的 Moon 辉光默认关闭。截图与日志位于 `.dsh-tmp/moon-logo-*` 和测试实例的 screenshots 目录。

运行中重新载入世界时，一次测试接口过早打开聊天触发了无玩家异常；等待进入世界后重试成功。未发现 Logo HUD 绘制异常。

开发客户端中分别切换 LogoHUD / Logo glow，截图确认 HUD 开关关闭时无轮廓、开启时显示白色轮廓光；随后将 HUD 开关恢复关闭。该设置只控制游戏内 LogoHUD。

2026-11-16：按后续要求将开始菜单 Logo 的全局「Logo glow」默认值改为开启。已有配置中显式保存的值不覆盖；LogoHUD 的独立开关仍默认关闭。
