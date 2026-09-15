状态：不适用

## 对照证据

参考侧没有 CHAT 类模块，没有批量传送请求（mass tpa）之类的实现。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 目标列表 | `hacks/MassTpaHack.java:87-100`：取 `connection.getOnlinePlayers()`，剥颜色代码、排除自己、随机洗牌 | 正常 |
| 无人可发 | `:105-109` 报错并自动关闭 | 正常 |
| 命令值安全性 | `:33-41,85` 命令来自 `TextFieldSetting`，validator 为 `^/+[a-zA-Z0-9_\-]+$`；而 `settings/TextFieldSetting.java:93-107` 的 `setValue` 只保存通过 validator 的值（`fromJson` 也再验一次），所以 `substring(1)` 拿到的一定是合法命令名，不会因为用户在 GUI 里输错而向服务器发出垃圾命令 | 已核实不是问题 |
| 发送间隔 | `:122-138` `timer = delay.getValueI() - 1`，配合递减语义两次发送相隔正好 `delay` tick | 与滑条描述一致 |
| 接受判定 | `:158-167` 识别英/德文关键词决定停止 | 只覆盖英德（建议见下） |

无改动。

## 建议（未做）

- 接受/拒绝关键词只有英语与德语（`"accepted"+"request"`、`"akzeptiert"+"anfrage"`），中文服务器上
  `Stop when accepted` 不会生效。加语言需要用户可配置的词表，属于新功能，未做。
- `onReceivedMessage` 里的 `/help`、`permission` 判定同样是英文关键词。
