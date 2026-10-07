# Wallpaper Engine 场景包（scene.pkg）

> 范围：v1.6 根目录 Forge 1.20.1 工程。代码在 `net.wurstclient.background`：
> `WePackage` / `WeTexture` / `WeScene` / `WeSceneLayout` / `WeSceneWallpaper`。
> 本文记录**格式是怎么推出来的**、**哪些部分已经逐字节核对过**，以及
> **哪些部分只有静态图层**。

## 1. 为什么要自己读

Wallpaper Engine 的 Scene 型壁纸本体是一个 `scene.pkg`：里面有图层树（JSON）、
贴图（`.tex`）、GLSL 着色器（`.frag` / `.vert`）和音频。它没有公开规范，官方运行时
是闭源的。本项目的做法是**只渲染静态图层树**——读包、解析图层、把每张贴图解出来按
位置画上去——效果类着色器（godrays / 模糊 / 胶片颗粒 / 水波）一律不跑。

参照物是工坊 2359043440「Persica」（4K 樱花枝），它已经装在开发机上，所以每一步都能
拿真实字节核对。

## 2. 包格式（已核对）

```
[int32 长度]["PKGV0013"]
[int32 条目数]
每条：[int32 名字长度][名字 UTF-8][int32 偏移][int32 长度]
```

**条目里的偏移是相对「条目表结束处」的**，不是相对文件头。漏掉这个基准的后果不是
报错，而是把整包内容读成随机字节。Persica 的实测值：`PKGV0013`、52 个条目、表尾
偏移 2407，条目首尾严格相接（`rel=0 len=821`、`rel=821 len=21192`、
`rel=22013 …`）。

## 3. `.tex` 格式（已核对）

拿五张贴图逐字节比对后得到的布局：

```
"TEXV0005\0"          9 字节，NUL 结尾
"TEXI0001\0"          9 字节，NUL 结尾
int32 format          RGBA8888 时为 0
int32 flags           实测 2
int32 textureWidth    显存尺寸，2 的幂（4000 -> 4096）
int32 textureHeight
int32 imageWidth      真实像素尺寸
int32 imageHeight
int32 校验字段         高字节恒为 ff，低三字节随文件而变
"TEXB0003\0"          9 字节，NUL 结尾
int32 x8              x[3]=宽 x[4]=高，x[7]=载荷长度
载荷                   x[7] 字节，正好到文件末尾
```

两个结论值得单独记下来，因为**第一版两条都判断反了**：

1. **字符串是 NUL 结尾，不是长度前缀。** 按长度前缀去读时，`TEXV0005` 后面紧跟的
   `00` 被当成「长度为 0 的字符串」，于是解析器认为这不是贴图。当时的排查结论是
   「整个 14 MB 文件里搜不到 `TEXV`，新版 WE 换了容器」——错的。判别办法很简单：
   版本串后面那个 `0x00` 就是终止符。
2. **载荷就是一张完整的 PNG / JPEG。** `format == 0` 时五张贴图的载荷分别以
   `89 50 4E 47`（PNG，4 张）和 `FF D8 FF`（JPEG，1 张）开头，`dataSize` 与
   「文件长度减去载荷起点」**严格相等（tail = 0）**，Pillow 解出来的尺寸正好等于头部
   的 `imageWidth x imageHeight`（4000x2250 / 2000x1125）。所以**不需要 DXT 解压**，
   直接把这段字节交给 `NativeImage.read` 即可。代码里 `isStandardImage()` 按魔数
   判断，不去信 `format` 的取值表。

未核对的：`format` 的其它取值（社区资料里的 DXT1/3/5）没有真实样本，所以
`WeTexture` 只保留实测到的 `FORMAT_RGBA8888`；`TEXB0003` 之外的容器版本布局不同，
一律抛「暂不支持的贴图容器」而不是照 v3 猜。

## 4. 图层树（已核对）

`scene.json` 里真正决定画面的是三个地方：

```json
"general": {
  "orthogonalprojection": { "width": 3840, "height": 2160 },
  "zoom": 1.0,
  "cameraparallax": true,
  "cameraparallaxamount": 0.5
}
```

- **画布是作者自己定的**，写在 `orthogonalprojection` 里（Persica 是 3840x2160，
  也就是作者在 4K 上摆的版），对象坐标以画布左上角为原点、**y 向下**，
  `alignment: center` 时 `origin` 是图层中心。
- 每个要画的对象是 `image` → `models/*.json` → `materials/*.json` →
  `passes[].textures[0]` 这条链，最后那个裸名字在包里对应
  `materials/<名字>.tex`。

Persica 的 20 个对象里只有 **3 个真的要画**（`Blossom backdrop`、`backdrop flowers`、
`Branch`），其余是 17 个分隔用的全屏层（引用 `models/util/fullscreenlayer.json`，这个
模型不在包里，查不到就被跳过），加上粒子层（雪）、时钟 / 日期文字层和一条 mp3。

## 5. 绘制

- **cover 缩放**：`max(屏宽/画布宽, 屏高/画布高) * zoom`，任何宽高比都铺满、不拉伸，
  和静图那条路的 `object-fit: cover` 语义一致。
- **位置**：`屏幕中心 + (origin - 画布中心) * scale`，尺寸是
  `size * 图层 scale * scale`；`autosize` 且没写 `size` 时用贴图自身尺寸。
- **视差**：`parallaxDepth` 是「鼠标移动时这一层跟多少」，0 不动、1 与鼠标 1:1。这里
  取 `-depth * amount * influence * 鼠标相对屏幕中心的偏移`（屏幕像素）。Persica 的
  深度是 0.05 / 0.07 / 0.09，`amount` 0.5，所以鼠标跨过半个屏幕时三层分别错动约
  15 / 21 / 27 像素——看得出层次，但不会像贴纸一样乱跑。
- **顺滑（踩过的坑）**：第一版直接把位置 `Math.round` 到整数像素，结果是**一顿一顿**的。
  原因不难算：深度 0.05～0.09 再乘 `amount` 0.5，鼠标每动一个逻辑像素图层只该动
  0.025～0.045 像素，取整会把这点位移全部吞掉，攒够一整像素才跳一次。修法是两点：
  ① **亚像素定位**——位置取下整后把小数部分交给模型矩阵（`pose().translate(fx, fy, 0)`
  再 `blit` 整数坐标），顶点矩阵是浮点的，位移就连续了；② **阻尼跟随**——目标位移按帧
  时间做指数逼近（`alpha = 1 - exp(-dt / tau)`，`tau` 来自场景的
  `cameraparallaxdelay`，上限 0.25 秒，Persica 因此是 0.125 秒），并且与帧率无关：
  一帧拆成两半跑两次和整帧跑一次结果相同（`WeSceneLayoutTest` 里有这条用例）。
  第一帧直接贴到目标值，免得刚进主界面时所有图层从中心滑出来。
- **上传**：解码在后台线程（`WeSceneWallpaper.decode`），`DynamicTexture` 的构造与
  注册回到客户端线程（`bind`），和 GIF 那条路径同一套规矩。
- **像素所有权**：`Decoded` 用「取走一张就摘掉一张」的方式把 `NativeImage` 交给纹理，
  这样上传中途失败时，`close()` 只会关掉还没人接手的那几张，**不会重复释放**已经交给
  纹理的像素。

## 6. 有意没做的（如实说明）

1. **不套用场景里记录的相机机位。** Persica 的 `camera.eye` 是 `-213.6, -20.9`，那是
   编辑器里的取景；照它平移会让 4000 宽的底图偏出 3840 的画布、右边缘露出
   `clearcolor`（黑）。作者自己的工坊缩略图也更接近不套用机位的取景。
2. **不跑效果着色器**：godrays、blurprecise、filmgrain、waterwaves 都是 GLSL 后期，
   静态渲染只画基础图层。所以同样的场景**比 Wallpaper Engine 里淡一些**（少了光线与
   泛光），颗粒与水波也不动。包里的 `shaders/effects/*.frag|vert` 已随包解出，接线
   属于后续工作。
3. **粒子（雪）已实现，但只按预设的 sprite 语义画**：`WeParticlePreset` 读
   `particles/presets/*.json`（速率、寿命、尺寸、速度、颜色、摆动、淡入、上限），
   `WeParticles` 是纯模拟，画的时候用运行时生成的柔光点做加性混合。两点如实说明：
   ① 预设引用的 `particle/chromaticdot` 是 Wallpaper Engine 的内置资源，包里没有，
   所以光点是自己生成的（中心白、`(1-r)^2` 衰减）；② 预设的尺寸是 2～30 **画布单位**，
   乘上 cover 比例（1080p 下 0.169）后屏幕上只有 0.3～5 像素——原版也是这么小，
   所以它是"远处的细雪"而不是雪花特效。
4. **预设空间是 y 向上**，画布是 y 向下：`camera.up = "0 1 0"` 说明 Wallpaper Engine
   的世界 y 向上，所以雪的初速度 y 是负的（往下落）。画的时候翻了符号，不翻的话
   雪会往上飘。
5. **时钟 / 日期文字层不画**、**音频不播**。
6. **`WeTexture` 只认 `TEXB0003`**：其它容器版本会被拒绝，那一层就跳过。**这条的
   实际代价已量化**（2026-10-07，用户库里 83 个场景包、每个取第一个 `.tex` 的内层
   容器串）：`TEXB0003=64`、**`TEXB0004=17`**、`TEXB0002=1`。也就是说约 **22% 的
   场景包**至少有一层的贴图会被拒掉——最坏情况是整幅空场景。这一条是当前"场景看起来
   不对/缺东西"的头号嫌疑，不是边角情况。
7. **场景卡片不生成缩略图**：`scene.pkg` 不是图片，导入时用它的 `preview`（
   `Candidate.thumbnail`），没有预览图就是一张空卡片。

## 6.1 贴图载荷格式（当前进度，2026-10-07）

**权威来源**：`linux-wallpaperengine` 的
[`docs/textures/TEXTURE_FORMAT.md`](https://github.com/Almamu/linux-wallpaperengine/blob/main/docs/textures/TEXTURE_FORMAT.md)
（2021 年就写下的逆向文档）。**下面这张表取代了本文件早先那版从 RePKG 推的映射 ——
那版是错的**（它写 3/4/5 = DXT5/DXT3/DXT1，与实测出现的 format 值完全对不上）。

### 头部（`WeTexture` 已按这个读）

```
TEXV0005\0  (9)   TEXI0001\0  (9)
  Texture type      4   ← 见下表；偏移 18
  Texture flags     4   （1=Interpolation, 2=ClampUVs, 4=IsGif）
  Texture Width     4   （显存宽度）
  Texture Height    4
  Width             4   （图像宽度）
  Height            4
  Unknown           4
  Container version 8   TEXB0003 / TEXB0002 / TEXB0001
  \0                1   → 容器名在 46 结束于 55
```

### Texture type（**权威映射**）

| 值 | 编码 |
|---:|---|
| **0** | ARGB8888 |
| **4** | DXT5 |
| **6** | DXT3 |
| **7** | DXT1 |
| **8** | RG88 |
| **9** | R8 |

### 容器内容（**这才是之前一直读错的地方**）

```
TEXB0003:  Unknown(4) + FreeImageFormat(4) + MipLevels(4) + Mipmap entry × MipLevels
TEXB0002/0001:      Unknown(4) + MipLevels(4) + Mipmap entry × MipLevels

Mipmap entry:  Width(4) Height(4) CompressionFlag(4)
               UncompressedSize(4) CompressedSize(4)  Pixels(CompressedSize)
```

**关键**：容器名之后**不是**像素，而是一张 **mipmap 条目表**，每条带 **20 字节头**。
这一点精确解释了实测的魔数位置：`55 + 12 + 20 = 87`（TEXB0003 的 JPEG 落在 87）✓，
TEXB0004 多 4 字节 → `91` ✓。也解释了为什么"8/9 个 int32 之后就是载荷"会读出
`512×512` 只有 6105 字节这种荒唐结果 —— 那 6105 字节是**条目的头部与压缩数据**，
不是未压缩像素。

`CompressionFlag` 非零时 `Pixels` 是**压缩**数据，长度由 `CompressedSize` 给出、
解开后应是 `UncompressedSize`。**压缩算法还没确认**（zlib 解不开，见下）。

### 仍未解决

1. **压缩算法**：实测这些载荷既不是 PNG/JPEG（全文件都没有魔数），也不是 zlib
   （`Inflater` 解不开），头字节形如 `ff 01 00 ff 49 92 24 …`。`CompressionFlag`
   非零时用的是什么编解码，还得看参考实现的解压代码。
2. **TEXB0004 比 TEXB0003 多出的 4 字节**落在哪一段（容器块还是 mipmap 条目头）。

### 规范已用真实包验证（2026-10-07，392 张 `.tex`）

按上面的结构自己解析头部与第 0 条 mipmap 条目，结果与规范**完全吻合**：

```
TEXB0002 type=0 img=128x512   entry=128x512  flag=1 uncomp=262144   comp=64859   pixels@83
TEXB0003 type=0 img=1600x1737 entry=1600x1737 flag=0 uncomp=0       comp=1416601 pixels@87
TEXB0002 type=0 img=1920x1080 entry=2048x2048 flag=1 uncomp=16777216 comp=376140  pixels@83

392 张：条目尺寸与头一致=341  UncompressedSize 对上=186  flag!=0=226  像素处是 PNG/JPEG=151
```

- **像素起点对上了**：`TEXB0002` 在 `55+8+20 = 83`、`TEXB0003` 在 `55+12+20 = 87`，
  与规范逐字节一致（`TEXB0004` 多 4 字节 → 91，与实测一致）。
- `entry.Width/Height` 有时比 `imageWidth/Height` 大（如 1920x1080 → 2048x2048），
  那是 **GPU 用的 2 的幂尺寸**，不是解析错误。
- `flag=0` 的条目里像素就是 **PNG/JPEG**（151 张命中魔数）—— 这正是今天能画出来的那部分。
- `flag=1` 时 `UncompressedSize` 是解压目标，且**数值精确等于 `entry宽×entry高×每像素字节`**
  （例：2048×2048×4 = 16777216 ✓，type=0 即 ARGB8888）。
- **`TEXB0002`/`TEXB0003`/`TEXB0004` 三种容器的像素偏移都已按规范核对通过。**

### 压缩算法 = LZ4 块格式（已从参考实现源码确认）

来源：`linux-wallpaperengine` 的
[`src/WallpaperEngine/Data/Parsers/TextureParser.cpp`](https://github.com/Almamu/linux-wallpaperengine/blob/main/src/WallpaperEngine/Data/Parsers/TextureParser.cpp)。
它 `#include <lz4.h>`，核心几行：

```cpp
result->compression      = file.nextUInt32 ();
result->uncompressedSize = file.nextInt ();
result->compressedSize   = file.nextInt ();
if (result->compression == 0)
    result->uncompressedSize = result->compressedSize;  // 未压缩：这字段其实是文件长度
...
if (result->compression == 1) {
    file.next (result->compressedData.get (), result->compressedSize);
    LZ4_decompress_safe (result->compressedData.get (),
                         result->uncompressedData.get (),
                         result->compressedSize, result->uncompressedSize);
} else {
    file.next (result->uncompressedData.get (), result->uncompressedSize);
}
```

**所以 `CompressionFlag == 1` 的那 226 张用的是 LZ4 块压缩**（不是 deflate —— 与实测吻合），
解压目标长度就是 `UncompressedSize`；`= 0` 时数据原样存放（那 151 张就是 PNG/JPEG）。

### 容器块逐字段（与实测偏移完全一致）

魔数之后读这些 int32，**数量随容器版本变**，这正是"第 9 个字段"一直读错的原因：

| 容器 | int32 数 | 字段 |
| --- | ---: | --- |
| `TEXB0001` / `TEXB0002` | **7** | imageCount, mipmapCount, width, height, compression, uncompressedSize, compressedSize |
| `TEXB0003` | **8** | 上面再加一个 freeImageFormat |
| `TEXB0004` | **9** | 再加一个 `isVideoMp4` |

实测像素起点与之一一对应：`TEXB0002 = 55+28 = 83`、`TEXB0003 = 55+32 = 87`、
`TEXB0004 = 55+36 = 91`。**所以"TEXB0004 多出的那 4 字节"就是 `isVideoMp4`，它在容器块里、
不在 mipmap 条目里。**（补充：源码在 `freeImageFormat != FIF_MP4` 时会把 TEXB0004
**降级按 TEXB0003 解析**；只有 mp4 贴图才走每条目额外的
`2×u32 + 以 NUL 结尾的 json 字符串 + u32` 那段。）

`WeTexture` 现在跳过的 8/9 个 int32 恰好等于 容器块 + 20 字节条目头，所以它的
`dataOffset` **本来就落在像素上**、`fields[最后一个]` **本来就是 `compressedSize`** ——
这两点此前被我误判为"读错了"。

### 仍未做

1. **mp4 贴图那一条分支**：`flow萤 健全_60帧_batch`（format=0、2460×2688、载荷 15,729,705
   字节）仍被跳过。源码里 `TEXB0004` + `freeImageFormat == FIF_MP4` 时容器**不降级**，
   且**每个 mip 条目**前面多出 `2×u32 + 以 NUL 结尾的 json 字符串 + u32` 这段 ——
   非 mp4 的 TEXB0004 会降级按 0003 解析（这也解释了为什么实测非 mp4 的 TEXB0004
   像素点正好在 91、没有 json）。这一段还没实现。
2. 没有画面截图（见下）。

### 已实现并验证（2026-10-07）

- **容器解析**：`TEXB0001/0002 = 7`、`0003 = 8`、`0004 = 9` 个 int32，末三个是
  `compression / uncompressedSize / compressedSize`。
- **LZ4**：`compression == 1` 时在 `WeTexture.parse` 里就地解压（`Lz4Block`，自写）。
- **format 映射**：`0=ARGB8888`、`4=DXT5`、`6=DXT3`、`7=DXT1`、`8=RG88`、`9=R8`。
- **尺寸判据**：同时试 `imageWidth/Height` 与 `textureWidth/Height`。后者是 GPU 的 2 的
  幂填充尺寸，实测大量贴图（如 `树叶` 1024×885 的实际载荷是 1024×1024 的 DXT5 =
  1,048,576 字节）只有按它算才对得上。

**实机结果**：流萤场景（工坊 `3292361861`）从 **2 layers 提升到 21 layers**（共 23 层），
日志只剩 1 层被跳过（就是上面那条 mp4 分支）。

**正确性证据（不依赖截图）**：对真实包逐层量"水平相邻像素的平均绝对差" —— 真实图像
高度相关，这个值应很小；解错的数据会像噪声（通常 80 以上）。实测二十余张全部落在
真图像范围：法线图 `waterripplenormal` 是 11.52、`text_viss0` 0.95、`opacitymap1` 0.17、
各种纯色 UI/遮罩 0.00~0.29，**没有一张呈噪声特征**。纯色图接近 0 是正常的。

**没有画面确认的原因**：改完之后 F2 截图全部失败（启动脚本重试 8 次、缩放脚本 4 次，
之后手动再敲一次并等 60 秒仍无新图；`run/screenshots` 里最新的一张还是改动之前
02:41 的）。进程 `responding=True`、窗口缩放消息也都被处理，所以不是硬冻结，更像是
**窗口没拿到前台焦点**（MC 只在窗口聚焦时处理按键），也不排除 21 层大贴图把帧时间拖长。
这一步没查完，所以"21 层画面对不对"目前只有层数与相邻像素两项间接证据。

### 画面确认（已解决，2026-10-07）

上面那条"没有画面确认"的根因**不是本项目的问题**：实测 `GetForegroundWindow()` 返回
**0**，也就是当前会话里**没有任何窗口处于前台**（锁屏 / 非交互桌面的典型状态）。MC 只在
`Window.isFocused()` 为真时处理按键，所以 F2 根本进不到游戏里 —— 这也解释了为什么
02:41 之前能截图、之后全部失败。

**换一条不依赖前台的抓取路径就解决了**：`PrintWindow(hwnd, hdc, 2)`
（`PW_RENDERFULLCONTENT`），把窗口客户区画进位图再存 PNG。实测 1296×672 的客户区
采样 3080 点里 2993 点非黑、1376 种颜色 —— 抓到的是真实内容，不是黑图。
以后再要实机画面证据，用这条，不要再折腾 `SetForegroundWindow` / F2。

**画面结论**：流萤场景渲染正常 —— 海天背景、左侧人物（肤色正常）、场景自带的灰色 UI
面板、底层 MC 标题界面，**画面里没有噪声**。这同时是**通道序正确的证据**：若 ARGB8888
或 DXT5 的通道序搞反，肤色会偏蓝、海天会偏橙，一眼可见。至此 21 层的画面得到确认。

### 订正："流浪地球那个场景 52 层只画出 1 层" —— 这是我的错误估计

实测这个包（工坊 `1646702957`）**只有 83 个条目，其中 `.tex` 只有 6 个、`.json`
有 72 个**（材质定义），而 `scene.json` 解析出来**本来就只有 1 层**：

```
包内条目 83 个，场景层 1 个
解析成功 1 / 失败 0
条目后缀：.tex=6  .json=72  其它=5
```

那一层引用的是 `materials/流浪地球全景.tex`，**一层就是整幅 4K 全景图**。

**画面确认（`PrintWindow` 抓的）**：雪原、城市、天空、飞船、行星全都在，画面完整
清晰 —— **这个场景渲染完全正常，没有任何层被丢弃**。此前的"52 层"是我自己的错误
估计（日志里从来只报 `1 layers`，而这个数字是对的）。

**顺带记下一个真实的、但与贴图载荷无关的差距**：这类场景里有大量 `.json` 材质定义，
对象通过**材质**引用贴图；`WeScene.parse` 目前不解析材质系统，所以走材质那一路的
对象不会变成可画图层。这属于**场景图/材质系统的功能缺口，不是载荷格式问题**，
不在本文件讨论的范围内。

### 全库覆盖普查（2026-10-07）

用**真实解码路径**（`WeTexture.parse` → 按 format 解 → 长度精确判据）遍历工坊里
30 个场景包、每包最多采样 6 张 `.tex`，抽样 87 张：

```
结果分类: {标准图像 PNG/JPEG=36, 裸像素/DXT 解出=26, 按填充尺寸解出=5, 太大未试=20}
format 分布: {f0=53, f8=19, f9=15}
失败原因: {}          ← 一条都没有
```

**46 张解出 + 5 张按填充尺寸解出 + 36 张走了标准图像那条路 = 87 张全部有交代**，
没有任何"长度对不上"、也没有解析失败。20 张"太大未试"是**探针自己的体积上限**
（单文件 6MB、单词 150 万像素）挡掉的，不是解不出来 —— 因为测试 JVM 的堆很小，
不设上限会直接被 OOM 打死（试过两次）。

**这份抽样有两个偏差，得说清楚**：

1. 它没抽到 DXT 那几种（`f4/f6/f7`）—— 多半是被 6MB 上限滤掉，以及"每包取前 6 个
   `materials/*.tex`"的取法偏向了小文件。不过 DXT 那条路另有证据：实机 21 层里
   就有多张 DXT5（`树叶`、`opacitymap1`、`text_viss0`），且逐张量过相邻像素差
   （0.17 / 0.95 等，都在真图像范围）。
2. mp4 贴图也没抽到（它们单文件十几 MB，同样被上限挡掉），但那条路已在实机里
   跑到 23/23 层验证过。

### 按显示尺寸降采样图层贴图（2026-10-07 做成）

**动机**：实测某场景给一根钟表指针用了 2000×2000 的贴图，屏幕上只有 500 见方 ——
`dot_top` 更离谱，2048×2048 画成 200×200，**浪费 105 倍**。六层指针/圆点/表盘合计
29.9M 像素，把 64M 的预算吃掉一半，4 层直接被挤掉。官方文档也承认这类浪费
（建议作者导入时把图层裁剪到最小），但**已发布的素材改不了，只能在渲染端补**。

**做法**（`LayerResampler` + `WeSceneWallpaper.shrink`）：

- 显示尺寸 = `size × scale`（画布单位）。**这个 `scale` 是整件事的关键**：第一版只拿
  `size`（2000）去比，2048 的贴图"看起来"不比它大，安全阀永远不放行、收益为零。
- 目标边长 = 显示尺寸 × `HEADROOM`(1.25)，倍率夹在 `MIN_FACTOR`(2) 与 `MAX_FACTOR`(16)
  之间。留 1.25 倍余量是因为屏幕尺寸、scene zoom、视差位移都会把实际显示顶上去。
- 面积平均降采样，**alpha 按预乘处理**（直接平均 R/G/B 会让半透明边缘渗色）。
- **安全阀**：场景没声明 `size` 时一律不动 —— 那时 `WeSceneLayout.rect` 是拿贴图尺寸
  当显示尺寸的（`sizeX > 0 ? sizeX : textureWidth`），缩了会把图层一并画小。有单测钉住。

**实机结果**（绪山真寻-0004测试）：

```
「dial」1700x1700 -> 531x531（显示 425x425，省 91%）
「hand_hour」/「hand_minute」/「dot_middle」2000x2000 -> 625x625（显示 500x500，省 91%）
「hand_seconds」2818x2818 -> 881x881（显示 705x705，省 91%）
「dot_top」2000x2000 -> 250x250（显示 200x200，省 99%）
Scene 绪山真寻-0004测试: 26 layers          ← 从 22 层涨到 26 层，不再有预算跳过
```

六层贴图合计 **29.9M 像素 → 2.75M 像素**，省下约 27M 像素（≈108 MB），正好把原先被
挤掉的 4 层腾了出来。

**画面不变**：同一场景降采样前后各用 `PrintWindow` 截一张逐像素比 —— 采样 96768 点，
**平均差 0.02/765**（最大差 200 出现在一条细边上，属重采样位移一个像素），视觉上无差别。

### 全部已导入场景的实机普查（2026-10-07）


库里共 8 个场景壁纸，全部逐个上机验证过（设置背景 → 启动客户端 → 读 `[Background]`
日志 → `PrintWindow` 截图）：

| 场景 | 层数 | 预算跳过 | 画面 |
| --- | ---: | ---: | --- |
| `4k-customizable-where-dream-has-been-夢が残した足跡` | **33** | 1 | ✅ 窗景/城市/桌椅正常 |
| `互动观赏双模式…流萤…崩坏星穹铁道` | 21（64M）/ 23（128M） | 2 | ✅ 海天/人物/UI 正常 |
| `绪山真寻-0004测试` | 22 | 4 | ✅ 人物/便利店街景正常 |
| `绪山真寻-纯0004` | 22 | 4 | ✅ 同上 |
| `4k动态音乐壁纸-流浪地球-the-wandering-earth-工坊测试` | 1 | 0 | ✅ 4K 全景完整 |
| `4k-relaxing-ganyu-genshin-impact-原神-工坊测试` | 1 | 0 | ✅ |
| `neutron-star-animated-4k-wallpaper-0004测试` | 1 | 0 | ✅ |
| `persica` | — | — | ✅ 此前手工适配的那个 |

**8 个里没有任何一个退回内置背景**（日志里没有「场景包读不出来」或「场景里没有可画的
图层」）。其中 `绪山真寻` 系列特别值得一提：它们此前被 64M 像素上限**整幅判死**，
现在能画出 22 层、只跳过 4 层 —— 这正是把上限从「throw」改成「跳过超预算的层」的收益。

层数从 1 到 33 不等是**场景本身的差异**，不是渲染失败：`流浪地球`／`原神`／
`neutron-star` 只有 1 层，因为那一层就是整幅图（例如 `materials/流浪地球全景.tex`）。

**批量验证脚本的两个坑**（下次直接避开）：
1. 脚本里有中文时**必须带 UTF-8 BOM** —— 否则 Windows PowerShell 5.1 按 GBK 读，
   中文字符串被拆坏、整个脚本语法错误（与 `docs/CLIENT-LAUNCH.md` §五 记的是同一条）。
2. 判据要用 `[Background]` 日志里的 `N layers` 行 + 截图，**不要只看"有没有报错"** ——
   少画几层是静默的（预算跳过那行是后加的）。

**被预算跳掉的到底是什么**（现已逐层打日志）：拿 `绪山真寻-0004测试` 看，

```
预算不够，跳过「hand_minute」：2000x2000（累计已 60M 像素）
预算不够，跳过「hand_seconds」：2818x2818
预算不够，跳过「dot_middle」：2000x2000
预算不够，跳过「dot_top」：2000x2000
```

全是**钟表的指针与圆点**。也就是说美术给一根指针用了 2000×2000~2818×2818 的贴图，
四层加起来 20M 像素（约 80 MB），而它们在屏幕上只有几根细针那么细。**预算在这里做得
完全正确**：它牺牲的是装饰性小元素，保住的是 22 层主体内容。

**由此看出一条真正的优化方向**（尚未做）：按**显示尺寸**而不是原始尺寸上传图层。
一根屏幕上约 100 像素的指针不需要 2000×2000；若按显示尺寸降采样，显存会大幅下降，
更多层也就能同时画出来。场景的图层几何里本来就有显示尺寸信息，所以这是可行的 ——
但需要自己写重采样（`NativeImage` 没有现成的缩放接口），不是小改动。

### 库里同名的两条不是重复，是同一作品的两种形态

排查时踩到一次：`run/wurst/backgrounds/` 里同时存在

```
4k-customizable-where-dream-has-been-夢が残した足跡         media.gif 941KB + thumbnail.png
4k-customizable-where-dream-has-been-夢が残した足跡-工坊测   media.pkg 25MB
4k动态音乐壁纸-流浪地球-the-wandering-earth                 media.jpg 117KB + thumbnail.png
4k动态音乐壁纸-流浪地球-the-wandering-earth-工坊测试          media.pkg 15.8MB
```

**这不是重复导入**：工坊作品里可以同时带 GIF/图片与场景（`.pkg`），导入器把它们各建了
一个条目，两条都合法。选中 GIF/图片那条时它按静态图静默加载（**不会有任何
`[Background]` 日志**），看起来就像"背景没生效" —— 我这次就是误选了它才拿到空日志。

**认条目的判据**：看目录里是 `media.pkg`（场景）还是 `media.gif`/`media.jpg`（图片），
别只看名字前缀。

### 帧率实测（2026-10-07）


场景渲染的每帧入口是 `WeSceneWallpaper.render(...)`，在它开头临时加了埋点（每 200 帧
统计一次），跑流萤那个 21 层场景得到：

```
[Background][tmp] 200 帧用时 3360ms → 59.5 fps
[Background][tmp] 200 帧用时 3355ms → 59.6 fps
[Background][tmp] 200 帧用时 3342ms → 59.8 fps
…（连续多窗口都是同一水平）
```

**稳定 59.5~59.8 fps**，即 vsync 锁在 60；每 200 帧的耗时波动只有 ±0.3%
（3342~3362ms），说明**没有偶发卡顿**。所以 21 层不是靠卡换来的，场景本身跑得动。

**两点保留**：① vsync 锁 60 时这个数字看不出"还剩多少余量"，只能说明**没有掉到 60 以下**；
要量余量得关 vsync，本次没做。② 这台机器上窗口始终没有前台焦点，MC 在失焦时的帧率
策略可能影响绝对值 —— 但方向上是**偏低**的，所以 59.6 不是虚高。

**埋点已撤掉**，仓库里不留测量代码。

### 像素预算实测：64M 与 128M（2026-10-07）


把 `WeSceneWallpaper.MAX_PIXELS` 临时从 64M 提到 **128M** 跑了一次，结论分两半：

**好的那一半** —— mp4 视频贴图的**实机集成确认跑通**（此前只有独立探针验证过）：

```
[Background] Scene 互动观赏双模式…流萤…: 23 layers, canvas 3840x2160
```

**23/23 层全部画出**，没有预算跳过日志；日志里没有 `OutOfMemory` 也没有任何异常；
`PrintWindow` 抓一次客户区只花 **15ms**，说明渲染没被拖住。

**需要权衡的那一半** —— 多出来的那 2 层在这里**并不更好看**：它们是**蓝幕素材**
（文件名里的「蓝幕」就是给抠像用的蓝底），WE 那边靠材质的 chroma-key 把蓝底去掉，
而本项目没有材质系统，所以画出的是**未抠像的蓝幕帧** —— 画面上多出一块蓝色矩形。

**因此默认值保持 64M**：多画两层换来一块蓝幕矩形，不划算；而 64M（≈256 MB 显存）
是经过实测的内存护栏（此前「绪山真寻」就是被它从"整幅场景判死"救成"画出前面几层"）。
**要改的话 128M 是可用的**（实测无 OOM、无卡顿），但那是显存换图层数的取舍，
不是修 bug —— 等材质系统做出来、蓝幕能被抠掉之后，再提上去才有意义。

**官方文档事后给了这个问题一个明确答案（2026-10-07 补）**：Wallpaper Engine 自己的
设计文档
[Texture Optimization](https://docs.wallpaperengine.io/en/scene/performance/texture.html)
写着

> we recommend using around **300 MB VRAM or less** for great performance. Using less
> than **500 MB** is also still acceptable but you should try and **not exceed this limit**

对照下来：**64M 像素 = 256 MB，正好落在官方推荐的 ~300 MB 以内**；而 **128M 像素
= 512 MB，已经越过官方"不要超过 500 MB"的线**。所以这个取舍不必再讨论 ——
**保持 64M 是有官方依据的**，不是保守。

同一页还印证了另外两件我们早先靠实测推出来的事：

1. **DXT5 / DXT1 只占未压缩格式的四分之一显存**（1 字节/像素 vs 4），与
   `WeTexture` 里那张每像素字节表一致。
2. **2 的幂填充是 WE 自己的行为**：原文说 DXT5/DXT1 贴图分辨率必须是 2 的幂，
   "Wallpaper Engine will quietly take care of this in the background and add invisible
   pixels"。这正解释了实测看到的 `树叶 1024×885 → 按 1024×1024 存`、
   `preview 1080×1080 → 按 2048×2048 存` —— 那是它的约定，不是我们解析错。
3. 官方明说 **GPU 不支持直接渲染 JPEG/PNG，WE 必须先转换** —— 与 `.tex` 里
   `compression == 0` 存 PNG/JPEG、`compression == 1` 存 LZ4 压缩的裸像素/DXT 对应。

顺带一提：官方建议作者在导入时**把图层裁剪到最小尺寸**（含去掉 padding），而
「一根钟表指针用 2000×2000」正是没做这件事的结果 —— 那属于素材没优化，
不是渲染端的问题。渲染端若要做，就是我上面记的"按显示尺寸上传图层"。

**取规范的可网络通道**（这台机器上实测）：`raw.githubusercontent.com` **不通**、
`wallpaper-engine.fandom.com` 与 `raw.githack.com` **不通**、`deepwiki.com` 返回 429、
`cdn.jsdelivr.net` 通但返回 `application/octet-stream`（抓取工具拒收）。
**能用的是 GitHub API**：

```
https://api.github.com/repos/<owner>/<repo>/contents/<path>     # JSON + base64
https://api.github.com/repos/<owner>/<repo>/commits/<sha>        # 含 files[].patch
```

后者尤其省预算：一个 commit 的 diff 就能拿到关键表格。别拿它列大目录。

## 7. 验证状态（诚实声明）

**已经在真实文件上跑通的**（`WeSceneRealFileTest`，文件不在时自动跳过）：

- `WePackage` 解析 14 MB 的真实包：版本、条目表、偏移基准；
- 真实 `scene.json` 解析出 3 个图层，名字与顺序与作者工程一致；
- 每层的贴图名都能在包里解析到 `materials/*.tex`，载荷都是 PNG/JPEG，且
  解出的像素尺寸与图层声明的 `size` 完全一致（4000x2250 / 2000x1125）。

**只做过 CPU 端合成比对**：把三层按上面的公式合成到 3840x2160，与作者工坊缩略图
（250x250，方形中心裁切）对照，取景一致；两者的差异主要来自缩略图带的效果与相机
机位，没有把缩略图当作像素级基准。

**已经在游戏里跑通**：dev 客户端（`gradlew runClient`）主菜单实际渲染出该场景，日志
`[Background] Scene persica: 3 layers, canvas 3840x2160`，配色与构图与上面的 CPU 合成
一致，见 [wallpaper-engine-scene-ingame.png](wallpaper-engine-scene-ingame.png)（游戏自己的
F2 截图，缩放到 1296x672）。

**视差的「手感」没有客观测到**：自动化截图期间这台机器上有人在用鼠标，游戏窗口一拿到
焦点，鼠标事件就进了游戏，于是「鼠标不动连拍两张」的基线本身就有几十像素的位移，
所有基于静止帧的测量都被污染了。所以亚像素定位与阻尼只有单元测试（帧率无关性、收敛、
长时间卡顿截断）与机制层面的依据（`GuiGraphics.blit` 走模型矩阵，仓库里
`GuiIcon.drawRotated` 就是「小数 translate + blit」的现成先例），最终手感要靠人眼确认。

**没有在 Wallpaper Engine 里逐帧对照过**，也没有实现效果层，所以「像不像原版」这件事
只能说到「构图层对得上」。

**雪只做到"弱正向"验证**：预设的 `starttime` 是 15 秒，等待到 30 秒后连拍三帧，天空
区域的亮斑峰值从 134 升到 157、亮点数从 5837 升到 6040，8 秒内有 16% 的亮点换了位置；
方向与量级都对，但雪本身只有 0.3～5 像素，肉眼与统计都很难把它和壁纸自身的白色花瓣
区分开，所以**没有做到逐颗对照**。
