# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(__file__).resolve().parents[1] / 'CHANGELOG.md'
s = p.read_text(encoding='utf-8')
marker = '## 未发布 — Soar v8.0 新增 6 个模块（2026-10-25）'
if marker in s:
    print('already present')
    raise SystemExit

section = '''## 未发布 — Soar v8.0 新增 6 个模块（2026-10-25）

- 并入 v8.0 相对 v7.1.2 新增的 **AutoGG、ForceMainHand、JumpResetIndicator、
  PitchDisplay、SnapTap、YawDisplay**，同步 66 个 v1.5.1 工程与根 Forge v1.6.0。
- 新增 `PlayerMixin`/`AvatarMixin` 主手钩子（1.21.9+ 的 `getMainArm` 已迁到
  `Avatar`），以及 `KeyBindingMixin`/`KeyMappingMixin` 的 SnapTap 同时按键消解。
- JumpResetIndicator 用 `hurtTime` 和跳跃状态在本端计算，不需要额外 Mixin。
- 实机验证：1.21.11 逐项启用 6 个模块无异常；`disableAll` 后
  `enabled-hacks.json` 为 `[]`。

---

'''
idx = s.find('\n---\n')
idx = idx + len('\n---\n') if idx != -1 else len(s)
if idx == len(s):
    s = s + '\n\n' + section
else:
    s = s[:idx] + '\n' + section + s[idx:]
p.write_text(s, encoding='utf-8', newline='\n')
print('inserted')
