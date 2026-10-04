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
3. **粒子层不画**（雪）、**时钟 / 日期文字层不画**、**音频不播**。Persica 的雪是
   `particles/presets/snowflat.json` 里 100000 个粒子的 sprite 系统。
4. **`WeTexture` 只认 `TEXB0003`**：其它容器版本会被拒绝，那一层就跳过。
5. **场景卡片不生成缩略图**：`scene.pkg` 不是图片，导入时用它的 `preview`（
   `Candidate.thumbnail`），没有预览图就是一张空卡片。

## 7. 验证状态（诚实声明）

**已经在真实文件上跑通的**（`WeSceneRealFileTest`，文件不在时自动跳过）：

- `WePackage` 解析 14 MB 的真实包：版本、条目表、偏移基准；
- 真实 `scene.json` 解析出 3 个图层，名字与顺序与作者工程一致；
- 每层的贴图名都能在包里解析到 `materials/*.tex`，载荷都是 PNG/JPEG，且
  解出的像素尺寸与图层声明的 `size` 完全一致（4000x2250 / 2000x1125）。

**只做过 CPU 端合成比对**：把三层按上面的公式合成到 3840x2160，与作者工坊缩略图
（250x250，方形中心裁切）对照，取景一致；两者的差异主要来自缩略图带的效果与相机
机位，没有把缩略图当作像素级基准。

**没有在 Wallpaper Engine 里逐帧对照过**，也没有实现效果层，所以「像不像原版」这件事
只能说到「构图层对得上」。
