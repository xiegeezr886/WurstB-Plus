"""Restore SDL mouse/key semantics in the three Minecraft 26.3 ports.

Restores reviewed input comparisons, QA event construction and test-server
shutdown. Game events and shared controls retain native button numbering.
"""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'
CONSTANTS = 'com.mojang.blaze3d.platform.InputConstants.'
BUTTONS = {0: 'MOUSE_BUTTON_LEFT', 1: 'MOUSE_BUTTON_RIGHT', 2: 'MOUSE_BUTTON_MIDDLE'}


def adapt(rel, text):
    if not rel.endswith('.java') or '.disabled/' in rel:
        return text
    if rel.startswith(JAVA):
        text = re.sub(r'\b(button|mouseButton)\s*([!=]=)\s*([012])\b',
                      lambda m: m[1] + ' ' + m[2] + ' ' + CONSTANTS + BUTTONS[int(m[3])], text)
    if rel.endswith('/music/MusicScreen.java'):
        keys = {256: 'KEY_ESCAPE', 257: 'KEY_RETURN', 335: 'KEY_NUMPADENTER', 32: 'KEY_SPACE'}
        text = re.sub(r'\bkey == (256|257|335|32)\b', lambda m: 'key == ' + CONSTANTS + keys[int(m[1])], text)
    if rel.endswith('/clickgui2/MoonRowInteractionTest.java'):
        text = re.sub(r'(MoonRowInteraction\.action\([^,]+,\s*[^,]+,\s*)([012])(,)',
                      lambda m: m[1] + CONSTANTS + BUTTONS[int(m[2])] + m[3], text)
    if rel.endswith('/util/TestControlServer.java'):
        if 'public static synchronized void stop()' not in text:
            text = text.replace('private static HttpServer server;',
                                'private static HttpServer server;\n\tprivate static Thread keepAliveThread;')
            text = text.replace('\tprivate static void startPauseKeepAlive()', '''\tpublic static synchronized void stop()
\t{
\t\tif(keepAliveThread != null) {
\t\t\tkeepAliveThread.interrupt();
\t\t\tkeepAliveThread = null;
\t\t}
\t\tHttpServer current = server;
\t\tserver = null;
\t\tif(current == null) return;
\t\tcurrent.stop(0);
\t\tif(current.getExecutor() instanceof java.util.concurrent.ExecutorService executor)
\t\t\texecutor.shutdownNow();
\t}

\tprivate static void startPauseKeepAlive()''')
            text = text.replace('\t\tthread.setDaemon(true);\n\t\tthread.start();',
                                '\t\tthread.setDaemon(true);\n\t\tkeepAliveThread = thread;\n\t\tthread.start();')
        # QA uses native events just like Minecraft's input dispatcher.
        text = text.replace('new net.minecraft.client.input.MouseButtonInfo(0, 0)',
                            'new net.minecraft.client.input.MouseButtonInfo(' + CONSTANTS + 'MOUSE_BUTTON_LEFT, 0)')
        if 'case "click":' in text and 'query.getOrDefault("button", "left")' not in text:
            start = text.index('case "click":')
            end = text.index('case "scroll":', start)
            text = text[:start] + '''case "click":
                if(WurstClient.MC.gui.screen() != null) {
                    double x = Double.parseDouble(query.getOrDefault("x", "0"));
                    double y = Double.parseDouble(query.getOrDefault("y", "0"));
                    int button = switch(query.getOrDefault("button", "left")) {
                        case "left" -> com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
                        case "right" -> com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
                        case "middle" -> com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE;
                        default -> Integer.parseInt(query.get("button"));
                    };
                    var clickedScreen = WurstClient.MC.gui.screen();
                    var event = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0));
                    clickedScreen.mouseClicked(event, false);
                    if(WurstClient.MC.gui.screen() == clickedScreen) clickedScreen.mouseReleased(event);
                }
                break;
                ''' + text[end:]
        old = 'WurstClient.MC.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(Integer.parseInt(query.getOrDefault("key", "256")), 0, 0));'
        if old in text:
            text = text.replace(old, '''{
                        int key = switch(query.getOrDefault("key", "escape")) {
                            case "escape", "256" -> com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE;
                            case "enter", "257" -> com.mojang.blaze3d.platform.InputConstants.KEY_RETURN;
                            case "tab", "258" -> com.mojang.blaze3d.platform.InputConstants.KEY_TAB;
                            case "space", "32" -> com.mojang.blaze3d.platform.InputConstants.KEY_SPACE;
                            default -> Integer.parseInt(query.get("key"));
                        };
                        int keycode = org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(key, (short)0, false);
                        WurstClient.MC.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(key, keycode, 0));
                    }''')
    if rel.endswith('/mixin/MinecraftClientMixin.java') and 'TestControlServer.stop();' not in text:
        anchor = 'net.wurstclient.neoraven.music.MusicManager.get().shutdown();'
        if anchor not in text:
            raise ValueError('Missing client close lifecycle hook')
        text = text.replace(anchor, 'net.wurstclient.util.TestControlServer.stop();\n\t\t' + anchor)
    return text


def configure(project):
    from prepare_v152 import props
    if props(project / 'gradle.properties')['minecraft_version'] != '26.3':
        raise ValueError('SDL input adapter only applies to Minecraft 26.3')
    changed = 0
    for path in (project / 'src').rglob('*.java'):
        rel = path.relative_to(project).as_posix()
        before = path.read_text('utf-8-sig')
        after = adapt(rel, before)
        if after != before:
            path.write_text(after, encoding='utf-8', newline='\n')
            changed += 1
    return changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('projects', nargs='+', type=Path)
    args = parser.parse_args()
    for project in args.projects:
        project = project.resolve()
        if not project.is_relative_to(ROOT):
            raise ValueError('Project outside workspace')
        print(str(project.relative_to(ROOT)) + ': ' + str(configure(project)) + ' files updated')


if __name__ == '__main__':
    main()
