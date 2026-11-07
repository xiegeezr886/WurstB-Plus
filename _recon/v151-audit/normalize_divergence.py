"""Mapping-normalized divergence between fork v1.5.1 (Mojang mappings) and upstream (Yarn).

Applies a Yarn->Mojang identifier substitution to both sides, drops import/copyright
noise, then counts genuinely changed lines. Produces normalized_divergence.csv.
"""
import csv
import difflib
import os
import re

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
FORK = os.path.join(ROOT, "fabric", "src", "main", "java", "net", "wurstclient")
UP = os.path.join(ROOT, "reference", "upstream-sources", "Wurst7_1_20_1_src",
                  "Wurst7-1.20.1", "src", "main", "java", "net", "wurstclient")
OUT = os.path.join(ROOT, "_recon", "v151-audit")

# Yarn (upstream) -> Mojang (fork). Applied to BOTH sides, so either spelling
# collapses to one canonical token.
PAIRS = [
    # classes
    ("MinecraftClient", "Minecraft"), ("ClientPlayerEntity", "LocalPlayer"),
    ("ClientWorld", "ClientLevel"), ("ClientPlayNetworkHandler", "ClientPacketListener"),
    ("ClientPlayerInteractionManager", "MultiPlayerGameMode"),
    ("WorldChunk", "LevelChunk"), ("World", "Level"),
    ("PlayerEntity", "Player"), ("PlayerInventory", "Inventory"),
    ("LivingEntity", "LivingEntity"), ("Hand", "InteractionHand"),
    ("Vec3d", "Vec3"), ("MathHelper", "Mth"), ("Box", "AABB"),
    ("MutableText", "MutableComponent"), ("Text", "Component"),
    ("Formatting", "ChatFormatting"), ("DrawContext", "GuiGraphics"),
    ("MatrixStack", "PoseStack"), ("VertexConsumerProvider", "MultiBufferSource"),
    ("RenderLayer", "RenderType"), ("KeyBinding", "KeyMapping"),
    ("SimpleOption", "OptionInstance"), ("Perspective", "CameraType"),
    ("StatusEffectInstance", "MobEffectInstance"), ("StatusEffect", "MobEffect"),
    ("StatusEffects", "MobEffects"), ("AbstractBlockState", "BlockBehaviour"),
    ("AbstractBlock", "Block"), ("AbstractSignBlock", "SignBlock"),
    ("AbstractFireBlock", "BaseFireBlock"), ("TripwireBlock", "TripWireBlock"),
    ("FluidBlock", "LiquidBlock"), ("ScreenHandler", "AbstractContainerMenu"),
    ("PlayerInventory", "Inventory"), ("ClientPlayerEntity", "LocalPlayer"),
    ("Keyboard", "KeyboardHandler"), ("Mouse", "MouseHandler"),
    ("NbtCompound", "CompoundTag"), ("NbtList", "ListTag"), ("NbtElement", "Tag"),
    ("NbtString", "StringTag"), ("NbtInt", "IntTag"), ("NbtDouble", "DoubleTag"),
    ("NbtFloat", "FloatTag"), ("NbtByte", "ByteTag"), ("NbtLong", "LongTag"),
    ("NbtShort", "ShortTag"), ("NbtByteArray", "ByteArrayTag"),
    ("NbtIntArray", "IntArrayTag"), ("NbtLongArray", "LongArrayTag"),
    ("ItemStack", "ItemStack"), ("ScreenHandlerSlot", "Slot"),
    ("SoundEvent", "SoundEvent"), ("RegistryEntry", "Holder"),
    # methods / fields
    ("getPos()", "position()"), ("getBlockPos()", "blockPosition()"),
    ("isOnGround()", "onGround()"), ("getYaw()", "getYRot()"),
    ("getPitch()", "getXRot()"), ("setYaw(", "setYRot("), ("setPitch(", "setXRot("),
    ("getEyePos()", "getEyePosition()"), ("squaredDistanceTo(", "distanceToSqr("),
    ("getWorld()", "level()"), ("getStackInHand(", "getItemInHand("),
    ("getMainHandStack()", "getMainHandItem()"), ("getOffHandStack()", "getOffHandItem()"),
    ("getBlockState(", "getBlockState("), ("blocksMovement()", "blocksMotion()"),
    ("getTopY()", "getMaxBuildHeight()"), ("getBottomY()", "getMinBuildHeight()"),
    ("up()", "above()"), ("down()", "below()"), ("offset(", "relative("),
    ("isChunkLoaded(", "hasChunkAt("), ("sendPacket(", "send("),
    ("getNetworkHandler()", "getConnection()"), ("getItemCooldownManager()", "getCooldowns()"),
    ("hasStatusEffect(", "hasEffect("), ("getStatusEffect(", "getEffect("),
    ("setVelocity(", "setDeltaMovement("), ("getVelocity()", "getDeltaMovement()"),
    ("getBoundingBox()", "getBoundingBox()"), ("getVelocity()", "getDeltaMovement()"),
    ("getHorizontalFacing()", "getDirection()"), ("getDisplayName()", "getDisplayName()"),
    ("getUuid()", "getUUID()"), ("getUuidAsString()", "getStringUUID()"),
    ("isInSneakingPose()", "isCrouching()"), ("isSneaking()", "isShiftKeyDown()"),
    ("setSneaking(", "setShiftKeyDown("), ("getHungerManager()", "getFoodData()"),
    ("getAttackCooldownProgress(", "getAttackStrengthScale("),
    ("getStackInSlot(", "getItem("), ("size()", "size()"),
    ("isRemoved()", "isRemoved()"),
]

IDENT = re.compile(r"[A-Za-z_$][A-Za-z0-9_$.]*")


def canonical(text: str) -> str:
    for a, b in PAIRS:
        # avoid partial-word replacement for identifiers
        if a.endswith("(") or a.endswith("()"):
            text = text.replace(a, b)
        else:
            text = re.sub(r"\b" + re.escape(a) + r"\b", b, text)
    return text


def strip_noise(lines):
    out = []
    for ln in lines:
        s = ln.strip()
        if s.startswith("import ") or s.startswith("package "):
            continue
        if s.startswith("*") or s.startswith("/*") or s.startswith("//"):
            continue
        if s in ("", "{", "}"):
            continue
        out.append(canonical(ln.strip()))
    return out


def walk(base):
    d = {}
    for dirpath, _dn, files in os.walk(base):
        for fn in files:
            if fn.endswith(".java"):
                d[os.path.relpath(os.path.join(dirpath, fn), base).replace("\\", "/")] = os.path.join(dirpath, fn)
    return d


def main():
    fork, up = walk(FORK), walk(UP)
    rows = []
    for rel in sorted(set(fork) & set(up)):
        a = strip_noise(open(up[rel], encoding="utf-8", errors="replace").read().splitlines())
        b = strip_noise(open(fork[rel], encoding="utf-8", errors="replace").read().splitlines())
        sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
        changed_up = changed_fork = 0
        changed_blocks = 0
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag != "equal":
                changed_up += i2 - i1
                changed_fork += j2 - j1
                if i2 - i1 or j2 - j1:
                    changed_blocks += 1
        rows.append({"path": rel, "fork_lines": len(b), "up_lines": len(a),
                     "changed_upstream": changed_up, "changed_fork": changed_fork,
                     "changed_blocks": changed_blocks,
                     "ratio": round(sm.ratio(), 3)})
    with open(os.path.join(OUT, "normalized_divergence.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    same = [r for r in rows if r["changed_fork"] == 0]
    print(f"shared: {len(rows)}  normalized-identical: {len(same)}  diverged: {len(rows)-len(same)}")
    print("\nTop 45 by changed fork lines:")
    for r in sorted(rows, key=lambda r: -r["changed_fork"])[:45]:
        print(f"  {r['changed_fork']:5d} fork / {r['changed_upstream']:5d} up / {r['changed_blocks']:3d} blocks  {r['path']}")


if __name__ == "__main__":
    main()
