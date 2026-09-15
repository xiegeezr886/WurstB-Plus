状态：不适用

## 对照证据

参考侧有 `player/Scaffold.kt` 的模板建造思路，但没有"读取模板文件并自动建造"的对应模块。
本项审计方式：**全文模式扫描（调试输出/反射/线程/按键/世界边界）+ 结构核对（设置项、生命周期、
事件对称、异常处理）**，未逐行精读全部 315 行。

| 关注点 | 结论 |
| --- | --- |
| 事件对称 | `:130-132` 加（Update/RightClick/Render），`:138-140` 摘 —— 三个都成对 |
| 关闭清理 | `:136-144` `remainingBlocks.clear()` + 状态回落到 `IDLE`/`NO_TEMPLATE` | 正确 |
| 设置项 | 模板文件、范围、视线检查、只用已存方块、FastPlace、严格建造顺序（`:45-72`） | 与说明一致 |
| 读取模板失败 | `:289-300` 捕获 `IOException | JsonException`，向聊天报错 + 报异常类名与消息 + `setEnabled(false)` | 处理完整（`e.printStackTrace()` 属调试输出，见建议） |
| 未发现 | 未发现反射、未捕获异常、未受控线程、写死的世界边界常量、强制按键后不复位等问题（该模块不按移动键） |

无改动。

## 建议（未做）

- `:298` 在已经用 `ChatUtils.error` + 类名/消息告知用户之后仍然 `e.printStackTrace()`。
  与 `InstaBuildHack:228` 是同一份复制代码。属于控制台噪音而非缺陷，未改（删掉会损失排错信息）。
- 未逐行核对建造顺序、`useSavedBlocks` 与 FastPlace 的具体实现细节；没有实机建造验证。

## 第二轮精读（建造循环 / 物品切换 / 模板加载，逐行）

本轮把 `:136-315` 逐行读完，未发现可举证缺陷。新增证据与留档：

- `status` 在 `:78` 显式初始化为 `NO_TEMPLATE`（`onEnable` 不设它也不会让 `switch` 拿到 null），
  与 `:144-147` 的回落逻辑配对，重开模块后的状态是确定的。
- `:241` 那处「嵌套 if + 无括号 else」读起来难受，但 else 绑定的就是 `if(strictBuildOrder.isChecked())`，
  语义正是想要的：严格顺序 = 遇到够不到的方块立刻停，否则跳过它继续找下一个。
- `:247-252` 勾选「只用已存方块」而背包里没有该方块时，会**每 tick 空转并 return**（模块卡住不动）。
  这是「严格按模板」的必然结果，没有反例说明它是错误行为，按第 9 条只记录。
- `:279-301` 模板加载不需要额外空值保护：`FileSetting:36` 把 `selectedFile` 初始化为 "",
  `:72` 有 `Objects.requireNonNull`，`:85-92` 有文件时取第一个 ⇒ `getSelectedFile()` 不会返回 null；
  文件夹为空时 `folder.resolve("")` 指向目录本身，`JsonUtils.parseFileToObject` 抛 IOException，
  已被 `:289` 捕获并自我关闭。
- 物品切换的时序安全：原版 `MultiPlayerGameMode.ensureHasSentCarriedItem()`（反编译源 `:285-290`）
  对比 `carriedIndex` 与 `inventory.selected`，并在每次交互前（`:200/:272/:294/:362/:407/:416/:422/:485`）
  补发 `ServerboundSetCarriedItemPacket` ⇒ `giveOrSelectItem()` 换手后即便同 tick 就放置，
  服务端也会先收到换手包。
- 共享的 `util/AutoBuildTemplate.java` 里发现并修掉 1 处 NPE（模板里出现不存在的方块名时），见覆盖文档 §0.1。
