"""Guard the two network-library packaging routes in the NeoForge builds."""
from pathlib import Path
import re
import unittest


NEO = Path(__file__).resolve().parents[2] / "neoforge"
LIBRARIES = (
    "org.java-websocket:Java-WebSocket",
    "io.netty:netty-codec-socks",
    "io.netty:netty-handler-proxy",
)


class NeoForgeNetworkPackagingTest(unittest.TestCase):
    def test_one_provider_per_library(self):
        for path in [NEO / "build.gradle", *sorted(NEO.glob("versions/*/build.gradle"))]:
            source = path.read_text(encoding="utf-8-sig")
            # Do not interpret a quoted Gradle glob such as "**/*.bbmodel" as a comment.
            source = re.sub(r"^[ \t]*(?:/\*.*?\*/|//[^\n]*)", "", source, flags=re.M | re.S)
            # 1.20.x version projects unpack these libraries; the root and
            # modern projects use JarJar. Plain implementation isn't packaging.
            unpack = path.parent.name.startswith("1.20.")
            for library in LIBRARIES:
                with self.subTest(project=path.parent.name, library=library):
                    coordinate = rf'''["']{re.escape(library)}:[^"']+["']'''
                    group, name = map(re.escape, library.split(":"))
                    nested = re.findall(
                        rf"\bjarJar\s*\(\s*implementation\s*\(\s*{coordinate}"
                        rf'''|\bjarJar\s*\(\s*group:\s*["']{group}["'],\s*name:\s*["']{name}["']''',
                        source,
                    )
                    shaded = re.findall(rf"\bv152MusicRuntime\s*\(?\s*{coordinate}", source)
                    self.assertEqual((len(nested), len(shaded)), (0, 1) if unpack else (1, 0))
