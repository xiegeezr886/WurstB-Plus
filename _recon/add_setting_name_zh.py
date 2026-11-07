# -*- coding: utf-8 -*-
"""Compose Chinese names for all new hack settings and add setting.name.* keys."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

EXACT = {
    'AI name': 'AI 名称', 'Action': '操作', 'Advanced': '高级', 'Aimbot': '自动瞄准',
    'Angle': '角度', 'Animals': '动物', 'Attacks': '攻击', 'Balance': '平衡',
    'Barrels': '木桶', 'Blink': '闪现', 'Boats': '船', 'Blocks only': '仅方块',
    'Bypass negative timer': '绕过负计时器', 'Cats': '猫', 'Chestplate': '胸甲',
    'Chests': '箱子', 'Chickens': '鸡', 'Chinese': '中文', 'Climb Speed': '攀爬速度',
    'Combat particle': '战斗粒子', 'Confirmed color': '已确认颜色', 'Connected': '已连接',
    'Cows': '牛', 'Crystals': '水晶', 'Debug': '调试', 'Detonate slot': '引爆槽位',
    'Direction': '方向', 'Disable NoFall': '禁用无摔落', 'Double place': '双重放置',
    'Drag': '阻力', 'Effect': '效果', 'Enchanted golden apples': '附魔金苹果',
    'Entity actions': '实体操作', 'Fire particle': '火焰粒子', 'Firework': '烟花',
    'Flight control': '飞行控制', 'Force': '强制', 'Friend box': '好友框',
    'Friend box color': '好友框颜色', 'Friend color': '好友颜色', 'Friend fill': '好友填充',
    'Friend fill color': '好友填充颜色', 'Full bright': '全亮', 'Gold color': '金颜色',
    'Golden apples': '金苹果', 'HUD color': 'HUD 颜色', 'HUD notifications': 'HUD 通知',
    'Hand': '手', 'Health': '生命值', 'Horses': '马', 'Ignore friends': '忽略好友',
    'Iron color': '铁颜色', 'Item': '物品', 'Items': '物品', 'Key 1': '按键 1',
    'Key 2': '按键 2', 'Key 3': '按键 3', 'Key 4': '按键 4', 'Keywords': '关键词',
    'Left CPS': '左键 CPS', 'Legit mode': '保守模式', 'Lightning': '闪电',
    'Liquid': '液体', 'Lock anytime': '随时锁定', 'Log out': '退出', 'Looked-at entity': '注视的实体',
    'Mace mode': '重锤模式', 'Magic particle': '魔法粒子', 'Map scale': '地图缩放',
    'Message': '消息', 'Messages': '消息', 'Middle click action': '中键操作',
    'Middle click behavior': '中键行为', 'Middle click lock': '中键锁定',
    'Minecarts': '矿车', 'Mobs': '生物', 'Movements': '移动', 'Name': '名称',
    'Nature particle': '自然粒子', 'Navigation': '导航', 'Nether roof': '下界顶',
    'No suicide': '禁止自杀', 'Normal furnaces': '普通熔炉', 'Notification': '通知',
    'Notifications': '通知', 'Notify': '通知', 'Notify friends': '通知好友',
    'Nuker max Y': '范围挖掘最大 Y', 'Nuker min Y': '范围挖掘最小 Y',
    'Nuker mode': '范围挖掘模式', 'Nuker range': '范围挖掘范围', 'Obfuscated': '混淆',
    'Offset': '偏移', 'Only adults': '仅成年', 'Only in hole': '仅在洞中',
    'Only in holes': '仅在洞中', 'Only librarians': '仅图书管理员',
    'Only main hand': '仅主手', 'Only on click': '仅点击时', 'Only on ground': '仅在地面',
    'Only on shield': '仅举盾时', 'Only targeted': '仅被瞄准时',
    'Only unlit furnaces': '仅未点燃熔炉', 'Only when flying': '仅飞行时',
    'Only when looking up': '仅向上看时', 'Only while held': '仅按住时',
    'Only while holding use': '仅按住使用键时', 'Only while moving': '仅移动时',
    'Only while sneaking': '仅潜行时', 'Only with Flight': '仅配合飞行',
    'Only with Killaura': '仅配合杀戮', 'Opacity': '不透明度', 'Other animals': '其他动物',
    'Other entities': '其他实体', 'Other players': '其他玩家', 'Own blocks': '自己的方块',
    'Password': '密码', 'Pearl landing': '珍珠落地', 'Pigs': '猪',
    'Players': '玩家', 'Pop counter': '图腾计数', 'Prefix': '前缀',
    'Prefix color': '前缀颜色', 'Prefix end color': '前缀末端颜色', 'Prefix text': '前缀文本',
    'Preset': '预设', 'Priority': '优先级', 'Progress': '进度', 'Quartz color': '石英颜色',
    'Quick swap': '快速切换', 'Radius': '半径', 'Rainbow spread': '彩虹扩散',
    'Regex': '正则', 'Register': '注册', 'Render': '渲染', 'Require gear': '需要装备',
    'Require mending': '需要经验修补', 'Require right click': '需要右键',
    'Require use key': '需要使用键', 'Requirements': '需求', 'Sheep': '羊',
    'Shield blocks': '盾牌方块', 'Shield breaker': '破盾', 'Shulker boxes': '潜影盒',
    'Shulker contents': '潜影盒内容', 'Sign': '告示', 'Silent': '静默',
    'Silent bow': '静默弓', 'Silent switch': '静默切换', 'Single side': '单面',
    'Slimes': '史莱姆', 'Smart': '智能', 'Smokers': '烟熏炉', 'Smooth': '平滑',
    'Sneak': '潜行', 'Sound': '声音', 'Sound ID': '声音 ID', 'Spam depth': '连发深度',
    'Speed randomization': '速度随机化', 'Stem': '茎', 'Strength': '力度',
    'Strikethrough': '删除线', 'Swing': '挥手', 'Swings': '挥手', 'Tab size': 'Tab 大小',
    'Text': '文本', 'Text box': '文本框', 'Theme': '主题', 'Timestamps': '时间戳',
    'Title': '标题', 'Toggle messages': '切换消息', 'Totem bypass': '图腾绕过',
    'Totem pops': '图腾弹出', 'Totem target': '图腾目标', 'Tracers': '追踪线',
    'Transactions': '事务', 'Trim materials': '纹饰材料', 'Trim targets': '纹饰目标',
    'Trim templates': '纹饰模板', 'Underline': '下划线', 'Unstackable': '不可堆叠',
    'Upgrade targets': '升级目标', 'Use Baritone': '使用 Baritone',
    'Use enchantments': '使用附魔', 'Use inventory axe': '使用背包斧',
    'VClip': 'VClip', 'VClip step': 'VClip 步长', 'Vanilla color': '原版颜色',
    'Villager type': '村民类型', 'Villagers': '村民', 'Visibility': '可见性',
    'Visual range': '视距', 'Vitals warnings': '生命体征警告', 'Volume': '音量',
    'Wolves': '狼', 'Word wrap': '自动换行', 'Write when book screen opens': '书本界面打开时写入',
    'Zoom': '缩放', 'Action delay': '操作延迟', 'Active preset': '当前预设',
    'Aim mode': '瞄准模式', 'Aim only while using': '仅使用时瞄准', 'Aim range': '瞄准范围',
    'Air only': '仅空中', 'All projectiles': '所有投掷物', 'Allow airborne': '允许空中',
    'Allow enchanted golden apple': '允许附魔金苹果', 'Allow into void': '允许进入虚空',
    'Altitude and depth': '高度与深度', 'Anchor click range': '重生锚点击范围',
    'Anchor max damage': '重生锚最大伤害', 'Anchor scan border': '重生锚扫描边界',
    'Animation exponent': '动画指数', 'Anti anchor': '防锚', 'Anti crystal': '防水晶',
    'Anti pull': '防拉拽', 'Anti-clear': '防清屏', 'Anti-kick delay': '防踢延迟',
    'Anti-kick mode': '防踢模式', 'Anti-spam': '防刷屏', 'Attack delay': '攻击延迟',
    'Attack mobs': '攻击生物', 'Attack players': '攻击玩家',
    'Attack while using items': '使用物品时攻击', 'Auto clean': '自动清理',
    'Auto close': '自动关闭', 'Auto disable': '自动禁用', 'Auto drop': '自动丢弃',
    'Auto elytra': '自动鞘翅', 'Auto fire': '自动开火', 'Auto firework': '自动烟花',
    'Auto height': '自动高度', 'Auto mine': '自动挖掘', 'Auto protect': '自动保护',
    'Auto refill': '自动补充', 'Auto save password': '自动保存密码', 'Auto search': '自动搜索',
    'Auto shoot': '自动射击', 'Auto store': '自动存储', 'Auto takeoff': '自动起飞',
    'Auto toggle': '自动切换', 'Auto tool': '自动工具', 'Auto walk': '自动行走',
    'Axe trigger': '斧触发', 'Bad words': '不良词', 'Baritone walk': 'Baritone 行走',
    'Beam height': '光束高度', 'Blast furnaces': '高炉', 'Blink + Lunge': '闪现 + 突进',
    'Blink aimbot': '闪现自动瞄准', 'Blink back': '闪现返回',
    'Blink lunge delay': '闪现突进延迟', 'Blink lunge strength': '闪现突进力度',
    'Block anchor': '方块锚', 'Block fabric payloads': '拦截 Fabric 负载',
    'Block outgoing': '拦截发出', 'Blocks per tick': '每刻方块数', 'Boost Factor': '加速系数',
    'Brand name': '品牌名称', 'Breach swapping': '破甲切换', 'Break delay': '破坏延迟',
    'Break piston': '破坏活塞', 'Breaking': '破坏', 'Buy items': '购买物品',
    'Cancel C0B': '取消 C0B', 'Case sensitivity': '大小写敏感', 'Characters per page': '每页字符',
    'Charge delay': '蓄力延迟', 'Charge speed': '蓄力速度', 'Charge time': '蓄力时间',
    'Charge tolerance': '蓄力容差', 'Chat coordinates': '聊天坐标', 'Chat info': '聊天信息',
    'Check down': '检查下方', 'Check effects': '检查效果', 'Check line of sight': '检查视线',
    'Check saddle': '检查鞍', 'Check self': '检查自身', 'Chunk radius': '区块半径',
    'Circle color': '圆颜色', 'Clean mode': '清理模式', 'Click delay': '点击延迟',
    'Close delay': '关闭延迟', 'Close inventory': '关闭背包', 'Coal color': '煤炭颜色',
    'Color mode': '颜色模式', 'Color range': '颜色范围', 'Column height': '柱高度',
    'Command interval': '命令间隔', 'Command mode': '命令模式', 'Compatibility mode': '兼容模式',
    'Container range': '容器范围', 'Copper color': '铜颜色', 'Crit height': '暴击高度',
    'Crossbow delay': '弩延迟', 'Crossbow mode': '弩模式', 'Crystal color': '水晶颜色',
    'Crystal hit range': '水晶命中范围', 'Crystal max damage': '水晶最大伤害',
    'Crystal scan border': '水晶扫描边界', 'Custom commands': '自定义命令',
    'Custom keywords': '自定义关键词', 'Custom time': '自定义时间', 'Damage indicator': '伤害指示',
    'Death coords': '死亡坐标', 'Debris color': '残骸颜色', 'Default message': '默认消息',
    'Depth test': '深度测试', 'Detonate delay': '引爆延迟', 'Diamond color': '钻石颜色',
    'Digs': '挖掘', 'Dip distance': '下沉距离', 'Disable after jump': '跳跃后禁用',
    'Disable in creative': '创造模式禁用', 'Disable on disconnect': '断开时禁用',
    'Disable on flag': '触发时禁用', 'Disconnect on reach': '到达时断开',
    'Distance boost': '距离增强', 'Distance check': '距离检查', 'Drift speed': '漂移速度',
    'Dual multiplier': '双倍率', 'Emerald color': '绿宝石颜色', 'Enable FoKillAura': '启用 FoKillAura',
    'End fill color': '终点填充颜色', 'End line color': '终点线条颜色',
    'Entity range': '实体范围', 'Explosion smoke': '爆炸烟雾', 'Fall protection': '坠落保护',
    'Fall speed': '坠落速度', 'Fill mode': '填充模式', 'Filter color': '过滤颜色',
    'Filter velocity': '过滤速度', 'Firework delay': '烟花延迟', 'Floor blocks': '地板方块',
    'Flush range': '刷新范围', 'Follow adventure': '跟随冒险', 'Follow adventure players': '跟随冒险玩家',
    'Follow creative': '跟随创造', 'Follow creative players': '跟随创造玩家',
    'Follow spectator': '跟随旁观', 'Follow survival': '跟随生存',
    'Follow survival players': '跟随生存玩家', 'Food items': '食物物品',
    'Food values': '食物数值', 'Force timeout': '强制超时', 'Forced flush range': '强制刷新范围',
    'Freeze look': '冻结视角', 'Freeze look silently': '静默冻结视角',
    'Fuzzy match': '模糊匹配', 'Ground check': '地面检查', 'Health threshold': '生命值阈值',
    'Height offset': '高度偏移', 'Hide icons': '隐藏图标', 'Hide matches': '隐藏匹配项',
    'Hide when inside block': '在方块内时隐藏', 'Highlight boxes': '高亮框',
    'Highlight color': '高亮颜色', 'Hole height': '洞高度', 'Horizontal Strength': '水平力度',
    'Horizontal offset': '水平偏移', 'Horizontal radius': '水平半径', 'Idle color': '空闲颜色',
    'Ignore behind walls': '忽略墙后', 'Ignore case': '忽略大小写', 'Ignore commands': '忽略命令',
    'Ignore named': '忽略命名', 'Ignore named mobs': '忽略命名生物', 'Ignore own': '忽略自己的',
    'Ignore pets': '忽略宠物', 'Ignore self': '忽略自身', 'Ignore tamed mobs': '忽略已驯服生物',
    'Include other storage': '包括其他容器', 'Initial delay': '初始延迟',
    'Insert fuel': '放入燃料', 'Insert items': '放入物品', 'Inspect delay': '检查延迟',
    'Instant break': '瞬间破坏', 'Interact range': '交互范围', 'Inventory swap': '背包切换',
    'Item FOV': '物品视野', 'Item saver': '物品保护', 'Jitter': '抖动',
    'Jump speed': '跳跃速度', 'Keep elytra': '保留鞘翅', 'Keep history': '保留历史',
    'Keep items': '保留物品', 'Keep time': '保留时间', 'Keep-alives': '保活',
    'Lagback': '回弹', 'Lapis color': '青金石颜色', 'Light level': '光照等级',
    'Light threshold': '光照阈值', 'Lightning amount': '闪电数量',
    'Limit per second': '每秒限制', 'Liquid': '液体', 'Locate command': '定位命令',
    'Login command': '登录命令', 'Look at target': '看向目标', 'Low health': '低生命值',
    'Lunge direction': '突进方向', 'Lunge strength': '突进力度', 'Mace trigger': '重锤触发',
    'Mark on right-click': '右键点击标记', 'Max attempts': '最大尝试', 'Max blocks per tick': '每刻最大方块',
    'Max distance': '最大距离', 'Max drift': '最大漂移', 'Max lag': '最大延迟',
    'Max lava blocks': '最大岩浆方块', 'Max markers': '最大标记', 'Max packet data': '最大数据包数据',
    'Max packets': '最大数据包', 'Max points': '最大点', 'Max range': '最大范围',
    'Max reach': '最大触及', 'Max retries': '最大重试', 'Max self damage': '最大自身伤害',
    'Max targets': '最大目标', 'Max threshold': '最大阈值', 'Max trades': '最大交易',
    'Max veins': '最大矿脉', 'Max velocity': '最大速度', 'Maximum delay': '最大延迟',
    'Merge stacks': '合并堆叠', 'Middle click lock': '中键锁定', 'Min count': '最小数量',
    'Min crystal age': '最小水晶存活', 'Min damage': '最小伤害',
    'Min damage advantage': '最小伤害优势', 'Min distance': '最小距离',
    'Min health': '最小生命值', 'Min lag': '最小延迟', 'Min level': '最小等级',
    'Min range': '最小范围', 'Min threshold': '最小阈值', 'Min vein size': '最小矿脉大小',
    'Min velocity': '最小速度', 'Mine piston': '挖掘活塞', 'Mine threshold': '挖掘阈值',
    'Minimum charge': '最小蓄力', 'Minimum delay': '最小延迟',
    'Minimum durability': '最小耐久', 'Minimum range': '最小范围',
    'Minimum storage': '最小容器', 'Misc particle': '杂项粒子', 'Move distance': '移动距离',
    'Move from hotbar': '从快捷栏移动', 'Move speed': '移动速度', 'Multi place': '多重放置',
    'Multi shot': '多重射击', 'Notifier': '通知器', 'Notify limit reached': '达到限制时通知',
    'Only below feet': '仅在脚下', 'Only fully oxidized': '仅完全氧化',
    'Open delay': '打开延迟', 'Open opacity': '打开不透明度', 'Open timeout': '打开超时',
    'Opened color': '已打开颜色', 'Operation mode': '操作模式', 'Orbit speed': '环绕速度',
    'Other color': '其他颜色', 'Overwrite existing pages': '覆盖现有页面',
    'Packet limit': '数据包限制', 'Packet rotation': '数据包旋转', 'Pages': '页',
    'Paper max distance': '纸最大距离', 'Paper packets': '纸数据包',
    'Particle category': '粒子类别', 'Particle count': '粒子数量', 'Particle shape': '粒子形状',
    'Particle speed': '粒子速度', 'Particles': '粒子', 'Path color': '路径颜色',
    'Pause on eat': '进食时暂停', 'Pause on use': '使用时暂停', 'Pause while mining': '挖掘时暂停',
    'Pause while using': '使用时暂停', 'Pause while using item': '使用物品时暂停',
    'Ping compensation': '延迟补偿', 'Piston color': '活塞颜色', 'Piston push': '活塞推动',
    'Pitch angle': '俯仰角度', 'Pitch lock mode': '俯仰锁定模式', 'Place at feet': '脚下放置',
    'Place delay': '放置延迟', 'Place range': '放置范围', 'Place string bottom': '放置线底部',
    'Place string middle': '放置线中部', 'Place string top': '放置线顶部',
    'Placements': '放置', 'Placing': '放置', 'Potion color': '药水颜色',
    'Potion shadow': '药水阴影', 'Predict acceleration': '预测加速度',
    'Predict movement': '预测移动', 'Prediction scale': '预测缩放', 'Prefix text': '前缀文本',
    'Preserve pitch': '保持俯仰', 'Preserve yaw': '保持偏航', 'Price mode': '价格模式',
    'Print preset': '打印预设', 'Print rate limit': '打印速率限制',
    'Projectile items': '投掷物物品', 'Pull speed': '拉拽速度', 'Random delay': '随机延迟',
    'Random length': '随机长度', 'Random order': '随机顺序', 'Random range': '随机范围',
    'Random whisper': '随机私聊', 'Read wait': '读取等待', 'Ready ticks modifier': '就绪刻修正',
    'Recheck delay': '重新检查延迟', 'Recoil amount': '后坐力数量', 'Redstone color': '红石颜色',
    'Redstone mode': '红石模式', 'Register command': '注册命令', 'Rename existing': '重命名现有',
    'Render box': '渲染框', 'Render confirmed': '渲染已确认', 'Render distance': '渲染距离',
    'Render hooks': '渲染钩子', 'Render mode': '渲染模式', 'Render name': '渲染名称',
    'Render path': '渲染路径', 'Render suspected': '渲染可疑', 'Repair blocks': '修复方块',
    'Repair threshold': '修复阈值', 'Replacement': '替换', 'Reset on miss': '未命中时重置',
    'Reset rotation': '重置旋转', 'Resistance level check': '抗性等级检查',
    'Restock cooldown': '补货冷却', 'Retry delay': '重试延迟',
    'Return to original': '返回原始', 'Return to original position': '返回原始位置',
    'Right CPS': '右键 CPS', 'Rotation Speed': '旋转速度', 'Same enchants': '相同附魔',
    'Save current hotbar': '保存当前快捷栏', 'Scan interval': '扫描间隔',
    'Scan range': '扫描范围', 'Scroll sensitivity': '滚轮灵敏度', 'Scroll step': '滚轮步长',
    'Search inventory': '搜索背包', 'Search now': '立即搜索', 'Search radius': '搜索半径',
    'Search range': '搜索范围', 'Second box color': '第二个框颜色',
    'Second fill color': '第二填充颜色', 'Second shot delay': '第二发延迟',
    'Second shot strength': '第二发力度', 'Select torch': '选择火把', 'Sell items': '出售物品',
    'Send on enable': '启用时发送', 'Server address': '服务器地址', 'Server lag': '服务器延迟',
    'Shape mode': '形状模式', 'Shoot delay': '射击延迟', 'Shot delay': '射击延迟',
    'Shovel durability': '铲子耐久', 'Show background': '显示背景', 'Show biome': '显示生物群系',
    'Show block name': '显示方块名称', 'Show coordinates': '显示坐标', 'Show count': '显示数量',
    'Show days': '显示天数', 'Show deaths': '显示死亡', 'Show distance': '显示距离',
    'Show effect names': '显示效果名称', 'Show feedback': '显示反馈', 'Show gamemode': '显示游戏模式',
    'Show health': '显示生命值', 'Show icon': '显示图标', 'Show invisible': '显示隐身',
    'Show kills': '显示击杀', 'Show level table': '显示等级表', 'Show mined blocks': '显示已挖方块',
    'Show name': '显示名称', 'Show names': '显示名称', 'Show ping': '显示延迟',
    'Show playtime': '显示游玩时间', 'Show progress': '显示进度', 'Show seconds': '显示秒数',
    'Show targets': '显示目标', 'Show time': '显示时间', 'Show weather': '显示天气',
    'Silent switch': '静默切换', 'Simulation steps': '模拟步数', 'Sleep wait': '睡眠等待',
    'Smart strength': '智能力度', 'Smeltable items': '可熔炼物品', 'Smooth progress': '平滑进度',
    'Sneak to scroll': '潜行滚动', 'Sort delay': '整理延迟', 'Sort hotbar': '整理快捷栏',
    'Sort on open': '打开时整理', 'Spam crossbows': '连发弩', 'Spam packets': '连发数据包',
    'Spin speed': '旋转速度', 'Sprint color': '疾跑颜色', 'Start fill color': '起点填充颜色',
    'Start line color': '起点线条颜色', 'Static color': '静态颜色', 'Stay on': '保持开启',
    'Sticky target': '粘性目标', 'Stop at target': '到达目标停止', 'Stop distance': '停止距离',
    'Stop in water': '水中停止', 'Stop on book': '书上停止', 'Stop on kill': '击杀时停止',
    'Store color': '存储颜色', 'Store empty slots': '存储空槽', 'Strict physics on join': '加入时严格物理',
    'Supply box name': '补给箱名称', 'Supply color': '补给颜色', 'Supply stacks': '补给堆叠',
    'Suspected color': '可疑颜色', 'Swap back': '切回', 'Swap-back delay': '切回延迟',
    'Swing hand': '挥手', 'Switch advantage': '切换优势', 'Switch delay': '切换延迟',
    'Sword trigger': '剑触发', 'TP direction': '传送方向', 'Take output': '取出产物',
    'Takeoff delay': '起飞延迟', 'Target X': '目标 X', 'Target Y': '目标 Y', 'Target Z': '目标 Z',
    'Target adventure players': '目标冒险玩家', 'Target babies': '目标幼年',
    'Target biome': '目标生物群系', 'Target blocks': '目标方块', 'Target color': '目标颜色',
    'Target creative players': '目标创造玩家', 'Target distance': '目标距离',
    'Target hostile mobs': '目标敌对生物', 'Target memory': '目标记忆',
    'Target mobs': '目标生物', 'Target player': '目标玩家', 'Target players': '目标玩家',
    'Target priority': '目标优先级', 'Target range': '目标范围', 'Target slot': '目标槽位',
    'Target survival players': '目标生存玩家', 'Text X': '文本 X', 'Text Y': '文本 Y',
    'Text color': '文本颜色', 'Text scale': '文本缩放', 'Text shadow': '文本阴影',
    'Through walls': '穿墙', 'Thunder level': '雷等级', 'Toggle on break': '破坏时切换',
    'Totem health': '图腾生命值', 'Tracer color': '追踪线颜色', 'Track range': '追踪范围',
    'Trigger': '触发', 'Unopened color': '未打开颜色', 'Update interval': '更新间隔',
    'Upward lift': '向上升力', 'Velocity XZ': 'XZ 速度', 'Velocity Y': 'Y 速度',
    'Vertical Strength': '垂直力度', 'Vertical correction': '垂直修正',
    'Vertical offset': '垂直偏移', 'Wait for hurt time': '等待无敌帧', 'Wait mine': '等待挖掘',
    'Wake-up speed': '唤醒速度', 'Walk distance': '行走距离', 'Walk home': '走回家',
    'Wall range': '墙壁范围', 'Warning X': '警告 X', 'Warning Y': '警告 Y',
    'Warning color': '警告颜色', 'Weapon': '武器', 'Weather and temperature': '天气与温度',
    'When holding right click': '按住右键时', 'While in screens': '界面内',
    'Whisper command': '私聊命令', 'Whisper mode': '私聊模式', 'Window delay': '窗口延迟',
    'Y offset': 'Y 偏移', 'Yaw angle': '偏航角度', 'Yaw deceive': '偏航欺骗',
    'Yaw lock mode': '偏航锁定模式', 'Yaw offset': '偏航偏移',
}

EXACT.update({
    'Affect': '影响',
    'Alpha': '透明度',
    'Contrast': '对比度',
    'Density': '密度',
    'Gravity': '重力',
    'Hotbar': '快捷栏',
    'Hue': '色相',
    'Saturation': '饱和度',
    'Sharpness': '锋利',
    'Width': '宽度',
    'Size': '大小',
    'Stained': '染色',
    'Pushing': '推动',
    'Spin speed': '旋转速度',
    'Max': '最大数量',
    'URL': '网址',
    'Show protocol': '显示协议',
    'Show version': '显示版本',
    'Open on enable': '启用时打开',
    'Affect self': '影响自己',
    'Only sprinting': '仅冲刺时',
    'Block hit': '方块命中',
    'Hide attack indicator': '隐藏攻击指示器',
    'Old damage tilt': '旧版伤害倾斜',
    'Always sharpness': '始终锋利粒子',
    'Always criticals': '始终暴击粒子',
    'Sharpness amount': '锋利粒子数量',
    'Criticals amount': '暴击粒子数量',
    'X scale': 'X 缩放',
    'Y scale': 'Y 缩放',
    'Z scale': 'Z 缩放',
    'Normal glass': '普通玻璃',
    'Stained glass': '染色玻璃',
    'Hotbar overlay': '快捷栏覆盖',
    'Show players': '显示玩家',
    'Show mobs': '显示生物',
    'Show other': '显示其他',
    'Master': '主音量',
    'Music': '音乐',
    'Records': '唱片',
    'Weather': '天气',
    'Blocks': '方块',
    'Hostile mobs': '敌对生物',
    'Neutral mobs': '中立生物',
    'Ambient': '环境',
    'Voice': '语音',
    'Duration': '持续时间',
    'Highlight': '高亮',
    'Through walls': '穿墙',
    'Mode': '模式',
    'Amount': '强度',
    'Range': '范围',
    'Height': '高度',
    'Scale': '缩放',
    'Background': '背景',
    'Speed': '速度',
    'Delay': '延迟',
    'Strength': '强度',
    'Show name': '显示名称',
    'Hide pumpkin': '隐藏南瓜',
    'Hide fire': '隐藏火焰',
    'X': 'X',
    'Y': 'Y',
    'Z': 'Z',
})

WORDS = {
    'ai': 'AI', 'name': '名称', 'above': '上方', 'height': '高度', 'trigger': '触发',
    'distance': '距离', 'action': '操作', 'delay': '延迟', 'active': '当前', 'preset': '预设',
    'advanced': '高级', 'aim': '瞄准', 'mode': '模式', 'only': '仅', 'while': '当',
    'using': '使用', 'range': '范围', 'aimbot': '自动瞄准', 'air': '空中', 'all': '所有',
    'projectiles': '投掷物', 'allow': '允许', 'airborne': '空中', 'enchanted': '附魔',
    'golden': '金', 'apple': '苹果', 'into': '进入', 'void': '虚空', 'altitude': '高度',
    'depth': '深度', 'anchor': '重生锚', 'click': '点击', 'max': '最大', 'damage': '伤害',
    'scan': '扫描', 'border': '边界', 'angle': '角度', 'animals': '动物', 'animation': '动画',
    'exponent': '指数', 'anti': '防', 'crystal': '水晶', 'pull': '拉拽', 'clear': '清屏',
    'kick': '踢出', 'spam': '刷屏', 'attack': '攻击', 'mobs': '生物', 'players': '玩家',
    'items': '物品', 'attacks': '攻击', 'auto': '自动', 'clean': '清理', 'close': '关闭',
    'disable': '禁用', 'drop': '丢弃', 'elytra': '鞘翅', 'fire': '开火', 'firework': '烟花',
    'mine': '挖掘', 'protect': '保护', 'refill': '补充', 'save': '保存', 'password': '密码',
    'search': '搜索', 'shoot': '射击', 'store': '存储', 'switch': '切换', 'takeoff': '起飞',
    'toggle': '切换', 'tool': '工具', 'walk': '行走', 'axe': '斧', 'background': '背景',
    'bad': '不良', 'words': '词', 'balance': '平衡', 'baritone': 'Baritone', 'barrels': '木桶',
    'beam': '光束', 'blast': '高炉', 'furnaces': '熔炉', 'blink': '闪现', 'lunge': '突进',
    'back': '返回', 'strength': '力度', 'block': '方块', 'fabric': 'Fabric', 'payloads': '负载',
    'outgoing': '发出', 'blocks': '方块', 'boats': '船', 'bold': '粗体', 'boost': '加速',
    'factor': '系数', 'box': '框', 'brand': '品牌', 'breach': '破甲', 'swapping': '切换',
    'break': '破坏', 'piston': '活塞', 'breaking': '破坏', 'buy': '购买', 'bypass': '绕过',
    'negative': '负', 'timer': '计时器', 'cancel': '取消', 'case': '大小写',
    'sensitivity': '灵敏度', 'cats': '猫', 'characters': '字符', 'page': '页', 'charge': '蓄力',
    'chat': '聊天', 'coordinates': '坐标', 'info': '信息', 'check': '检查', 'down': '下方',
    'effects': '效果', 'line': '线', 'sight': '视线', 'saddle': '鞍', 'self': '自身',
    'chestplate': '胸甲', 'chests': '箱子', 'chickens': '鸡', 'chinese': '中文',
    'chunk': '区块', 'circle': '圆', 'climb': '攀爬', 'inventory': '背包', 'coal': '煤炭',
    'color': '颜色', 'column': '柱', 'combat': '战斗', 'particle': '粒子', 'command': '命令',
    'commands': '命令', 'compatibility': '兼容', 'confirmed': '已确认', 'connected': '已连接',
    'container': '容器', 'cooldown': '冷却', 'copper': '铜', 'cows': '牛', 'crit': '暴击',
    'crossbow': '弩', 'hit': '命中', 'custom': '自定义', 'time': '时间', 'cycle': '循环',
    'indicator': '指示', 'death': '死亡', 'coords': '坐标', 'debris': '残骸', 'debug': '调试',
    'default': '默认', 'message': '消息', 'messages': '消息', 'test': '测试',
    'detonate': '引爆', 'slot': '槽位', 'diamond': '钻石', 'digs': '挖掘', 'dip': '下沉',
    'direction': '方向', 'nofall': '无摔落', 'after': '之后', 'jump': '跳跃',
    'creative': '创造', 'disconnect': '断开', 'reach': '触及', 'double': '双重',
    'place': '放置', 'drag': '阻力', 'drift': '漂移', 'dual': '双', 'multiplier': '倍率',
    'effect': '效果', 'emerald': '绿宝石', 'enable': '启用', 'end': '终点', 'fill': '填充',
    'entity': '实体', 'actions': '操作', 'explosion': '爆炸', 'smoke': '烟雾', 'fov': '视野',
    'fall': '坠落', 'protection': '保护', 'speed': '速度', 'filter': '过滤',
    'velocity': '速度', 'flight': '飞行', 'control': '控制', 'floor': '地板',
    'flush': '刷新', 'follow': '跟随', 'adventure': '冒险', 'spectator': '旁观',
    'survival': '生存', 'food': '食物', 'values': '数值', 'force': '强制',
    'timeout': '超时', 'forced': '强制', 'freeze': '冻结', 'look': '视角',
    'silently': '静默', 'friend': '好友', 'fuel': '燃料', 'full': '全', 'bright': '亮',
    'fuzzy': '模糊', 'match': '匹配', 'gold': '金', 'apples': '苹果', 'ground': '地面',
    'hud': 'HUD', 'notifications': '通知', 'hand': '手', 'health': '生命值',
    'threshold': '阈值', 'offset': '偏移', 'hide': '隐藏', 'icons': '图标',
    'matches': '匹配项', 'highlight': '高亮', 'boxes': '框', 'hole': '洞', 'holes': '洞',
    'horizontal': '水平', 'radius': '半径', 'horses': '马', 'idle': '空闲',
    'ignore': '忽略', 'behind': '后面', 'walls': '墙', 'friends': '好友',
    'named': '命名', 'own': '自己的', 'pets': '宠物', 'tamed': '已驯服',
    'include': '包括', 'other': '其他', 'storage': '容器', 'initial': '初始',
    'insert': '放入', 'inspect': '检查', 'instant': '瞬间', 'interact': '交互',
    'interval': '间隔', 'swap': '切换', 'iron': '铁', 'item': '物品', 'saver': '保护',
    'jitter': '抖动', 'keep': '保留', 'history': '历史', 'key': '按键',
    'keywords': '关键词', 'lagback': '回弹', 'lapis': '青金石', 'left': '左', 'cps': 'CPS',
    'legit': '保守', 'light': '光照', 'level': '等级', 'lightning': '闪电',
    'amount': '数量', 'limit': '限制', 'liquid': '液体', 'locate': '定位',
    'lock': '锁定', 'anytime': '随时', 'log': '记录', 'out': '退出', 'login': '登录',
    'looked-at': '注视的', 'low': '低', 'magic': '魔法', 'map': '地图', 'scale': '缩放',
    'mark': '标记', 'marker': '标记', 'maximum': '最大', 'merge': '合并', 'stacks': '堆',
    'middle': '中', 'min': '最小', 'minimum': '最小', 'misc': '杂项', 'move': '移动',
    'movements': '移动', 'multi': '多重', 'nature': '自然', 'navigation': '导航',
    'nether': '下界', 'roof': '顶', 'no': '无', 'normal': '普通',
    'notification': '通知', 'notify': '通知', 'nuker': '范围挖掘', 'obfuscated': '混淆',
    'opacity': '不透明度', 'open': '打开', 'opened': '已打开', 'operation': '操作',
    'orbit': '环绕', 'ores': '矿石', 'overwrite': '覆盖', 'existing': '现有', 'pages': '页',
    'packet': '数据包', 'packets': '数据包', 'paper': '纸', 'path': '路径',
    'pause': '暂停', 'pearl': '珍珠', 'ping': '延迟', 'pitcher': '投掷',
    'placements': '放置', 'placing': '放置', 'pop': '图腾', 'counter': '计数',
    'position': '坐标', 'potion': '药水', 'predict': '预测', 'prediction': '预测',
    'prefix': '前缀', 'preserve': '保持', 'price': '价格', 'print': '打印',
    'priority': '优先级', 'progress': '进度', 'projectile': '投掷物', 'quartz': '石英',
    'quick': '快速', 'rain': '雨', 'rainbow': '彩虹', 'random': '随机', 'read': '读取',
    'ready': '就绪', 'recheck': '重新检查', 'recoil': '后坐力', 'redstone': '红石',
    'regex': '正则', 'register': '注册', 'rename': '重命名', 'render': '渲染',
    'repair': '修复', 'replacement': '替换', 'require': '需要', 'reset': '重置',
    'resistance': '抗性', 'restock': '补货', 'retry': '重试', 'return': '返回',
    'right': '右', 'rotate': '旋转', 'rotation': '旋转', 'same': '相同',
    'scroll': '滚轮', 'second': '第二', 'select': '选择', 'sell': '出售',
    'send': '发送', 'server': '服务器', 'shadow': '阴影', 'shape': '形状',
    'sheep': '羊', 'shield': '盾牌', 'shot': '射击', 'shovel': '铲子',
    'show': '显示', 'shulker': '潜影盒', 'sign': '告示', 'silent': '静默',
    'simulation': '模拟', 'single': '单', 'side': '面', 'sleep': '睡眠',
    'slimes': '史莱姆', 'smart': '智能', 'smeltable': '可熔炼', 'smokers': '烟熏炉',
    'smooth': '平滑', 'sneak': '潜行', 'sort': '整理', 'sound': '声音', 'id': 'ID',
    'spam': '连发', 'spin': '旋转', 'sprint': '疾跑', 'start': '开始',
    'static': '静态', 'stay': '保持', 'stem': '茎', 'sticky': '粘性',
    'stop': '停止', 'strict': '严格', 'physics': '物理', 'strikethrough': '删除线',
    'supply': '补给', 'suspected': '可疑', 'swing': '挥手', 'sword': '剑',
    'tab': 'Tab', 'take': '取出', 'output': '产物', 'target': '目标',
    'targets': '目标', 'text': '文本', 'theme': '主题', 'through': '穿',
    'thunder': '雷', 'timestamps': '时间戳', 'title': '标题', 'tolerance': '容差',
    'totem': '图腾', 'tracer': '追踪线', 'tracers': '追踪线', 'track': '追踪',
    'transactions': '事务', 'trim': '纹饰', 'materials': '材料', 'templates': '模板',
    'underline': '下划线', 'unopened': '未打开', 'unstackable': '不可堆叠',
    'update': '更新', 'upgrade': '升级', 'upward': '向上', 'lift': '升力',
    'use': '使用', 'enchantments': '附魔', 'vclip': 'VClip', 'validate': '验证',
    'vanilla': '原版', 'vertical': '垂直', 'correction': '修正', 'villager': '村民',
    'villagers': '村民', 'visibility': '可见性', 'visual': '视距', 'vitals': '生命体征',
    'warnings': '警告', 'volume': '音量', 'wait': '等待', 'wake-up': '唤醒',
    'wall': '墙壁', 'warning': '警告', 'weapon': '武器', 'weather': '天气',
    'temperature': '温度', 'whisper': '私聊', 'window': '窗口', 'wolves': '狼',
    'word': '自动', 'wrap': '换行', 'write': '写入', 'yaw': '偏航',
    'deceive': '欺骗', 'zoom': '缩放', 'attack': '攻击', 'mine': '挖掘',
    'acceleration': '加速度', 'activation': '激活', 'aimassist': '瞄准辅助',
    'always': '总是', 'ancient': '远古', 'area': '区域', 'armor': '护甲',
    'autoreconnect': '自动重连', 'autocenter': '自动居中', 'automation': '自动化',
    'bpt': 'BPT', 'barrel': '木桶', 'bedrock': '基岩', 'biome': '生物群系',
    'bite': '咬合', 'blocking': '格挡', 'bob': '晃动', 'boss': 'Boss',
    'catch': '捕捉', 'chance': '概率', 'change': '更改', 'chest': '箱子',
    'clickaura': '点击光环', 'clickgui': 'ClickGUI', 'cobweb': '蜘蛛网',
    'cocoa': '可可', 'collect': '收集', 'corner': '角落', 'creeper': '苦力怕',
    'creepers': '苦力怕', 'critical': '暴击', 'criticals': '暴击', 'crops': '作物',
    'cull': '剔除', 'data': '数据', 'day': '白天', 'detect': '检测',
    'dispenser': '发射器', 'draw': '绘制', 'dropper': '投掷器', 'duration': '持续时间',
    'esp': '透视', 'eat': '进食', 'edge': '边缘', 'ender': '末影',
    'entries': '条目', 'event': '事件', 'expand': '扩大', 'explosions': '爆炸',
    'fade': '淡出', 'fail': '失败', 'fastplace': '快速放置', 'feet': '脚下',
    'flat': '平坦', 'forward': '前进', 'furnace': '熔炉', 'gui': '界面',
    'guis': '界面', 'glide': '滑翔', 'guide': '辅助线', 'hspeed': '水平速度',
    'haste': '急迫', 'hidden': '隐藏', 'hold': '按住', 'honey': '蜂蜜',
    'hopper': '漏斗', 'hungry': '饥饿', 'hurt': '受伤', 'injured': '受伤',
    'injury': '伤害', 'intensity': '强度', 'italic': '斜体', 'living': '生物',
    'method': '方式', 'miss': '未命中', 'mixed': '混合', 'mob': '生物',
    'moon': '月亮', 'motion': '运动', 'movement': '移动', 'multitill': '多重耕地',
    'natural': '自然', 'near': '附近', 'new': '新', 'night': '夜晚',
    'nocheat': 'NoCheat', 'non': '非', 'not': '不', 'obsidian': '黑曜石',
    'old': '旧', 'omnidirectional': '全向', 'on': '开启', 'opponent': '对手',
    'passengers': '乘客', 'patience': '耐心', 'phase': '相位', 'pitch': '俯仰',
    'player': '玩家', 'powder': '细雪', 'power': '力度', 'prefer': '优先',
    'preferred': '首选', 'prevent': '防止', 'prioritize': '优先', 'pullback': '拉回',
    'pulse': '脉冲', 'query': '查询', 'rtp': 'RTP', 'raycast': '射线检测',
    'raytrace': '射线追踪', 're': '重新', 'release': '释放', 'remove': '移除',
    'replant': '补种', 'respawn': '重生', 'retain': '保留', 'reverse': '反向',
    'rounded': '圆角', 'safe': '安全', 'saplings': '树苗', 'see': '查看',
    'simulate': '模拟', 'skip': '跳过', 'slime': '史莱姆', 'slow': '缓慢',
    'snap': '吸附', 'soft': '柔和', 'soul': '灵魂', 'stack': '堆叠',
    'status': '状态', 'steal': '偷取', 'stems': '茎', 'strafe': '侧移',
    'super': '超级', 'support': '支撑', 'sweet': '甜浆果', 'tag': '标签',
    'template': '模板', 'the': '该', 'throwables': '投掷物', 'tools': '工具',
    'tooltip': '提示', 'totems': '图腾', 'tower': '搭塔', 'trap': '陷阱',
    'trident': '三叉戟', 'tunnel': '隧道', 'turn': '转向', 'twerk': '摇摆',
    'uuid': 'UUID', 'unblock': '解除阻挡', 'unlimited': '无限', 'vspeed': '垂直速度',
    'valid': '有效', 'view': '视角', 'visible': '可见', 'wanted': '通缉',
    'what': '什么', "won't": '不会', 'world': '世界', 'wrong': '错误',
    'x': 'X', 'xp': '经验', 'y': 'Y', 'accurate': '精确',
    'absorption': '伤害吸收', 'accepted': '已接受', 'age': '存活时间',
    'anchors': '重生锚', 'animals': '动物', 'as': '作为', 'at': '在',
    'aura': '光环', 'axis': '轴', 'baby': '幼年', 'bar': '条',
    'before': '之前', 'berry': '浆果', 'blindness': '失明', 'boat': '船',
    'books': '书', 'brightness': '亮度', 'build': '建造', 'bush': '灌木',
    'button': '按钮', 'buttons': '按钮', 'by': '由', 'cart': '矿车',
    'carts': '矿车', 'chest': '箱子', 'chorus': '紫颂', 'chunks': '区块',
    'closing': '关闭', 'collision': '碰撞', 'crystals': '水晶', 'danger': '危险',
    'difference': '差值', 'dispensers': '发射器', 'droppers': '投掷器',
    'drops': '掉落物', 'duplicate': '重复', 'durability': '耐久', 'each': '每个',
    'eating': '进食', 'edges': '边缘', 'empty': '空', 'enemy': '敌人',
    'equipment': '装备', 'errors': '错误', 'exact': '精确', 'exiting': '退出',
    'exposed': '暴露', 'fast': '快速', 'feed': '喂食', 'fluids': '流体',
    'fly': '飞行', 'flying': '飞行', 'frame': '展示框', 'frames': '展示框',
    'from': '从', 'fruit': '水果', 'game': '游戏', 'gateway': '传送门',
    'gateways': '传送门', 'glitch': '故障', 'hands': '手', 'held': '按住',
    'hitbox': '碰撞箱', 'hits': '命中', 'holding': '按住', 'hoppers': '漏斗',
    'horse': '马', 'hunger': '饥饿', 'if': '如果', 'ignored': '已忽略',
    'illegal': '非法', 'in': '在', 'increase': '增加', 'indicators': '指示',
    'input': '输入', 'invisible': '隐身', 'item': '物品', 'jumping': '跳跃',
    'language': '语言', 'like': '类似', 'mob': '生物', 'mouse': '鼠标',
    'moving': '移动', 'names': '名称', 'of': '的', 'off': '关',
    'offhand': '副手', 'order': '顺序', 'particles': '粒子', 'point': '点',
    'poison': '中毒', 'portal': '传送门', 'portals': '传送门',
    'predicted': '预测', 'press': '按下', 'primary': '主要', 'profile': '配置',
    'randomization': '随机化', 'rate': '速率', 'reasons': '原因',
    'reblock': '重新阻挡', 'received': '收到', 'reduction': '减少',
    'riding': '骑乘', 'rule': '规则', 'sampled': '采样', 'sand': '沙子',
    'saved': '已保存', 'scaffold': '脚手架', 'screen': '界面', 'screens': '界面',
    'sent': '发送', 'settings': '设置', 'should': '应该', 'shows': '显示',
    'shulkers': '潜影盒', 'sleeping': '睡眠', 'slowdown': '减速',
    'slowness': '缓慢', 'smoothing': '平滑', 'sneaking': '潜行', 'snow': '雪',
    'spots': '位置', 'sprinting': '疾跑', 'still': '静止', 'stroke': '描边',
    'style': '样式', 'suicide': '自杀', 'surround': '包围', 'swords': '剑',
    'tags': '标签', 'technique': '技巧', 'tick': '刻', 'timing': '时机',
    'to': '到', 'torches': '火把', 'tracker': '追踪', 'trade': '交易',
    'type': '类型', 'types': '类型', 'unload': '卸载', 'untamed': '未驯服',
    'used': '已使用', 'vein': '矿脉', 'veins': '矿脉', 'walking': '行走',
    'water': '水', 'when': '当', 'with': '配合', 'yeet': '投掷',
    '(experimental)': '(实验性)', '(sec)': '(秒)', '(tps)': '(TPS)',
    "target's": '目标的', 'animals.': '动物。', 'at.': '在。',
    'hitbox.': '碰撞箱。', 'item.': '物品。', 'mob.': '生物。',
    'player.': '玩家。', 'players.': '玩家。', 'tracker.': '追踪。',
    'veins.': '矿脉。', r'animals.\n': '动物。', r'item.\n': '物品。',
    r'mob.\n': '生物。', r'player.\n': '玩家。',
    r'\u00a7lAccurate\u00a7r': '精确',
}

SUFFIX = {
    'delay': '延迟', 'range': '范围', 'speed': '速度', 'mode': '模式',
    'color': '颜色', 'opacity': '不透明度', 'height': '高度', 'width': '宽度',
    'offset': '偏移', 'interval': '间隔', 'threshold': '阈值', 'limit': '限制',
    'amount': '数量', 'count': '数量', 'size': '大小', 'scale': '缩放',
    'distance': '距离', 'radius': '半径', 'time': '时间', 'level': '等级',
    'chance': '概率', 'ticks': '刻', 'steps': '步数', 'blocks': '方块',
    'items': '物品', 'players': '玩家', 'mobs': '生物', 'targets': '目标',
    'trades': '交易', 'veins': '矿脉', 'pages': '页', 'seconds': '秒',
    'strength': '力度', 'health': '生命值', 'damage': '伤害',
    'priority': '优先级', 'slot': '槽位', 'hand': '手', 'packets': '数据包',
}

PREFIX = {
    'auto': '自动', 'anti': '防', 'show': '显示', 'hide': '隐藏',
    'ignore': '忽略', 'target': '目标', 'attack': '攻击', 'only': '仅',
    'max': '最大', 'min': '最小', 'maximum': '最大', 'minimum': '最小',
    'allow': '允许', 'require': '需要', 'follow': '跟随', 'place': '放置',
    'break': '破坏', 'use': '使用', 'render': '渲染', 'enable': '启用',
    'disable': '禁用', 'keep': '保留', 'pause': '暂停', 'predict': '预测',
    'through': '穿',
}


def snake(n):
    n = n.lower().replace("'", '')
    n = re.sub(r'[^a-z0-9]+', '_', n)
    return n.strip('_')


def translate(name):
    if name in EXACT:
        return EXACT[name], []
    low = name.lower()
    # suffix rules
    for suf, zh in SUFFIX.items():
        if low.endswith(' ' + suf):
            base = name[:-len(suf) - 1]
            base_zh, unk = translate(base)
            return base_zh + zh, unk
    # prefix rules
    for pre, zh in PREFIX.items():
        p = pre + ' '
        if low.startswith(p):
            rest = name[len(p):]
            rest_zh, unk = translate(rest)
            return zh + rest_zh, unk
    # word-by-word
    parts = re.split(r'([ +/,-])', name)
    out = []
    unknown = []
    for part in parts:
        if not part or part in ' +/,-':
            out.append(part)
            continue
        lowp = part.lower()
        if lowp in WORDS:
            out.append(WORDS[lowp])
        else:
            out.append(part)
            unknown.append(part)
    s = ''.join(out)
    # drop spaces between Chinese characters
    s = re.sub(r'(?<=[\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])', '', s)
    return s, unknown


def main():
    # collect names from representative project files
    names = set()
    for p in project_dirs():
        hack_dir = p / 'src/main/java/net/wurstclient/hacks'
        for f in hack_dir.rglob('*Hack.java'):
            t = f.read_text(encoding='utf-8', errors='ignore')
            for m in re.finditer(r'new\s+\w*Setting(?:<[^>]*>)?\(\s*"([^"]+)"', t):
                names.add(m.group(1))
    mapping = {}
    unknown = set()
    for n in names:
        r = translate(n)
        if isinstance(r, tuple):
            mapping[n] = r[0]
            unknown.update(r[1])
        else:
            mapping[n] = r
    print('names', len(names), 'unknown tokens', len(unknown))
    print('unknown:', ', '.join(sorted(unknown)[:60]))

    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        zh_file = p / 'src/main/resources/assets/wurst/translations/zh_cn.json'
        if not zh_file.exists():
            continue
        text = zh_file.read_text(encoding='utf-8')
        keys = {'setting.name.' + snake(n): mapping[n] for n in names}
        # update already-present keys in place
        for k, v in keys.items():
            if not v:
                continue
            pat = re.compile(r'("' + re.escape(k) + r'"\s*:\s*")[^"]*(")')
            text = pat.sub(
                lambda m, v=v: m.group(1)
                + v.replace('\\', '\\\\').replace('"', '\\"') + m.group(2),
                text)
        existing = set(re.findall(r'^\s*"(setting\.name\.[^"]+)"', text, re.M))
        missing = [(k, v) for k, v in keys.items()
                   if k not in existing and v and v != n]
        if not missing:
            zh_file.write_text(text, encoding='utf-8', newline='\n')
            stats['exists'] = stats.get('exists', 0) + 1
            continue
        lines = text.splitlines(keepends=True)
        anchor = None
        for i, line in enumerate(lines):
            if '"setting.name.' in line:
                anchor = i
                break
        if anchor is None:
            for i, line in enumerate(lines):
                if '"description.wurst.hack.' in line:
                    anchor = i
                    break
        if anchor is None:
            continue
        if not lines[anchor].rstrip().endswith(','):
            lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
        ins = ['"%s": "%s",\n' % (k, v) for k, v in missing]
        lines[anchor + 1:anchor + 1] = ins
        new = ''.join(lines)
        json.loads(new)
        zh_file.write_text(new, encoding='utf-8', newline='\n')
        stats[len(missing)] = stats.get(len(missing), 0) + 1
    print('stats', stats)


if __name__ == '__main__':
    main()
