"""Stage12 dedicated-AVD preflight/protocol. No create, boot, daemon or adb CLI API.

The CLI reads an explicitly supplied host observation and local files only.
Specs are inert plans. The injected smart-socket probe has no default connector,
process launcher or retry/fallback; production transport remains a later gate.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path, PureWindowsPath
import re
import stat
import sys

from stage12_local import (ValidationError, canonical_uuid, digest, exact, integer,
                           read_bounded, require, sha, strict_json)
import json


NAME = "MiGuardia_Stage12_API36_20261023"
ROOT = "C:/Users/User/.cache/miguardia-stage12-avd/20261023"
SDK = "C:/Users/User/AppData/Local/Android/Sdk"
IMAGE = SDK + "/system-images/android-36/default/x86_64"
CONTRACT = "f76b15c6903beb52f0233f31086b7ec896ecd8d28174202c37e68a46bb930b35"
ADB_HASH = "b4a6b455702684652cccf7b46258b29e653538904359a58fd4931cf3ef286b3f"
EMULATOR_HASH = "653dfa9797ea524723c3eef98fd532776de9a056a1de2ef389dce782ab92037d"
STUB = str(Path(__file__).resolve().parents[2] /
           ".local-evidence/stage12/20261023-local2/avd/Stage12AdbCliRefused.exe").replace("\\", "/")
STUB_HASH = "998bdab32c547fc8b1e221eb5e9979b538068f16e14bce11f55e34c9c5330ad1"
SERVER_PORT = 5048
GIB = 1024**3


def canonical_windows(value: str) -> str:
    require(type(value) is str and "\\" not in value and "\x00" not in value and
            re.match(r"^[A-Z]:/", value) is not None, "Canonical drive path required")
    parts = value[3:].split("/")
    require(all(p not in {"", ".", ".."} and not p.endswith((".", " ")) and
                not any(ord(c) < 32 or c in ':*?"<>|' for c in p) for p in parts), "Unsafe Windows path")
    require(str(PureWindowsPath(value)).replace("\\", "/") == value, "Noncanonical path")
    return value


def no_reparse(path: Path) -> None:
    for item in (path, *path.parents):
        try:
            info = item.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(info.st_mode) and not
                getattr(info, "st_file_attributes", 0) & 0x400, "Symlink/reparse boundary refused")


def root_ownership(path: Path, session_id=None) -> dict:
    """Only inspect this destination/ancestors; never enumerate the global AVD home."""
    require(canonical_windows(str(path.absolute()).replace("\\", "/")) == ROOT,
            "Ownership inspection escaped the exact Stage12 root")
    no_reparse(path)
    if not path.exists():
        require(session_id is None, "Ownership cannot be asserted for an absent root")
        return {"state": "ABSENT", "mutation_performed": False}
    require(path.is_dir() and session_id is not None, "Existing root has no admitted ownership")
    owner_path = path / ".stage12-owner.json"
    no_reparse(owner_path)
    owner = strict_json(read_bounded(owner_path, 4096), 4096)
    exact(owner, ("schema", "name", "root", "session_id", "contract_sha256"))
    require(owner["schema"] == "stage12-avd-owner-v1" and owner["name"] == NAME and
            canonical_windows(owner["root"]) == ROOT and owner["contract_sha256"] == CONTRACT and
            canonical_uuid(owner["session_id"]) == canonical_uuid(session_id), "Foreign/mismatched root owner")
    return {"state": "OWNED", "session_id": session_id, "mutation_performed": False}


def inspect_existing_inputs() -> dict:
    """Read fixed existing SDK metadata/binaries; never run an SDK tool."""
    hashes = {}
    for label, relative, expected in (
            ("adb", "platform-tools/adb.exe", ADB_HASH),
            ("emulator", "emulator/emulator.exe", EMULATOR_HASH),
            ("avdmanager", "cmdline-tools/22.0/bin/avdmanager.bat", None)):
        target = Path(SDK) / relative
        no_reparse(target)
        value = digest(read_bounded(target, 64 * 1024 * 1024))
        require(expected is None or value == expected, "Existing tool bytes changed")
        hashes[label] = value
    image_root = Path(IMAGE)
    no_reparse(image_root)
    metadata = {}
    for name in ("source.properties", "build.prop", "package.xml"):
        target = image_root / name; no_reparse(target)
        metadata[name] = read_bounded(target, 1024 * 1024)
    properties = metadata["source.properties"].decode("utf-8").replace("\r\n", "\n")
    required = {"Pkg.Revision": "2", "AndroidVersion.ApiLevel": "36", "SystemImage.Abi": "x86_64",
                "SystemImage.TagId": "default"}
    for key, value in required.items():
        require(re.findall(r"^" + re.escape(key) + r"=(.+)$", properties, re.M) == [value],
                "Installed image property differs")
    require(re.findall(r"^ro.build.id=(.+)$", metadata["build.prop"].decode("utf-8").replace("\r\n", "\n"), re.M) ==
            ["BE2A.250530.026.D1"], "Installed image build differs")
    command_metadata = Path(SDK) / "cmdline-tools/22.0/source.properties"
    no_reparse(command_metadata)
    command_properties = read_bounded(command_metadata, 4096).decode("utf-8").replace("\r\n", "\n")
    require(re.findall(r"^Pkg.Revision=(.+)$", command_properties, re.M) == ["22.0"],
            "Installed command-line tools revision differs")
    stub = Path(STUB); no_reparse(stub)
    require(digest(read_bounded(stub, 64 * 1024)) == STUB_HASH, "Local inert refusal stub pin differs")
    sizes = {}
    for name in ("system.img", "vendor.img", "ramdisk.img", "kernel-ranchu"):
        target = image_root / name; no_reparse(target)
        require(target.is_file() and target.stat().st_size > 0, "Installed image component missing")
        sizes[name] = target.stat().st_size
    return {"tool_sha256": hashes, "metadata_sha256": digest(metadata["source.properties"]),
            "metadata_files_sha256": {k: digest(v) for k, v in metadata.items()},
            "image_component_bytes": sizes, "full_images_hashed": False, "stub_sha256": STUB_HASH}


def resource(config: dict) -> dict:
    exact(config, ("name", "root", "console_port", "serial", "server_host", "server_port",
                   "cores", "ram_mib", "userdata_gib", "resolution", "density"), ("alternate_declaration",))
    port = integer(config["console_port"], "console port", 65535)
    require(config["name"] == NAME and canonical_windows(config["root"]) == ROOT and
            port in {5588, 5590} and config["serial"] == f"emulator-{port}" and
            config["server_host"] == "127.0.0.1" and type(config["server_port"]) is int and
            config["server_port"] == SERVER_PORT, "Resource identity/ports differ")
    if port == 5590:
        declaration = exact(config.get("alternate_declaration"), ("recorded_before_start", "reason"))
        require(declaration["recorded_before_start"] is True and type(declaration["reason"]) is str and
                declaration["reason"].strip(), "Alternate port was not predeclared")
    else:
        require("alternate_declaration" not in config, "Unexpected alternate declaration")
    for key, target in {"cores": 2, "ram_mib": 2048, "userdata_gib": 6, "density": 320}.items():
        require(type(config[key]) is int and config[key] == target, "Resource sizing differs")
    require(config["resolution"] == "720x1600", "Resolution differs")
    return dict(config)


def server_spec(config: dict) -> dict:
    r = resource(config)
    return {"state": "PROPOSED_NOT_EXECUTABLE_HERE", "argv": [SDK + "/platform-tools/adb.exe", "-L",
            "tcp:127.0.0.1:5048", "--one-device", r["serial"], "server", "nodaemon"],
            "environment": {"ADB_USB": "0", "ADB_EMU": "0", "ADB_MDNS": "0",
                "ADB_MDNS_AUTO_CONNECT": "0", "ADB_REJECT_KILL_SERVER": "1",
                "ADB_SERVER_SOCKET": "tcp:127.0.0.1:5048", "ANDROID_ADB_SERVER_PORT": "5048",
                "ANDROID_ADB_SERVER_ADDRESS": "127.0.0.1", "ANDROID_SERIAL": r["serial"],
                "ANDROID_HOME": SDK, "ANDROID_SDK_ROOT": SDK,
                "ANDROID_USER_HOME": ROOT + "/user", "ANDROID_EMULATOR_HOME": ROOT + "/user",
                "ANDROID_AVD_HOME": ROOT + "/avd", "ADB_VENDOR_KEYS": ROOT + "/keys"},
            "windows_window_style": "Hidden", "shell": False,
            "environment_policy": "Future launcher must build a clean child environment from reviewed Windows OS variables plus these overrides; never inherit arbitrary ADB/ANDROID/proxy variables",
            "start_server_daemon_common_kill_server_or_devices_commands": "FORBIDDEN",
            "limits": "--one-device alone limits USB only; the three discovery initializers must stay disabled"}


def emulator_spec(config: dict) -> dict:
    r = resource(config)
    return {"state": "PROPOSED_REQUIRES_MAIN_REVIEW_AND_FIRST_BOOT_OBSERVATION",
            "argv": [SDK + "/emulator/emulator.exe", "-avd", NAME, "-ports",
                f'{r["console_port"]},{r["console_port"] + 1}', "-adb-path", STUB, "-no-window", "-no-snapshot-load",
                "-no-snapshot-save", "-no-snapstorage", "-no-audio", "-no-metrics",
                "-crash-report-mode", "disabled", "-cores", "2", "-memory", "2048"],
            "environment": server_spec(r)["environment"], "shell": False,
            "not_enabled": ["wipe-data", "read-only", "snapshot", "public APK", "USB passthrough",
                            "no-direct-adb", "global Android setting mutation"],
            "native_client_gap": "Stub refusal is locally proved; installed-emulator custom-path priority remains a falsifiable first-boot hypothesis requiring MAIN technical admission"}


def validate_owned_process(current: dict, launched: dict, role: str, config: dict) -> dict:
    """Compare a fresh OS identity to the previously sealed exact launch receipt.

    Call before and after each future socket operation and before stopping a PID.
    Names alone never authorize access, reuse, termination or a replacement PID.
    """
    require(role in {"server", "emulator"}, "Unknown process role")
    fields = ("pid", "parent_pid", "image", "argv", "created_utc", "binary_sha256", "session_id")
    exact(current, fields); exact(launched, fields)
    require(current == launched and integer(current["pid"], "owned PID") > 0 and
            integer(current["parent_pid"], "owned parent PID") > 0, "PID replaced or ownership differs")
    canonical_uuid(current["session_id"])
    spec = server_spec(config) if role == "server" else emulator_spec(config)
    expected_hash = ADB_HASH if role == "server" else EMULATOR_HASH
    require(canonical_windows(current["image"]) == spec["argv"][0] and current["argv"] == spec["argv"] and
            sha(current["binary_sha256"]) == expected_hash and type(current["created_utc"]) is str and
            re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T.+(?:Z|\+00:00)", current["created_utc"]),
            "Process executable/start time/arguments differ")
    return {"identity": "MATCHES_EXACT_OWN_LAUNCH_RECEIPT", "role": role, "pid": current["pid"],
            "permission_to_stop_or_operate": "NOT_GRANTED_BY_THIS_FORMAT_CHECK"}


def validate_snapshot(snapshot: dict, now=None) -> dict:
    exact(snapshot, ("schema", "utc", "contract_sha256", "resource", "root_state", "capacity",
                     "listeners", "processes", "image", "tools", "acceleration", "restriction_proof"))
    require(snapshot["schema"] == "stage12-avd-preflight-v1" and snapshot["contract_sha256"] == CONTRACT,
            "Wrong preflight/contract")
    r = resource(snapshot["resource"])
    require(snapshot["root_state"] in {"ABSENT", "OWNED"}, "Root absent/ownership proof required")
    try:
        observed = datetime.fromisoformat(snapshot["utc"].replace("Z", "+00:00"))
        require(observed.tzinfo is not None, "Observation timezone missing")
    except (ValueError, AttributeError) as error:
        raise ValidationError("Invalid observation timestamp") from error
    now = now or datetime.now(timezone.utc)
    age = (now - observed).total_seconds()
    require(0 <= age <= 60, "Observation must be refreshed immediately before the next resource step")
    capacity = exact(snapshot["capacity"], ("free_ram_bytes", "free_disk_bytes"))
    require(integer(capacity["free_ram_bytes"], "free RAM") >= 4 * GIB and
            integer(capacity["free_disk_bytes"], "free disk") >= 12 * GIB, "Host capacity insufficient")
    ports = {r["console_port"], r["console_port"] + 1, SERVER_PORT}
    require(type(snapshot["listeners"]) is list, "Listener observations missing")
    for listener in snapshot["listeners"]:
        exact(listener, ("port", "pid", "address"))
        require(integer(listener["port"], "listener port", 65535) not in ports, "Reserved port occupied")
        integer(listener["pid"], "listener PID")
    require(type(snapshot["processes"]) is list, "Process observations missing")
    for process in snapshot["processes"]:
        exact(process, ("pid", "parent_pid", "image", "command_line", "created_utc"))
        integer(process["pid"], "PID"); integer(process["parent_pid"], "parent PID")
        canonical_windows(process["image"])
        require(type(process["command_line"]) is str and type(process["created_utc"]) is str,
                "Incomplete process identity")
        require(not re.search(r"(?:^|/)(?:emulator|qemu-system[^/]*)\.exe$", process["image"], re.I) and
                NAME not in process["command_line"] and ROOT not in process["command_line"] and
                not re.search(r"(?:^|[^0-9])5048(?:[^0-9]|$)", process["command_line"]),
                "An emulator/session process already exists; ownership/reuse requires a separate reviewed receipt")
    image = exact(snapshot["image"], ("path", "api", "abi", "tag", "revision", "build", "metadata_sha256",
                                      "full_images_hashed"))
    require(canonical_windows(image["path"]) == IMAGE and type(image["api"]) is int and image["api"] == 36 and
            image["abi"] == "x86_64" and image["tag"] == "default" and type(image["revision"]) is int and
            image["revision"] == 2 and image["build"] == "BE2A.250530.026.D1" and
            image["full_images_hashed"] is False, "Installed image metadata differs")
    sha(image["metadata_sha256"])
    tools = exact(snapshot["tools"], ("adb", "emulator", "avdmanager"))
    for tool, expected_path, expected_hash, version in (
            ("adb", SDK + "/platform-tools/adb.exe", ADB_HASH, "37.0.1-15733141"),
            ("emulator", SDK + "/emulator/emulator.exe", EMULATOR_HASH, "37.2.12.0-16428233")):
        item = exact(tools[tool], ("path", "sha256", "version"))
        require(canonical_windows(item["path"]) == expected_path and sha(item["sha256"]) == expected_hash and
                item["version"] == version, "Pinned local tool differs")
    avd = exact(tools["avdmanager"], ("path", "metadata_revision", "sha256"))
    require(canonical_windows(avd["path"]) == SDK + "/cmdline-tools/22.0/bin/avdmanager.bat" and
            avd["metadata_revision"] == "22.0", "Installed avdmanager metadata differs")
    sha(avd["sha256"])
    acceleration = exact(snapshot["acceleration"], ("status", "detail"))
    require(acceleration["status"] in {"NOT_MEASURED", "AVAILABLE", "UNAVAILABLE"} and
            type(acceleration["detail"]) is str and acceleration["detail"].strip(), "Acceleration evidence ambiguous")
    proof = exact(snapshot["restriction_proof"], ("one_device_usb_only", "adb_usb_zero", "adb_emu_zero",
                    "adb_mdns_zero", "raw_client_no_autostart_mock", "native_emulator_no_autostart_proven",
                    "restricted_server_runtime_verified", "main_review"))
    require(all(type(value) is bool for value in proof.values()) and proof["one_device_usb_only"] and
            proof["adb_usb_zero"] and proof["adb_emu_zero"] and proof["adb_mdns_zero"] and
            proof["raw_client_no_autostart_mock"], "Restriction design missing or incorrectly typed")
    blocked = []
    if acceleration["status"] != "AVAILABLE": blocked.append("ACCELERATION_NOT_PROVEN_AVAILABLE")
    for key in ("native_emulator_no_autostart_proven", "restricted_server_runtime_verified", "main_review"):
        if not proof[key]: blocked.append(key.upper())
    return {"validation": "LOCAL_PREFLIGHT_OBSERVATION_VALID", "resource": r, "observation_age_seconds": age,
            "android_admitted": False, "creation_or_boot_performed": False, "blocking_gates": blocked,
            "next_step": "MAIN_REVIEWS_PROTOCOL_BEFORE_ANY_SERVER_CREATION_OR_BOOT",
            "server_spec": server_spec(r), "emulator_spec": emulator_spec(r)}


def validate_server_guard(guard: dict, config: dict, now=None) -> dict:
    exact(guard, ("observed_utc", "current", "launched", "environment", "listener"))
    identity = validate_owned_process(guard["current"], guard["launched"], "server", config)
    require(guard["environment"] == server_spec(config)["environment"], "Server discovery/environment restriction differs")
    require(guard["listener"] == {"address": "127.0.0.1", "port": SERVER_PORT, "pid": identity["pid"]},
            "Socket belongs to a different process/interface")
    try:
        observed = datetime.fromisoformat(guard["observed_utc"].replace("Z", "+00:00"))
        require(observed.tzinfo is not None, "Guard timezone missing")
        age = ((now or datetime.now(timezone.utc)) - observed).total_seconds()
    except (ValueError, AttributeError) as error:
        raise ValidationError("Invalid fresh server identity") from error
    require(0 <= age <= 2, "Server guard stale; refresh identity/port before and after each exchange")
    return identity


def request_frame(service: str, config: dict, server_guard=None, now=None) -> bytes:
    r = resource(config)
    scoped = {"host:emulator:" + str(r["console_port"] + 1), "host:devices-l"}
    require(service in {"host:version", "host:transport:" + r["serial"]} | scoped,
            "Unreviewed smart socket service/port/serial")
    if service in scoped:
        require(server_guard is not None, "Fresh owned server guard required for registration/inventory")
        validate_server_guard(server_guard, config, now)
    data = service.encode("ascii")
    return f"{len(data):04x}".encode("ascii") + data


def parse_owned_inventory(payload: bytes, config: dict, phase: str) -> dict:
    r = resource(config)
    require(phase in {"before", "after"} and type(payload) is bytes and len(payload) <= 8192,
            "Invalid bounded dedicated-server inventory")
    try:
        text = payload.decode("utf-8")
    except UnicodeError as error:
        raise ValidationError("Invalid inventory encoding") from error
    require(not any(ord(c) < 32 and c not in "\n\t" for c in text), "Invalid inventory control byte")
    lines = text.splitlines()
    if phase == "before":
        require(not lines, "Dedicated server is not empty before boot")
    else:
        # Never include an unexpected serial or raw server response in errors/receipts.
        require(len(lines) == 1 and len(lines[0].split()) >= 2 and
                lines[0].split()[:2] == [r["serial"], "device"], "Unexpected dedicated-server transport set/state")
    return {"phase": phase, "count": len(lines), "expected_only": True,
            "payload_sha256": digest(payload), "raw_unexpected_serials_disclosed": False}


def probe_no_autostart(connector, config: dict) -> str:
    """Injected future loopback connector only. This module has no real connector."""
    resource(config)
    connection = None
    try:
        connection = connector(("127.0.0.1", SERVER_PORT), 2.0)
        connection.sendall(request_frame("host:version", config))
        def receive(count):
            data = b""
            while len(data) < count:
                part = connection.recv(count - len(data))
                require(type(part) is bytes and 0 < len(part) <= count - len(data), "Partial/invalid smart socket response")
                data += part
            return data
        require(receive(4) == b"OKAY", "Dedicated server rejected request")
        require(receive(4) == b"0004" and receive(4) == b"0029", "Dedicated server ADB protocol differs")
        return "DEDICATED_SOCKET_PROTOCOL_41_ONLY_NOT_OWNERSHIP_OR_DEVICE_PROOF"
    except (OSError, TimeoutError) as error:
        raise ValidationError("Dedicated socket missing/failed; no launch, restart, common port or fallback") from error
    finally:
        if connection is not None:
            connection.close()


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        snapshot = strict_json(read_bounded(args.snapshot, 1024 * 1024))
        ownership = root_ownership(Path(ROOT))
        require(snapshot["root_state"] == ownership["state"] == "ABSENT", "CLI preparation admits only a fresh absent root")
        actual = inspect_existing_inputs()
        require(snapshot["image"]["metadata_sha256"] == actual["metadata_sha256"] and
                all(snapshot["tools"][key]["sha256"] == actual["tool_sha256"][key]
                    for key in ("adb", "emulator", "avdmanager")), "Observation/local files differ")
        result = validate_snapshot(snapshot)
        result["local_inputs"] = actual
        print(json.dumps(result, ensure_ascii=True, sort_keys=True))
        return 0
    except (ValidationError, OSError, UnicodeError, KeyError, TypeError) as error:
        print(json.dumps({"validation": "REJECTED", "error_type": type(error).__name__}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
