# -*- coding: utf-8 -*-
"""Add Chinese hack.name.* entries for the v1.6.0 root project."""
import json
import re
from pathlib import Path

ROOT = Path(r'C:\Users\ui863\Documents\trae_projects\VAP')
ZH_FILE = ROOT / 'src/main/resources/assets/wurst/translations/zh_cn.json'
HACK_DIRS = [ROOT / 'src/main/java/net/wurstclient/hacks',
             ROOT / 'common/src/main/java/net/wurstclient/hacks']

ZH = {
    'AimAssist': '瞄准辅助', 'AirPlace': '空中放置', 'Anchor': '重生锚',
    'AnchorAura': '重生锚光环', 'AntiAFK': '防挂机', 'AntiAim': '防自瞄',
    'AntiBlind': '防失明', 'AntiBot': '反机器人', 'AntiCactus': '防仙人掌',
    'AntiEntityPush': '防实体推动', 'AntiHunger': '防饥饿', 'AntiSpam': '防刷屏',
    'AntiVoid': '防虚空', 'AntiWaterPush': '防水流推动', 'AntiWobble': '防摇晃',
    'ArrowDMG': '箭矢增伤', 'AutoArmor': '自动穿甲', 'AutoBuild': '自动建造',
    'AutoCity': '自动攻城', 'AutoComplete': '自动补全', 'AutoDrop': '自动丢弃',
    'AutoEat': '自动进食', 'AutoFarm': '自动农场', 'AutoFish': '自动钓鱼',
    'AutoLeave': '自动离开', 'AutoLibrarian': '自动图书管理员', 'AutoMine': '自动挖矿',
    'AutoPotion': '自动药水', 'AutoReconnect': '自动重连', 'AutoRespawn': '自动重生',
    'AutoSign': '自动告示', 'AutoSoup': '自动蘑菇汤', 'AutoSprint': '自动疾跑',
    'AutoSteal': '自动偷取', 'AutoSwim': '自动游泳', 'AutoSwitch': '自动切换',
    'AutoSword': '自动剑', 'AutoTool': '自动工具', 'AutoTotem': '自动图腾',
    'AutoTrap': '自动困敌', 'AutoWalk': '自动行走', 'AutoWeb': '自动蜘蛛网',
    'BaritoneClearArea': '范围清理', 'BaritoneMine': '自动挖矿',
    'BaritoneTreeBot': '自动砍树', 'BaritoneWalk': '自动寻路',
    'BarrierESP': '屏障透视', 'BaseFinder': '基地搜索', 'Blink': '闪现',
    'BoatFly': '船飞', 'BonemealAura': '骨粉光环', 'BossStack': 'Boss 叠加',
    'BowAimbot': '弓箭自瞄', 'Breadcrumbs': '面包屑', 'BuildRandom': '随机建造',
    'BunnyHop': '兔子跳', 'Burrow': '埋身', 'CameraDistance': '相机距离',
    'CameraNoClip': '相机穿墙', 'CaveFinder': '洞穴搜索', 'ChatTranslator': '聊天翻译',
    'ChestESP': '箱子透视', 'CityESP': '城市透视', 'ClickAura': '点击光环',
    'ClickGUI': '点击界面', 'CrashChest': '崩服箱', 'CreativeFlight': '创造飞行',
    'Criticals': '暴击', 'CrystalAura': '水晶光环', 'DankBobbing': '视角晃动',
    'DelayRemover': '延迟移除', 'Derp': '鬼畜', 'Dolphin': '海豚',
    'ElytraFly': '鞘翅飞行', 'EntityCulling': '实体剔除', 'Excavator': '挖掘机',
    'ExtraElytra': '额外鞘翅', 'FakeLag': '假延迟', 'FancyChat': '花式聊天',
    'FastBreak': '快速破坏', 'FastLadder': '快速爬梯', 'FastPlace': '快速放置',
    'FastUse': '快速使用', 'FeedAura': '喂食光环', 'FightBot': '战斗机器人',
    'Fish': '钓鱼', 'Flight': '飞行', 'Follow': '跟随', 'ForceOP': '强制 OP',
    'Freecam': '自由视角', 'Fullbright': '全亮', 'Glide': '滑翔',
    'HandNoClip': '手部穿墙', 'HeadRoll': '摇头', 'HealthTags': '血量标签',
    'HighJump': '高跳', 'Hitboxes': '碰撞箱', 'HoleESP': '洞透视',
    'HoleFiller': '填洞', 'InfiniChat': '无限聊天', 'InstaBuild': '瞬间建造',
    'InstantBunker': '瞬间地堡', 'InvWalk': '背包行走', 'ItemESP': '物品透视',
    'ItemGenerator': '物品生成', 'Jesus': '水上行走', 'Jetpack': '喷气背包',
    'Kaboom': '爆炸', 'KeepSprint': '保持疾跑', 'KillPotion': '击杀药水',
    'Killaura': '杀戮光环', 'LSD': 'LSD', 'LightOverlay': '光照覆盖',
    'Liquids': '液体', 'LogoutSpots': '下线点', 'MassTPA': '批量 TPA',
    'MileyCyrus': '麦克风', 'MobESP': '生物透视', 'MobSpawnESP': '刷怪透视',
    'MultiAura': '多重光环', 'MusicPlayer': '音乐播放器', 'NameProtect': '名字保护',
    'NameTags': '名字标签', 'Navigator': '导航器', 'NewChunks': '新区块',
    'NoBackground': '无背景', 'NoClip': '穿墙', 'NoFall': '无摔落',
    'NoFireOverlay': '无火焰遮挡', 'NoFog': '无雾', 'NoHurtcam': '无受伤晃动',
    'NoJumpDelay': '无跳跃延迟', 'NoLevitation': '无漂浮',
    'NoMissCooldown': '无未命中冷却', 'NoOverlay': '无遮挡',
    'NoPumpkin': '无南瓜头', 'NoShieldOverlay': '无盾牌遮挡',
    'NoSlowdown': '无减速', 'NoVelocity': '无击退', 'NoVignette': '无暗角',
    'NoWeather': '无天气', 'NoWeb': '无蜘蛛网', 'Notebot': '自动音符盒',
    'Nuker': '范围破坏', 'NukerLegit': '保守范围破坏',
    'OpenWaterESP': '开放水域透视', 'Overlay': '覆盖层',
    'PacketCanceller': '数据包取消', 'PacketFly': '数据包飞行',
    'PacketLogger': '数据包记录', 'Panic': '紧急停止', 'Parkour': '跑酷',
    'PerimeterDigger': '边界挖掘', 'PlayerESP': '玩家透视',
    'PlayerHalo': '玩家光环', 'PopChams': '图腾透视', 'PortalESP': '传送门透视',
    'PortalGUI': '传送门界面', 'PotionSaver': '药水保护',
    'ProjectilePuncher': '投掷物打击', 'ProphuntESP': '躲猫猫透视',
    'Protect': '保护', 'Radar': '雷达', 'RadialMenu': '径向菜单',
    'RainbowUI': '彩虹界面', 'Reach': '触及', 'RemoteView': '远程视角',
    'Restock': '补货', 'RightClicker': '自动右键', 'RotationSnap': '旋转吸附',
    'SafeWalk': '安全行走', 'ScaffoldWalk': '脚手架行走', 'Search': '搜索',
    'SeedOreESP': '种子矿物透视', 'SeedStructureESP': '种子结构透视',
    'SelfTrap': '自我困住', 'SkinDerp': '皮肤鬼畜', 'Sneak': '潜行',
    'SnowShoe': '雪鞋', 'SpeedHack': '速度', 'SpeedMine': '快速挖掘',
    'SpeedNuker': '快速范围破坏', 'Spider': '蜘蛛', 'Step': '自动上台阶',
    'SuperKnockback': '超级击退', 'Surround': '包围', 'TP-Aura': '传送光环',
    'TargetShader': '目标着色', 'TargetStrafe': '目标环绕',
    'TemplateTool': '模板工具', 'Throw': '投掷', 'Tillaura': '耕地光环',
    'Timer': '计时器', 'Tired': '疲惫', 'TooManyHax': '太多作弊',
    'Trajectories': '轨迹', 'TreeBot': '自动砍树', 'TriggerBot': '触发机器人',
    'TrollPotion': '恶作剧药水', 'TrueSight': '真实视野',
    'Tunneller': '隧道挖掘', 'Twerk': '摇摆', 'VehicleBoost': '载具加速',
    'VeinMiner': '矿脉挖掘', 'Vomit': '呕吐', 'WTap': 'WTap',
    'X-Ray': 'X-Ray 透视',
}


def main():
    zh = json.loads(ZH_FILE.read_text(encoding='utf-8'))
    add = {}
    for d in HACK_DIRS:
        if not d.exists():
            continue
        for f in d.rglob('*Hack.java'):
            t = f.read_text(encoding='utf-8', errors='ignore')
            m = re.search(r'super\("([^"]+)"\)', t)
            if not m:
                continue
            name = m.group(1)
            key = 'hack.name.' + name.lower()
            if key in zh or name not in ZH:
                continue
            add[key] = ZH[name]
    if not add:
        print('nothing to add')
        return
    text = ZH_FILE.read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)
    anchor = next((i for i, l in enumerate(lines) if '"hack.name.' in l), None)
    if anchor is None:
        anchor = next((i for i, l in enumerate(lines)
                       if '"setting.name.' in l), None)
    if anchor is None:
        print('no anchor')
        return
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    lines[anchor + 1:anchor + 1] = ['"%s": "%s",\n' % (k, v)
                                     for k, v in add.items()]
    new = ''.join(lines)
    json.loads(new)
    ZH_FILE.write_text(new, encoding='utf-8', newline='\n')
    print('added', len(add))


if __name__ == '__main__':
    main()
