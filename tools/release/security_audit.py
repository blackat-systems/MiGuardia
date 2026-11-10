"""Read-only redacted pattern scan of tracked inputs, reachable Git blobs and APK entries.
No credentials, matched values, or context lines are emitted. Not a universal secret detector.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re, subprocess, zipfile
RULES = {
    "private-key-pem": rb"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----",
    "aws-access-key": rb"\bAKIA[0-9A-Z]{16}\b",
    "github-token": rb"\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{40,255})\b",
    "google-api-key": rb"\bAIza[A-Za-z0-9_-]{35}\b",
    "slack-token": rb"\bxox[baprs]-[A-Za-z0-9-]{20,255}\b",
}
def _literal_identifiers(values):
    if not isinstance(values, list) or not values:
        raise ValueError("Identifiers must be a nonempty JSON list of nonempty strings")
    needles=[]
    for value in values:
        if not isinstance(value, str) or not value.strip():
            raise ValueError("Identifiers must be a nonempty JSON list of nonempty strings")
        try:
            needle=value.encode("utf-8").lower()
        except UnicodeError:
            raise ValueError("Invalid identifier encoding") from None
        if needle not in needles: needles.append(needle)
    return tuple(needles)

def load_identifiers(path):
    """Read private literal input; errors never include its contents or context."""
    try:
        values=json.loads(path.read_text(encoding="utf-8"))
        _literal_identifiers(values)
    except (OSError, ValueError, UnicodeError):
        raise ValueError("Invalid private identifier file") from None
    return values

def _scan_bytes(data, needles):
    findings=[{"rule": name, "matches": len(re.findall(pattern, data))}
              for name, pattern in RULES.items() if re.search(pattern, data)]
    if needles:
        lowered=data.lower()  # bytes.lower folds ASCII only; literals have no word boundaries.
        count=sum(lowered.count(needle) for needle in needles)
        if count: findings.append({"rule":"explicit-identifier", "matches":count})
    return findings

def scan_bytes(data, identifiers=None):
    """Optional UTF-8 literals match with ASCII case folding, without regex syntax."""
    return _scan_bytes(data, () if identifiers is None else _literal_identifiers(identifiers))

def _location(value, needles):
    if not needles: return value
    raw=value.encode("utf-8")
    if any(needle in raw.lower() for needle in needles):
        return "sha256:"+hashlib.sha256(raw).hexdigest()
    return value
def git(root, *args, redact_stderr=False):
    return subprocess.check_output(["git", "-C", str(root), *args],
                                   stderr=subprocess.DEVNULL if redact_stderr else None)
def audit(root, apks, identifiers=None):
    needles=() if identifiers is None else _literal_identifiers(identifiers)
    results=[]; counts={"tree":0,"history_blobs":0,"apk_entries":0}
    for raw in git(root,"ls-files","-co","--exclude-standard","-z",redact_stderr=bool(needles)).split(b"\0"):
        if not raw: continue
        name=raw.decode("utf-8"); path=root/name
        if not path.is_file() or path.is_symlink(): continue
        counts["tree"]+=1
        for finding in _scan_bytes(path.read_bytes(), needles): results.append({"surface":"tree","path":_location(name,needles),**finding})
    objects=git(root,"rev-list","--objects","--all",redact_stderr=bool(needles)).splitlines()
    proc=subprocess.Popen(["git","-C",str(root),"cat-file","--batch"],stdin=subprocess.PIPE,stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL if needles else None)
    try:
        for item in objects:
            oid=item.split(b" ",1)[0]; proc.stdin.write(oid+b"\n"); proc.stdin.flush()
            header=proc.stdout.readline().split()
            if len(header)!=3: raise ValueError("Invalid Git object header")
            size=int(header[2]); data=proc.stdout.read(size); proc.stdout.read(1)
            if header[1]!=b"blob": continue
            counts["history_blobs"]+=1
            for finding in _scan_bytes(data, needles): results.append({"surface":"history","object":_location(oid.decode(),needles),**finding})
    finally:
        proc.stdin.close(); proc.stdout.close(); proc.wait()
    for path in apks:
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                if entry.file_size>256*1024*1024: raise ValueError("Entry limit")
                counts["apk_entries"]+=1
                for finding in _scan_bytes(archive.read(entry), needles): results.append({"surface":"apk","artifact":_location(path.name,needles),"entry":_location(entry.filename,needles),**finding})
    return {"counts":counts,"signals":results,"limit":"Pattern signals require private review; zero signals is not proof of absence. No matched value is retained."}
def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument("--root",type=pathlib.Path,default=pathlib.Path.cwd()); parser.add_argument("--apk",type=pathlib.Path,action="append",default=[]); parser.add_argument("--output",type=pathlib.Path,required=True); parser.add_argument("--identifiers-file",type=pathlib.Path,help="Private JSON list of explicit literal identifiers; values are never emitted"); args=parser.parse_args()
    try:
        identifiers=load_identifiers(args.identifiers_file) if args.identifiers_file else None
    except ValueError as error:
        parser.error(str(error))
    if args.output.exists(): raise ValueError("Evidence must be new")
    try:
        args.output.write_text(json.dumps(audit(args.root.resolve(),args.apk,identifiers),indent=2),encoding="utf-8")
    except Exception:
        if identifiers is None: raise
        parser.error("Audit failed with private identifier input")
if __name__=="__main__": main()
