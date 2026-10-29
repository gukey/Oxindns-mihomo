# SPDX-License-Identifier: GPL-3.0-or-later
"""Build a clean arm64 module and matching upstream source bundle; no phone files are read."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.1.3"
MODULE_FILES = {
    "module.prop", "skip_mount", "service.sh", "ctl.sh", "uninstall.sh", "customize.sh",
    "META-INF/com/google/android/update-binary", "META-INF/com/google/android/updater-script",
    "config/oxidns.yaml", "config/mihomo.yaml", "config/mihomo-provider.example.yaml", "config/tun.example.yaml",
    "LICENSE", "licenses/oxidns-LICENSE", "licenses/mihomo-LICENSE", "licenses/metacubexd-LICENSE", "upstream.lock.json",
}


def download(url, path, expected):
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + ".partial")
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "NetPanel-builder"}), timeout=120) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        temporary.replace(path)
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError("SHA-256 mismatch: " + path.name)
    return path


def archive_files(archive, prefix, selected=None):
    result = {}
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            if not member.isfile():
                continue
            name = PurePosixPath(member.name)
            if name.is_absolute() or ".." in name.parts:
                raise ValueError("Unsafe upstream archive path")
            clean = str(name)
            if selected is None or clean.startswith(selected + "/"):
                relative = clean[len(selected) + 1:] if selected else clean
                result[prefix + relative] = source.extractfile(member).read()
    return result


def write_zip(path, files, module=False):
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as output:
        for name, data in sorted(files.items()):
            executable = module and (name.startswith("bin/") or name.endswith(".sh") or name.endswith("update-binary"))
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 29, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | (0o755 if executable else 0o644)) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(info, data)


def build(output, cache):
    output.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / "root-module/upstream.lock.json").read_text(encoding="utf-8"))
    downloads = {}
    sources = {}
    for item in lock:
        downloads[item["component"]] = download(item["url"], cache / item["filename"], item["sha256"])
        source = download(item["source_url"], cache / item["source_filename"], item["source_sha256"])
        sources[item["source_filename"]] = source.read_bytes()
    files = {p.relative_to(ROOT / "root-module").as_posix(): p.read_bytes() for p in (ROOT / "root-module").rglob("*") if p.is_file()}
    if set(files) != MODULE_FILES:
        raise ValueError("Unexpected or missing module files; review before packaging")
    assert b"proxies: []" in files["config/mihomo.yaml"] and b"__MIHOMO_SECRET__" in files["config/mihomo.yaml"]
    assert b"__DNS_PASSWORD__" in files["config/oxidns.yaml"]
    assert b"https://subscription.example.invalid/" in files["config/mihomo-provider.example.yaml"]
    with tarfile.open(downloads["oxidns"]) as archive:
        files["bin/oxidns"] = archive.extractfile("oxidns").read()
    files.update(archive_files(downloads["oxidns"], "web/oxidns/", "webui"))
    files["bin/mihomo"] = gzip.decompress(downloads["mihomo"].read_bytes())
    files.update(archive_files(downloads["metacubexd"], "web/mihomo/"))
    files["DEPLOYMENT.md"] = (ROOT / "docs/DEPLOYMENT.md").read_bytes()
    files["THIRD_PARTY.md"] = (ROOT / "docs/THIRD_PARTY.md").read_bytes()
    files["RELEASE-VERIFICATION.md"] = (ROOT / "docs/RELEASE-0.1.3.md").read_bytes()
    assert "web/oxidns/index.html" in files and "web/mihomo/index.html" in files
    assert not any("key.txt" in name or name.endswith("/personal.yaml") or name.startswith(("logs/", "run/", "state/")) for name in files)
    write_zip(output / f"NetPanel-Root-{VERSION}-arm64.zip", files, module=True)
    sources["upstream.lock.json"] = (ROOT / "root-module/upstream.lock.json").read_bytes()
    sources["THIRD_PARTY.md"] = files["THIRD_PARTY.md"]
    for path in (ROOT / "root-module/licenses").iterdir():
        sources["licenses/" + path.name] = path.read_bytes()
    write_zip(output / f"NetPanel-Upstream-Sources-{VERSION}.zip", sources)
    configs = {p.name: p.read_bytes() for p in (ROOT / "root-module/config").iterdir() if p.is_file()}
    write_zip(output / f"NetPanel-Configs-{VERSION}.zip", configs)
    shutil.copyfile(ROOT / "docs/DEPLOYMENT.md", output / "DEPLOYMENT.md")
    shutil.copyfile(ROOT / "docs/THIRD_PARTY.md", output / "THIRD_PARTY.md")
    shutil.copyfile(ROOT / "docs/RELEASE-0.1.3.md", output / "RELEASE-VERIFICATION.md")
    for file in sorted(output.glob("*")):
        if file.is_file():
            print(file.name, file.stat().st_size)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "dist/release-0.1.3")
    parser.add_argument("--cache", type=Path, default=ROOT / ".tools/upstream")
    args = parser.parse_args()
    build(args.output, args.cache)
