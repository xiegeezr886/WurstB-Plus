# 混入登记缺口（自动生成）

> 本文件由 `scripts/audit-mixin-registration.ps1` 生成，请勿手工编辑。
> 重新生成：`pwsh -File scripts/audit-mixin-registration.ps1`

比对每个工程的 `*.mixins.json` 登记项与 `mixin/` 包里**实际带 `@Mixin` 注解**的 `.java`。
两种失效模式：

| 模式 | 含义 | 后果 |
| --- | --- | --- |
| **登记了但没有文件** | 配置里写了一个不存在的混入类 | Mixin 视为硬错误 —— **启动失败** |
| **有文件但没登记** | 真实混入类，没有任何配置引用它 | 它永远不加载 —— **功能静默失效**（不崩，就是没反应） |

## 汇总

| 指标 | 数量 |
| --- | ---: |
| 工程总数 | 67 |
| **登记了但没有文件**（硬错误） | **0** |
| **有文件但没登记**（静默失效） | **330** |
| 　└ A 类：根工程同名且已登记（最可能是真漏登记） | 159 |
| 　└ B 类：根工程同名但根工程也没登记 | 0 |
| 　└ C 类：根工程没有该文件（版本特有，需对 jar 核注入点） | 171 |
| 受影响工程数 | 24 |

## 二、有文件但没登记（功能静默失效）

### A 类 —— 根工程同名且已登记，最可能是真漏登记

| 工程 | 混入类 |
| --- | --- |
| fabric\versions\26.1 | AbstractBlockStateMixin |
| fabric\versions\26.1 | BackgroundRendererMixin |
| fabric\versions\26.1 | ChatHudMixin |
| fabric\versions\26.1 | ClientPlayerEntityMixin |
| fabric\versions\26.1 | MouseMixin |
| fabric\versions\26.1 | PlayerInventoryMixin |
| fabric\versions\26.1 | WorldRendererMixin |
| fabric\versions\26.1.1 | AbstractBlockStateMixin |
| fabric\versions\26.1.1 | BackgroundRendererMixin |
| fabric\versions\26.1.1 | ChatHudMixin |
| fabric\versions\26.1.1 | ClientPlayerEntityMixin |
| fabric\versions\26.1.1 | IngameHudMixin |
| fabric\versions\26.1.1 | InGameOverlayRendererMixin |
| fabric\versions\26.1.1 | KeyboardMixin |
| fabric\versions\26.1.1 | MobEntityRendererMixin |
| fabric\versions\26.1.1 | MouseMixin |
| fabric\versions\26.1.1 | PlayerInventoryMixin |
| fabric\versions\26.1.1 | StatusEffectInstanceMixin |
| fabric\versions\26.1.1 | WorldRendererMixin |
| fabric\versions\26.1.2 | AbstractBlockStateMixin |
| fabric\versions\26.1.2 | BackgroundRendererMixin |
| fabric\versions\26.1.2 | ChatHudMixin |
| fabric\versions\26.1.2 | ClientPlayerEntityMixin |
| fabric\versions\26.1.2 | IngameHudMixin |
| fabric\versions\26.1.2 | InGameOverlayRendererMixin |
| fabric\versions\26.1.2 | KeyboardMixin |
| fabric\versions\26.1.2 | MobEntityRendererMixin |
| fabric\versions\26.1.2 | MouseMixin |
| fabric\versions\26.1.2 | PlayerInventoryMixin |
| fabric\versions\26.1.2 | StatusEffectInstanceMixin |
| fabric\versions\26.1.2 | WorldRendererMixin |
| fabric\versions\26.2 | AbstractBlockStateMixin |
| fabric\versions\26.2 | BackgroundRendererMixin |
| fabric\versions\26.2 | ChatHudMixin |
| fabric\versions\26.2 | ClientPlayerEntityMixin |
| fabric\versions\26.2 | IngameHudMixin |
| fabric\versions\26.2 | InGameOverlayRendererMixin |
| fabric\versions\26.2 | KeyboardMixin |
| fabric\versions\26.2 | MobEntityRendererMixin |
| fabric\versions\26.2 | MouseMixin |
| fabric\versions\26.2 | PlayerInventoryMixin |
| fabric\versions\26.2 | StatusEffectInstanceMixin |
| fabric\versions\26.3 | AbstractBlockStateMixin |
| fabric\versions\26.3 | BackgroundRendererMixin |
| fabric\versions\26.3 | ChatHudMixin |
| fabric\versions\26.3 | ClientPlayerEntityMixin |
| fabric\versions\26.3 | IngameHudMixin |
| fabric\versions\26.3 | InGameOverlayRendererMixin |
| fabric\versions\26.3 | KeyboardMixin |
| fabric\versions\26.3 | MobEntityRendererMixin |
| fabric\versions\26.3 | MouseMixin |
| fabric\versions\26.3 | PlayerInventoryMixin |
| fabric\versions\26.3 | StatusEffectInstanceMixin |
| neoforge\versions\26.1 | AbstractBlockStateMixin |
| neoforge\versions\26.1 | BackgroundRendererMixin |
| neoforge\versions\26.1 | ChatHudMixin |
| neoforge\versions\26.1 | ClientPlayerEntityMixin |
| neoforge\versions\26.1 | MouseMixin |
| neoforge\versions\26.1 | PlayerInventoryMixin |
| neoforge\versions\26.1 | WorldRendererMixin |
| neoforge\versions\26.1.1 | AbstractBlockStateMixin |
| neoforge\versions\26.1.1 | BackgroundRendererMixin |
| neoforge\versions\26.1.1 | ChatHudMixin |
| neoforge\versions\26.1.1 | ClientPlayerEntityMixin |
| neoforge\versions\26.1.1 | IngameHudMixin |
| neoforge\versions\26.1.1 | InGameOverlayRendererMixin |
| neoforge\versions\26.1.1 | KeyboardMixin |
| neoforge\versions\26.1.1 | MobEntityRendererMixin |
| neoforge\versions\26.1.1 | MouseMixin |
| neoforge\versions\26.1.1 | PlayerInventoryMixin |
| neoforge\versions\26.1.1 | StatusEffectInstanceMixin |
| neoforge\versions\26.1.1 | WorldRendererMixin |
| neoforge\versions\26.1.2 | AbstractBlockStateMixin |
| neoforge\versions\26.1.2 | BackgroundRendererMixin |
| neoforge\versions\26.1.2 | ChatHudMixin |
| neoforge\versions\26.1.2 | ClientPlayerEntityMixin |
| neoforge\versions\26.1.2 | IngameHudMixin |
| neoforge\versions\26.1.2 | InGameOverlayRendererMixin |
| neoforge\versions\26.1.2 | KeyboardMixin |
| neoforge\versions\26.1.2 | MobEntityRendererMixin |
| neoforge\versions\26.1.2 | MouseMixin |
| neoforge\versions\26.1.2 | PlayerInventoryMixin |
| neoforge\versions\26.1.2 | StatusEffectInstanceMixin |
| neoforge\versions\26.1.2 | WorldRendererMixin |
| neoforge\versions\26.2 | AbstractBlockStateMixin |
| neoforge\versions\26.2 | BackgroundRendererMixin |
| neoforge\versions\26.2 | ChatHudMixin |
| neoforge\versions\26.2 | ClientPlayerEntityMixin |
| neoforge\versions\26.2 | IngameHudMixin |
| neoforge\versions\26.2 | InGameOverlayRendererMixin |
| neoforge\versions\26.2 | KeyboardMixin |
| neoforge\versions\26.2 | MobEntityRendererMixin |
| neoforge\versions\26.2 | MouseMixin |
| neoforge\versions\26.2 | PlayerInventoryMixin |
| neoforge\versions\26.2 | StatusEffectInstanceMixin |
| neoforge\versions\26.3 | AbstractBlockStateMixin |
| neoforge\versions\26.3 | BackgroundRendererMixin |
| neoforge\versions\26.3 | ChatHudMixin |
| neoforge\versions\26.3 | ClientPlayerEntityMixin |
| neoforge\versions\26.3 | IngameHudMixin |
| neoforge\versions\26.3 | InGameOverlayRendererMixin |
| neoforge\versions\26.3 | KeyboardMixin |
| neoforge\versions\26.3 | MobEntityRendererMixin |
| neoforge\versions\26.3 | MouseMixin |
| neoforge\versions\26.3 | PlayerInventoryMixin |
| neoforge\versions\26.3 | StatusEffectInstanceMixin |
| versions\26.1 | AbstractBlockStateMixin |
| versions\26.1 | BackgroundRendererMixin |
| versions\26.1 | ChatHudMixin |
| versions\26.1 | ClientPlayerEntityMixin |
| versions\26.1 | MouseMixin |
| versions\26.1 | PlayerInventoryMixin |
| versions\26.1 | WorldRendererMixin |
| versions\26.1.1 | AbstractBlockStateMixin |
| versions\26.1.1 | BackgroundRendererMixin |
| versions\26.1.1 | ChatHudMixin |
| versions\26.1.1 | ClientPlayerEntityMixin |
| versions\26.1.1 | IngameHudMixin |
| versions\26.1.1 | InGameOverlayRendererMixin |
| versions\26.1.1 | KeyboardMixin |
| versions\26.1.1 | MobEntityRendererMixin |
| versions\26.1.1 | MouseMixin |
| versions\26.1.1 | PlayerInventoryMixin |
| versions\26.1.1 | StatusEffectInstanceMixin |
| versions\26.1.1 | WorldRendererMixin |
| versions\26.1.2 | AbstractBlockStateMixin |
| versions\26.1.2 | BackgroundRendererMixin |
| versions\26.1.2 | ChatHudMixin |
| versions\26.1.2 | ClientPlayerEntityMixin |
| versions\26.1.2 | IngameHudMixin |
| versions\26.1.2 | InGameOverlayRendererMixin |
| versions\26.1.2 | KeyboardMixin |
| versions\26.1.2 | MobEntityRendererMixin |
| versions\26.1.2 | MouseMixin |
| versions\26.1.2 | PlayerInventoryMixin |
| versions\26.1.2 | StatusEffectInstanceMixin |
| versions\26.1.2 | WorldRendererMixin |
| versions\26.2 | AbstractBlockStateMixin |
| versions\26.2 | BackgroundRendererMixin |
| versions\26.2 | ChatHudMixin |
| versions\26.2 | ClientPlayerEntityMixin |
| versions\26.2 | IngameHudMixin |
| versions\26.2 | InGameOverlayRendererMixin |
| versions\26.2 | KeyboardMixin |
| versions\26.2 | MobEntityRendererMixin |
| versions\26.2 | MouseMixin |
| versions\26.2 | PlayerInventoryMixin |
| versions\26.2 | StatusEffectInstanceMixin |
| versions\26.3 | AbstractBlockStateMixin |
| versions\26.3 | BackgroundRendererMixin |
| versions\26.3 | ChatHudMixin |
| versions\26.3 | ClientPlayerEntityMixin |
| versions\26.3 | IngameHudMixin |
| versions\26.3 | InGameOverlayRendererMixin |
| versions\26.3 | KeyboardMixin |
| versions\26.3 | MobEntityRendererMixin |
| versions\26.3 | MouseMixin |
| versions\26.3 | PlayerInventoryMixin |
| versions\26.3 | StatusEffectInstanceMixin |

### B 类 —— 根工程同名，但根工程也没登记

（无）

### C 类 —— 根工程没有该文件（按工程聚合）

| 工程 | 未登记混入数 | 混入类 |
| --- | ---: | --- |
| fabric\versions\26.1 | 22 | ClientCommonPacketListenerImplMixin ClientLevelMixin ClientPacketListenerMixin ClientTelemetryManagerMixin CommandSuggestionsMixin ConnectionMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor CreativeModeInventoryScreenMixin DeltaTrackerTimerMixin DirectJoinServerScreenMixin InventoryAccessor JoinMultiplayerScreenMixin KeyBindsListMixin LevelMixin MobEffectInstanceMixin OptionInstanceMixin PackSelectionScreenMixin ServerNameResolverMixin SkinManagerMixin StorageMixin StringDecomposerMixin |
| fabric\versions\26.1.1 | 2 | CreativeModeInventoryScreenAccessor InventoryAccessor |
| fabric\versions\26.1.2 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin StorageMixin StringDecomposerMixin |
| fabric\versions\26.2 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin SectionOcclusionGraphMixin StringDecomposerMixin |
| fabric\versions\26.3 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin SectionOcclusionGraphMixin StringDecomposerMixin |
| neoforge\versions\1.20.2 | 1 | FogRendererAccessor |
| neoforge\versions\1.20.3 | 1 | FogRendererAccessor |
| neoforge\versions\1.20.4 | 1 | FogRendererAccessor |
| neoforge\versions\1.20.5 | 1 | FogRendererAccessor |
| neoforge\versions\1.20.6 | 1 | FogRendererAccessor |
| neoforge\versions\26.1 | 22 | ClientCommonPacketListenerImplMixin ClientLevelMixin ClientPacketListenerMixin ClientTelemetryManagerMixin CommandSuggestionsMixin ConnectionMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor CreativeModeInventoryScreenMixin DeltaTrackerTimerMixin DirectJoinServerScreenMixin InventoryAccessor JoinMultiplayerScreenMixin KeyBindsListMixin LevelMixin MobEffectInstanceMixin OptionInstanceMixin PackSelectionScreenMixin ServerNameResolverMixin SkinManagerMixin StorageMixin StringDecomposerMixin |
| neoforge\versions\26.1.1 | 2 | CreativeModeInventoryScreenAccessor InventoryAccessor |
| neoforge\versions\26.1.2 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin StorageMixin StringDecomposerMixin |
| neoforge\versions\26.2 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin SectionOcclusionGraphMixin StringDecomposerMixin |
| neoforge\versions\26.3 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin SectionOcclusionGraphMixin StringDecomposerMixin |
| versions\1.20.2 | 1 | FogRendererAccessor |
| versions\1.20.3 | 1 | FogRendererAccessor |
| versions\1.20.4 | 1 | FogRendererAccessor |
| versions\1.20.6 | 1 | FogRendererAccessor |
| versions\26.1 | 22 | ClientCommonPacketListenerImplMixin ClientLevelMixin ClientPacketListenerMixin ClientTelemetryManagerMixin CommandSuggestionsMixin ConnectionMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor CreativeModeInventoryScreenMixin DeltaTrackerTimerMixin DirectJoinServerScreenMixin InventoryAccessor JoinMultiplayerScreenMixin KeyBindsListMixin LevelMixin MobEffectInstanceMixin OptionInstanceMixin PackSelectionScreenMixin ServerNameResolverMixin SkinManagerMixin StorageMixin StringDecomposerMixin |
| versions\26.1.1 | 2 | CreativeModeInventoryScreenAccessor InventoryAccessor |
| versions\26.1.2 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin StorageMixin StringDecomposerMixin |
| versions\26.2 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin SectionOcclusionGraphMixin StringDecomposerMixin |
| versions\26.3 | 10 | ClientLevelMixin CommandSuggestionsMixin ContainerScreenMixin CreativeModeInventoryScreenAccessor DeltaTrackerTimerMixin InventoryAccessor LevelMixin MobEffectInstanceMixin SectionOcclusionGraphMixin StringDecomposerMixin |

## 三、建议的处理顺序

1. **先清硬错误**（第一节）。这类是启动就崩，不能留。
2. **A 类逐批登记**：每批之后必须真启动一次（`scripts/probe-smoke-clients.py` 或
   `scripts/run-version-tests.ps1`）。混入是硬注入 —— 注入点对不上不会降级，而是抛错。
3. **C 类不要凭名字登记**：先用 `scripts/audit-mixin-injections.py` /
   `audit-mixin-targets.py` 对着真实 jar 核注入点是否存在，筛完再登记。
4. 每批之后重跑本脚本，确认数量单调下降。

> **为什么不能一次全登记**：登记一个注入点已不存在的混入，结果不是"功能仍然坏"，
> 而是**客户端起不来**。而 C 类恰恰是版本差异最大、最需要逐个核对的一批。

