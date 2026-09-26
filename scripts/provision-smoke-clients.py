#!/usr/bin/env python3
"""Install the repository's real client test instances under .test/versions.

Downloads prefer BMCLAPI. Existing instances and libraries are left in place.
Run `py -3 scripts/provision-smoke-clients.py --all` to fill every project.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import zipfile
from urllib.error import URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / ".test"
VERSIONS = TEST / "versions"
LIBRARIES = TEST / "libraries"
WORK = ROOT / "_smoke" / "provision"
BMCL = "https://bmclapi2.bangbang93.com"
LOADERS = ("forge", "fabric", "neoforge")


def properties(path):
    return dict(re.findall(r"^([\w.]+)\s*=\s*(\S+)",
                           path.read_text(encoding="utf-8"), re.M))


def projects(version=None, loaders=None):
    for loader, base in (("forge", ROOT), ("fabric", ROOT / "fabric"),
                         ("neoforge", ROOT / "neoforge")):
        if loaders and loader not in loaders:
            continue
        for project in (base, *sorted((base / "versions").glob("*"))):
            props = project / "gradle.properties"
            if not props.is_file():
                continue
            data = properties(props)
            mc = data.get("minecraft_version")
            if not mc or (version and version != mc):
                continue
            release = (data.get("forge_version") if loader == "forge" else
                       data.get("loader_version") if loader == "fabric" else
                       data.get("neoforge_version") or data.get("neo_version") or
                       data.get("forge_version"))
            if not release:
                raise ValueError("Missing loader version: %s" % props)
            yield mc, loader, release


def existing_instance(mc, loader):
    pattern = re.compile(r"^%s-%s(?:[-_ ]|$)" %
                         (re.escape(mc), loader), re.I)
    for path in VERSIONS.iterdir():
        if path.is_dir() and pattern.match(path.name) and (
                path / (path.name + ".json")).is_file():
            return path
    return None


def complete_instance(path):
    profile = json.loads((path / (path.name + ".json")).read_text(
        encoding="utf-8"))
    for lib in profile.get("libraries", []):
        artifact = lib.get("downloads", {}).get("artifact", {})
        if artifact.get("path") and not artifact.get("url") and not (
                LIBRARIES / artifact["path"]).is_file():
            return False
    return True


def sha1(path):
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def urls(original):
    if original.startswith(BMCL):
        return [original]
    if "/v1/objects/" in original:
        return [BMCL + "/v1/objects/" + original.split("/v1/objects/", 1)[1],
                original]
    if "/releases/" in original:
        tail = original.split("/releases/", 1)[1]
    elif "/maven/" in original:
        tail = original.split("/maven/", 1)[1]
    else:
        tail = original.split("/", 3)[-1]
    return [BMCL + "/maven/" + tail, original]


def download(candidates, path, expected=None):
    if path.is_file() and (not expected or sha1(path) == expected):
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    last = None
    for url in candidates:
        try:
            with urlopen(Request(url, headers={"User-Agent": "WurstB smoke setup"}),
                         timeout=90) as response, (path.parent / (path.name + ".part")).open("wb") as out:
                shutil.copyfileobj(response, out)
            part = path.parent / (path.name + ".part")
            if expected and sha1(part) != expected:
                raise ValueError("SHA-1 mismatch: %s" % url)
            part.replace(path)
            return
        except (OSError, ValueError, URLError) as error:
            last = error
    raise RuntimeError("Download failed for %s: %s" % (path, last))


def version_json(mc):
    path = VERSIONS / mc / (mc + ".json")
    if not path.is_file():
        download([BMCL + "/version/%s/json" % mc,
                  "https://piston-meta.mojang.com/mc/game/%s.json" % mc], path)
    result = json.loads(path.read_text(encoding="utf-8"))
    if result.get("id") != mc or "downloads" not in result:
        raise ValueError("Invalid vanilla version JSON: %s" % path)
    client = result["downloads"]["client"]
    download(urls(client["url"]), VERSIONS / mc / (mc + ".jar"),
             client.get("sha1"))
    return result


def allowed(rules):
    if not rules:
        return True
    answer = False
    for rule in rules:
        platform = rule.get("os", {})
        if platform.get("name") not in (None, "windows"):
            continue
        if platform.get("arch") not in (None, "x86_64"):
            continue
        if rule.get("features"):
            continue
        answer = rule.get("action") == "allow"
    return answer


def library_artifacts(lib):
    downloads = lib.get("downloads", {})
    if downloads.get("artifact"):
        yield downloads["artifact"]
    elif lib.get("name"):
        parts = lib["name"].split(":")
        if len(parts) >= 3:
            group, name, version = parts[:3]
            classifier = "-" + parts[3] if len(parts) > 3 else ""
            rel = "%s/%s/%s/%s-%s%s.jar" % (
                group.replace(".", "/"), name, version, name, version, classifier)
            yield {"path": rel, "url": lib.get("url", BMCL + "/maven/").rstrip("/") + "/" + rel}
    natives = lib.get("natives", {})
    if "windows" in natives:
        key = natives["windows"].replace("${arch}", "64")
        native = downloads.get("classifiers", {}).get(key)
        if native:
            yield native


def install_libraries(*profiles):
    for profile in profiles:
        for lib in profile.get("libraries", []):
            if not allowed(lib.get("rules")):
                continue
            for artifact in library_artifacts(lib):
                relative = Path(artifact["path"])
                if relative.is_absolute() or ".." in relative.parts:
                    raise ValueError("Unsafe library path: %s" % relative)
                if not artifact.get("url"):
                    if not (LIBRARIES / relative).is_file():
                        raise FileNotFoundError("Installer output is missing: %s" % relative)
                    continue
                download(urls(artifact["url"]), LIBRARIES / relative,
                         artifact.get("sha1"))


def fabric_api_version(mc):
    project = ROOT / "fabric" / ("." if mc == "1.20.1" else "versions/" + mc)
    version = properties(project / "gradle.properties").get("fabric_version")
    if not version:
        raise ValueError("Fabric API version is missing for %s" % mc)
    return version


def installed_fabric_api(instance):
    return any(zipfile.is_zipfile(jar) for jar in
               (instance / "mods").glob("fabric-api-*.jar"))


def ensure_fabric_api(mc, instance):
    if installed_fabric_api(instance):
        return
    version = fabric_api_version(mc)
    filename = "fabric-api-%s.jar" % version
    target = instance / "mods" / filename
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api" / version
    for cached in cache.glob("*/" + filename):
        if zipfile.is_zipfile(cached):
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cached, target)
            return
    relative = "net/fabricmc/fabric-api/fabric-api/%s/%s" % (version, filename)
    download([BMCL + "/maven/" + relative,
              "https://maven.fabricmc.net/" + relative], target)
    if not zipfile.is_zipfile(target):
        raise ValueError("Invalid Fabric API JAR: %s" % target)


def java_for(mc):
    major = (17 if mc in ("1.20.1", "1.20.2", "1.20.3", "1.20.4")
             else 21 if mc.startswith(("1.20", "1.21")) else 25)
    choices = [os.getenv("WURSTBPLUS_JAVA%d" % major),
               "C:/Program Files/Java/jdk-%d" % major,
               "C:/Program Files/Java/jdk-25.0.4"]
    for home in choices:
        if home and (Path(home) / "bin/java.exe").is_file():
            return str(Path(home) / "bin/java.exe")
    raise FileNotFoundError("JDK %d is required for %s" % (major, mc))


def normalize_installed_profile(mc, loader, release):
    originals = ([VERSIONS / ("neoforge-" + release)] if loader == "neoforge"
                 else [VERSIONS / (mc + "-forge" + release)])
    renamed = VERSIONS / ("%s-%s-%s" % (mc, loader, release))
    for original in originals:
        if not original.is_dir() or renamed.exists():
            continue
        original.rename(renamed)
        source_json = renamed / (original.name + ".json")
        target_json = renamed / (renamed.name + ".json")
        source_json.rename(target_json)
        profile = json.loads(target_json.read_text(encoding="utf-8"))
        profile["id"] = renamed.name
        target_json.write_text(json.dumps(profile, ensure_ascii=False, indent=2),
                               encoding="utf-8")


def install_loader(mc, loader, release):
    if loader != "fabric":
        normalize_installed_profile(mc, loader, release)
    existing = existing_instance(mc, loader)
    if existing and complete_instance(existing):
        return existing
    if loader == "fabric":
        name = "%s-fabric-%s" % (mc, release)
        path = VERSIONS / name
        url = BMCL + "/fabric-meta/v2/versions/loader/%s/%s/profile/json" % (mc, release)
        profile_path = path / (name + ".json")
        download([url, url.replace(BMCL + "/fabric-meta", "https://meta.fabricmc.net")],
                 profile_path)
        profile = json.loads(profile_path.read_text(encoding="utf-8"))
        profile["id"] = name
        profile_path.write_text(json.dumps(profile, ensure_ascii=False, indent=2),
                                encoding="utf-8")
        return path
    coordinate = ("net/minecraftforge/forge/%s-%s/forge-%s-%s" %
                  (mc, release, mc, release) if loader == "forge" else
                  "net/neoforged/neoforge/%s/neoforge-%s" % (release, release))
    installer = WORK / "installers" / (coordinate.rsplit("/", 1)[1] + "-installer.jar")
    download([BMCL + "/maven/" + coordinate + "-installer.jar"], installer)
    with zipfile.ZipFile(installer) as archive:
        install_profile = json.loads(archive.read("install_profile.json"))
    downloadable = [lib for lib in install_profile.get("libraries", []) if
                    not lib.get("downloads", {}).get("artifact") or
                    lib["downloads"]["artifact"].get("url")]
    install_libraries({"libraries": downloadable})
    java = java_for(mc)
    help_text = subprocess.run([java, "-jar", str(installer), "--help"],
                               capture_output=True, text=True, errors="replace").stdout
    if "installClient" in help_text or "install-client" in help_text:
        command = [java, "-jar", str(installer), "--installClient", str(TEST),
                   "--mirror", BMCL + "/maven/"]
    else:
        command = [java, "-cp", str(installer),
                   str(ROOT / "scripts" / "LegacyClientInstall.java"),
                   str(TEST), str(installer)]
    log = WORK / "installers" / (mc + "-" + loader + ".log")
    with log.open("wb") as out:
        result = subprocess.run(command, cwd=ROOT, stdout=out,
                                stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError("%s installer failed (%s): %s" % (loader, result.returncode, log))
    normalize_installed_profile(mc, loader, release)
    existing = existing_instance(mc, loader)
    if not existing or not complete_instance(existing):
        raise RuntimeError("%s installer produced no profile: %s" % (loader, log))
    return existing


def ensure_world(mc, profile):
    target = profile / "saves" / "WurstSmokeFresh" / "level.dat"
    if target.is_file():
        return
    server = WORK / "servers" / mc
    world = server / "world" / "level.dat"
    if not world.is_file():
        metadata = version_json(mc)["downloads"]["server"]
        jar = WORK / "servers" / (mc + ".jar")
        download(urls(metadata["url"]), jar, metadata.get("sha1"))
        server.mkdir(parents=True, exist_ok=True)
        (server / "eula.txt").write_text("eula=true\n", encoding="ascii")
        (server / "server.properties").write_text(
            "online-mode=false\nmax-players=1\nview-distance=2\nsimulation-distance=2\n",
            encoding="ascii")
        log = server / "server.log"
        with log.open("wb") as out:
            proc = subprocess.Popen([java_for(mc), "-Xmx2G", "-jar", str(jar), "nogui"],
                                    cwd=server, stdin=subprocess.PIPE, stdout=out,
                                    stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline and proc.poll() is None:
                if re.search(rb"Done \(", log.read_bytes()):
                    proc.stdin.write(b"stop\n")
                    proc.stdin.flush()
                    break
                time.sleep(2)
            else:
                if proc.poll() is None:
                    proc.terminate()
            try:
                proc.wait(timeout=45)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
        if not world.is_file():
            raise RuntimeError("World generation failed for %s: %s" % (mc, log))
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(world, target)
    (profile / "quickPlay").mkdir(exist_ok=True)
    options = profile / "options.txt"
    if not options.is_file():
        options.write_text("onboardAccessibility:false\n", encoding="utf-8")


def verify_instance(mc, loader):
    profile_path = existing_instance(mc, loader)
    if not profile_path:
        return "instance profile missing"
    vanilla_path = VERSIONS / mc / (mc + ".json")
    client_path = VERSIONS / mc / (mc + ".jar")
    if not vanilla_path.is_file() or not client_path.is_file():
        return "vanilla JSON or client JAR missing"
    vanilla = json.loads(vanilla_path.read_text(encoding="utf-8"))
    profile = json.loads((profile_path / (profile_path.name + ".json")).read_text(
        encoding="utf-8"))
    if profile.get("inheritsFrom") and profile["inheritsFrom"] != mc:
        return "profile inherits the wrong Minecraft version"
    for item in (vanilla, profile):
        for lib in item.get("libraries", []):
            if not allowed(lib.get("rules")):
                continue
            for artifact in library_artifacts(lib):
                if not (LIBRARIES / artifact["path"]).is_file():
                    return "library missing: " + artifact["path"]
    index = vanilla.get("assetIndex", {}).get("id")
    if not index or not (TEST / "assets" / "indexes" / (index + ".json")).is_file():
        return "asset index missing"
    if not (profile_path / "saves" / "WurstSmokeFresh" / "level.dat").is_file():
        return "quick-play world missing"
    if loader == "fabric" and not installed_fabric_api(profile_path):
        return "Fabric API mod missing"
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("version", nargs="?")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--from-version", help="resume at this Minecraft version")
    parser.add_argument("--loaders", nargs="+", choices=LOADERS)
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--no-assets", action="store_true")
    parser.add_argument("--no-world", action="store_true")
    args = parser.parse_args()
    if not args.all and not args.version:
        parser.error("specify a version or --all")
    targets = sorted(projects(None if args.all else args.version,
                              args.loaders),
                     key=lambda item: (tuple(map(int, item[0].split("."))), item[1]))
    if args.from_version:
        floor = tuple(map(int, args.from_version.split(".")))
        targets = [item for item in targets if
                   tuple(map(int, item[0].split("."))) >= floor]
    if not targets:
        parser.error("no matching projects")
    if args.list:
        for mc, loader, release in targets:
            instance = existing_instance(mc, loader)
            print("%-9s %-8s %-18s %s" %
                  (mc, loader, release, instance or "MISSING"))
        return
    if args.verify:
        failures = []
        for mc, loader, _ in targets:
            problem = verify_instance(mc, loader)
            print("%-9s %-8s %s" % (mc, loader, problem or "READY"))
            if problem:
                failures.append((mc, loader, problem))
        print("Ready: %d/%d" % (len(targets) - len(failures), len(targets)))
        if failures:
            sys.exit(1)
        return
    WORK.mkdir(parents=True, exist_ok=True)
    errors = []
    prepared = set()
    for index, (mc, loader, release) in enumerate(targets, 1):
        print("[%d/%d] %s %s %s" % (index, len(targets), mc, loader, release),
              flush=True)
        try:
            vanilla = version_json(mc)
            instance = install_loader(mc, loader, release)
            profile = json.loads((instance / (instance.name + ".json")).read_text(
                encoding="utf-8"))
            install_libraries(vanilla, profile)
            if loader == "fabric":
                ensure_fabric_api(mc, instance)
            if not args.no_assets and mc not in prepared:
                log = WORK / (mc + "-assets.log")
                with log.open("wb") as out:
                    result = subprocess.run([sys.executable,
                        str(ROOT / "scripts" / "fetch-assets.py"), mc,
                        "--assets-dir", str(TEST / "assets")],
                        cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
                if result.returncode:
                    raise RuntimeError("Asset prefetch failed: %s" % log)
                prepared.add(mc)
            if not args.no_world:
                ensure_world(mc, instance)
            print("  READY %s" % instance, flush=True)
        except Exception as error:
            errors.append((mc, loader, str(error)))
            print("  ERROR %s" % error, flush=True)
    print("Ready: %d/%d" % (len(targets) - len(errors), len(targets)))
    for mc, loader, error in errors:
        print("  %s %s: %s" % (mc, loader, error))
    if errors:
        sys.exit(1)


if __name__ == "__main__":
    main()
