"""Native 1.21.9-10 port from the corresponding Fabric submit-node donor."""
from pathlib import Path
import re

JAVA='src/main/java/net/wurstclient/'


def conflicts(rel,text,theirs):
    if rel==JAVA+'hack/HackList.java':
        # All target-only modules remain in the corresponding Fabric donor.
        ours=text.split('<<<<<<<',1)[-1].split('=======',1)[0]
        known=set(re.findall(r'public final \w+ (\w+)\s*=',theirs.decode('utf-8-sig')))
        extra=set(re.findall(r'public final \w+ (\w+)\s*=',ours))-known
        if extra:raise ValueError('Native target-only registrations need review: '+str(extra))
        return theirs
    from adapt_v152_native1216 import conflicts as common
    resolved=common(rel,text,theirs)
    if resolved is not None:return resolved
    if rel.removeprefix(JAVA) in {'altmanager/screens/AltEditorScreen.java',
        'clickgui2/ClickGuiScreen.java','clickgui2/NavigatorScreen.java'}:
        return theirs
    if rel.removeprefix(JAVA) in {'options/WurstOptionsScreen.java','serverfinder/CleanUpScreen.java'}:
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S).encode()
    return None


def configure(project:Path,donor:Path):
    from adapt_v152_native1216 import configure as common
    common(project,donor)
    (project/(JAVA+'mixin/NeoRavenReceivingLevelMixin.java')).unlink(missing_ok=True)
