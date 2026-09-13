# OpenEpsilon 参考重构 · 覆盖清单

参考：`CakeSlayers/OpenEpsilon`（Kotlin，已 clone 到 `D:\WurstB\_oe_ref`，只读）。
本文件是**进度账本**：每个 hack 一行，状态在重构过程中更新。

状态含义：`待办` 未动 / `已优化` 改了实现细节 / `已重构` 换了算法或架构 / `不适用` OpenEpsilon 无对应模块或差异无意义 / `共享核心` 主要逻辑已移入共享层。

## 覆盖统计

- hack 总数：**210**
- 单文件行数合计：**33196**
- 标记为「常用」的：**76**

## 常用 hack（优先覆盖）

| hack | 分类 | 行数 | 状态 |
| --- | --- | --- | --- |
| `AutoFarm` | BLOCKS | 379 | 待办 |
| `AutoTool` | BLOCKS | 289 | 待办 |
| `Excavator` | BLOCKS | 549 | 待办 |
| `FastBreak` | BLOCKS | 109 | 待办 |
| `Nuker` | BLOCKS | 165 | 待办 |
| `ScaffoldWalk` | BLOCKS | 332 | 待办 |
| `VeinMiner` | BLOCKS | 246 | 待办 |
| `AimAssist` | COMBAT | 275 | 待办 |
| `AnchorAura` | COMBAT | 547 | 待办 |
| `AntiBot` | COMBAT | 177 | 待办 |
| `AutoArmor` | COMBAT | 262 | 待办 |
| `AutoCity` | COMBAT | 159 | 待办 |
| `AutoSword` | COMBAT | 207 | 待办 |
| `AutoTotem` | COMBAT | 156 | 待办 |
| `AutoTrap` | COMBAT | 191 | 待办 |
| `BowAimbot` | COMBAT | 261 | 待办 |
| `Burrow` | COMBAT | 259 | 待办 |
| `ClickAura` | COMBAT | 139 | 待办 |
| `Criticals` | COMBAT | 228 | 待办 |
| `CrystalAura` | COMBAT | 443 | 待办 |
| `HoleFiller` | COMBAT | 152 | 待办 |
| `KeepSprint` | COMBAT | 30 | 待办 |
| `Killaura` | COMBAT | 1259 | 待办 |
| `MultiAura` | COMBAT | 1162 | 待办 |
| `SelfTrap` | COMBAT | 176 | 待办 |
| `SuperKnockback` | COMBAT | 202 | 待办 |
| `Surround` | COMBAT | 220 | 待办 |
| `TriggerBot` | COMBAT | 233 | 待办 |
| `WTap` | COMBAT | 126 | 待办 |
| `AutoEat` | ITEMS | 351 | 待办 |
| `AutoSteal` | ITEMS | 108 | 待办 |
| `FastUse` | ITEMS | 105 | 待办 |
| `Restock` | ITEMS | 180 | 待办 |
| `AutoSprint` | MOVEMENT | 121 | 待办 |
| `Blink` | MOVEMENT | 198 | 待办 |
| `BunnyHop` | MOVEMENT | 87 | 待办 |
| `ElytraFly` | MOVEMENT | 155 | 待办 |
| `FakeLag` | MOVEMENT | 117 | 待办 |
| `FastLadder` | MOVEMENT | 53 | 待办 |
| `Flight` | MOVEMENT | 189 | 待办 |
| `Glide` | MOVEMENT | 120 | 待办 |
| `HighJump` | MOVEMENT | 36 | 待办 |
| `InvWalk` | MOVEMENT | 129 | 待办 |
| `Jesus` | MOVEMENT | 180 | 待办 |
| `NoClip` | MOVEMENT | 95 | 待办 |
| `NoFall` | MOVEMENT | 186 | 待办 |
| `NoSlowdown` | MOVEMENT | 143 | 待办 |
| `NoVelocity` | MOVEMENT | 256 | 待办 |
| `PacketFly` | MOVEMENT | 158 | 待办 |
| `Parkour` | MOVEMENT | 79 | 待办 |
| `ReverseStep` | MOVEMENT | 141 | 待办 |
| `SafeWalk` | MOVEMENT | 109 | 待办 |
| `Sneak` | MOVEMENT | 150 | 待办 |
| `SpeedHack` | MOVEMENT | 210 | 待办 |
| `Spider` | MOVEMENT | 49 | 待办 |
| `Step` | MOVEMENT | 161 | 待办 |
| `Reach` | OTHER | 67 | 待办 |
| `Timer` | OTHER | 38 | 待办 |
| `BaseFinder` | RENDER | 251 | 待办 |
| `CaveFinder` | RENDER | 246 | 待办 |
| `ChestEsp` | RENDER | 275 | 待办 |
| `EntityCulling` | RENDER | 99 | 待办 |
| `Freecam` | RENDER | 183 | 待办 |
| `Fullbright` | RENDER | 197 | 待办 |
| `HoleEsp` | RENDER | 193 | 待办 |
| `ItemEsp` | RENDER | 124 | 待办 |
| `LightOverlay` | RENDER | 117 | 待办 |
| `LogoutSpots` | RENDER | 199 | 待办 |
| `MobEsp` | RENDER | 177 | 待办 |
| `NameTags` | RENDER | 227 | 待办 |
| `NewChunks` | RENDER | 259 | 待办 |
| `PlayerEsp` | RENDER | 315 | 待办 |
| `Radar` | RENDER | 120 | 待办 |
| `Search` | RENDER | 256 | 待办 |
| `Trajectories` | RENDER | 287 | 待办 |
| `XRay` | RENDER | 244 | 待办 |

## 全部 hack

### ?（2）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `ClickGui` | 96 | 待办 |
| `Navigator` | 44 | 待办 |

### BLOCKS（31）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AirPlace` | 131 | 待办 |
| `AntiCactus` | 43 | 待办 |
| `AutoBuild` | 315 | 待办 |
| `AutoFarm` | 379 | 待办 |
| `AutoMine` | 115 | 待办 |
| `AutoSign` | 43 | 待办 |
| `AutoTool` | 289 | 待办 |
| `BaritoneClearArea` | 125 | 待办 |
| `BaritoneMine` | 160 | 待办 |
| `BaritoneTreeBot` | 300 | 待办 |
| `BonemealAura` | 281 | 待办 |
| `BuildRandom` | 206 | 待办 |
| `Excavator` | 549 | 待办 |
| `FastBreak` | 109 | 待办 |
| `FastPlace` | 41 | 待办 |
| `HandNoClip` | 49 | 待办 |
| `InstaBuild` | 239 | 待办 |
| `InstantBunker` | 118 | 待办 |
| `Kaboom` | 109 | 待办 |
| `Liquids` | 41 | 待办 |
| `Nuker` | 165 | 待办 |
| `NukerLegit` | 205 | 待办 |
| `PerimeterDigger` | 358 | 待办 |
| `ScaffoldWalk` | 332 | 待办 |
| `SpeedMine` | 85 | 待办 |
| `SpeedNuker` | 110 | 待办 |
| `TemplateTool` | 190 | 待办 |
| `Tillaura` | 171 | 待办 |
| `TreeBot` | 477 | 待办 |
| `Tunneller` | 925 | 待办 |
| `VeinMiner` | 246 | 待办 |

### CHAT（7）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiSpam` | 150 | 待办 |
| `AutoComplete` | 161 | 待办 |
| `ChatTranslator` | 150 | 待办 |
| `FancyChat` | 68 | 待办 |
| `ForceOp` | 305 | 待办 |
| `InfiniChat` | 22 | 待办 |
| `MassTpa` | 170 | 待办 |

### COMBAT（38）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AimAssist` | 275 | 待办 |
| `AnchorAura` | 547 | 待办 |
| `AntiBot` | 177 | 待办 |
| `ArrowDmg` | 88 | 待办 |
| `AutoArmor` | 262 | 待办 |
| `AutoCity` | 159 | 待办 |
| `AutoLeave` | 138 | 待办 |
| `AutoPotion` | 134 | 待办 |
| `AutoRespawn` | 55 | 待办 |
| `AutoSoup` | 189 | 待办 |
| `AutoSword` | 207 | 待办 |
| `AutoTotem` | 156 | 待办 |
| `AutoTrap` | 191 | 待办 |
| `AutoWeb` | 157 | 待办 |
| `BowAimbot` | 261 | 待办 |
| `Burrow` | 259 | 待办 |
| `ClickAura` | 139 | 待办 |
| `Criticals` | 228 | 待办 |
| `CrystalAura` | 443 | 待办 |
| `DelayRemover` | 61 | 待办 |
| `FightBot` | 303 | 待办 |
| `Hitboxes` | 36 | 待办 |
| `HoleFiller` | 152 | 待办 |
| `KeepSprint` | 30 | 待办 |
| `Killaura` | 1259 | 待办 |
| `KillauraLegit` | 349 | 待办 |
| `MultiAura` | 1162 | 待办 |
| `NoMissCooldown` | 67 | 待办 |
| `ProjectilePuncher` | 136 | 待办 |
| `Protect` | 386 | 待办 |
| `RightClicker` | 124 | 待办 |
| `SelfTrap` | 176 | 待办 |
| `SuperKnockback` | 202 | 待办 |
| `Surround` | 220 | 待办 |
| `TargetStrafe` | 144 | 待办 |
| `TpAura` | 192 | 待办 |
| `TriggerBot` | 233 | 待办 |
| `WTap` | 126 | 待办 |

### FUN（12）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiAim` | 134 | 待办 |
| `DankBobbing` | 65 | 待办 |
| `Derp` | 53 | 待办 |
| `HeadRoll` | 50 | 待办 |
| `Lsd` | 49 | 待办 |
| `MileyCyrus` | 60 | 待办 |
| `Notebot` | 217 | 待办 |
| `RainbowUi` | 24 | 待办 |
| `SkinDerp` | 53 | 待办 |
| `Tired` | 45 | 待办 |
| `Twerk` | 45 | 待办 |
| `Vomit` | 48 | 待办 |

### ITEMS（10）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AutoDrop` | 90 | 待办 |
| `AutoEat` | 351 | 待办 |
| `AutoSteal` | 108 | 待办 |
| `AutoSwitch` | 47 | 待办 |
| `CrashChest` | 62 | 待办 |
| `FastUse` | 105 | 待办 |
| `ItemGenerator` | 87 | 待办 |
| `KillPotion` | 119 | 待办 |
| `Restock` | 180 | 待办 |
| `TrollPotion` | 117 | 待办 |

### MOVEMENT（45）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AirJump` | 101 | 待办 |
| `Anchor` | 68 | 待办 |
| `AntiEntityPush` | 45 | 待办 |
| `AntiHunger` | 55 | 待办 |
| `AntiVoid` | 54 | 待办 |
| `AntiWaterPush` | 86 | 待办 |
| `AutoSprint` | 121 | 待办 |
| `AutoSwim` | 51 | 待办 |
| `AutoWalk` | 43 | 待办 |
| `BaritoneWalk` | 90 | 待办 |
| `Blink` | 198 | 待办 |
| `BoatFly` | 90 | 待办 |
| `BunnyHop` | 87 | 待办 |
| `CreativeFlight` | 131 | 待办 |
| `Dolphin` | 49 | 待办 |
| `ElytraFly` | 155 | 待办 |
| `ExtraElytra` | 147 | 待办 |
| `FakeLag` | 117 | 待办 |
| `FastLadder` | 53 | 待办 |
| `Fish` | 49 | 待办 |
| `Flight` | 189 | 待办 |
| `Follow` | 278 | 待办 |
| `Glide` | 120 | 待办 |
| `HighJump` | 36 | 待办 |
| `InvWalk` | 129 | 待办 |
| `Jesus` | 180 | 待办 |
| `Jetpack` | 45 | 待办 |
| `NoClip` | 95 | 待办 |
| `NoFall` | 186 | 待办 |
| `NoJumpDelay` | 79 | 待办 |
| `NoLevitation` | 25 | 待办 |
| `NoRotate` | 68 | 待办 |
| `NoSlowdown` | 143 | 待办 |
| `NoVelocity` | 256 | 待办 |
| `NoWeb` | 40 | 待办 |
| `PacketFly` | 158 | 待办 |
| `Parkour` | 79 | 待办 |
| `ReverseStep` | 141 | 待办 |
| `SafeWalk` | 109 | 待办 |
| `Sneak` | 150 | 待办 |
| `SnowShoe` | 25 | 待办 |
| `SpeedHack` | 210 | 待办 |
| `Spider` | 49 | 待办 |
| `Step` | 161 | 待办 |
| `VehicleBoost` | 74 | 待办 |

### OTHER（15）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiAfk` | 259 | 待办 |
| `AutoFish` | 274 | 待办 |
| `AutoLibrarian` | 528 | 待办 |
| `AutoReconnect` | 38 | 待办 |
| `FeedAura` | 210 | 待办 |
| `MusicPlayer` | 27 | 待办 |
| `PacketCanceller` | 100 | 待办 |
| `PacketLogger` | 91 | 待办 |
| `Panic` | 47 | 待办 |
| `PortalGui` | 24 | 待办 |
| `PotionSaver` | 53 | 待办 |
| `Reach` | 67 | 待办 |
| `Throw` | 71 | 待办 |
| `Timer` | 38 | 待办 |
| `TooManyHax` | 159 | 待办 |

### RENDER（50）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiBlind` | 28 | 待办 |
| `AntiWobble` | 25 | 待办 |
| `BarrierEsp` | 24 | 待办 |
| `BaseFinder` | 251 | 待办 |
| `BossStack` | 74 | 待办 |
| `Breadcrumbs` | 149 | 待办 |
| `CameraDistance` | 35 | 待办 |
| `CameraNoClip` | 24 | 待办 |
| `CaveFinder` | 246 | 待办 |
| `ChestEsp` | 275 | 待办 |
| `CityEsp` | 150 | 待办 |
| `EntityCulling` | 99 | 待办 |
| `Freecam` | 183 | 待办 |
| `Fullbright` | 197 | 待办 |
| `HealthTags` | 54 | 待办 |
| `HoleEsp` | 193 | 待办 |
| `ItemEsp` | 124 | 待办 |
| `LightOverlay` | 117 | 待办 |
| `LogoutSpots` | 199 | 待办 |
| `MobEsp` | 177 | 待办 |
| `MobSpawnEsp` | 193 | 待办 |
| `NameProtect` | 56 | 待办 |
| `NameTags` | 227 | 待办 |
| `NewChunks` | 259 | 待办 |
| `NoBackground` | 46 | 待办 |
| `NoFireOverlay` | 36 | 待办 |
| `NoFog` | 24 | 待办 |
| `NoHurtcam` | 24 | 待办 |
| `NoOverlay` | 26 | 待办 |
| `NoPumpkin` | 24 | 待办 |
| `NoShieldOverlay` | 48 | 待办 |
| `NoVignette` | 24 | 待办 |
| `NoWeather` | 69 | 待办 |
| `OpenWaterEsp` | 71 | 待办 |
| `Overlay` | 67 | 待办 |
| `PlayerEsp` | 315 | 待办 |
| `PlayerHalo` | 47 | 待办 |
| `PopChams` | 181 | 待办 |
| `PortalEsp` | 223 | 待办 |
| `ProphuntEsp` | 73 | 待办 |
| `Radar` | 120 | 待办 |
| `RemoteView` | 180 | 待办 |
| `RotationSnap` | 106 | 待办 |
| `Search` | 256 | 待办 |
| `SeedOreEsp` | 541 | 待办 |
| `SeedStructureEsp` | 265 | 待办 |
| `TargetShader` | 79 | 待办 |
| `Trajectories` | 287 | 待办 |
| `TrueSight` | 56 | 待办 |
| `XRay` | 244 | 待办 |

