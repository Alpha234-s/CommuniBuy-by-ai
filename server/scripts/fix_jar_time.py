import zipfile
import os
import sys
from pathlib import Path

OUTPUTS = "build/outputs"

def normalize_jar(path):
    tmp = path + ".tmp"
    with zipfile.ZipFile(path, "r") as zin, \
         zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED) as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            info.date_time = (1980, 1, 1, 0, 0, 0)
            zout.writestr(info, data)
    os.replace(tmp, path)

def main(outputs):
    if not outputs.exists():
        # outputs 不存在，跳过
        sys.exit(0)
    jars = list(outputs.rglob("*.jar"))
    if not jars:
        # 未找到 jar，跳过
        sys.exit(0)
    for jar in jars:
        try:
            normalize_jar(str(jar))
        except Exception:
            # 不是 jar 文件，跳过
            pass

if __name__ == "__main__":
    outputs = Path(OUTPUTS)
    main(outputs)
