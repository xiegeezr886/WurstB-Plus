#!/usr/bin/env python3
import json
import sys
import zipfile

jar_path = sys.argv[1]
with zipfile.ZipFile(jar_path) as jar:
    mods = jar.read("META-INF/mods.toml").decode("utf-8")
    if 'versionRange="[47,48)"' not in mods:
        raise SystemExit("Forge compatibility range is not [47,48)")

    try:
        metadata = json.loads(jar.read("META-INF/jarjar/metadata.json"))
    except KeyError:
        metadata = {"jars": []}

    embedded = {
        (entry["identifier"]["group"], entry["identifier"]["artifact"])
        for entry in metadata.get("jars", [])
    }
    forbidden = {
        ("io.github.llamalad7", "mixinextras-forge"),
        ("baritone", "baritone-api-forge"),
    }
    conflicts = embedded & forbidden
    if conflicts:
        raise SystemExit(f"Conflicting embedded mods found: {sorted(conflicts)}")

    manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")
    if "BaritoneMixinConnector" in manifest or "mixins.baritone.json" in manifest:
        raise SystemExit("Baritone launch hooks must not be present")

print("Forge range and large-modpack compatibility checks passed")
