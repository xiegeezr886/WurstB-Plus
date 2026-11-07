# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(__file__).resolve().parents[1] / 'CHANGELOG.md'
s = p.read_text(encoding='utf-8')
marker = '## 未发布 — Soar 剩余 30 个模块（2026-10-25）'
if marker in s:
    print('already present')
    raise SystemExit

section = '''## 未发布 — Soar 剩余 30 个模块（2026-10-25）

- **并入 Soar v7.1.2 剩余 30 个模块**，并同步到 66 个 v1.5.1 工程和根 Forge v1.6.0：
  ChunkAnimator、ClearGlass、ClearWater、ColorSaturation、CustomHeldItems、DamageTilt、
  DamageTint、EntityCulling、FPSSpoofer、GlintColor、HitColor、HitDelayFix、ItemPhysics、
  Items2D、MinimalDamageShake、MoBends、MotionBlur、OldAnimations、OverlayEditor、
  ParticleCustomizer、RawInput、Rearview、Skin3D、SkinProtect、SlowSwing、SoundModifier、
  SoundSubtitles、ViaVersion、WaveyCapes、WebBrowser。
- **新增/扩展 Mixin 钩子**：FPS 伪装、命中延迟修复、慢速挥手、伤害倾斜、手持物品变换、
  皮肤保护、清水/覆盖层、实体距离剔除；26.x 的 `shouldRender(float)` 与
  `FirstPersonHandsAndItemsRenderer` 已单独适配。
- **HUD 兼容三套 API**：1.20.2–1.21.5 的 `GuiGraphics`、1.21.6–1.21.11 的 Wurst
  `GuiGraphicsExtractor`、26.x 的原生 `GuiGraphicsExtractor`。
- **验证**：66 个发布 jar 全部含新类且版本为 1.5.1；Fabric 1.20.2、1.21.11、26.3 与
  Forge 1.20.2、NeoForge 26.3 实机启动无 Mixin 注入错误；1.21.11 进入世界后逐个启用
  30 个模块，无异常日志。

---

'''

# Insert after the first horizontal rule following the intro.
idx = s.find('\n---\n')
if idx == -1:
    idx = len(s)
    s = s + '\n\n' + section
else:
    idx += len('\n---\n')
    s = s[:idx] + '\n' + section + s[idx:]
p.write_text(s, encoding='utf-8', newline='\n')
print('inserted')
