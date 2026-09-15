状态：不适用

## 对照证据

参考侧有 `misc/RemoteView.kt`（1.12.2 的"附身"）。本工程 `hacks/RemoteViewHack.java`（180 行）
实现相同：把本地玩家的位置复制到目标实体上、自己不可见、并不把位置发给服务器。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 不告诉服务器 | `:174-179` 取消所有出站 `ServerboundMovePlayerPacket` | 正确（这就是"服务端视角不变"的关键） |
| 位置/视线对齐 | `:161-168` `copyPosition` + 按眼高差修正 `setPosRaw` + `setOldPosAndRot` + 速度清零 | 正常 |
| 目标隐身 | `:79,107,171` 记录进入前的 `isInvisible`，关闭时还原 | 正确（不会把目标永久改成隐身） |
| 假玩家 | `:85,115-119` 造 `FakePlayerEntity` 顶替自己的位置，关闭时先 `resetPlayerPosition()` 再 `despawn()` | 正确 |
| 目标死亡/移除 | `:154-159` 自动关闭 | 正确 |
| 找不到目标 | `:70-75` 报错并自动关闭 | 正确 |
| 不存档 | `:29` `@DontSaveState` | 合理 |
| 命令行入口 | `:122-149` `onToggledByCommand(name)`，按名字找实体 | 正常 |

无改动。

## 建议（未做）

- `:82` 开启时把 `MC.player.noPhysics = true`、`:112` 关闭时一律置回 `false`。若玩家同时开着
  `NoClip`，关闭 RemoteView 会把 `noPhysics` 置为 false，但 `NoClipHack` 每 tick 会重新写 true
  （`NoClipHack.java:81`），下一 tick 就自愈了，没有构造出可触发的反例，只记录。
- 关闭 RemoteView 后本地玩家仍在目标位置，服务器下一次位置校验会把你拉回去（橡皮筋）。这是该机制
  的固有表现，参考也一样，未改。
