"""Find which trees use the reflective MovementPlanner.getMoveVector and how
their Flight/Freecam read input."""
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")


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


for root in roots():
    mp = os.path.join(root, PKG, "util", "MovementPlanner.java")
    if not os.path.isfile(mp):
        continue
    planner = read(mp)
    if "getMoveVector" not in planner:
        continue
    flight = os.path.join(root, PKG, "hacks", "FlightHack.java")
    freecam = os.path.join(root, PKG, "hacks", "FreecamHack.java")
    flight_text = read(flight) if os.path.isfile(flight) else ""
    freecam_text = read(freecam) if os.path.isfile(freecam) else ""
    print(f"{os.path.relpath(root, ROOT):42s} "
          f"reflect={'getMethod' in planner} "
          f"keyPresses={'keyPresses' in planner} "
          f"flight={'getMoveVector' in flight_text} "
          f"freecam={'getMoveVector' in freecam_text}")
