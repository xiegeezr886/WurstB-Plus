状态：不适用（参考侧无对应模块，零改动；发现一处有量化上限的既有局限，见「建议」）

## 参考侧核对

`D:\WurstB\_oe_ref` 全仓 449 个 `.kt` 里没有 FakeLag / 假延迟模块（`Select-String -Pattern FakeLag` 零匹配）。参考的 `LagBackCheck.kt` 是被拉回时的告警，`PingSpoof.kt`（misc）是伪造 ping 数值，都不缓存移动包，因此无可对照实现，判「不适用」。

## 本工程现状（自审）

`hacks/FakeLagHack.java`，117 行（全文已读）：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 拦什么 | `:80-88` 所有 `ServerboundMovePlayerPacket`（`Rot` 除外）→ `event.cancel()` + 入队 | 只拦移动，转视角不拦（合理：`Rot` 不影响位置） |
| 两种模式 | `:26-29` `Always`（永不发）/ `Pulse`（每 N 秒 flush） | 与设置描述一致 |
| Pulse 间隔 | `:31-33` 默认 1 秒，范围 0.1–5 秒；`:67-76` 用 `System.currentTimeMillis()` 比较 | 用墙钟而非 tick，与参考的 `AutoClicker` 同类做法 |
| 关 hack | `:56-62` 摘监听器后 `sendPackets()`，`:90-97` 先拷贝再清空再发 | 顺序正确，不会漏发 |

## 故意不搬

- 参考无此模块。
- 1.12.2 常见的 `CPacketPlayer` + `SPacketPlayerPosLook` 组合（在收到服务端位置纠正时假装自己一直在那）在 1.20.1 上服务端会直接 `absMoveTo` 回正，收益不明，不搬。

## 建议（未做）

1. **`Always` 模式下 `queue` 是无界的（`:35-36,85`）**，只有 `Pulse` 到点或关 hack 时才清空。量化一下：原版客户端约 20 个移动包/秒，每个 `ServerboundMovePlayerPacket.Pos` 对象含 3 个 double + 2 个 float + 布尔，约 40–56 字节，10 分钟 ≈ 12000 个 ≈ 0.5–0.7 MB 堆内存——内存本身不致命，真正的问题是**关闭瞬间 `:90-97` 一次性把 12000 个包塞进一条 TCP 连接**：服务端会在同一 tick 里做完 12000 次移动校验（`ServerGamePacketListenerImpl#handleMovePlayer`，每次含碰撞/传送判定），大概率触发服务器的移动包频率限制或直接掉线。
   可行的最小修法（未做）：给 `queue` 加一个固定上限（例如 1000），超出时丢弃最旧的一个——位置类缓存丢旧的不会改变最终落点，因为服务端最终状态由最后一个包决定。之所以没直接改：这属于「改变发包语义」，按 brief 第 5 条我不擅自改既存行为，而且需要实机确认服务端对补发的容忍度。
2. 无实机验证：`Pulse` 间隔用墙钟比较（`System.currentTimeMillis()`），如果客户端卡顿导致一 tick 超过 1 秒，会连续 flush 多次；这是所有墙钟间隔 hack 的共性问题，未动。
