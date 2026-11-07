"""Verify every project tree actually contains each fix (marker-based)."""
import glob
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")

CHECKS = {
    "altmanager/AltsFile.java": ["if(!loadFailed)"],
    "altmanager/Encryption.java": ["looksLikeJson("],
    "hacks/AimAssistHack.java":
        ['must score before switching.", 0.1, 0,'],
    "hacks/BlinkHack.java": ["Do not clear the queue here"],
    "hacks/FlightHack.java": ["Restore the pre-hack flying state"],
    "hacks/KillauraHack.java": ["attackRecords::get", "hideFakeSwings", 'false);'],
    "hacks/KillauraLegitHack.java":
        ['must score before switching.", 0.1, 0,'],
    "hacks/MultiAuraHack.java": ["OpenAura halves it internally"],
    "hacks/ScaffoldWalkHack.java": ["Only swallow right clicks"],
    "hacks/aura/OpenAura.java":
        ["AttackRecord", "FakePlayerEntity", "getAngleToLookVec"],
    "hacks/scaffold/HeypixelScaffoldMode.java":
        ["getVanillaRotation(ScaffoldUtils.rotationFromBlock(pos, face))"],
    "hacks/scaffold/PlayerSimulation.java":
        ["player.xxa, player.yya, player.zza"],
    "hacks/scaffold/SilenceTellyScaffoldMode.java": ["blockFlySwapped"],
    "hud2/compat/HudItemIcons.java": ["MAX_CACHE"],
    "util/TargetTracker.java": ["switchAdvantageFraction"],
}


def roots():
    result = [os.path.join(ROOT, b) for b in
              ("src", os.path.join("fabric", "src"), os.path.join("neoforge", "src"))]
    for sub in ("versions", os.path.join("fabric", "versions"),
                os.path.join("neoforge", "versions")):
        base = os.path.join(ROOT, sub)
        if os.path.isdir(base):
            for name in sorted(os.listdir(base)):
                result.append(os.path.join(base, name, "src"))
    return [r for r in result if os.path.isdir(os.path.join(r, PKG))]


def read(path):
    data = open(path, "rb").read()
    if data.startswith(b"\xef\xbb\xbf"):
        data = data[3:]
    return data.replace(b"\r\n", b"\n").decode("utf-8", "replace")


def main():
    all_roots = roots()
    print(f"trees: {len(all_roots)}")
    missing_total = 0
    for rel, markers in CHECKS.items():
        ok = absent = stub = problem = 0
        bad = []
        for root in all_roots:
            path = os.path.join(root, PKG, rel.replace("/", os.sep))
            if not os.path.isfile(path):
                absent += 1
                continue
            text = read(path)

            # HudItemIcons stubs legitimately have no icon cache at all.
            if rel.endswith("HudItemIcons.java") and "CACHE" not in text:
                stub += 1
                continue

            if all(marker in text for marker in markers):
                ok += 1
            else:
                problem += 1
                bad.append(os.path.relpath(path, ROOT))

        missing_total += problem
        print(f"{rel:52s} fixed={ok:2d} stub/n-a={stub:2d} "
              f"absent={absent:2d} NOT-FIXED={problem:2d}")
        for path in bad:
            print("    NOT FIXED:", path)

    print(f"\nNOT-FIXED total: {missing_total}")


if __name__ == "__main__":
    main()
