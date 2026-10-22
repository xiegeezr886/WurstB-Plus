# 测试控制口（Test Control Server）

给 67 个工程内置的**本地测试接口**，用于自动化测试时快速启用/禁用 hack、读取客户端日志，
避免逐项操作 GUI。

**默认关闭，不监听任何端口。**

## 启用

二选一：

1. 在 `gameDir/wurst/test-control.properties` 写入：

   ```properties
   port=25566
   token=testtoken
   ```

2. 或给游戏 JVM 加参数：

   ```text
   -Dwurst.test.port=25566 -Dwurst.test.token=testtoken
   ```

未提供 `token` 时会自动生成，并写入 `gameDir/wurst/test-token.txt`。

服务只绑定 `127.0.0.1`，所有非 `/ping` 请求必须携带
`X-Wurst-Token: <token>`（或 `?token=<token>`）。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/ping` | 就绪检查，返回 `ok` |
| GET | `/hacks` | 全部 hack 及其启用状态（JSON） |
| POST | `/hacks/enable?name=Killaura` | 启用指定 hack |
| POST | `/hacks/disable?name=Killaura` | 禁用指定 hack |
| POST | `/hacks/enableAll` | 启用全部可启用 hack（跳过 ClickGUI/Navigator/Panic） |
| POST | `/hacks/disableAll` | 禁用全部 hack |
| GET | `/logs?lines=200&errorsOnly=0` | 读取 `logs/latest.log` 末尾若干行 |

所有修改 hack 状态的操作都会切回客户端主线程执行，等待上限 120 秒。
`enableAll` / `disableAll` 会为每个 hack 打印 `[WurstTest] enable <name>`，
卡住时可直接看 `latest.log` 的最后一行定位。

## 示例

```bash
curl -s -H "X-Wurst-Token: testtoken" http://127.0.0.1:25566/ping
curl -s -H "X-Wurst-Token: testtoken" http://127.0.0.1:25566/hacks
curl -s -X POST -H "X-Wurst-Token: testtoken" \
  "http://127.0.0.1:25566/hacks/enableAll"
curl -s -H "X-Wurst-Token: testtoken" \
  "http://127.0.0.1:25566/logs?lines=300&errorsOnly=1"
```

`enableAll` 返回：

```json
{"ok":true,"changed":184,"failed":9,
 "failedNames":["AutoComplete","CrashChest","Follow","InstantBunker",
                "KillPotion","LSD","MassTPA","RemoteView","TrollPotion"]}
```

不同版本/场景下 `failed` 列表会变化，取决于 hack 的 `canEnable()` 条件。

## 安全边界

- 仅 loopback；不要改成 `0.0.0.0`。
- token 是唯一认证；不要提交 `test-control.properties` 或 `test-token.txt`。
- 测试完成后删除配置文件，恢复默认关闭。
- 该接口能启用作弊功能，`enableAll` 会实际改变游戏状态；只在测试世界使用。

## 实现位置

- `net.wurstclient.util.TestControlServer`（67 个工程各一份）
- 启动入口：`WurstClient.initialize()` 末尾的 `TestControlServer.startIfConfigured()`
