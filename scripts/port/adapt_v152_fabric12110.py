"""1.21.10 patch port from the event-input 1.21.9 implementation."""
from pathlib import Path
import re

JAVA='src/main/java/net/wurstclient/'


def conflicts(rel, text, theirs):
    if rel==JAVA+'hack/HackList.java':
        # These modules remain registered in the donor; retaining the old
        # conflict hunk as well would instantiate them a second time.
        return theirs
    if rel.removeprefix(JAVA) in {
        'altmanager/screens/AltEditorScreen.java','altmanager/screens/AltManagerScreen.java',
        'clickgui2/ClickGuiScreen.java','clickgui2/NavigatorScreen.java',
        'gui/title/WurstTitleButton.java'}:
        return theirs
    if rel.removeprefix(JAVA) in {'options/WurstOptionsScreen.java',
        'serverfinder/CleanUpScreen.java','WurstClient.java'}:
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S).encode()
    return None


def configure(project: Path, donor: Path):
    from adapt_v152_fabric1218 import configure as common
    common(project,donor)
    path=project/'build.gradle'
    path.write_text(path.read_text('utf-8').replace('seedcrackerX:2.15.4','seedcrackerX:2.15.5'),encoding='utf-8')
    # The new LevelLoadingScreen handles both terrain and generation stages.
    (project/(JAVA+'mixin/NeoRavenReceivingLevelMixin.java')).unlink(missing_ok=True)
