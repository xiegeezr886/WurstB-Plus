"""Complete source settings and SilenceTelly placement without legacy slot swaps."""
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section


def main():
    pending={}
    for p in projects():
        def read(n):return (p/PACKAGE/n).read_text(encoding='utf-8')
        def put(n,s):pending[p/PACKAGE/n]=s
        s=read('hacks/ScaffoldWalkHack.java')
        s=replace(s,'\tpublic final CheckboxSetting silenceMark =', '''    public final CheckboxSetting silenceDebug = new CheckboxSetting("ST Debug", false)
        .visibleWhen(this::isSilenceTellySetting);
    public final CheckboxSetting silenceKeepFov = new CheckboxSetting("ST Keep FOV", true)
        .visibleWhen(this::isSilenceTellySetting);
    public final SliderSetting silenceFov = new SliderSetting("ST FOV", 1.1, 1, 2.1, 0.05, ValueDisplay.DECIMAL)
        .visibleWhen(() -> isSilenceTellySetting() && silenceKeepFov.isChecked());
    public final CheckboxSetting hypixelBoost = new CheckboxSetting("Hypixel Boost", true)
        .visibleWhen(this::isHypixelServer);
\tpublic final CheckboxSetting silenceMark =''')
        s=replace(s,'\t\taddSetting(silenceMark);','\t\taddSetting(silenceMark);\n        addSetting(silenceDebug);\n        addSetting(silenceKeepFov);\n        addSetting(silenceFov);\n        addSetting(hypixelBoost);')
        speed='MOVEMENT_SPEED' if 'MobEffects.MOVEMENT_SPEED' in s else 'SPEED'
        # Determine the version's effect name from an existing source that uses speed.
        aura=read('hacks/scaffold/TellyScaffoldMode.java')
        if 'MobEffects.MOVEMENT_SPEED' in aura:speed='MOVEMENT_SPEED'
        else:
            props=(p/'gradle.properties').read_text(encoding='utf-8-sig')
            import re
            v=tuple(map(int,re.search(r'minecraft_version=([\d.]+)',props).group(1).split('.')))
            speed='MOVEMENT_SPEED' if v >= (1,20,5) else 'MOVEMENT_SPEED'
        # Mojmap SPEED is MOVEMENT_SPEED across the supported API families.
        s=replace(s,'    public void onOpalPostMove()', '''    /** The literal override in OpenOpal GameRendererMixin, with host CustomFov priority. */
    public double getSilenceTellyFov(double original) {
        if(!isEnabled() || !isSilenceTellyMode() || !silenceKeepFov.isChecked()
            || MC.player == null || !ScaffoldUtils.isMoving()) return original;
        double result = silenceFov.getValue();
        var effect = MC.player.getEffect(MobEffects.MOVEMENT_SPEED);
        if(effect != null) result += (effect.getAmplifier() + 1) * 0.13F;
        return result;
    }

    public void onOpalPostMove()''')
        put('hacks/ScaffoldWalkHack.java',s)
        s=read('hacks/CustomFovHack.java')
        s=replace(s,'\t\treturn WURST.getOtfs().zoomOtf.changeFovBasedOnZoom(original);', '''        if(!isEnabled()) original = WURST.getHax().scaffoldWalkHack.getSilenceTellyFov(original);
\t\treturn WURST.getOtfs().zoomOtf.changeFovBasedOnZoom(original);''')
        put('hacks/CustomFovHack.java',s)
        s=read('hacks/scaffold/SilenceTellyScaffoldMode.java')
        start=s.index('\t\tboolean spoof =',s.index('private boolean tryPlaceArmed'))
        end=s.index('\n\t\tlastPlaceAge =',start)
        body=s[s.index('\t\t\tif(hack.silenceAbuseRotation',start):s.index('\n\t\t}finally',start)]
        body=body.replace('\t\t\t','\t\t')
        body=body.replace('\t\tif(!hack.canPlaceThisTick())\n\t\t\treturn false;\n','')
        # Previous broad dedent makes the return have two tabs; remove explicitly.
        body=body.replace('\t\tif(!hack.canPlaceThisTick())\n\t\treturn false;\n','')
        s=s[:start]+body+s[end:]
        s=replace(s,'\t\thack.notePlaced(lastPlacePosition);','\t\thack.notePlaced(lastPlacePosition);\n        debug("place ok hand=" + armedSlot.hand() + " slot=" + armedSlot.slot() + " face=" + armedData.facing());')
        s=replace(s,'\t\tplacementArmed = true;','\t\tdebug("placement armed face=" + requestedData.facing());\n\t\tplacementArmed = true;')
        s=replace(s,'\tprivate float randomFloat(','''    private String lastDebugMessage;
    private int lastDebugAge;
    private void debug(String message) {
        if(!hack.silenceDebug.isChecked() || WurstClient.MC.player == null) return;
        if(message.equals(lastDebugMessage) && WurstClient.MC.player.tickCount - lastDebugAge < 8) return;
        lastDebugMessage = message;
        lastDebugAge = WurstClient.MC.player.tickCount;
        net.wurstclient.util.ChatUtils.message("SilenceTelly | " + message);
    }

\tprivate float randomFloat(''')
        s=replace(s,'\t\tserverRotation = null;','\t\tserverRotation = null;\n        lastDebugMessage = null;\n        lastDebugAge = -1;')
        put('hacks/scaffold/SilenceTellyScaffoldMode.java',s)
    for path in pending:
        if path.exists():assert not (PLAN/'before-settings-port'/path.relative_to(ROOT)).exists(),path
    for path,s in pending.items():
        b=PLAN/'before-settings-port'/path.relative_to(ROOT)
        b.parent.mkdir(parents=True,exist_ok=True)
        b.write_bytes(path.read_bytes())
        path.write_text(s,encoding='utf-8',newline='\n')
    print('Applied final settings:',len(pending),'files')


if __name__=='__main__':main()
