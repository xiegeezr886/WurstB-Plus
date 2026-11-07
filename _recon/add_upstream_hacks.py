# -*- coding: utf-8 -*-
"""Add upstream Wurst7 hacks that are missing locally.

Usage:
  python add_upstream_hacks.py antiknockback
  python add_upstream_hacks.py macedmg
  python add_upstream_hacks.py attributeswap
  python add_upstream_hacks.py killaura_legit
"""
import os
import re
import sys
from pathlib import Path

REPO = Path(os.environ.get('VAP_REPO',
                           Path(__file__).resolve().parents[1])).resolve()
REF = REPO / 'reference' / 'artifacts' / '26.1.2' / 'Wurst7-ref-26.1.2' / \
    'Wurst7-26.1.2' / 'src' / 'main' / 'java' / 'net' / 'wurstclient'

HEADER = """/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
"""


def project_dirs():
    """Every Gradle project that has a HackList (root + 66 v1.5 projects)."""
    out = []
    for list_path in REPO.rglob('src/main/java/net/wurstclient/hack/HackList.java'):
        parts = list_path.parts
        if 'reference' in parts or 'build' in parts:
            continue
        out.append(list_path.parents[6])
    return sorted(set(out))


ANTIKNOCKBACK = HEADER + """package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KnockbackListener;
import net.wurstclient.events.KnockbackListener.KnockbackEvent;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"anti knockback", "AntiVelocity", "anti velocity", "NoKnockback",
\t"no knockback", "AntiKB", "anti kb"})
public final class AntiKnockbackHack extends Hack implements KnockbackListener
{
\tprivate final SliderSetting hStrength =
\t\tnew SliderSetting("Horizontal Strength",
\t\t\t"How far to reduce horizontal knockback.\\n"
\t\t\t\t+ "-100% = double knockback\\n" + "0% = normal knockback\\n"
\t\t\t\t+ "100% = no knockback\\n" + ">100% = reverse knockback",
\t\t\t1, -1, 2, 0.01, ValueDisplay.PERCENTAGE);
\t
\tprivate final SliderSetting vStrength =
\t\tnew SliderSetting("Vertical Strength",
\t\t\t"How far to reduce vertical knockback.\\n"
\t\t\t\t+ "-100% = double knockback\\n" + "0% = normal knockback\\n"
\t\t\t\t+ "100% = no knockback\\n" + ">100% = reverse knockback",
\t\t\t1, -1, 2, 0.01, ValueDisplay.PERCENTAGE);
\t
\tpublic AntiKnockbackHack()
\t{
\t\tsuper("AntiKnockback");
\t\tsetCategory(Category.COMBAT);
\t\taddSetting(hStrength);
\t\taddSetting(vStrength);
\t}
\t
\t@Override
\tprotected void onEnable()
\t{
\t\tEVENTS.add(KnockbackListener.class, this);
\t}
\t
\t@Override
\tprotected void onDisable()
\t{
\t\tEVENTS.remove(KnockbackListener.class, this);
\t}
\t
\t@Override
\tpublic void onKnockback(KnockbackEvent event)
\t{
\t\tdouble verticalMultiplier = 1 - vStrength.getValue();
\t\tdouble horizontalMultiplier = 1 - hStrength.getValue();
\t\t
\t\tevent.setX(event.getDefaultX() * horizontalMultiplier);
\t\tevent.setY(event.getDefaultY() * verticalMultiplier);
\t\tevent.setZ(event.getDefaultZ() * horizontalMultiplier);
\t}
}
"""


MACEDMG = HEADER + """package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;

@SearchTags({"mace dmg", "MaceDamage", "mace damage"})
public final class MaceDmgHack extends Hack
\timplements PlayerAttacksEntityListener
{
\tpublic MaceDmgHack()
\t{
\t\tsuper("MaceDMG");
\t\tsetCategory(Category.COMBAT);
\t}
\t
\t@Override
\tprotected void onEnable()
\t{
\t\tEVENTS.add(PlayerAttacksEntityListener.class, this);
\t}
\t
\t@Override
\tprotected void onDisable()
\t{
\t\tEVENTS.remove(PlayerAttacksEntityListener.class, this);
\t}
\t
\t@Override
\tpublic void onPlayerAttacksEntity(Entity target)
\t{
\t\tif(MC.hitResult == null
\t\t\t|| MC.hitResult.getType() != HitResult.Type.ENTITY)
\t\t\treturn;
\t\t
\t\tif(!MC.player.getMainHandItem().is(Items.MACE))
\t\t\treturn;
\t\t\t
\t\t// See ServerGamePacketListenerImpl.handleMovePlayer()
\t\t// for why it's using these numbers.
\t\tfor(int i = 0; i < 4; i++)
\t\t\tsendFakeY(0);
\t\tsendFakeY(Math.sqrt(500));
\t\tsendFakeY(0);
\t}
\t
\tprivate void sendFakeY(double offset)
\t{
\t\tMC.player.connection
\t\t\t.send(new Pos(MC.player.getX(), MC.player.getY() + offset,
\t\t\t\tMC.player.getZ(), false, MC.player.horizontalCollision));
\t}
}
"""


ATTRIBUTESWAP = HEADER + """package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"attribute swap", "weapon swap", "auto breach swap", "mace swap",
\t"auto swap", "auto shield break", "auto mace", "disable shield",
\t"item swap", "hotbar swap", "item saver", "durability saver"})
public final class AttributeSwapHack extends Hack
\timplements PlayerAttacksEntityListener, UpdateListener
{
\tprivate final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
\t\t"Simple: swaps to a fixed hotbar slot on attack.\\n"
\t\t\t+ "Smart: checks target and picks the best item automatically.",
\t\tMode.values(), Mode.SIMPLE);
\t
\tprivate final SliderSetting targetSlot = new SliderSetting("Target slot",
\t\t"Hotbar slot to swap to (Simple mode).", 1, 1, 9, 1,
\t\tValueDisplay.INTEGER);
\t
\tprivate final CheckboxSetting swapBack = new CheckboxSetting("Swap back",
\t\t"Swap back to the original slot after delay.", true);
\t
\tprivate final SliderSetting swapBackDelay =
\t\tnew SliderSetting("Swap-back delay",
\t\t\t"How many ticks to wait before swapping back.", 2, 0, 20, 1,
\t\t\tValueDisplay.INTEGER.withSuffix(" ticks").withLabel(0, "instant"));
\t
\tprivate final CheckboxSetting breachSwapping = new CheckboxSetting(
\t\t"Breach swapping",
\t\t"Swaps to a mace with Breach to deal more damage (Smart mode).", true);
\t
\tprivate final CheckboxSetting shieldBreaker = new CheckboxSetting(
\t\t"Shield breaker",
\t\t"Swaps to an axe when target is blocking (Smart mode).", true);
\t
\tprivate final CheckboxSetting itemSaver = new CheckboxSetting("Item saver",
\t\t"Swaps to a non-damageable item to save main weapon durability (Smart mode).",
\t\ttrue);
\t
\tprivate final CheckboxSetting onlyWithKillAura = new CheckboxSetting(
\t\t"Only with Killaura", "Only activate when Killaura is enabled.", false);
\t
\tprivate int backTimer;
\tprivate boolean awaitingBack;
\tprivate int originalSlot;
\t
\tpublic AttributeSwapHack()
\t{
\t\tsuper("AttributeSwap");
\t\tsetCategory(Category.COMBAT);
\t\taddSetting(mode);
\t\taddSetting(targetSlot);
\t\taddSetting(swapBack);
\t\taddSetting(swapBackDelay);
\t\taddSetting(shieldBreaker);
\t\taddSetting(itemSaver);
\t\taddSetting(breachSwapping);
\t\taddSetting(onlyWithKillAura);
\t}
\t
\t@Override
\tprotected void onEnable()
\t{
\t\tbackTimer = 0;
\t\tawaitingBack = false;
\t\toriginalSlot = -1;
\t\t
\t\tEVENTS.add(PlayerAttacksEntityListener.class, this);
\t\tEVENTS.add(UpdateListener.class, this);
\t}
\t
\t@Override
\tprotected void onDisable()
\t{
\t\tEVENTS.remove(PlayerAttacksEntityListener.class, this);
\t\tEVENTS.remove(UpdateListener.class, this);
\t\t
\t\tif(awaitingBack)
\t\t{
\t\t\tdoSwapBack();
\t\t\tawaitingBack = false;
\t\t}
\t\tbackTimer = 0;
\t\toriginalSlot = -1;
\t}
\t
\t@Override
\tpublic void onPlayerAttacksEntity(Entity target)
\t{
\t\tif(onlyWithKillAura.isChecked()
\t\t\t&& !WURST.getHax().killauraHack.isEnabled())
\t\t\treturn;
\t\t
\t\tperformSwap(target);
\t}
\t
\t@Override
\tpublic void onUpdate()
\t{
\t\tif(!awaitingBack)
\t\t\treturn;
\t\tif(backTimer-- > 0)
\t\t\treturn;
\t\t
\t\tdoSwapBack();
\t\tawaitingBack = false;
\t}
\t
\tprivate void performSwap(Entity target)
\t{
\t\tif(awaitingBack)
\t\t\treturn;
\t\t
\t\tif(mode.getSelected() == Mode.SIMPLE)
\t\t{
\t\t\tdoSwap(targetSlot.getValueI() - 1);
\t\t\treturn;
\t\t}
\t\t
\t\tdoSwap(getSmartSlot(target));
\t}
\t
\tprivate void doSwap(int slotIndex)
\t{
\t\tif(awaitingBack)
\t\t\treturn;
\t\tif(slotIndex < 0 || slotIndex > 8)
\t\t\treturn;
\t\t
\t\tint current = MC.player.getInventory().getSelectedSlot();
\t\tif(slotIndex == current)
\t\t\treturn;
\t\t
\t\toriginalSlot = current;
\t\tMC.player.getInventory().setSelectedSlot(slotIndex);
\t\t
\t\tif(swapBack.isChecked())
\t\t{
\t\t\tawaitingBack = true;
\t\t\tbackTimer = swapBackDelay.getValueI();
\t\t}
\t}
\t
\tprivate void doSwapBack()
\t{
\t\tif(originalSlot >= 0 && originalSlot <= 8)
\t\t\tMC.player.getInventory().setSelectedSlot(originalSlot);
\t\t
\t\toriginalSlot = -1;
\t}
\t
\tprivate int getSmartSlot(Entity target)
\t{
\t\tItemStack current = MC.player.getMainHandItem();
\t\t
\t\tif(target instanceof LivingEntity living && shieldBreaker.isChecked()
\t\t\t&& living.isBlocking())
\t\t{
\t\t\tif(current.is(ItemTags.AXES))
\t\t\t\treturn -1;
\t\t\t
\t\t\tint axeSlot =
\t\t\t\tInventoryUtils.indexOf(s -> s.is(ItemTags.AXES), 9);
\t\t\tif(axeSlot != -1)
\t\t\t\treturn axeSlot;
\t\t}
\t\t
\t\tif(breachSwapping.isChecked() && target instanceof LivingEntity le
\t\t\t&& le.getAttributes().getValue(Attributes.ARMOR) > 0)
\t\t{
\t\t\tint breachSlot =
\t\t\t\tInventoryUtils.indexOf(s -> s.getItem() instanceof MaceItem
\t\t\t\t\t&& EnchantmentUtils.getLevel(Enchantments.BREACH, s) > 0, 9);
\t\t\t
\t\t\tif(breachSlot != -1)
\t\t\t\treturn breachSlot;
\t\t}
\t\t
\t\tint bestSlot = -1;
\t\tint bestScore = getDurabilityScore(current);
\t\t
\t\tfor(int slot = 0; slot < 9; slot++)
\t\t{
\t\t\tif(slot == MC.player.getInventory().getSelectedSlot())
\t\t\t\tcontinue;
\t\t\t
\t\t\tItemStack stack = MC.player.getInventory().getItem(slot);
\t\t\tif(stack.isEmpty())
\t\t\t\tcontinue;
\t\t\t
\t\t\tint score = getDurabilityScore(stack);
\t\t\tif(score > bestScore)
\t\t\t{
\t\t\t\tbestScore = score;
\t\t\t\tbestSlot = slot;
\t\t\t}
\t\t}
\t\t
\t\treturn bestSlot;
\t}
\t
\tprivate int getDurabilityScore(ItemStack stack)
\t{
\t\tif(!itemSaver.isChecked())
\t\t\treturn 0;
\t\tif(!stack.isDamageableItem())
\t\t\treturn 4;
\t\t
\t\treturn 0;
\t}
\t
\tprivate enum Mode
\t{
\t\tSIMPLE("Simple"),
\t\tSMART("Smart");
\t\t
\t\tprivate final String name;
\t\t
\t\tprivate Mode(String name)
\t\t{
\t\t\tthis.name = name;
\t\t}
\t\t
\t\t@Override
\t\tpublic String toString()
\t\t{
\t\t\treturn name;
\t\t}
\t}
}
"""


def mc_version(project):
    props = project / 'gradle.properties'
    if not props.exists():
        return None
    m = re.search(r'^minecraft_version=(.+)$',
                  props.read_text(encoding='utf-8'), re.M)
    return m.group(1).strip() if m else None


def version_tuple(v):
    if not v:
        return (0, 0, 0)
    parts = re.findall(r'\d+', v)
    nums = [int(x) for x in parts[:3]]
    while len(nums) < 3:
        nums.append(0)
    return tuple(nums)


def add_hack(project, cls_name, source, field_line, anchor='antiHungerHack'):
    target = project / 'src/main/java/net/wurstclient/hacks' / f'{cls_name}.java'
    target.write_text(source, encoding='utf-8', newline='\n')

    list_path = project / 'src/main/java/net/wurstclient/hack/HackList.java'
    text = list_path.read_text(encoding='utf-8')
    if field_line.strip() in text:
        return 'exists'
    m = re.search(r'^([ \t]*)public final \w+ ' + anchor + r' = .*$',
                  text, re.M)
    if not m:
        return 'NO ANCHOR'
    text = text[:m.end()] + '\n' + field_line + text[m.end():]
    list_path.write_text(text, encoding='utf-8', newline='\n')
    return 'added'


def main():
    what = sys.argv[1] if len(sys.argv) > 1 else 'antiknockback'
    projects = project_dirs()
    results = {}
    for p in projects:
        ver = mc_version(p)
        vt = version_tuple(ver)
        if what == 'antiknockback':
            r = add_hack(
                p, 'AntiKnockbackHack', ANTIKNOCKBACK,
                '\tpublic final AntiKnockbackHack antiKnockbackHack = '
                'new AntiKnockbackHack();')
        elif what == 'macedmg':
            if vt < (1, 21, 0):
                r = 'skip-version'
            else:
                src = MACEDMG
                if vt < (1, 21, 2):
                    # 1.21/1.21.1 的 Pos 只有 4 参构造器
                    src = src.replace('false, MC.player.horizontalCollision',
                                      'false')
                r = add_hack(
                    p, 'MaceDmgHack', src,
                    '\tpublic final MaceDmgHack maceDmgHack = '
                    'new MaceDmgHack();',
                    anchor='killauraLegitHack')
        elif what == 'attributeswap':
            if vt < (1, 21, 5):
                r = 'skip-version'
            else:
                src = ATTRIBUTESWAP
                if vt >= (26, 0, 0):
                    # 26.x 的 ItemStack.is() 只接受 Predicate，标签检查走 Holder
                    src = src.replace(
                        'current.is(ItemTags.AXES)',
                        'current.getItem().builtInRegistryHolder()'
                        '.is(ItemTags.AXES)')
                    src = src.replace(
                        's.is(ItemTags.AXES)',
                        's.getItem().builtInRegistryHolder().is(ItemTags.AXES)')
                r = add_hack(
                    p, 'AttributeSwapHack', src,
                    '\tpublic final AttributeSwapHack attributeSwapHack = '
                    'new AttributeSwapHack();',
                    anchor='arrowDmgHack')
        else:
            raise SystemExit('unknown task: ' + what)
        results[r] = results.get(r, 0) + 1
    print(what, 'projects=', len(projects), results)


if __name__ == '__main__':
    main()
