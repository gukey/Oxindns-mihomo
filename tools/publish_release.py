# SPDX-License-Identifier: GPL-3.0-or-later
"""Publish an explicitly requested release via GitHub API; local key.txt is never uploaded."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "gukey/Oxindns-mihomo"
VERSION = "0.1.3"
FILES = [
    f"NetPanel-{VERSION}-debug.apk", f"NetPanel-Root-{VERSION}-arm64.zip",
    f"NetPanel-Configs-{VERSION}.zip", f"NetPanel-{VERSION}-source.zip",
    f"NetPanel-Upstream-Sources-{VERSION}.zip", "DEPLOYMENT.md", "THIRD_PARTY.md",
    "RELEASE-VERIFICATION.md", "SHA256SUMS.txt",
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--commit", required=True, help="Full, already pushed commit SHA")
    parser.add_argument("--publish", action="store_true", help="Publish only after all uploads verify")
    parser.add_argument("--directory", type=Path, default=ROOT / "dist/release-0.1.3")
    args = parser.parse_args()
    if len(args.commit) != 40 or any(c not in "0123456789abcdef" for c in args.commit):
        raise ValueError("Expected a full commit SHA")
    directory = args.directory
    for name in FILES:
        if not (directory / name).is_file():
            raise ValueError("Missing release asset: " + name)
    token = (ROOT / "key.txt").read_text(encoding="utf-8-sig").strip()
    if not token or "\n" in token:
        raise ValueError("Expected one token in local key.txt")

    def call(url, method="GET", payload=None, binary=None):
        headers = {"Authorization": "Bearer " + token, "User-Agent": "NetPanel-release", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
        data = binary
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif binary is not None:
            headers["Content-Type"] = "application/octet-stream"
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=180) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            # Do not log request headers or credentials, even on failed uploads.
            raise RuntimeError("GitHub HTTP " + str(error.code)) from None

    api = "https://api.github.com/repos/" + REPOSITORY
    head = call(api + "/git/ref/heads/main")["object"]["sha"]
    if head != args.commit:
        raise ValueError("Remote main changed; review before publishing")
    existing = call(api + "/releases?per_page=100")
    release = next((r for r in existing if r["tag_name"] == "v" + VERSION), None)
    if release and not release["draft"]:
        raise ValueError("A published release already exists; do not overwrite it automatically")
    notes = (ROOT / "docs/RELEASE-NOTES-0.1.3.md").read_text(encoding="utf-8")
    if release is None:
        release = call(api + "/releases", "POST", {"tag_name": "v" + VERSION, "target_commitish": args.commit, "name": "NetPanel 0.1.3 · APP 与 Root 配套部署", "body": notes, "draft": True, "prerelease": True})
    upload = release["upload_url"].split("{")[0]
    assets = {a["name"]: a for a in call(release["url"] + "/assets")}
    for name in FILES:
        content = (directory / name).read_bytes()
        digest = "sha256:" + hashlib.sha256(content).hexdigest()
        asset = assets.get(name)
        if asset is None:
            asset = call(upload + "?" + urllib.parse.urlencode({"name": name}), "POST", binary=content)
        if asset.get("size") != len(content) or asset.get("state") != "uploaded" or asset.get("digest") != digest:
            raise ValueError("Uploaded asset hash/size/state mismatch: " + name)
        print("Verified asset:", name, len(content))
    if args.publish:
        release = call(release["url"], "PATCH", {"draft": False, "body": notes, "prerelease": True})
    print("Published:" if not release["draft"] else "Draft:", release["html_url"])


if __name__ == "__main__":
    main()
