"""26.1.1/26.1.2 share the 26.1 extraction and packed-fluid APIs."""
from pathlib import Path

JAVA='src/main/java/net/wurstclient/'


def conflicts(rel,text,theirs):
    if rel.removeprefix(JAVA) in {'mixin/IngameHudMixin.java',
        'mixin/KeyboardMixin.java','WurstClient.java','clickgui2/ClickGui.java',
        'clickgui2/Window.java','gui/title/WurstTitleMenu.java'}:
        return theirs
    return None


def configure(project:Path,donor:Path):
    from adapt_v152_fabric261 import configure as common
    common(project,donor)
