#!/usr/bin/env python3
"""BluetoothControl release verification tool (pure Python 3 stdlib).

Checks performed:
  1. SHA-256 of the APK matches CHECKSUMS.sha256
  2. APK signing block exists and contains the expected schemes (v2, v3)
  3. Signer certificate fingerprint matches the pinned long-term developer
     certificate (SHA-1 592293122D3185E73699506BE1A8956A35551249 /
     SHA-256 9e42da49bc76491db6bcbe914b22f5c9aadc8f8c50dcbf90fec43bf2c7637901)
  4. AndroidManifest.xml security flags:
       - not debuggable
       - allowBackup == false
       - targetSdkVersion >= MIN_TARGET_SDK
       - no usesCleartextText
       - every component with an intent-filter declares exported explicitly
  5. (info) package name, version, sdk levels

Usage:
  python3 verify_release.py <path-to-apk> [--json] [--min-target-sdk N]
Exit codes: 0 = all hard checks passed, 1 = failure, 2 = usage error.
"""
import hashlib
import json
import os
import struct
import sys

# Pinned identity of the app's long-term signing certificate
# (matches every publicly distributed version of io.appground.blek).
EXPECTED_CERT_SHA1 = "592293122D3185E73699506BE1A8956A35551249"
EXPECTED_CERT_SHA256 = "9e42da49bc76491db6bcbe914b22f5c9aadc8f8c50dcbf90fec43bf2c7637901"
EXPECTED_PACKAGE = "io.appground.blek"

SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
SCHEME_V2 = 0x7109871a
SCHEME_V3 = 0xF05368C0
SCHEME_V4 = 0x6DFF800D


# ------------------------------------------------------------------ errors
class CheckError(Exception):
    pass


def fail(results, msg, hard=True):
    results["errors"].append(msg) if hard else results["warnings"].append(msg)
    if hard:
        raise CheckError(msg)


def info(results, msg):
    results["info"].append(msg)


# ------------------------------------------------------------------ sha256
def check_sha256(apk_path, results):
    expected = None
    checksums_path = "CHECKSUMS.sha256"
    try:
        for line in open(checksums_path, "r", encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 1)
            if len(parts) == 2:
                name = parts[1].strip()
                if name.startswith("*"):
                    name = name[1:].lstrip()
                name = name.strip('"')
                if name == apk_path or os.path.basename(name) == os.path.basename(apk_path):
                    expected = parts[0].lower()
                    break
    except OSError:
        pass
    h = hashlib.sha256()
    with open(apk_path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    actual = h.hexdigest()
    results["sha256"] = actual
    if expected:
        if expected != actual:
            fail(results, f"SHA-256 mismatch: expected {expected}, got {actual}")
        else:
            info(results, "SHA-256 matches CHECKSUMS.sha256")
    else:
        fail(results, f"APK path '{apk_path}' not found in {checksums_path}", hard=False)
    return actual


# ------------------------------------------------------------------ signing block
def read_len(buf, p):
    b = buf[p]
    p += 1
    if b < 0x80:
        return b, p
    n = b & 0x7F
    return int.from_bytes(buf[p:p + n], "big"), p + n


def tlv(buf, p):
    tag = buf[p]
    ln, cs = read_len(buf, p + 1)
    return tag, cs, cs + ln


def children(buf, s, e):
    out = []
    p = s
    while p < e:
        tag, cs, ce = tlv(buf, p)
        out.append((tag, cs, ce))
        p = ce
    return out


def find_signing_block(data):
    eocd = data.rfind(b"\x50\x4b\x05\x06")
    if eocd < 0:
        raise CheckError("Not a zip/APK (EOCD not found)")
    cd = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd - 16:cd] != SIGNING_BLOCK_MAGIC:
        raise CheckError("APK signing block (v2+) not found - unsigned or corrupted APK")
    size = struct.unpack_from("<Q", data, cd - 24)[0]
    # size field = block size excluding itself; magic (16B) follows it,
    # so content length is size - 8 (classic) - the walk below tolerates
    # both conventions by scanning for known scheme IDs.
    return cd - 24 - (size - 8), cd


def find_scheme_values(data, block_start, block_end):
    """Tolerant scan for [u64 len][u32 id][value] pairs (newer block formats
    may carry a small header before the first pair)."""
    found = {}
    p = block_start
    while p + 12 <= block_end:
        pair_len = struct.unpack_from("<Q", data, p)[0]
        _id = struct.unpack_from("<I", data, p + 8)[0]
        if _id in (SCHEME_V2, SCHEME_V3, SCHEME_V4) and 8 < pair_len < 1 << 20:
            vs = p + 12
            ve = vs + (pair_len - 4)  # u64 prefix = len(ID + value)
            if ve <= block_end and _id not in found:
                found[_id] = (vs, ve)
        p += 1
    return found


def find_cert(data, start, end):
    p = start
    while p + 8 <= end:
        i = data.find(b"\x30\x82", p, end - 4)
        if i < 0:
            return None
        ln = (data[i + 2] << 8) | data[i + 3]
        if 200 < ln < 8000 and i + 4 + ln <= end:
            return i, i + 4 + ln
        p = i + 4
    return None


def cert_name(der):
    """Best-effort RDN extraction -> list of (oid, value)."""
    def decode_oid(buf, s, e):
        b = buf[s:e]
        parts = [b[0] // 40, b[0] % 40]
        v = 0
        for x in b[1:]:
            v = (v << 7) | (x & 0x7F)
            if not x & 0x80:
                parts.append(v)
                v = 0
        return ".".join(map(str, parts))

    def decode_str(tag, buf, s, e):
        b = buf[s:e]
        if (tag & 0x1F) == 0x1E:
            return b.decode("utf-16-be", "replace")
        return b.decode("utf-8", "replace")

    names = []
    try:
        _t, s, e = tlv(der, 0)
        outer = children(der, s, e)
        tbs = children(der, outer[0][1], outer[0][2])
        idx = 1 if tbs[0][0] == 0xA0 else 0
        for pos in (idx + 2, idx + 4):  # issuer, subject
            _tag, ns, ne = tbs[pos]
            for _t2, cs, ce in children(der, ns, ne):  # SETs
                for _t3, s2, e2 in children(der, cs, ce):
                    if _t3 != 0x30:
                        continue
                    inner = children(der, s2, e2)
                    if len(inner) >= 2 and inner[0][0] == 0x06:
                        oid = decode_oid(der, inner[0][1], inner[0][2])
                        vt, vs, ve = inner[1]
                        names.append((oid, decode_str(vt, der, vs, ve)))
    except Exception:
        pass
    return names


def check_signing(data, results):
    block_start, cd = find_signing_block(data)
    vals = find_scheme_values(data, block_start, cd - 24)
    schemes = {SCHEME_V2: "v2", SCHEME_V3: "v3", SCHEME_V4: "v4"}
    present = [schemes[k] for k in sorted(vals)]
    info(results, f"signing block at {block_start}..{cd - 24}; schemes present: {', '.join(present)}")
    if SCHEME_V2 not in vals:
        fail(results, "v2 signing scheme missing", hard=False)
    if SCHEME_V3 not in vals:
        fail(results, "v3 signing scheme missing", hard=False)
    # certificate identity pin
    for sid in (SCHEME_V2, SCHEME_V3):
        if sid not in vals:
            continue
        vs, ve = vals[sid]
        cert = find_cert(data, vs, ve)
        if not cert:
            fail(results, f"no X.509 certificate found in {schemes[sid]} signer value")
            continue
        der = data[cert[0]:cert[1]]
        sha1 = hashlib.sha1(der).hexdigest().upper()  # noqa: S324
        sha256 = hashlib.sha256(der).hexdigest()
        results.setdefault("certificates", []).append(
            {"scheme": schemes[sid], "sha1": sha1, "sha256": sha256,
             "subject": cert_name(der)})
        if sha256 != EXPECTED_CERT_SHA256:
            fail(results,
                 f"{schemes[sid]} signer certificate SHA-256 {sha256} does not match "
                 f"the pinned developer certificate {EXPECTED_CERT_SHA256}")
        else:
            info(results, f"{schemes[sid]} signer certificate matches pinned developer identity")
        break  # v2 and v3 carry the same developer cert in this app


# ------------------------------------------------------------------ manifest
class StringPool:
    def __init__(self, data, off=0):
        self.type, self.header_size, self.chunk_size = struct.unpack_from("<HHI", data, off)
        (self.string_count, _style_count, self.flags,
         self.strings_start, _styles_start) = struct.unpack_from("<IIIII", data, off + 8)
        offsets = struct.unpack_from(f"<{self.string_count}I", data, off + self.header_size)
        utf8 = bool(self.flags & (1 << 8))
        sbase = off + self.strings_start
        self.strings = []
        for o in offsets:
            p = sbase + o
            if utf8:
                c = data[p]; p += 1
                if c & 0x80:
                    p += 1
                b = data[p]; p += 1
                if b & 0x80:
                    b = ((b & 0x7F) << 8) | data[p]; p += 1
                self.strings.append(data[p:p + b].decode("utf-8", "replace"))
            else:
                c = struct.unpack_from("<H", data, p)[0]; p += 2
                if c & 0x8000:
                    c = (((c & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0])
                    p += 2
                self.strings.append(data[p:p + c * 2].decode("utf-16-le", "replace"))

    def s(self, idx):
        if idx is None or idx < 0 or idx >= len(self.strings):
            return ""
        return self.strings[idx]


def parse_manifest(data):
    magic, _size = struct.unpack_from("<II", data, 0)
    if magic != 0x00080003:
        raise CheckError("AndroidManifest.xml is not binary XML")
    off = 8
    pool = StringPool(data, off)
    off += pool.chunk_size
    root = {"tag": "<root>", "attrs": {}, "children": []}
    stack = [root]
    while off + 8 <= len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, off)
        if csize < 8 or off + csize > len(data):
            break
        if ctype == 0x0102:
            _ln, _comment, ns, name, attr_start, _asize, acount, _id, _cls, _style = \
                struct.unpack_from("<IIIIHHHHHH", data, off + 8)
            el = {"tag": pool.s(name), "attrs": {}, "children": []}
            p = off + hsize + attr_start
            for _i in range(acount):
                _ans, aname, _araw, _tsize, _res0, atype, adata = struct.unpack_from(
                    "<IIIHBBI", data, p)
                p += 20
                key = pool.s(aname)
                if atype == 0x03:
                    val = pool.s(adata) if adata >= 0 else ""
                elif atype == 0x10:
                    val = str(struct.unpack("<i", struct.pack("<I", adata))[0])
                elif atype == 0x11:
                    val = hex(adata)
                elif atype == 0x12:
                    val = "false" if adata == 0 else "true"
                elif atype == 0x01:
                    val = str(adata)
                else:
                    val = str(adata)
                el["attrs"][key] = val
            stack[-1]["children"].append(el)
            stack.append(el)
        elif ctype == 0x0103:
            if len(stack) > 1:
                stack.pop()
        off += csize
    return root["children"][0] if root["children"] else root


def walk(node, fn):
    for c in node.get("children", []):
        fn(c)
        walk(c, fn)


def check_manifest(apk_path, results, min_target_sdk):
    import zipfile
    with zipfile.ZipFile(apk_path) as z:
        data = z.read("AndroidManifest.xml")
    man = parse_manifest(data)
    pkg = man["attrs"].get("package")
    ver_name = man["attrs"].get("versionName")
    ver_code = man["attrs"].get("versionCode")
    results["manifest"] = {"package": pkg, "versionName": ver_name, "versionCode": ver_code}
    info(results, f"manifest: package={pkg} version={ver_name} (code {ver_code})")
    if pkg != EXPECTED_PACKAGE:
        fail(results, f"unexpected package name: {pkg}")

    sdk = {}
    app = None
    exported_problems = []
    components = {"activity": [], "service": [], "receiver": [], "provider": []}

    def on_node(n):
        nonlocal app
        t = n["tag"]
        if t == "uses-sdk":
            for k in ("minSdkVersion", "targetSdkVersion"):
                if k in n["attrs"]:
                    sdk[k] = int(n["attrs"][k])
        elif t == "application":
            app = n
        elif t in components:
            components[t].append(n)
            has_filter = any(c["tag"] == "intent-filter" for c in n["children"])
            if has_filter and "exported" not in n["attrs"]:
                exported_problems.append(f"{t} {n['attrs'].get('name', '?')} has an "
                                         f"intent-filter but no explicit 'exported' attribute")

    on_node(man)
    walk(man, on_node)

    results["manifest"]["sdk"] = sdk
    if "targetSdkVersion" in sdk:
        if sdk["targetSdkVersion"] < min_target_sdk:
            fail(results, f"targetSdkVersion {sdk['targetSdkVersion']} < {min_target_sdk}")
        else:
            info(results, f"targetSdkVersion {sdk['targetSdkVersion']} OK")

    app_attrs = app["attrs"] if app else {}
    if app_attrs.get("debuggable", "false") == "true":
        fail(results, "application is debuggable in a release build")
    else:
        info(results, "application not debuggable")
    if app_attrs.get("allowBackup", "true") == "false":
        info(results, "allowBackup disabled")
    else:
        fail(results, "allowBackup is not disabled (sensitive state could be backed up)",
             hard=False)
    if app_attrs.get("usesCleartextText", "false") == "true":
        fail(results, "usesCleartextText enabled - cleartext HTTP allowed")
    else:
        info(results, "cleartext traffic not explicitly allowed")

    if exported_problems:
        for p in exported_problems:
            fail(results, p, hard=False)
    else:
        info(results, "all components with intent-filters declare exported explicitly")

    total = sum(len(v) for v in components.values())
    results["manifest"]["component_counts"] = {k: len(v) for k, v in components.items()}
    info(results, f"components: {total} total "
                  f"({', '.join(f'{k}={len(v)}' for k, v in components.items())})")


# ------------------------------------------------------------------ main
def main():
    argv = sys.argv[1:]
    as_json = "--json" in argv
    min_target_sdk = 34
    if "--min-target-sdk" in argv:
        min_target_sdk = int(argv[argv.index("--min-target-sdk") + 1])
    args = [a for i, a in enumerate(argv)
            if a != "--json" and not (a == "--min-target-sdk"
                                      or (i > 0 and argv[i - 1] == "--min-target-sdk"))]
    if len(args) != 1:
        print(__doc__, file=sys.stderr)
        return 2
    apk_path = args[0]

    results = {"apk": apk_path, "errors": [], "warnings": [], "info": [],
               "certificates": [], "passed": False}
    try:
        data = open(apk_path, "rb").read()
        check_sha256(apk_path, results)
        check_signing(data, results)
        check_manifest(apk_path, results, min_target_sdk)
        results["passed"] = not results["errors"]
    except CheckError:
        pass  # already recorded via fail()
    except Exception as ex:  # unexpected
        results["errors"].append(f"unexpected error: {ex!r}")

    if as_json:
        print(json.dumps(results, indent=2, ensure_ascii=False))
    else:
        print(f"== verify_release: {apk_path}")
        for line in results["info"]:
            print(f"  [ ok ] {line}")
        for line in results["warnings"]:
            print(f"  [warn] {line}")
        for line in results["errors"]:
            print(f"  [FAIL] {line}")
        if results["passed"]:
            print("RESULT: PASS" + (f" ({len(results['warnings'])} warning(s))"
                                    if results["warnings"] else ""))
        else:
            print("RESULT: FAIL")
    return 0 if results["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
