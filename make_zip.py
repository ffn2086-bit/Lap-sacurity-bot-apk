"""Creates SentinelLaptopGuard.zip (repo files at the zip root, ready to push to GitHub)."""
import os
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, "SentinelLaptopGuard.zip")
SKIP_DIRS = {".git", ".gradle", "build", "__pycache__", ".idea"}
SKIP_FILES = {"SentinelLaptopGuard.zip", "config.json"}

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
    for folder, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if name in SKIP_FILES or name.endswith(".log"):
                continue
            full = os.path.join(folder, name)
            rel = os.path.relpath(full, ROOT).replace(os.sep, "/")
            info = zipfile.ZipInfo.from_file(full, rel)
            if name == "gradlew":
                info.external_attr = (0o100755 << 16)
            info.compress_type = zipfile.ZIP_DEFLATED
            with open(full, "rb") as fh:
                zf.writestr(info, fh.read())
print("Created", OUT)
