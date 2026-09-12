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
def scan_bytes(data):
    return [{"rule": name, "matches": len(re.findall(pattern, data))}
            for name, pattern in RULES.items() if re.search(pattern, data)]
def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])
def audit(root, apks):
    results=[]; counts={"tree":0,"history_blobs":0,"apk_entries":0}
    for raw in git(root,"ls-files","-co","--exclude-standard","-z").split(b"\0"):
        if not raw: continue
        name=raw.decode("utf-8"); path=root/name
        if not path.is_file() or path.is_symlink(): continue
        counts["tree"]+=1
        for finding in scan_bytes(path.read_bytes()): results.append({"surface":"tree","path":name,**finding})
    objects=git(root,"rev-list","--objects","--all").splitlines()
    proc=subprocess.Popen(["git","-C",str(root),"cat-file","--batch"],stdin=subprocess.PIPE,stdout=subprocess.PIPE)
    try:
        for item in objects:
            oid=item.split(b" ",1)[0]; proc.stdin.write(oid+b"\n"); proc.stdin.flush()
            header=proc.stdout.readline().split()
            if len(header)!=3: raise ValueError("Invalid Git object header")
            size=int(header[2]); data=proc.stdout.read(size); proc.stdout.read(1)
            if header[1]!=b"blob": continue
            counts["history_blobs"]+=1
            for finding in scan_bytes(data): results.append({"surface":"history","object":oid.decode(),**finding})
    finally:
        proc.stdin.close(); proc.stdout.close(); proc.wait()
    for path in apks:
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                if entry.file_size>256*1024*1024: raise ValueError("Entry limit")
                counts["apk_entries"]+=1
                for finding in scan_bytes(archive.read(entry)): results.append({"surface":"apk","artifact":path.name,"entry":entry.filename,**finding})
    return {"counts":counts,"signals":results,"limit":"Pattern signals require private review; zero signals is not proof of absence. No matched value is retained."}
def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument("--root",type=pathlib.Path,default=pathlib.Path.cwd()); parser.add_argument("--apk",type=pathlib.Path,action="append",default=[]); parser.add_argument("--output",type=pathlib.Path,required=True); args=parser.parse_args()
    if args.output.exists(): raise ValueError("Evidence must be new")
    args.output.write_text(json.dumps(audit(args.root.resolve(),args.apk),indent=2),encoding="utf-8")
if __name__=="__main__": main()
