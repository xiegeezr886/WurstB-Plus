"""Prepare an isolated source rebuild of SeedCrackerX for MC 1.20.5.

Never relabels the 1.20.6 binary. Compile/remap the pinned upstream sources with
1.20.5 mappings, then verify the resulting mod in a real client before release.
"""
from pathlib import Path
import io
import json
import hashlib
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
COMMIT = '0ae7b3649e4cd386f89a13003598a88e3beab16a'
UPSTREAM_SHA = 'f453bae397cfa66f1667e0ed3ce1983c96006ddde9b90b40ead260fd80a1cded'


def main():
    project = ROOT/'.dsh-tmp/v152-seedcracker1205-source'
    project.mkdir(exist_ok=True)
    if not (project/'src/main').exists():
        raw = subprocess.run(['git','archive','--format=zip',COMMIT],
            cwd=ROOT/'reference/clients/SeedcrackerX',check=True,capture_output=True).stdout
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            z.extractall(project)
    libs = project/'libs'
    libs.mkdir(exist_ok=True)
    upstream = ROOT/'.dsh-tmp/v152-upstream-seed/seedcrackerX-2.14.7.jar'
    assert hashlib.sha256(upstream.read_bytes()).hexdigest() == UPSTREAM_SHA
    with zipfile.ZipFile(upstream) as z:
        # Upstream shades seedfinding classes. Reuse those pure Java libraries,
        # while all Minecraft-facing SeedCracker classes are compiled from source.
        with zipfile.ZipFile(libs/'seedfinding.jar','w',zipfile.ZIP_DEFLATED) as out:
            for name in z.namelist():
                if name.startswith('com/seedfinding/'):
                    out.writestr(name,z.read(name))
        for row in json.loads(z.read('fabric.mod.json'))['jars']:
            if 'cloth-config' in row['file']:
                (libs/Path(row['file']).name).write_bytes(z.read(row['file']))
    (project/'build.gradle').write_text('''plugins { id 'fabric-loom' version '1.9.2' }
version = '2.14.7-wurstb-mc1.20.5'
group = 'kaptainwutax'
base { archivesName = 'seedcrackerX' }
java.toolchain.languageVersion = JavaLanguageVersion.of(21)
repositories { flatDir { dirs 'libs' }; mavenCentral(); maven { url 'https://maven.fabricmc.net/' }; maven { url 'https://maven.terraformersmc.com/releases' } }
dependencies {
 minecraft 'com.mojang:minecraft:1.20.5'
 mappings 'net.fabricmc:yarn:1.20.5+build.1:v2'
 modImplementation 'net.fabricmc:fabric-loader:0.19.5'
 modImplementation 'net.fabricmc.fabric-api:fabric-api:0.97.8+1.20.5'
 modCompileOnly 'com.terraformersmc:modmenu:10.0.0-beta.1'
 implementation files('libs/seedfinding.jar')
 modApi 'local:cloth-config-fabric:14.0.126:fabric'
 include 'local:cloth-config-fabric:14.0.126:fabric'
}
sourceSets.main.java.srcDir 'src/api/java'
jar { from(zipTree('libs/seedfinding.jar')); from('LICENSE.txt') }
processResources { filesMatching('fabric.mod.json') { expand(version:project.version) } }
tasks.withType(JavaCompile).configureEach { options.encoding='UTF-8'; options.release=21 }
''',encoding='utf-8')
    meta = project/'src/main/resources/fabric.mod.json'
    data = json.loads(meta.read_text('utf-8-sig'))
    data['depends']['minecraft'] = '1.20.5'
    data['depends']['java'] = '>=21'
    meta.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    (project/'gradle.properties').write_text('org.gradle.jvmargs=-Xmx2G\norg.gradle.daemon=false\n',encoding='utf-8')
    # Match the host project's known wrapper and plugin repositories.
    for relative in ('gradlew.bat','gradle/wrapper/gradle-wrapper.jar',
                     'gradle/wrapper/gradle-wrapper.properties','settings.gradle'):
        dest = project/relative
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes((ROOT/'fabric/versions/1.20.5'/relative).read_bytes())
    print(project)


if __name__ == '__main__':
    main()
