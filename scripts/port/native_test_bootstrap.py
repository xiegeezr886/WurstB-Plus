"""Supply loader discovery context for plain JUnit, never the game runtime."""
from pathlib import Path


def configure(project: Path, neo: bool):
    package = 'net.neoforged' if neo else 'net.minecraftforge'
    setup = '''
        if (LoadingModList.get() == null)
          LoadingModList.of(java.util.List.of(), java.util.List.of(),
              java.util.List.of(), java.util.List.of(), java.util.Map.of());
''' if neo else '''
        if (net.minecraftforge.fml.loading.FMLLoader.getLoadingModList() == null) {
          var list = LoadingModList.of(java.util.List.of(), java.util.List.of(), null);
          list.setBrokenFiles(java.util.List.of());
          var field = net.minecraftforge.fml.loading.FMLLoader.class
              .getDeclaredField("loadingModList");
          field.setAccessible(true);
          field.set(null, list);
        }
        var layers = net.minecraftforge.fml.loading.FMLLoader.class
            .getDeclaredField("moduleLayerManager");
        layers.setAccessible(true);
        if (layers.get(null) == null)
          layers.set(null, (cpw.mods.modlauncher.api.IModuleLayerManager)
              layer -> java.util.Optional.of(ModuleLayer.boot()));
'''
    if not neo and project.name.startswith('26.'):
        # Forge 62 replaced the mutable LoadingModList singleton with a
        # lazily initialized record backed by ModSorter.State.
        setup = '''
        var stateClass = Class.forName("net.minecraftforge.fml.loading.ModSorter$State");
        var constructor = stateClass.getDeclaredConstructor(java.util.List.class, java.util.List.class);
        constructor.setAccessible(true);
        var state = constructor.newInstance(java.util.List.of(), java.util.List.of());
        var listClass = Class.forName("net.minecraftforge.fml.loading.LoadingModListImpl");
        var temporary = listClass.getDeclaredField("temp");
        temporary.setAccessible(true);
        temporary.set(null, state);
        net.minecraftforge.fml.loading.LoadingModList.getMods();
        temporary.set(null, null);
        var layers = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("moduleLayerManager");
        layers.setAccessible(true);
        if (layers.get(null) == null)
          layers.set(null, (cpw.mods.modlauncher.api.IModuleLayerManager)
              layer -> java.util.Optional.of(ModuleLayer.boot()));
'''
    source = '''package net.wurstclient.test;

import net.minecraft.server.Bootstrap;
import PACKAGE.fml.loading.LoadingModList;

/** Empty discovery context for settings/behavior tests without ModLauncher.
 * Does not validate loader events or mixins; those require a game launch. */
public final class NativeTestBootstrap {
  private NativeTestBootstrap() {}
  public static synchronized void bootstrap() {
    try {
SETUP
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Native unit-test bootstrap failed", e);
    }
    Bootstrap.bootStrap();
  }
}
'''.replace('PACKAGE', package).replace('SETUP', setup)
    if neo and project.name == '1.20.5':
        source = source.replace('LoadingModList.of(java.util.List.of(), java.util.List.of(),\n'
            '              java.util.List.of(), java.util.List.of(), java.util.Map.of());',
            'LoadingModList.of(java.util.List.of(), java.util.List.of(), null);')
    modern_neo=neo and (project.name in ('1.21.9', '1.21.10', '1.21.11') or project.name.startswith('26.'))
    if modern_neo:
        source = source.replace('java.util.List.of(), java.util.List.of(), java.util.Map.of());',
            'java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());')
        # FML 10 moved the discovery singleton into a loader instance. Plain
        # JUnit needs its real environment before SharedConstants initializes.
        source = source.replace('        if (LoadingModList.get() == null)', '''        var loaderClass = net.neoforged.fml.loading.FMLLoader.class;
        if (net.neoforged.fml.loading.FMLLoader.getCurrentOrNull() == null) {
          var constructor = loaderClass.getDeclaredConstructor(ClassLoader.class,
              String[].class, net.neoforged.api.distmarker.Dist.class,
              boolean.class, java.nio.file.Path.class);
          constructor.setAccessible(true);
          constructor.newInstance(Thread.currentThread().getContextClassLoader(),
              new String[]{"--fml.neoForgeVersion", "test", "--fml.mcVersion", "MC_VERSION",
                  "--fml.neoFormVersion", "test"},
              net.neoforged.api.distmarker.Dist.CLIENT, false, java.nio.file.Path.of("."));
        }
        if (LoadingModList.get() == null)'''.replace('MC_VERSION', project.name))
        source = source.replace('    Bootstrap.bootStrap();',
            '    net.minecraft.SharedConstants.tryDetectVersion();\n    Bootstrap.bootStrap();')
        source = source.replace('        if (LoadingModList.get() == null)\n'
            '          LoadingModList.of(java.util.List.of(), java.util.List.of(),\n'
            '              java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());',
            '''        var loadingList = loaderClass.getDeclaredField("loadingModList");
        loadingList.setAccessible(true);
        var loader = net.neoforged.fml.loading.FMLLoader.getCurrent();
        if (loadingList.get(loader) == null)
          loadingList.set(loader, LoadingModList.of(java.util.List.of(), java.util.List.of(),
              java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of()));''')
    if neo and not modern_neo:
        source = source.replace('    try {\n', '').replace(
            '    } catch (ReflectiveOperationException e) {\n'
            '      throw new IllegalStateException("Native unit-test bootstrap failed", e);\n'
            '    }\n', '')
    dest = project / 'src/test/java/net/wurstclient/test/NativeTestBootstrap.java'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(source, encoding='utf-8')
    for path in (project / 'src/test/java').rglob('*.java'):
        if path == dest:
            continue
        text = path.read_text('utf-8-sig')
        if modern_neo:
            # Discovery must precede SharedConstants, including in callers
            # that were already adapted by an earlier sync.
            text = text.replace('SharedConstants.tryDetectVersion();', '')
        if 'Bootstrap.bootStrap();' in text:
            text = text.replace('Bootstrap.bootStrap();',
                'net.wurstclient.test.NativeTestBootstrap.bootstrap();')
        path.write_text(text, encoding='utf-8')
