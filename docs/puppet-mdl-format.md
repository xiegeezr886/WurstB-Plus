# 木偶网格 `.mdl` 的段格式（外部权威说明，抄录备查）

这一份**不是我校出来的**，是从一份独立实现的反向工程文档里抄来的，用来做「让眼睛眨眼」
那件事的依据。原文（含 010 editor 模板与逐字段出处）：

- `linux-wallpaperengine` 的 `docs/rendering/MDL_FILES.md`
  <https://cdn.jsdelivr.net/gh/Almamu/linux-wallpaperengine@master/docs/rendering/MDL_FILES.md>
  （只给了结构骨架：`MDLVHEADER` / `MDLSHEADER` / `BONEENTRY` / `MDLAHEADER`）
- `open-wallpaper-engine-mac` 的 `docs/models-plan.md`（**逐字段、带 WE 二进制里的地址**）
  <https://cdn.jsdelivr.net/gh/deepratna-awale/open-wallpaper-engine-mac@main/docs/models-plan.md>

> 网络情况：本机 `raw.githubusercontent.com` 解析不了（DNS），但 jsDelivr 镜像与
> `github.com` 都能取到，所以上面两个链接用 jsDelivr 的。

## 容器与段

- 小端 `u8/u16/u32/u64/f32`；`cstr` 是 NUL 结尾 UTF-8；`blob` = `u32` 长度 + 那么多字节。
- `MDLV` 在最前；从 MDLV 13 起是段循环：`cstr tag`、`u32 sectionEnd`（**绝对文件偏移**）、
  段体，然后跳到 `sectionEnd`。空 tag 表示文件结束。
- `MDLS`/`MDLA` 只比对前 4 个字符、版本号取 `atoi(tag+4)`；`MDAT0001`/`MDMP0001`/
  `MDLE0002` 要求完全匹配。未知段跳过。

## `MDLV`：网格

```
cstr tag "MDLVnnnn"; V = atoi(tag+4)
u32 legacyFormat（V<15 用）; u32 materialsPerMesh M; u32 meshCount
mesh[]: cstr material[M]
        V>=4: u32 flags（bit0: u32 索引；0x4: SKINNING_ALPHA；0x400/0x800/0x1000/0x2000: 追加 MDMP）
        V>=17: f32 aabbMin[3], aabbMax[3]
        V>=15: u32 format（否则用 legacyFormat）
        blob vertices; blob indices
        V>=21: u8 has; [u32; blob 12*vertexCount]   ← 贴图布局下的顶点位置
               u8 has; [blob 16*boneCount]          ← 每根骨骼的索引区间
        V>=23: u32 n; n × { u64 id; cstr name; u32 flags; ... }（groups）
```

顶点属性**按表序交织**，不是位序（实测步长表）：

| format | 属性 | 步长 | 库内网格数 |
| --- | --- | --- | --- |
| `0xf` | position, normal, tangent4, uv | 48 | 73 |
| `0x180000f` | position, normal, tangent4, blendindices, blendweights, uv | 80 | 52 |
| `0x1800009` | position, blendindices, blendweights, uv | 52 | 25 |
| `0x9` | position, uv | 20 | 20 |
| `0xb` | position, normal, uv | 32 | 10 |
| `0x27` | position, normal, tangent4, uvVec4 | 56 | 6 |

本项目 `WePuppetMesh` 认的 80 字节/顶点正是 `0x180000f` 那一行（位置/法线/切线/骨骼索引/
骨骼权重/uv 的偏移 0/12/24/40/56/72 与之逐项吻合）。

## `MDLS`：骨骼（版本 1–4）

```
u32 boneCount NB（≤128）
bone[NB]: cstr name; u32 flags; u32 parent（0xFFFFFFFF = 根，父在前）;
          capped(64) localBindMatrix（行向量 mat4，平移在 12..14）; cstr propsJSON
S>=2: links、可选绑定矩阵、约束、u32 键映射、IK 集合、可选的每骨骼 {vec3, mat4} 块、
      可选的每骨骼 u32 数组（= 绘制深度，带符号）
S>=3: 又一个可选的每骨骼 u32 数组
S>=4: 约束标志（flags & 2 时再跟两个 float）
```

- 世界绑定矩阵 = `local · world(parent)`（WE 的行向量顺序）。
- `propsJSON` 里是 IK 与骨骼物理的键（`ik`/`ikce`/`se`/`re`/`ge`/`gd`/`m`/`tf`/`lamin`/`lamax`/`rax`…）。
- 骨骼 flag `0x1` = 骨架骨骼；`0x2` = 约束挂在它的 link 上。

> 这一段正是我之前卡住的地方：我按 010 模板猜「9 字节头 + u32 长度」，怎么走都到不了
> `MDLA@522923`。真实布局是 **cstr 名字 + flags + parent + capped(64) 矩阵 + propsJSON**，
> 而且**长度可变**（`propsJSON` 长短不一），所以 203 字节那个"步长"只是前 9 根同长骨骼的巧合。

## `MDLA`：动画（版本 1/5/6）

```
i32 animCount
anim: u64 id; cstr name; cstr mode（"loop"|"mirror"|"single"）; f32 fps; u32 frames F; u32 flags
      i32 trackCount（= NB）
      boneTrack[NB]: u32 trackFlags（bit0 = 禁用，保持绑定姿势）; blob 36*(F+1)
      A>=2: link 轨道（36B/帧）与约束轨道（4B/帧）
      A>=3: 两个标量轨道列表；A>=4: 每网格的形变权重轨道；A>=5: f32[6] 动画 AABB；
      A>=6: 第三个标量轨道列表
      flags&1: u16 sourceAnim; u32 startFrame, endFrame, frameOffset; i32 rootBone（−1=无）
      i32 eventCount; eventCount × { f32 frame; cstr name }
```

- **每帧采样 = 9 个 float**：位置 xyz、**欧拉角 xyz（弧度）**、缩放 xyz → 载入时转成四元数
  `q = qz·qy·qx`（即 `R = Rz·Ry·Rx`，X 先转）。数据自证：第 0 帧的欧拉 z 等于绑定矩阵的
  `atan2(m01, m00)`（对每根骨骼都成立）。
- **播放**：时长 = `F / fps`；`mirror` 往返、`single` 播一次、其它循环。位置与缩放**线性插值**，
  旋转按**短弧 nlerp**（不是 slerp）。
- 库内统计：80 个动画 / 40 个文件；73 loop、6 mirror、1 single；fps 30（69 个）、60（5）、
  15（3）、24、7.25、6；帧数 1–450。

## `MDAT` / `MDMP` / `MDLE`

- `MDAT0001`：挂点。`u16 n` + `{u16 bone; cstr name; mat4 offset}`。
- `MDMP0001`：形变目标（blend shapes），6 字节/顶点的 16 位有符号归一化位置增量。
- `MDLE0002`：**参考姿势**，每根骨骼一个 local mat4（本项目实测 `真寻_眼2_puppet.mdl`：
  34 × 64 字节，首个矩阵是单位旋转 + 平移在**第 4 行** `[0, 13.7022, −538.9, 0]`，与这一节吻合）。

## 蒙皮与骨骼上限

- 顶点带 4 个 `u32` 骨骼索引 + 4 个 `f32` 权重（库内权重和恒为 1）。
- 着色器 `uniform mat4x3 g_Bones[BONECOUNT]`，`BONECOUNT = min(nextPow2(max(bones,16)), 128)`。
- WE 的硬失败只有三处：骨骼数 > 128、骨骼/采样 blob 尺寸不符、IK 与分组索引越界。

## 对本项目的意义（真寻_眼2）

- `MDLS0004`：34 根骨骼；`MDLA0006`：2 个动画，其中一个 `id = 138`、名字 `动画 1`、
  `mode = loop` —— 正是场景对象上 `animationlayers[0].animation` 指的那个。
- 于是「眨眼」= 按 `fps` 推进帧号 → 每根骨骼 9 个 float 插值 → 组装 local mat4 →
  `world = local·world(parent)` → 蒙皮矩阵 → 逐顶点混合 → 重新光栅化（9683 个三角形那套）。

## 本项目的落地进度（2026-10-15）

已经做到的：

- `WePuppetRig`（`src/main/java/net/wurstclient/background/WePuppetRig.java`）**骨骼表读通了**，
  并在真文件上验证（`WePuppetRigRealFileTest`，未跳过）：`真寻_眼2_puppet.mdl` 解出 **34 根骨骼**，
  **恰好一个根**（父 = `0xFFFFFFFF`），其余骨骼的父索引全部在自己之前 —— 这条结构不变量正是
  辨真伪的判据：flags/parent 两种顺序里只有一种能让第一根骨骼成为根。
- **顺带纠正一处**：`MDLS` 读完 34 根骨骼后**并不落在 `sectionEnd` 上**（实测差 2,870 字节），
  那是对应 `MDLS` v2/v3/v4 的**可选块**（links／绑定矩阵／约束／u32 键映射／IK 集合／每骨骼
  `{vec3, mat4}`／每骨骼 u32 数组×2／约束标志）。所以"正好落在段尾"不能当判据，之前那一版
  就是被这条卡住的。

**动画头也对齐了（同一轮稍后）**：文档那套顺序是对的，卡住的原因是**我自己的解析器**——
第一段（id 138）读得完全正确，但第二段头里有版本相关的附加块（A≥3 的标量轨道、A≥5 的动画
AABB、A≥6 的第三组标量轨道、事件表……，布局没有权威说明），我的代码**一段失败就把整段丢掉**，
于是 `animations` 变成空的。改成"能读多少读多少"（保留已读出的段、失败就停）之后：

实测 `真寻_眼2_puppet.mdl` 的第一段动画头（逐字节核对过）：

```
+17  u64 id = 138
+25  cstr name = 「动画 1」   (NUL 在 +33)
+34  cstr mode = "loop"        (NUL 在 +38)
+39  f32  fps    = 60.0        ← 我之前当成"填充"的 00 00 70 42 就是它
+43  u32  frames = 600
+47  u32  flags  = 0
+51  i32  trackCount = 34      （= 骨骼数）
+55  轨道 0: u32 flags=0; u32 blob=21636 = 36 × 601 ✓; 采样自 +63 起
+63  第 0 帧 = 位置 (13.7022, −538.9, 0)（**正好等于骨骼 0 的绑定平移**）、
     欧拉 (0,0,0)、缩放 (1,1,1)
```

`WePuppetRigRealFileTest` 现在把这些都钉住了：id 138 找得到、轨道数 = 骨骼数、
每根轨道的采样数 = 9 × (帧数+1)、且**第 0 帧位置 = 绑定平移**的骨骼 ≥ 30/34
（这一条同时校验骨骼矩阵与采样偏移）。

**动画那一路的进展与卡点（2026-10-15/16）**：

- 姿势数学已实现（`WePuppetPose`：local = S·R·T、world = local·world(parent)、
  skin = inverse(bindWorld)·world、v' = Σ w·(v·skin)），**绑定姿势复现网格**这条自证通过
  （最大顶点位移 < 1px）。
- 但**动起来渲染是坏的**（`build/blink-strip.png`：眼睛被拉花、三角形拖影）。探针实测：
  动画 138 = 600 帧 @60fps = 10s，峰值顶点位移 213px（t=3.317s），光栅化 8.3ms/帧（808x369）。
- **逐帧读原始采样后否掉了"旋转约定错了"这个假设**（`build/mdl-samples.py`）：
  track 0/1 完全静止；track 2 的**缩放 y 从 1.0 掉到 ~0.0001** —— 眨眼是**用缩放把眼皮压平**，
  而且这些轨道里**欧拉角全是 0**。所以这次动画根本不经过旋转代码。
- `build/mdl-bind.py` 查过绑定矩阵朝向：34 根骨骼里 **18 根的 m01 明显非 0**（例如骨骼 3：
  row0=(−0.001,1,0)、row1=(−1,−0.001,0)，即 90° 绑定旋转）—— 这是骨架的正常结构（部件骨骼
  常带 90° 绑定朝向），**不能**据此断定行列读反（那条 `atan2(m01,m00)` 自证只对"动画里
  欧拉非 0"的库文件成立）。
- 于是剩下的嫌疑收窄到两处，且都**只能靠像素判**：① `invert()`（高斯-约当）是否可靠；
  ② `MDLS` 里那份矩阵到底是 **local** 还是已经是 **world** 绑定 —— 若是后者而我按 local 又
  乘了一遍父链，深层骨骼就会被推出去（而"绑定姿势自证"对这两种读法都成立，因为两边用的是
  同一批矩阵）。
- 下一步的判据：拿峰值帧的眼睛轮廓与 WE 自带预览帧（`docs/eyes-blink-preview.png`）比，
  并对 ①/② 各做一次对照（例如把绑定矩阵当 world 用一遍看是否立刻正常）。



