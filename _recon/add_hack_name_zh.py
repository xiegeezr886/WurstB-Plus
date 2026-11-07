# -*- coding: utf-8 -*-
"""Add Chinese display names (hack.name.*) for the 168 new hacks."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

ZH = {
    'AdaAutoHotbarHack': '自动快捷栏',
    'AdaManualCrystalHack': '手动水晶辅助',
    'AirJumpHack': '空中跳跃',
    'AmbienceHack': '环境氛围',
    'AncientCitySearchHack': '远古城市搜索',
    'AntiAnchorHack': '防锚',
    'AntiAntiXrayHack': '反反矿透',
    'AntiAnvilHack': '防铁砧',
    'AntiBedHack': '防床',
    'AntiKnockbackHack': '防击退',
    'AntiLagHack': '反卡顿',
    'ArrowDodgeHack': '躲箭',
    'AttackRangeIndicatorHack': '攻击距离指示',
    'AttractAuraHack': '吸引光环',
    'AttributeSwapHack': '属性切换',
    'AutoAnchorHack': '自动锚',
    'AutoAnvilHack': '自动铁砧',
    'AutoArmorPlusHack': '自动护甲+',
    'AutoBreedHack': '自动繁殖',
    'AutoBrewerHack': '自动酿造',
    'AutoChestAuraHack': '自动开箱光环',
    'AutoChorusHack': '自动紫颂果',
    'AutoClickerHack': '自动点击',
    'AutoDeoxidizerHack': '自动除锈',
    'AutoDoubleHandHack': '自动换手',
    'AutoEXPHack': '自动经验修补',
    'AutoFireworkHack': '自动烟花',
    'AutoGapHack': '自动金苹果',
    'AutoInvTotemHack': '背包图腾',
    'AutoJumpHack': '自动跳跃',
    'AutoKouZiHack': '自动喊话',
    'AutoLoginHack': '自动登录',
    'AutoMendHack': '自动修补',
    'AutoMineSandHack': '自动挖沙',
    'AutoMountHack': '自动骑乘',
    'AutoNametagHack': '自动命名',
    'AutoNodHack': '自动点头',
    'AutoOffHandHack': '自动副手',
    'AutoPlaceBlockHack': '自动放置',
    'AutoRefreshTradeHack': '自动刷新交易',
    'AutoReplenishHack': '自动补货',
    'AutoServerHack': '自动进服',
    'AutoShearerHack': '自动剪羊毛',
    'AutoSmelterHack': '自动熔炼',
    'AutoSmithingHack': '自动锻造',
    'AutoTorchHack': '自动火把',
    'AutoTPAcceptHack': '自动接受传送',
    'AutoTrashHack': '自动丢弃',
    'AutoVaultHack': '自动宝库',
    'AutoWeaponHack': '自动武器',
    'BacktrackHack': '回溯',
    'BedAuraHack': '床光环',
    'BetterBeaconsHack': '更好的信标',
    'BetterChatHack': '更好的聊天',
    'BetterTabHack': '更好的 Tab',
    'BetterTooltipsHack': '更好的提示',
    'BlockEspHack': '方块透视',
    'BlockSelectionHack': '方块选择',
    'BookBotHack': '自动写书',
    'BowSpamHack': '连射弓',
    'BreakEspHack': '破坏透视',
    'BreakIndicatorsHack': '破坏指示',
    'ChatFilterHack': '聊天过滤',
    'ChatHiderHack': '聊天隐藏',
    'ChatHighlightHack': '聊天高亮',
    'ChatPrefixCustomHack': '自定义前缀',
    'ChestSwapHack': '胸甲切换',
    'ClickTPHack': '点击传送',
    'CrossbowAuraHack': '弩光环',
    'CrystalAuraPlusHack': '水晶光环+',
    'CrystalMacroHack': '水晶宏',
    'CustomFovHack': '自定义视野',
    'CustomItemEspHack': '自定义物品透视',
    'DeathRenderHack': '死亡标记',
    'EChestFarmerHack': '末影箱农场',
    'ElytraBoostHack': '鞘翅加速',
    'ElytraCollectorHack': '鞘翅收集',
    'ElytraFollowerHack': '鞘翅跟随',
    'ElytraGrimAccelerateHack': '鞘翅 Grim 加速',
    'EntityOwnerHack': '实体主人',
    'EntityTagsHack': '实体标签',
    'EXPThrowerHack': '经验投掷',
    'FakePlayerHack': '假玩家',
    'FastClimbHack': '快速攀爬',
    'FastCrossbowHack': '快速弩',
    'FeedbackBlockerHack': '反馈屏蔽',
    'FillEspHack': '容器透视',
    'FireworkElytraFlyHack': '烟花鞘翅飞行',
    'FlamethrowerHack': '喷火器',
    'FlightAntiKickHack': '飞行防踢',
    'FoKillAuraHack': 'FO 杀戮光环',
    'FreezeHack': '冻结',
    'FurnaceUncloggerHack': '熔炉清理',
    'GAntiExplosionHack': '防爆',
    'GlassFillerHack': '玻璃填充',
    'HexChatHack': '彩色聊天',
    'HighwayBuilderHack': '高速公路建造',
    'HitboxESPHack': '碰撞箱透视',
    'ImgChamsHack': '线框透视',
    'ImgTipsHack': '提示增强',
    'ImgTotemParticleHack': '图腾粒子',
    'ImgWorldStatsHack': '世界统计',
    'InfinityMinerHack': '无限挖矿',
    'InventoryTweaksHack': '背包整理',
    'ItemDespawnTimerHack': '物品消失计时',
    'KillauraLegitHack': '保守杀戮',
    'KillFxHack': '击杀特效',
    'KnockbackDirectionHack': '击退方向',
    'LavaESPHack': '岩浆透视',
    'LegitNoFallHack': '保守无摔落',
    'LiquidFillerHack': '液体填充',
    'LongJumpHack': '远跳',
    'MaceAuraHack': '重锤光环',
    'MaceBreakerProHack': '破盾重锤',
    'MaceDmgHack': '重锤增伤',
    'MacroAnchorHack': '锚宏',
    'MarkerHack': '标记',
    'MessageAuraHack': '消息光环',
    'MiddleClickExtraHack': '中键增强',
    'MineEspHack': '挖掘透视',
    'MobHudHack': '生物 HUD',
    'NoGhostBlocksHack': '无幽灵方块',
    'NoRotateHack': '无视转向',
    'NoStatusEffectsHack': '隐藏状态效果',
    'NotifierHack': '通知器',
    'OdmGearHack': '立体机动',
    'OffhandHack': '副手',
    'OreVeinESPHack': '矿脉透视',
    'PacketMineHack': '数据包挖掘',
    'PearlPhaseHack': '珍珠穿墙',
    'PistonCrystalHack': '活塞水晶',
    'PitcherHack': '投掷器',
    'PlaceRenderHack': '放置渲染',
    'PortalGodModeHack': '传送门无敌',
    'PrinterHack': '打印建造',
    'PushCrystalHack': '推水晶',
    'QuiverHack': '箭袋',
    'ReplenishHack': '补充',
    'ReverseStepHack': '反向台阶',
    'RotationHack': '旋转锁定',
    'RTsearchHack': '结构搜索',
    'SchematicProHack': '投影建造',
    'ScreenActionsHack': '界面操作',
    'SelfAnvilHack': '自我铁砧',
    'SelfWebHack': '自我蜘蛛网',
    'ShieldEspHack': '盾牌透视',
    'SonarBypassHack': '声呐绕过',
    'SpamHack': '刷屏',
    'SpawnProoferHack': '刷怪封堵',
    'SpearExploitHack': '长矛利用',
    'SpearKillHack': '长矛击杀',
    'SprintStatusHack': '疾跑状态',
    'StashFinderHack': '物资点搜索',
    'StuckHack': '脱困',
    'SwordGapHack': '剑与金苹果',
    'TimeChangerHack': '时间修改',
    'TntBomberHack': 'TNT 轰炸',
    'TpAnchorHack': '传送锚',
    'TpBowAuraHack': '传送弓光环',
    'TpMachineGunHack': '传送机枪',
    'TracersHack': '追踪线',
    'TrailHack': '拖尾',
    'TunnelESPHack': '隧道透视',
    'VillagerTraderHack': '村民交易',
    'VoidESPHack': '虚空透视',
    'WeatherChangerHack': '天气修改',
    'XCarryHack': '合成栏保留',
    'ZoomHack': '缩放',
}


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        hack_dir = p / 'src/main/java/net/wurstclient/hacks'
        keys = {}
        for cls, zh in ZH.items():
            f = hack_dir / (cls + '.java')
            if not f.exists():
                continue
            t = f.read_text(encoding='utf-8', errors='ignore')
            m = re.search(r'super\("([^"]+)"\)', t)
            if not m:
                continue
            keys['hack.name.' + m.group(1).lower()] = zh

        zh_file = p / 'src/main/resources/assets/wurst/translations/zh_cn.json'
        if not zh_file.exists() or not keys:
            continue
        text = zh_file.read_text(encoding='utf-8')
        lines = text.splitlines(keepends=True)
        anchor = None
        for i, line in enumerate(lines):
            if '"description.wurst.hack.antiknockback"' in line:
                anchor = i
                break
        if anchor is None:
            continue
        missing = [(k, v) for k, v in keys.items()
                   if '"%s"' % k not in text]
        if not missing:
            stats['exists'] = stats.get('exists', 0) + 1
            continue
        if not lines[anchor].rstrip().endswith(','):
            lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
        ins = ['"%s": "%s",\n' % (k, v) for k, v in missing]
        lines[anchor + 1:anchor + 1] = ins
        new = ''.join(lines)
        json.loads(new)
        zh_file.write_text(new, encoding='utf-8', newline='\n')
        stats[len(missing)] = stats.get(len(missing), 0) + 1
    print('hack name zh', stats)


if __name__ == '__main__':
    main()
