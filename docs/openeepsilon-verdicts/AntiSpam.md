状态：不适用

## 对照证据

参考侧**没有任何 CHAT 类模块**：`_oe_ref` 的 `module/` 只有 client / combat / misc / movement / player /
render / setting 七个子目录（共 110 个模块），其中与聊天沾边的只有 `misc/ClientSpoof.kt`（改名/皮肤欺骗）
和 `misc/AntiCrasher.kt`（拦崩溃包），没有 AntiSpam / FancyChat / InfiniChat / MassTpa / ForceOP /
ChatTranslator / AutoComplete 中任何一个的对应实现。因此本模块没有可搬的参考实现，本项为自审（无改动）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 合并重复消息 | `hacks/AntiSpamHack.java:58-134`：把与上一条相同的消息合并成 `[xN]` | 逻辑正常 |
| 换行宽度口径 | `:52-56` 用 `chat.getWidth() / chat.getScale()` + `ComponentRenderUtils.wrapComponents` 复刻原版聊天行的换行方式 | 与渲染侧一致 |
| 删除时的下标安全 | `:131-132` 从 `i + matchingLines` 倒序删到 `i`；外层 `i` 递减后仍小于删除后的 `size`（删除只发生在 ≥ i 的位置） | 不会越界 |
| 计数解析 | `:94-127` 用 `MathUtils.isInteger` 校验 `[xN]` 里的数字，非法就 `matchingLines = 0` 重新找 | 不会抛 `NumberFormatException` |
| 可变文本崩溃 | `:138-147` 的注释说明了为什么必须 `MutableComponent.create(...)` 重建再 `append` | 保留 |

未发现可举证的问题，未改动代码。

## 建议（未做）

- `chatLines` 每收到一条消息就整体重扫（最坏 O(n²)），聊天记录上限 100 行，开销可忽略，未优化。
