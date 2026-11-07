# -*- coding: utf-8 -*-
"""Descriptions for the 63 IMG/FO batch-5 hacks."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

EN = {
    'AttractAuraHack': 'Pulls nearby entities toward you.',
    'AutoChestAuraHack': 'Automatically opens nearby chests.',
    'CrossbowAuraHack': 'Aims and shoots crossbows at targets.',
    'CrystalMacroHack': 'Legit crystal placement macro.',
    'MaceAuraHack': 'Attacks entities with a mace automatically.',
    'MaceBreakerProHack': 'Uses axe/mace combos to break shields.',
    'MacroAnchorHack': 'Automates anchor placement and detonation.',
    'PistonCrystalHack': 'Places pistons and crystals to detonate them.',
    'SpearKillHack': 'Automatically attacks with a spear or mace.',
    'SwordGapHack': 'Swaps between sword and golden apple.',
    'TntBomberHack': 'Drops and detonates TNT on targets.',
    'TpAnchorHack': 'Teleports to and detonates respawn anchors.',
    'TpBowAuraHack': 'Teleport bow aura.',
    'TpMachineGunHack': 'Rapid teleport attacks.',
    'PearlPhaseHack': 'Phases through blocks with ender pearls.',
    'BacktrackHack': 'Delays incoming packets to rewind positions.',
    'StuckHack': 'Teleports you out when stuck.',
    'PortalGodModeHack': 'Prevents portal teleportation.',
    'LegitNoFallHack': 'Prevents fall damage in a legit way.',
    'FreezeHack': 'Freezes your position and actions.',
    'AutoChorusHack': 'Eats chorus fruit automatically to teleport.',
    'AutoDoubleHandHack': 'Swaps items between hands automatically.',
    'AutoFireworkHack': 'Uses fireworks automatically.',
    'AutoInvTotemHack': 'Moves a totem from the inventory to the offhand.',
    'VillagerTraderHack': 'Automates villager trading.',
    'AdaManualCrystalHack': 'Manual crystal placement helper.',
    'AdaAutoHotbarHack': 'Automatically organizes the hotbar.',
    'FurnaceUncloggerHack': 'Clears clogged furnaces.',
    'SchematicProHack': 'Builds configured item counts (schematic helper).',
    'RTsearchHack': 'Searches for structures and biomes via commands.',
    'AutoMineSandHack': 'Automatically mines sand.',
    'FoKillAuraHack': 'FO-style kill aura.',
    'ChatFilterHack': 'Filters chat messages.',
    'ChatHiderHack': 'Hides chat unless the chat screen is open.',
    'ChatPrefixCustomHack': 'Customizes the chat prefix.',
    'FeedbackBlockerHack': 'Blocks matching chat feedback.',
    'GAntiExplosionHack': 'Prevents crystal and anchor explosion damage.',
    'HexChatHack': 'Sends colored hex chat messages.',
    'AutoServerHack': 'Runs commands automatically on specific servers.',
    'AutoTPAcceptHack': 'Automatically accepts teleport requests.',
    'AutoNodHack': 'Automatically nods or shakes your head.',
    'AutoKouZiHack': 'Sends timed chat messages.',
    'XCarryHack': 'Keeps items in the crafting grid.',
    'AmbienceHack': 'Client-side ambience (time and fullbright).',
    'AntiAntiXrayHack': 'Reveals ores hidden by anti-xray.',
    'AntiLagHack': 'Reduces lag from movement packets.',
    'BreakEspHack': 'Highlights blocks being broken.',
    'CustomFovHack': 'Custom field of view.',
    'CustomItemEspHack': 'Highlights configured items.',
    'ElytraFollowerHack': 'Follows a target while gliding.',
    'FillEspHack': 'Highlights containers.',
    'ImgChamsHack': 'Wireframe entity chams.',
    'MineEspHack': 'Highlights blocks being mined.',
    'MobHudHack': 'HUD with mob and world stats.',
    'ImgTipsHack': 'Extra notifications (visual range, lag, etc.).',
    'ImgTotemParticleHack': 'Custom totem pop particles.',
    'ImgWorldStatsHack': 'World statistics HUD.',
    'KillFxHack': 'Visual and sound effects on kills.',
    'KnockbackDirectionHack': 'Shows the knockback direction.',
    'SprintStatusHack': 'Shows your sprint status.',
    'SonarBypassHack': 'Bypasses simple sonar/anti-cheat checks.',
    'ScreenActionsHack': 'Performs actions inside screens.',
    'FlightAntiKickHack': 'Prevents flight kicks.',
}

ZH = {
    'AttractAuraHack': '把附近的实体拉向你。',
    'AutoChestAuraHack': '自动打开附近的箱子。',
    'CrossbowAuraHack': '自动瞄准并射击弩。',
    'CrystalMacroHack': '保守模式的水晶放置宏。',
    'MaceAuraHack': '自动用重锤攻击实体。',
    'MaceBreakerProHack': '用斧/锤组合破盾。',
    'MacroAnchorHack': '自动放置并引爆重生锚。',
    'PistonCrystalHack': '放置活塞和水晶并引爆。',
    'SpearKillHack': '自动用矛或重锤攻击。',
    'SwordGapHack': '在剑和金苹果之间快速切换。',
    'TntBomberHack': '向目标投放并引爆 TNT。',
    'TpAnchorHack': '传送到重生锚处并引爆。',
    'TpBowAuraHack': '传送弓光环。',
    'TpMachineGunHack': '快速连续传送攻击。',
    'PearlPhaseHack': '用末影珍珠穿墙。',
    'BacktrackHack': '延迟收到的数据包以回溯位置。',
    'StuckHack': '卡住时自动传送脱困。',
    'PortalGodModeHack': '阻止传送门传送。',
    'LegitNoFallHack': '以保守方式防止摔落伤害。',
    'FreezeHack': '冻结你的位置和操作。',
    'AutoChorusHack': '自动吃紫颂果传送。',
    'AutoDoubleHandHack': '自动在双手之间交换物品。',
    'AutoFireworkHack': '自动使用烟花火箭。',
    'AutoInvTotemHack': '自动把背包里的图腾移到副手。',
    'VillagerTraderHack': '自动与村民交易。',
    'AdaManualCrystalHack': '手动水晶放置辅助。',
    'AdaAutoHotbarHack': '自动整理快捷栏。',
    'FurnaceUncloggerHack': '自动清理堵塞的熔炉。',
    'SchematicProHack': '按配置数量自动建造（投影辅助）。',
    'RTsearchHack': '用命令搜索结构/生物群系。',
    'AutoMineSandHack': '自动挖沙子。',
    'FoKillAuraHack': 'FO 风格的杀戮光环。',
    'ChatFilterHack': '过滤聊天消息。',
    'ChatHiderHack': '打开聊天栏前隐藏聊天。',
    'ChatPrefixCustomHack': '自定义聊天前缀。',
    'FeedbackBlockerHack': '屏蔽匹配的聊天反馈。',
    'GAntiExplosionHack': '防止水晶和重生锚爆炸伤害。',
    'HexChatHack': '发送彩色十六进制聊天消息。',
    'AutoServerHack': '在指定服务器自动执行命令。',
    'AutoTPAcceptHack': '自动接受传送请求。',
    'AutoNodHack': '自动点头或摇头。',
    'AutoKouZiHack': '定时发送聊天消息。',
    'XCarryHack': '让合成栏物品保持不丢失。',
    'AmbienceHack': '客户端环境效果（时间与全亮）。',
    'AntiAntiXrayHack': '显示被反矿透隐藏的矿石。',
    'AntiLagHack': '减少移动数据包造成的延迟。',
    'BreakEspHack': '高亮正在被破坏的方块。',
    'CustomFovHack': '自定义视野范围。',
    'CustomItemEspHack': '高亮指定物品。',
    'ElytraFollowerHack': '滑翔时跟随目标。',
    'FillEspHack': '高亮容器。',
    'ImgChamsHack': '实体线框透视。',
    'MineEspHack': '高亮正在挖掘的方块。',
    'MobHudHack': '显示生物与世界状态的 HUD。',
    'ImgTipsHack': '额外提示（视距、延迟等）。',
    'ImgTotemParticleHack': '自定义图腾触发粒子。',
    'ImgWorldStatsHack': '世界统计 HUD。',
    'KillFxHack': '击杀时的视觉与音效。',
    'KnockbackDirectionHack': '显示击退方向。',
    'SprintStatusHack': '显示疾跑状态。',
    'SonarBypassHack': '绕过简单的声呐/反作弊检查。',
    'ScreenActionsHack': '在界面内执行操作。',
    'FlightAntiKickHack': '防止飞行被踢。',
}


def add_keys(path, mapping):
    text = path.read_text(encoding='utf-8')
    missing = [(c, c[:-4].lower()) for c in mapping
               if '"description.wurst.hack.%s"' % c[:-4].lower() not in text]
    if not missing:
        return 0
    lines = text.splitlines(keepends=True)
    anchor = None
    for i, line in enumerate(lines):
        if '"description.wurst.hack.antiknockback"' in line:
            anchor = i
            break
    if anchor is None:
        for i, line in enumerate(lines):
            if '"description.wurst.hack.' in line:
                anchor = i
                break
    if anchor is None:
        return -1
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    ins = ['"description.wurst.hack.%s": "%s",\n' % (k, mapping[c])
           for c, k in missing]
    lines[anchor + 1:anchor + 1] = ins
    new = ''.join(lines)
    json.loads(new)
    path.write_text(new, encoding='utf-8', newline='\n')
    return len(missing)


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        base = p / 'src/main/resources/assets/wurst/translations'
        for name, table in (('en_us.json', EN), ('zh_cn.json', ZH)):
            f = base / name
            if f.exists():
                n = add_keys(f, table)
                stats[n] = stats.get(n, 0) + 1
    print('translations5', stats)


if __name__ == '__main__':
    main()
