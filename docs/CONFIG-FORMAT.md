# WurstB+ Plus 配置格式与参数机制

本文档梳理 WurstB+ Plus 的配置文件格式、`.settings` 命令与 profile 加载机制，供编写/维护参数包（profile）使用。

## 1. 配置文件位置

| 文件 | 路径 | 说明 |
| --- | --- | --- |
| 主配置 | `<minecraft>/wurst/settings.json` | 每次加载/修改后由客户端自动写回（`SettingsFile.save()`） |
| profile 目录 | `<minecraft>/wurst/settings/` | `.settings` 命令的 profile 存放处 |

`<minecraft>` 即 `.minecraft`（或对应版本的游戏目录）。开发环境下为 `run/wurst/settings.json`。

## 2. 顶层结构

配置是一个 JSON 对象，**顶层 key 为功能名（feature name）**，值为该功能的设置对象：

```json
{
  "功能名": { "参数名": 值, ... },
  ...
}
```

功能名覆盖三类（见 `SettingsFile.createFeatureMap()`）：

- **Hack**：`Hack.getName()`（如 `Killaura`、`AimAssist`、`Speed`）
- **Command**：`Command.getName()`
- **OtherFeature**：`OtfList` 中的功能名

只有**存在至少一个 setting 的功能**才会进入配置文件（当前共 152 个）。

## 3. 参数名匹配规则

加载时（`SettingsFile.loadSettings()`）：

- 顶层功能名：精确匹配（区分大小写）到已注册 feature。
- 参数名：先转小写后与 setting 名/别名匹配（`Setting.matchesName()`），**不区分大小写**，并支持源码里通过 `.aliases(...)` 声明的别名。

不匹配的 key 会被静默跳过。

## 4. Setting 类型 → JSON 值格式

各 `Setting` 子类的序列化格式（`toJson()` / `fromJson()`）：

| Setting 类型 | JSON 值 | 示例 |
| --- | --- | --- |
| `SliderSetting` | number（double，四舍五入到 1e-6） | `"Range increase": 1.2` |
| `CheckboxSetting` | boolean；有子项时是对象 | `"Attack cooldown": true` |
| `CheckboxSetting`（带子项） | `{ "checked": bool, "children": {...} }` | — |
| `EnumSetting` | string（枚举常量名） | `"Rotation mode": "Silent"` |
| `ColorSetting` | string（hex，如 `#RRGGBB`） | `"Color": "#FF0000"` |
| `BlockSetting` | string（方块 ResourceLocation） | `"minecraft:stone"` |
| `ItemListSetting` | `"default"`，或 string 数组 | `["minecraft:diamond"]` |
| `TextFieldSetting` | string | `"Prefix": "."` |
| `FileSetting` | string（文件名） | `"myconfig.json"` |

### 4.1 范围与锁的注意点

- `SliderSetting.fromJson()` 会校验数值是否落在 `[minimum, maximum]`，**超范围的值会被忽略**，不会生效。
- `CheckboxSetting.fromJson()` 走 `setCheckedIgnoreLock()`，因此 profile **可以覆盖被锁（CheckboxLock）的开关**。
- `SliderLock` / `CheckboxLock` 本身不参与序列化（锁是运行时状态）。

## 5. `.settings` 命令

```text
.settings load-profile <file>    # 加载 profile（文件名自动补 .json）
.settings save-profile <file>    # 保存当前设置为 profile
.settings list-profiles [<page>] # 列出 profile（每页 8 个）
```

- profile 存于 `.minecraft/wurst/settings/`。
- `load-profile` 加载成功后立即写回主配置 `settings.json`。

## 6. profile 加载流程

`SettingsCmd.loadProfile` → `WurstClient.loadSettingsProfile` → `SettingsFile.loadProfile()`：

1. 校验文件名以 `.json` 结尾。
2. `JsonUtils.parseFileToObject` 解析 JSON。
3. 遍历顶层每个 feature，按名找到已注册 feature，再逐项 `fromJson`。
4. `save()` 把合并后的结果写回主配置。

因此 profile 是**增量/覆盖式**的：只写需要改的功能与参数即可，未出现的功能保持当前值。

## 7. Hack 参数说明

参数名 = 源码中 `new XxxSetting("参数名", ...)` 的第一个字符串。例如 `KillauraHack.java` 里的：

```java
private final SliderSetting rangeIncrease = new SliderSetting("Range increase", ...);
private final EnumSetting<RotationMode> rotationMode = new EnumSetting<>("Rotation mode", ...);
```

对应 JSON：

```json
{
  "Killaura": {
    "Range increase": 1.2,
    "Rotation mode": "Silent",
    "Minimum CPS": 5,
    "Maximum CPS": 8,
    "Attack cooldown": true,
    "Check line of sight": false
  }
}
```

`EnumSetting` 的值是枚举常量名（`toString()`），如 `RotationMode` 的 `"Silent"`、`"Normal"`；具体可选值见各 hack 源码里的枚举定义。

## 8. 手写 profile 要点

1. 顶层只写目标功能名，其余功能不动。
2. 参数名与源码 `Setting` 的 name/alias 一致（不区分大小写）。
3. `SliderSetting` 数值必须在源码声明的 min/max 内，否则被忽略。
4. `EnumSetting` 必须用合法枚举常量名，非法值会抛异常并跳过该项。
5. 文件名以 `.json` 结尾，放入 `.minecraft/wurst/settings/` 后用 `.settings load-profile <名>` 加载。
