"""S10: keep shared legacy key files when the native master key differs."""
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
REL = os.path.join(PKG, "altmanager", "Encryption.java")

OLD = """	private void cleanupLegacyFiles()
	{
		if(!legacyFilesPresent || !masterKeyStoredNatively)
			return;
		try
		{"""

NEW = """	private void cleanupLegacyFiles()
	{
		if(!legacyFilesPresent || !masterKeyStoredNatively)
			return;

		// The legacy key folder is shared by every Minecraft installation of
		// this user. Deleting it after migrating one file would strand the
		// account files that other installations have not migrated yet.
		if(legacyKeyDiffers())
			return;

		try
		{"""

HELPER = """

	private boolean legacyKeyDiffers()
	{
		return legacyKey != null
			&& !Arrays.equals(legacyKey.getEncoded(), masterKey.getEncoded());
	}
"""


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
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n").decode("utf-8"), eol, bom


def write(path, text, eol, bom):
    out = text.replace("\n", "\r\n").encode("utf-8") if eol == b"\r\n" \
        else text.encode("utf-8")
    if bom:
        out = b"\xef\xbb\xbf" + out
    open(path, "wb").write(out)


def main():
    apply = "--apply" in __import__("sys").argv
    patched = skipped = 0
    for root in roots():
        path = os.path.join(root, REL)
        if not os.path.isfile(path):
            continue
        text, eol, bom = read(path)
        if "legacyKeyDiffers" in text:
            skipped += 1
            continue
        if "import java.util.Arrays;" not in text:
            if "import java.util.Base64;" not in text:
                print("no Base64 import:", path)
                continue
            text = text.replace("import java.util.Base64;",
                                "import java.util.Arrays;\nimport java.util.Base64;", 1)
        if OLD not in text:
            print("cleanup anchor missing:", path)
            continue
        text = text.replace(OLD, NEW, 1)

        marker = "\n\tpublic static Path chooseEncryptionFolder()"
        if marker not in text:
            print("choose anchor missing:", path)
            continue
        text = text.replace(marker, HELPER + marker, 1)

        if apply:
            write(path, text, eol, bom)
        patched += 1
    print(f"Encryption {'patched' if apply else 'to patch'}: {patched}, already: {skipped}")


if __name__ == "__main__":
    main()
