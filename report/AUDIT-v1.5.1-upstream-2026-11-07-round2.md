# v1.5.1 第二轮潜藏 bug 审查

2026-11-07。新增确认 **8 类问题：1 类 P1、7 类 P2**。本轮没有修改产品源码；仅生成审计报告、范围清单与隔离验证材料。不重复第一轮的六类问题；第一轮报告已有其他工作的修复记录。

## 范围与上游依据

- 审查当前 `main`（`d7d4eba9`）及现有工作区中的 **66 个 v1.5.1 工程**，排除根 Forge `src/` 的 v1.6.0。已有大量修改予以保留。
- 历史标签为 `v1.5.1`，提交 `4686900ec86ecdec88bb4eecfd730c1d74537521`。标签与当前源码分别核对，不能将后续迁移回归写成已发布标签缺陷。
- 官方 Wurst7 1.20.1 固定基准：`d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de`；26.3 基准：`71e3af0f2747937086ef889e34359507d95cc9f8`。未声称这些是在线最新提交。
- 官方 1.20.1 没有本轮涉及的 Backtrack、NoGhostBlocks、Addon 或注解订阅实现。这些属于本地扩展，依据自身生命周期与 API 契约检查。官方 [EventManager](https://github.com/Wurst-Imperium/Wurst7/blob/d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de/src/main/java/net/wurstclient/event/EventManager.java) 使用监听器接口；[Blink](https://github.com/Wurst-Imperium/Wurst7/blob/d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de/src/main/java/net/wurstclient/hacks/BlinkHack.java) 的临时包队列功能标记了 `@DontSaveState`。缓存的两个源文件均已与官方 Git blob 校验一致，见 [核验记录](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/upstream-core-verification.json)。这只是生命周期设计参照，不是声称上游存在同名功能。
- 渲染项直接对照官方 26.3 的 [BlockComponent](https://github.com/Wurst-Imperium/Wurst7/blob/71e3af0f2747937086ef889e34359507d95cc9f8/src/main/java/net/wurstclient/clickgui/components/BlockComponent.java) 与 [RenderUtils](https://github.com/Wurst-Imperium/Wurst7/blob/71e3af0f2747937086ef889e34359507d95cc9f8/src/main/java/net/wurstclient/util/RenderUtils.java)，并核验本地真实 Minecraft 字节码。

## Standards：本地扩展的生命周期与 API 实现

本轴新增 **6 类 P2**。有明确状态污染或 API 失效证据；未把代码风格、重复代码或尚未证实的线索计入缺陷。

### S4 · [P2] Backtrack 在重连后通过新连接回放旧包

**当前 66/66；对应生产类在历史标签和 HEAD 中均相同。**

定位：[BacktrackHack.java:158](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hacks/BacktrackHack.java:158)，释放队列在第 335 行，接收／发送使用当前连接的入口在第 362、371 行。

三个队列与 `BacktrackData` 都没有保存所属世界或连接。功能没有 `@DontSaveState`，因此 [Hack.java:29](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hack/Hack.java:29) 将其视作需保存启用状态的功能；[HackLifecycleManager.java:25](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hack/HackLifecycleManager.java:25) 在离开世界时只关闭不保存状态的功能。Backtrack 的空世界更新直接返回，不清队列。

触发：保持 Backtrack 开启，在仍有待处理包时断开并进入另一世界／服务器。新世界首次更新可能因 Smart 模式找不到旧目标调用 `flushAll()`；它通过新的 `MC.getConnection()` 发送旧的移动／动作等包，并将旧实体更新交给新监听器。新世界若复用了实体 ID，还可能把旧实体更新套到无关实体。其他模式的定时释放同样没有归属检查。

验证：完整、未改动的 `BacktrackHack` 与 `HackLifecycleManager`，配连接记录器和实体／世界替身，经过真实收发入口入队、断开、换连接、更新：

```text
old outbound sent by new connection=1
old inbound applied by new listener=1
```

旧连接记录均为零。测试证明包被投递到错误连接；没有据此声称测得某个真实服务器的具体反应。

建议：队列绑定连接／世界代次，断开和世界替换时丢弃失效数据，保留用户的启用偏好与临时队列状态之间的区别。不要在检测到切换之后通过新连接补发旧包。

### S5 · [P2] NoGhostBlocks 把旧世界方块状态应用到新世界

**当前 66/66；生产类与历史标签、HEAD 均相同。**

定位：[NoGhostBlocksHack.java:89](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hacks/NoGhostBlocksHack.java:89)，消费在第 103–111 行，实际修改世界在第 133 行。

网络入口只保存包，没有保存所属世界。消费时直接使用当前 `MC.level`。队列只在启停或某次 `onUpdate()` 恰好看到空世界时清理；功能没有订阅 `WorldChangeListener`。切维度可直接由旧世界换成新世界，不能依靠一次空世界 tick。代表性 [ClientPlayerEntityMixin.java:64](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/mixin/ClientPlayerEntityMixin.java:64) 的 UpdateEvent 本身就在玩家 tick 中触发，也不能保证退出后出现空世界更新。

触发：旧世界方块更新已被网络入口排队，下一次玩家更新之前完成维度切换。待处理的旧更新会覆盖新世界同坐标的不同方块，制造本功能原本要消除的客户端状态不同步。

验证：完整未改生产类，将旧世界的 `old-world-stone` 更新入队，替换成同坐标为 `new-world-gold` 的世界，再更新。新世界被写为旧世界状态，旧世界没有写操作。对照中主动执行一次空世界更新后，队列确实被清空，说明缺口是世界切换时缺少确定性清理。

建议：记录世界代次并在消费前核对；世界替换时清理队列，避免依赖空世界 tick。两项世界生命周期复现及对照见 [执行结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/world-harness/result.txt)。

### S6 · [P2] 私有注解事件方法无法注册

**当前 66/66；相关订阅方法与 WurstSubscriber 在历史标签中均存在相同缺陷。仅涉及注解 API 使用者。**

定位：[WurstSubscriber.java:37](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/event/WurstSubscriber.java:37)，调用方 [EventManager.java:177](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/event/EventManager.java:177)。

`subscribeAnnotated()` 扫描 declared methods 并执行 `setAccessible(true)`，但后续用 `WurstSubscriber` 自己的 `MethodHandles.lookup()` 调用 `LambdaMetafactory`。外部类私有方法产生的句柄无法被此 lookup 正确解析为 lambda 实现，注册抛出异常：

```text
LambdaConversionException: MethodHandle(PrivateHandler,TestEvent)void
is not direct or cannot be cracked
```

验证：完整生产 `EventManager`、`WurstSubscriber` 等事件类未改动；外部 `audit` 包中的公共处理器正常调用一次，而同类结构的私有 `@WurstSubscribe` 方法在注册时失败。

建议：为声明类使用具有正确访问权限的调用策略，并验证私有方法与非公共声明类。当前没有证据表明内建功能正在使用私有注解处理器，因此不扩大为所有内建事件失效。

### S7 · [P2] 重写的注解事件处理器会执行两次

**当前 66/66；订阅扫描方法和判重规则在历史标签中相同。仅涉及继承式注解订阅者。**

定位：[EventManager.java:165](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/event/EventManager.java:165)，身份比较见 [WurstSubscriber.java:111](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/event/WurstSubscriber.java:111)。

扫描同时遍历子类和父类的 declared methods。父类方法与带相同注解的子类重写方法被视作不同 `Method`，都被登记；调用父类虚方法句柄时又会分派到子类重写。因此一个事件会执行两次相同处理逻辑，导致计数或其他副作用重复。

验证：公共父类的 `onEvent(TestEvent)` 和子类 `@Override @WurstSubscribe` 方法均通过真实注册入口订阅；发出一个事件后子类调用次数为 **2**，公共普通处理器对照为 **1**。

建议：按有效方法签名处理覆盖关系，在继承层级中只登记一次生效的重写方法。两项注解 API 的 [执行结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/annotation-harness/result.txt)。

### S8 · [P2] 一个损坏的 Addon 会阻断后续正常 Addon 发现

**当前 66/66；AddonManager 在历史标签、HEAD 中均相同。**

定位：[AddonManager.java:33](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/addon/AddonManager.java:33)，名称校验在第 49–53 行。

异常捕获位于整个 `ServiceLoader` 循环之外。第一个提供者构造失败时，`iterator.next()` 抛出的 `ServiceConfigurationError` 直接终止完整发现流程；即使后面有健康提供者也不会加载。`getName()` 返回空值／空白或抛异常也会发生同样问题，因为名称校验在 `registerAddon()` 的单 Addon 异常处理之外。

验证：完整未改的 `AddonManager`、`WurstAddon` 配真实 ServiceLoader 服务文件，依次声明损坏和正常提供者。构造失败或空白名称都使后面的正常 Addon 加载数为 **0**。两个对照确认：`onInitialize()` 内的失败能被正确隔离，健康 Addon 加载数为 **1**；相同 ServiceLoader 在逐提供者捕获构造错误后也能继续发现健康 Addon。

建议：把可恢复的提供者实例化错误与元数据／初始化错误隔离到单提供者边界，避免一个扩展阻断其他扩展。见 [执行结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/addon-harness/result.txt)。

### S9 · [P2] HUD 状态字段类型损坏会使初始化中断

**当前 66/66 使用相同 `loadState()`；历史标签确认 6 个相关加载器有同类异常缺口。** 另外 60 个标签目录没有该方法，本次不据此推断其历史配置恢复能力。

定位：[HudManager.java:506](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hud2/HudManager.java:506)，捕获在第 547 行；启动调用在第 83 行。

`loadState()` 捕获 `IOException | JsonException`，但 Gson 对合法 JSON 内错误字段类型的转换会抛出未捕获的运行时异常。例如 `soar-hud.json` 是 `{"theme":{}}`、`{"mods":{}}`，或已知模块的 `x` 为 `"oops"`。前两种分别抛 `UnsupportedOperationException`、`ClassCastException`，第三种抛 `NumberFormatException`。

这类手工编辑、旧配置或字段损坏会使 `HudManager.start()` 在 `started=true` 之前退出。66 个 `WurstClient` 都直接调用 `hudManager.start()`；代表性 [WurstClient.java:182](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/WurstClient.java:182) 未在此处恢复，Fabric 初始化入口同样没有捕获，因而异常会中断客户端初始化。

验证使用**原样提取的生产 `loadState()` 与 `JsonUtils.parseFile()` 方法体**，配真实缓存 Gson 2.10；没有编译完整 HudManager。正常保存格式加载成功，语法损坏 JSON 被原有 catch 处理；上述三个类型损坏样例均逸出异常。测试仅证明加载函数的异常边界，启动影响由调用链确认。见 [结果及对照](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/config-harness/result.txt)。

建议：校验字段类型和数值范围；按单模块隔离无效状态并回退默认值，保留损坏文件便于恢复。历史 6 个目录为 Fabric 1.20.1、1.20.2、1.21.4、1.21.6、1.21.11、26.3，见 [标签清单](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/config-tag-scope.json)。

## Spec：保持上游绘制语义的迁移回归

本轴新增 **1 类 P1、1 类 P2**。两项各影响 33 个源码副本：Forge、Fabric、NeoForge 各 11 个版本（1.21.6–1.21.11、26.1、26.1.1、26.1.2、26.2、26.3）。对应历史标签的 33 个目录均没有 NanoVGUI，因此归类为**标签之后本地 NanoVG 迁移中的回归**。

### U4 · [P1] 延迟 GUI 绘制共用可变纹理，覆盖 Navigator 背景与侧栏

定位：[NanoVgBlitter.java:52](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/hud2/nanovg/NanoVgBlitter.java:52)，实际覆盖纹理在第 109–111 行。

每次 `queueBlit()` 都把当前 FBO 复制到唯一静态纹理，再向 Minecraft GUI 队列加入引用同一 `textureView` 的 `BlitRenderState`。队列保存的是引用，没有图像快照。后一次复制会在队列消费之前改掉前一次的图像。

正常打开 [NavigatorScreen.java:163](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/clickgui2/NavigatorScreen.java:163) 就会触发：第一次 pass 绘制背景和分类侧栏，第 182 行的第二次 pass 绘制模块列表／设置。[NanoVGContext.java:326](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/hud2/nanovg/NanoVGContext.java:326) 在每次 beginFrame 清空 FBO，所以第二幅不包含第一幅的侧栏背景。两个排队元素最终都使用第二幅，造成侧栏和背景缺失，内容可能重复叠加。列 P1 是因为默认主界面的正常路径即可触发。

验证：完整未改 26.3 `NanoVgBlitter` 配 GPU／GL 边界记录器。单 pass 对照保留标记 `1`；连续提交 `1,2` 后两个元素共用 view，最终读取 **[2,2]**，预期 **[1,2]**。真实 26.3 MC 字节码进一步确认 `TextureSetup` 直接保存 view 引用，`GuiRenderState.Node.addGuiElement` 只有 `List.add`，不存在隐含即时绘制或快照。

33 份源码都存在 Navigator 直接 Screen 渲染路径与至少两次 pass；此触发链不依赖第一轮曾缺失的 HUD 事件注册。

建议：为同帧每次 pass 保留独立、稳定的图像，消费完成后再复用。仅新建 BlitRenderState 或 TextureSetup 对象无法解决底层纹理共用。

### U5 · [P2] 延迟物品图标丢失当前平移，设置面板中错位或被裁掉

定位：[NanoVGUI.java:540](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/hud2/nanovg/NanoVGUI.java:540)，flush 绘制在第 160 行。

`NanoVGUI.translate()` 改变 NanoVG 变换和 `translateX/Y`，没有改变 `graphics.pose()`。`item()` 只把原始局部 x/y 与裁剪区域排队，未保存或合并当前变换。flush 时直接把局部坐标交给 `RenderUtils.drawItem()`，导致文字背景正常移动而物品图标不随之移动。

触发：在 Navigator 打开带 BlockSetting 的功能设置。[NavigatorSettingsPanel.java:72](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/clickgui2/NavigatorSettingsPanel.java:72) 平移到面板正文与滚动位置，[BlockComponent.java:91](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/clickgui2/components/BlockComponent.java:91) 将局部坐标交给 item。实际图标在错误位置绘制，通常还会被全局裁剪框裁掉。

验证：原样提取 `item`、`flushDeferredItems`、`push/pop`、`translate` 与 DeferredItem。零平移对照得到 `(12,8)`；先平移 `(100,200)` 再提交同一图标，仍绘制 `(12,8)`，预期 `(112,208)`。没有修改生产方法体。

历史标签的旧面板使用 graphics pose 平移并同步绘制图标；官方 26.3 的 RenderUtils 同样依赖 graphics pose，无法自动读取本地 NanoVGUI 的平移状态。这是迁移丢失绘制语义。

建议：入队时保存组合变换，flush 时应用；仅平移路径至少合入当时的 translateX/Y，并兼顾调用方在 flush 前已 pop 的 graphics pose。

两项渲染的 [源码探针结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2-hook-validation.json)、[33 份范围与上游 blob 记录](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2-hook-scope.json) 及 [字节码证据](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2-mc263-GuiRenderState-Node-javap.txt)。

## 验证范围与保留项

| 验证 | 结果与边界 |
| --- | --- |
| 注解事件、Addon、跨世界状态 | JDK 17 编译并执行完整未改的相关生产类，仅替换游戏／宿主依赖；均有正常对照 |
| GUI 共用纹理 | 执行完整未改 Blitter；真实 MC 26.3 字节码核验引用保存与延迟入队 |
| GUI 物品坐标、HUD 配置 | 执行未改写的方法切片；配置使用真实 Gson；不声称编译了完整 GUI/HUD |
| 66 个当前工程及标签 | 静态范围扫描；[owner 清单](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/owner-scope.json)、[世界／配置清单](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round2/world-config-scope.json) |
| 游戏启动、真实 OpenGL 画面、全部构建 | 本轮未执行，33/66 表示源码范围，不表示启动了对应数量的客户端 |

没有将 NoRotate 的网络线程与恢复时序疑点计为已确认缺陷，尚缺真实调度交错证据。Fabric Addon Brigadier 注册线索也未计入：公开说明偏向 Forge，缺少明确的 Fabric 支持契约与集成复现。旧图标缓存泄漏等已有针对性修改的条目没有重新报告。

两轴分别为 **Standards：6 类 P2，最直接的风险是跨世界缓存污染；Spec：1 类 P1、1 类 P2，最严重的是主 GUI 图像覆盖**。

---

## 修复记录（2026-11-07，同一轮）

8 类问题全部修复，共改动 **464 个文件实例**（S4–S9 共 398 个 Java + U4/U5 各 33 个，其中 U4/U5 与 S 类文件无重叠）。26.x 的注入/渲染类无法在本机编译（缺 JDK 25），已用语法解析与 API 对照覆盖。

| 编号 | 修复内容 | 覆盖 |
| --- | --- | --- |
| S4 | `BacktrackHack` 订阅 `WorldChangeListener`，换世界/换连接时清空三组队列；`TimedPacket`/`BacktrackData` 记录所属 `ClientLevel` 与 `ClientPacketListener`，发送/回放前核对，旧包直接丢弃 | 67 |
| S5 | `NoGhostBlocksHack` 订阅世界切换并按世界过滤队列；队列元素改为 `(world, packet)`，消费时世界不符即丢弃 | 67 |
| S6 | `WurstSubscriber` 用 `MethodHandles.privateLookupIn(声明类, lookup())` 绑定；失败时退化为 `Method#invoke`，私有/包私有注解方法可注册 | 66 |
| S7 | `EventManager.subscribeAnnotated` 按 `方法名+参数类型` 记录已见签名，沿继承链只登记生效的重写方法，避免重复触发 | 66 |
| S8 | `AddonManager.discoverAddons` 改为逐 provider 隔离：`hasNext`/`next`/`registerAddon` 分别捕获，坏 Addon 只跳过自身 | 66 |
| S9 | `HudManager.start` 用 `try/catch(Throwable)` 包住 `loadState`；`loadState` 拆分 `loadModState` 并逐模块捕获、逐字段校验 JSON 类型，坏值回退默认且保留文件 | 66 |
| U4 | `NanoVgBlitter` 改为按 `GuiRenderState` 身份识别帧，帧内每次 blit 使用独立纹理槽（`MAX_SLOTS=8`），槽按 framebuffer 尺寸重建，避免后一次复制覆盖前一次排队图像 | 33 |
| U5 | `NanoVGUI.item` 记录入队时的 `translateX/translateY`，`flushDeferredItems` 通过 `graphics.pose()` 应用/还原平移 | 33 |

**实现要点**

- U4 的帧边界用 `GuiRenderState` 实例身份判断：它是每帧新建的，帧内所有 pass 共享同一个实例；同一帧第 N 个 pass 取第 N 个纹理槽，下一帧 reset 回 0。这样既保证排队中的 blit 不会互相覆盖，也不会无限创建纹理。
- U5 的 scissor 坐标本来就已含全局平移（`pushScissor` 中用 `translateX + x`），所以只在绘制项时应用平移，不改 scissor。
- S7 只对非 `private` 方法做覆盖去重：`private` 方法不参与重写，父类同名私有方法与子类方法互相独立。
- S6 的退化路径同时处理“私有方法”和“包私有类”两种不可达情形；`method.setAccessible` 失败时由 `invoke` 报出真实原因。

**验证**

| 检查 | 结果 |
| --- | --- |
| 编译：`fabric`(1.20.1)、`fabric/versions/1.20.2`、`fabric/versions/1.21.6`、`fabric/versions/1.21.10` | `compileJava --offline` 通过 |
| 编译：`neoforge`(1.20.1)、`versions/1.20.2`、`versions/1.21.5`、根工程 Forge 1.20.1（v1.6.0） | `compileJava --offline` 通过 |
| 500 个改动 Java 文件（Java 21 preview 语法解析） | 0 错误 |
| 独立测试：私有处理器绑定与调用 | 通过（`private handler invoked 2 times`） |
| 独立测试：重写方法只登记一次 + 私有处理器注册 | 通过（`override count=1, private handler registered, total=2`） |
| 修复标记逐树核对 | S4/S5/S8 66–67、S6/S7/S9 66、U4/U5 33 |

**保留事项**

- 根目录 Forge 1.20.1（v1.6.0）的 `WurstSubscriber`/`EventManager`/`HudManager` 与 66 个 v1.5.1 工程不是同一实现（前者带注解优先级与缓存），本轮未改这三处；S4/S5 已同步到根工程。若需要，可按根工程版本单独适配。
- 复核时发现第一轮把 `WurstClient.getFeatureByName` 的 HUD 查找也同步到了根工程，但根工程的 `HudElement` 不是 `Feature`（没有 `getAllFeatures()`），导致根工程编译失败。该处已回退，根工程现编译通过；66 个 v1.5.1 工程不受影响。
- 26.x 仍需 JDK 25 才能编译；其 U4/U5 已按 5 种 API 变体分别改写，并做语法解析与 jar API 对照。
- U4/U5 属渲染路径，本机无游戏内 OpenGL 验证；建议在 26.3 实机确认两段式 GUI（背景+侧栏 / 内容）不再互相覆盖，且设置面板中的物品图标随面板平移。

---

## 追加修复：1.21+ 的 NanoVG 图标不渲染（2026-11-07）

修复 U4/U5 时发现的相邻问题，不属于原 8 类：

- **定位**：`hud2/compat/HudTextures.java`。在 50 个工程（1.21–1.21.11、26.1–26.3，三加载器）里它是桩实现，`id()` 与 `localPlayerSkin()` 都直接 `return -1`；注释写明“NanoVG image drawing is therefore disabled”。
- **影响**：`NanoVGUI.image(Identifier)` 与 `NanoVGContext.drawPlayerHead` 都经由 `HudTextures.id()` 取 GL 纹理 id；-1 导致所有 NanoVG 图片静默跳过——主菜单按钮图标、ClickGUI 的 `GuiIcon`、HUD 里的图片全部不显示。
- **与 SVG 的关系**：资源目录里确有 3 个设计源文件 `close.svg`/`pin.svg`/`window_toggle.svg`，但 Java 代码只引用同名 `.png`（18 个 PNG 在每个树中都存在且可解码）。所以这不是 SVG 解析失败，而是纹理桥接被整体关掉。
- **修复**：50 个桩改为真实实现——从 `ResourceManager` 读取 PNG，用 `NativeImage` 解码，`GL11` 上传为私有 GL 纹理（RGBA8、线性过滤、CLAMP_TO_EDGE），按路径缓存并返回 GL id，供 `NanoVGContext.textureImage()` 包装。1.20.2–1.20.6 原有的 `AbstractTexture.getId()` 实现（14 个树）保持不动。
- **验证**：`fabric/versions/1.21.3`、`1.21.5`、`1.21.6`、`1.21.10`、`1.21.11` 编译通过；50 个文件语法解析 0 错误；26.x 已用本机 `C:\Users\ui863\.jdks\jdk25.0.4_10` 编译 `fabric/versions/26.3` 通过。进一步启动真实客户端验证（1.20.2 / 1.21.6 / 26.3 Fabric）：主菜单“单人游戏 / 多人游戏 / Minecraft Realms / 游戏设置”四个按钮图标均正常显示，日志中 `HudTextures` 返回的 GL 纹理 id 分别为 42–48、41–47、716–722。截图：[1.20.2](../_recon/v151-audit/title-1202.png)、[1.21.6](../_recon/v151-audit/title-1216.png)、[26.3](../_recon/v151-audit/title-263.png)。
- **已知限制**：`localPlayerSkin()` 仍返回 -1（玩家皮肤不是资源包文件，无法按此路径读取），HUD 玩家头像会继续空白；本次不改变该行为。
