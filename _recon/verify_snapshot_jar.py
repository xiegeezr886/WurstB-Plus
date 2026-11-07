# -*- coding: utf-8 -*-
import glob
import json
import os
import zipfile

base = r'C:/Users/ui863/Documents/trae_projects/VAP-v1.5.0-1.20.1/build/libs'
jars = [x for x in glob.glob(base + '/*.jar')
        if 'sources' not in x and 'dev' not in x]
j = jars[0]
z = zipfile.ZipFile(j)
n = set(z.namelist())
need = ['AutoGGHack', 'ForceMainHandHack', 'JumpResetIndicatorHack',
        'PitchDisplayHack', 'SnapTapHack', 'YawDisplayHack',
        'ChunkAnimatorHack', 'ClearGlassHack', 'SoundSubtitlesHack',
        'WebBrowserHack']
print(os.path.basename(j))
for c in need:
    print(c, ('net/wurstclient/hacks/%s.class' % c) in n)
print('zh keys',
      len(json.loads(z.read('assets/wurst/translations/zh_cn.json')
                     .decode('utf-8'))))
for c in ['net/wurstclient/hacks/AutoGGHack.class',
          'net/wurstclient/mixin/PlayerMixin.class',
          'net/wurstclient/mixin/KeyBindingMixin.class']:
    data = z.read(c)
    print(c, len(data))
