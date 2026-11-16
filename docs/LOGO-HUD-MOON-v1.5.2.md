# Moon 风格 Logo HUD

参考本地 `moon-rebase` 提交 `55cd694e898025531de94e61e169ba6eb221f9f3` 的 `WatermarkHudRenderer.renderLogo()`，新增独立 `LogoHUD` 模块，位于 HUD 分类。

- 使用 Moon LOGO 模式的无背景图片布局，默认位置 `(4, 4)`、宽度 68 GUI 单位，支持 HUD 编辑器拖动、缩放和开关。
- 图标复用开始菜单的用户提供图片，按原始 3:2 比例显示；没有复制 Moon 品牌图标或引入文字信息卡。
- Logo HUD 在自身设置里有独立「Logo 辉光」复选框，默认关闭；只影响这个 HUD。开始菜单的辉光仍由 HUD 设置中的 Logo 辉光控制，两者互不联动。轮廓辉光复用预生成图片，无额外实时模糊。
- HUD 和 PlayerESP / MobESP 的「Moon 辉光」默认关闭。
- 旧文字 Logo 模块保留；新模块独立保存位置与启用状态。已有明确保存的辉光设置仍按配置读取；本地开发与测试配置已检查并关闭已开启项。

源码位于 `hud2/mods/MoonLogoMod.java` 和 `gui/title/ClientLogo.java`。上游采用 Apache-2.0，许可随包保留在 `META-INF/licenses/moon-glow/`。

## 验证（2026-11-16）

完整构建和 141 项测试通过。Java 17 / Fabric 1.20.1 的隔离测试实例已验证开始菜单不带辉光、Logo HUD 显示、位置移动、1×/1.5× 缩放及辉光开关；检查配置确认 HUD、PlayerESP、MobESP 和 Logo 辉光最终均为 false。截图与日志位于 `.dsh-tmp/moon-logo-*` 和测试实例的 screenshots 目录。

运行中重新载入世界时，一次测试接口过早打开聊天触发了无玩家异常；等待进入世界后重试成功。未发现 Logo HUD 绘制异常。

后续更新：开发客户端中分别切换 LogoHUD / Logo glow，截图确认关闭时无轮廓、开启时显示白色轮廓光；随后恢复关闭。验证截图保存在 `.dsh-tmp/killaura-qa-run/screenshots/screenshots/logohud-independent-off` 和 `logohud-independent-on`。
