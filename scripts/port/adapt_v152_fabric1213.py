"""1.21.2/1.21.3 siblings: preserve target-owned item rendering and input."""
import re

JAVA = 'src/main/java/net/wurstclient/'


def conflicts(target, rel, text, theirs):
    if rel == JAVA + 'WurstClient.java':
        text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?', lambda m:m[2], text, flags=re.S)
        return text.replace('MC_VERSION = "1.21.4"', 'MC_VERSION = "' + target.rsplit('/',1)[1] + '"').encode()
    if rel == JAVA + 'hud2/nanovg/NanoVGUI.java':
        # The donor now captures the main target before entering its GL guard.
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?', lambda m:m[2], text, flags=re.S).encode()
    return None


def adapt(rel, text):
    if rel.endswith('PacketLoggerHack.java'):
        text = text.replace('filter("pick-block", ServerboundPickItemFromBlockPacket.class);\n    filter("pick-entity", ServerboundPickItemFromEntityPacket.class);',
                            'filter("pick-inventory", ServerboundPickItemPacket.class);')
        text = text.replace('if (packet instanceof ServerboundPickItemFromBlockPacket p) return "position=" + p.pos() + ", includeData=" + p.includeData();\n    if (packet instanceof ServerboundPickItemFromEntityPacket p) return "entity=" + p.id() + ", includeData=" + p.includeData();',
                            'if (packet instanceof ServerboundPickItemPacket p) return "slot=" + p.getSlot();')
        text = text.replace('"position=" + p.position()', '"position=" + p.getX() + "," + p.getY() + "," + p.getZ()')
    if rel.endswith('ArmorStatusMod.java'):
        text = re.sub(r'var state = new net.minecraft.client.renderer.item.ItemStackRenderState\(\);\s*MC.getItemModelResolver\(\).updateForLiving\(state, stack, net.minecraft.world.item.ItemDisplayContext.GUI, false, MC.player\);\s*var sprite = state.pickParticleIcon\(net.minecraft.util.RandomSource.create\(0\)\);',
                      'var sprite = MC.getItemRenderer().getModel(stack, MC.level, MC.player, 0).getParticleIcon();', text)
    return text


def configure(project):
    from adapt_v152_fabric1214 import configure as common
    common(project)
