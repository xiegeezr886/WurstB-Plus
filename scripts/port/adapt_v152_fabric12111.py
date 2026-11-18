"""1.21.11 Identifier, sampler and reorganized entity API migration."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[2]
JAVA='src/main/java/net/wurstclient/'


def conflicts(rel,text,theirs):
    short=rel.removeprefix(JAVA)
    original=ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/1.21.11'/rel
    if short=='hack/HackList.java':
        source=theirs.decode('utf-8-sig')
        names=set(re.findall(r'public final \w+ (\w+)\s*=',source))
        fields=re.findall(r'\s*public final \w+ (\w+)\s*=\s*new \w+\(\);',original.read_text('utf-8-sig'))
        extra=[]
        for name in fields:
            if name not in names:
                field=re.search(r'\s*public final \w+ '+name+r'\s*=\s*new \w+\(\);',original.read_text('utf-8-sig'))[0]
                extra.append(field)
        return source.replace('\tprivate final TreeMap<String, Hack> hax =',''.join(extra)+'\n\tprivate final TreeMap<String, Hack> hax =').encode()
    if short=='mixin/LocalPlayerMixin.java':
        source=re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S)
        old=original.read_text('utf-8-sig')
        hook=old[old.index('\t/**\n\t * This is the part that makes Liquids work.'):].rsplit('}',1)[0]
        return (source[:source.rfind('}')]+hook+'}\n').encode()
    if rel=='src/main/resources/wurstpenguin.mixins.json':
        data=json.loads(theirs)
        old=json.loads(original.read_text('utf-8-sig'))
        # New renderer hooks survive; remove aliases handled by the donor.
        renames={'xray.BlockModelRendererMixin':'xray.ModelBlockRendererMixin',
            'xray.FluidRendererMixin':'xray.LiquidBlockRendererMixin'}
        data['client']=list(dict.fromkeys(renames.get(n,n) for n in data['client']+
            [n for n in old['client'] if n not in {'KeyboardHandlerMixin',
                'OpenOpalPostMoveMixin','OpenOpalSkipTickMixin'}]))
        return (json.dumps(data,indent=2)+'\n').encode()
    if short in {'altmanager/screens/AltEditorScreen.java','altmanager/screens/AltManagerScreen.java',
        'clickgui2/ClickGuiScreen.java','clickgui2/NavigatorScreen.java',
        'gui/title/WurstTitleButton.java','gui/title/WurstTitleMenu.java',
        'hacks/AutoArmorHack.java','hacks/AutoArmorPlusHack.java',
        'hacks/AutoRefreshTradeHack.java','hacks/FollowHack.java',
        'hud2/compat/HudTextures.java','hud2/mods/TargetInfoMod.java',
        'hud2/nanovg/NanoVGContext.java'}:
        return theirs
    if short in {'options/WurstOptionsScreen.java','serverfinder/CleanUpScreen.java',
        'util/TestControlServer.java','WurstClient.java'}:
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S).encode()
    return None


def adapt(rel,text):
    text=text.replace('ResourceLocation','Identifier')
    text=text.replace('net.minecraft.Util','net.minecraft.util.Util')
    text=text.replace('import net.minecraft.client.renderer.RenderType;\n','') if rel.endswith('WurstTitleButton.java') else text
    text=text.replace('.location()', '.identifier()')
    for old,new in [('getPosition()', 'position()'),('getLookVector()', 'forwardVector()'),
                    ('getUpVector()', 'upVector()'),('getLeftVector()', 'leftVector()')]:
        text=text.replace('camera.'+old,'camera.'+new).replace('getMainCamera().'+old,'getMainCamera().'+new)
    text=text.replace('Vector3f look = camera.forwardVector()', 'var look = camera.forwardVector()')
    text=text.replace('Vector3f upVector = camera.upVector()', 'var upVector = camera.upVector()')
    text=text.replace('Vector3f leftVector = camera.leftVector()', 'var leftVector = camera.leftVector()')
    text=text.replace('MC.level.dimensionType().respawnAnchorWorks()',
        'MC.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, MC.player.blockPosition())')
    text=text.replace('net.minecraft.world.level.GameRules(', 'net.minecraft.world.level.gamerules.GameRules(')
    text=text.replace('protected void renderWidget(net.minecraft.client.gui.GuiGraphics ',
        'protected void renderContents(net.minecraft.client.gui.GuiGraphics ')
    text=re.sub(r'(\w+)\.setFilterMode\(com.mojang.blaze3d.textures.FilterMode.LINEAR\);',
        r'net.wurstclient.util.render.V152Gpu.linear(\1);',text)
    text=re.sub(r'(minecraft.getTextureManager\(\).getTexture\([^)]*\))\.setFilter\(true, false\);',
        r'net.wurstclient.util.render.V152Gpu.linear(\1);',text)
    if rel.endswith('MoonEntityGlow.java'):
        text=re.sub(r'        MultiBufferSource maskOnly =\s*type -> type.outline\(\).map\(outlines::getBuffer\).orElse\(DISCARD\);\n','',text)
        text=re.sub(r'  private static final VertexConsumer DISCARD =.*?\n      };', '',text,flags=re.S)
    if rel.endswith('V152Gpu.java'):
        text=text.replace('case LINE_STRIP, DEBUG_LINE_STRIP', 'case DEBUG_LINE_STRIP')
        if 'void linear(' not in text:
            text=text.replace('  private V152Gpu() {}', '''  private V152Gpu() {}
  public static void linear(net.minecraft.client.renderer.texture.AbstractTexture texture) {
    ((net.wurstclient.mixin.V152TextureSamplerAccessor)(Object)texture).wurst$setSampler(
        RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
  }
  public static void linear(RenderTarget target) {
    int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    try {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture(target));
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous); }
  }''')
    if rel.endswith('V152GuiBridge.java'):
        text=text.replace('new net.minecraft.client.gui.GuiGraphics(mc, renderer.renderState)',
            'new net.minecraft.client.gui.GuiGraphics(mc, renderer.renderState, 0, 0)')
    # Mojang split these entity packages; use their public 1.21.11 names.
    for before,after in {
        'world.entity.npc.Villager':'world.entity.npc.villager.Villager',
        'world.entity.npc.VillagerProfession':'world.entity.npc.villager.VillagerProfession',
        'world.entity.vehicle.AbstractMinecart':'world.entity.vehicle.minecart.AbstractMinecart',
        'world.entity.monster.Zombie':'world.entity.monster.zombie.Zombie',
        'world.entity.monster.AbstractSkeleton':'world.entity.monster.skeleton.AbstractSkeleton',
        'world.entity.npc.AbstractVillager':'world.entity.npc.villager.AbstractVillager',
    }.items(): text=text.replace(before,after)
    if rel.endswith('hud2/nanovg/NanoVGContext.java'):
        # Minecraft 1.21.11 binds independent samplers. NanoVG sets texture
        # parameters directly, so an inherited mipmapped sampler makes its
        # single-level font atlas incomplete. GlStateGuard restores sampler 0.
        if '// NanoVG owns sampler 0 while submitting.' not in text:
            text=text.replace('private static void prepareTextureUpload()\n\t{',
                'private static void prepareTextureUpload()\n\t{\n'
                '\t\torg.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);\n'
                '\t\torg.lwjgl.opengl.GL33.glBindSampler(0, 0);')
            text=text.replace('NanoVG.nvgEndFrame(vg);',
                '// NanoVG owns sampler 0 while submitting.\n'
                '\t\torg.lwjgl.opengl.GL33.glBindSampler(0, 0);\n'
                '\t\tNanoVG.nvgEndFrame(vg);')
    return text


def configure(project:Path,donor:Path):
    from adapt_v152_fabric1218 import configure as common
    common(project,donor)
    seed=ROOT/'.dsh-tmp/v152-upstream-seed/seedcrackerX-2.15.6.jar'
    digest='8695edc230f832339dc53c6fe3543e6d507ac054ef775082083b819fa8b4f227'
    assert hashlib.sha256(seed.read_bytes()).hexdigest()==digest
    with zipfile.ZipFile(seed) as archive:
        meta=json.loads(archive.read('fabric.mod.json'))
        assert meta['depends']['minecraft']=='>=1.21.11 <1.22'
        names={Path(row['file']).name for row in meta['jars']}
        for old in (project/'libs').glob('seedcrackerX-*.jar'):
            with zipfile.ZipFile(old) as previous:
                for row in json.loads(previous.read('fabric.mod.json'))['jars']:
                    name=Path(row['file']).name
                    if name not in names: (project/'libs/seedcrackerx-deps'/name).unlink(missing_ok=True)
            old.unlink()
        shutil.copy2(seed,project/'libs'/seed.name)
        for row in meta['jars']:
            (project/'libs/seedcrackerx-deps'/Path(row['file']).name).write_bytes(archive.read(row['file']))
    path=project/'build.gradle'
    build=re.sub(r'seedcrackerX:[^"\s]+','seedcrackerX:2.15.6',path.read_text('utf-8'))
    build+='\nsourceSets.main.java { exclude "net/wurstclient/clickgui2/component.disabled/**" }\n'
    path.write_text(build,encoding='utf-8')
    path=project/'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt'
    path.write_text('SeedCrackerX 2.15.6, MIT, unmodified upstream release for Minecraft 1.21.11.\n'
        'https://github.com/19MisterX98/SeedcrackerX/releases/tag/2.15.6\nSHA-256: '+digest+'\n',encoding='utf-8')
    for path in project.joinpath('src').rglob('*.java'):
        path.write_text(adapt(path.as_posix(),path.read_text('utf-8-sig')),encoding='utf-8')
    # Descriptors in AW files must match the Identifier rename as well.
    path=project/'src/main/resources/wurstpenguin.accesswidener'
    path.write_text(path.read_text('utf-8').replace('ResourceLocation','Identifier'),encoding='utf-8')
    for old,new in [('BlockModelRendererMixin','ModelBlockRendererMixin'),
                    ('FluidRendererMixin','LiquidBlockRendererMixin')]:
        path=project/(JAVA+'mixin/xray/'+old+'.java')
        (path.parent/(new+'.java')).write_text(path.read_text().replace(old,new),encoding='utf-8')
        path.unlink()
    (project/(JAVA+'mixin/KeyboardHandlerMixin.java')).unlink(missing_ok=True)
    # Current RenderType exposes pipeline(); this old accessor has no users.
    (project/(JAVA+'mixin/RenderTypeAccessor.java')).unlink(missing_ok=True)
    path=project/'src/main/resources/wurstpenguin.mixins.json'
    config=json.loads(path.read_text('utf-8'))
    config['client']=[n for n in config['client'] if n!='RenderTypeAccessor']
    renames={'xray.BlockModelRendererMixin':'xray.ModelBlockRendererMixin',
        'xray.FluidRendererMixin':'xray.LiquidBlockRendererMixin'}
    config['client']=[renames.get(n,n) for n in config['client']]
    config['client']=list(dict.fromkeys(config['client']+['V152TextureSamplerAccessor']))
    path.write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8')
    path=project/(JAVA+'mixin/V152TextureSamplerAccessor.java')
    path.write_text('''package net.wurstclient.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.blaze3d.textures.GpuSampler;
@Mixin(AbstractTexture.class)
public interface V152TextureSamplerAccessor {
  @Accessor("sampler") void wurst$setSampler(GpuSampler sampler);
}
''',encoding='utf-8')
