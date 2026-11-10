"""Pure validation of declared Stage12 normal-bootstrap technical resources.

No filesystem, Android, transport, process, resolver or network operation exists
here. Accepted JSON authenticates neither its producer nor the truth of a claim.
The existing three-guard runner/host protocol is deliberately not changed.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
import uuid


AUTHORITY = "b330ba9cb1a6c6019c23027390e477b18f32a93a9c4872d7f690af840ce95987"
SOURCE_REVIEW = "b2c14974b032e41d79dcc8dc78b93ddc90091e05cb43887e6560aca73e65caaa"
CORE_CLASSES_SHA256 = "9c84bdd11f6dcb9881a3955ff7040f59dbc860a9f8aba0158fe9978f2d22d878"
QA = "com.blackatsystems.miguardia.qa"
TEST = QA + ".test"
RUNNER = "com.blackatsystems.miguardia.stage12.Stage12Runner"
PUBLIC = "com.blackatsystems.miguardia"
CERT = "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617"
AVD = "MiGuardia_Stage12_API36_20261023"
ART_ID = "ART_QA_USER0_CURRENT_PROFILE"
ART_PATH = "/data/misc/profiles/cur/0/" + QA + "/primary.prof"
ROLE = "NORMAL_BOOTSTRAP_RESOURCE_CANDIDATE"
EMOJI_STATES = {"DISCOVERY_NOT_ADMITTED", "ABSENT", "IDENTIFIED_ADMITTED"}
EMOJI_DISCOVERY = {"DISCOVERY_NOT_ADMITTED": "PENDING_CLASSIFICATION", "ABSENT": "EMPTY_CONFIRMED",
                   "IDENTIFIED_ADMITTED": "ONE_LOCAL_SYSTEM_PROVIDER_PINNED"}
FONT_ROOTS = ("/system/fonts/", "/product/fonts/", "/system_ext/fonts/", "/vendor/fonts/")
QUERY = "androidx.content.action.LOAD_EMOJI_FONT"
READBACK_BYTES = b"stage12 normal bootstrap synthetic readback\n"
CANARY_BYTES = b"stage12 synthetic canary"
EVENTS = ["RUNNER_ADMITTED", "OWN_ROOT_BOUND", "FILEPROVIDER_FIRST_USE",
          "FILEPROVIDER_INDEPENDENT_READ", "ESCAPES_REJECTED"]


class ValidationError(ValueError):
    """A rejected declaration, never partial approval."""


def require(condition, message):
    if not condition:
        raise ValidationError(message)


def exact(value, keys, label):
    require(type(value) is dict and set(value) == set(keys), label + ": exact keys required")


def integer(value, minimum=0, maximum=2**63 - 1):
    require(type(value) is int and minimum <= value <= maximum, "Invalid integer")
    return value


def sha(value):
    require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None, "Invalid SHA256")
    return value


def digest(value):
    return hashlib.sha256(value).hexdigest()


def same(value, expected):
    """JSON equality that never treats False/True as integer 0/1."""
    if type(value) is not type(expected):
        return False
    if type(value) is dict:
        return set(value) == set(expected) and all(same(value[k], expected[k]) for k in expected)
    if type(value) is list:
        return len(value) == len(expected) and all(same(v, e) for v, e in zip(value, expected))
    return value == expected


def strict_loads(data):
    require(type(data) is bytes and len(data) <= 4 * 1024 * 1024, "Bounded UTF8 JSON required")

    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "Duplicate JSON key")
            result[key] = value
        return result

    def invalid_constant(_):
        raise ValidationError("Nonfinite JSON number")

    def finite_float(value):
        result = float(value)
        require(math.isfinite(result), "Nonfinite JSON number")
        return result

    try:
        return json.loads(data.decode("utf-8"), object_pairs_hook=pairs,
                          parse_constant=invalid_constant, parse_float=finite_float)
    except ValidationError:
        raise
    except (UnicodeError, ValueError, RecursionError) as error:
        raise ValidationError("Invalid JSON") from error


def run_id(value):
    require(type(value) is str, "UUID required")
    try:
        require(str(uuid.UUID(value)) == value, "Canonical UUID required")
    except (ValueError, AttributeError) as error:
        raise ValidationError("Invalid UUID") from error
    return value


def _target(target, qa_sha):
    exact(target, {"package", "user_id", "uid", "certificate_sha256", "apk_sha256",
                   "uid_observation_sha256"}, "Installed QA identity")
    require(target["package"] == QA and type(target["user_id"]) is int and target["user_id"] == 0,
            "Only exact QA user0 is admitted")
    integer(target["uid"], 10000, 19999)
    require(target["certificate_sha256"] == CERT and target["apk_sha256"] == qa_sha,
            "Wrong installed QA certificate or APK")
    sha(target["uid_observation_sha256"])


def _art_resource(resource, target):
    exact(resource, {"id", "path", "package", "user_id", "uid"}, "ART resource")
    require(resource["id"] == ART_ID and resource["path"] == ART_PATH, "Wrong ART identity/path")
    require(resource["package"] == QA and type(resource["user_id"]) is int and resource["user_id"] == 0,
            "Wrong ART package/user")
    require(type(resource["uid"]) is int and resource["uid"] == target["uid"], "Wrong ART UID")


def _provider(provider):
    exact(provider, {"package", "class", "authority", "uid", "certificate_sha256", "system_app",
                     "identity_observation_sha256", "local_only_proof_sha256"}, "Emoji provider")
    package = provider["package"]
    require(type(package) is str and re.fullmatch(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+", package)
            and package not in {QA, TEST, PUBLIC}, "Concrete platform provider package required")
    require(type(provider["class"]) is str and provider["class"].startswith(package + ".")
            and re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+", provider["class"]),
            "Concrete provider class required")
    require(type(provider["authority"]) is str
            and re.fullmatch(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+", provider["authority"])
            and provider["authority"] not in {QA + ".fileprovider", QA + ".androidx-startup"},
            "Concrete platform authority required")
    integer(provider["uid"], 0, 19999)
    require(provider["system_app"] is True, "System provider flag required")
    for key in ("certificate_sha256", "identity_observation_sha256", "local_only_proof_sha256"):
        sha(provider[key])


def _font(font, authority):
    exact(font, {"uri", "local_path", "bytes", "sha256"}, "Local font")
    require(type(font["uri"]) is str and font["uri"].startswith("content://" + authority + "/"),
            "Wrong font authority or URI scheme")
    tail = font["uri"][len("content://" + authority + "/"):]
    require(all(re.fullmatch(r"[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*", part) for part in tail.split("/")),
            "Generic, escaped or ambiguous font URI")
    path = font["local_path"]
    require(type(path) is str and any(path.startswith(root) and
            re.fullmatch(r"[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*\.(?:ttf|otf|ttc)", path[len(root):])
            for root in FONT_ROOTS), "Only exact local platform font file is admitted")
    integer(font["bytes"], 1, 32 * 1024 * 1024)
    sha(font["sha256"])


def _emoji_expected(emoji):
    exact(emoji, {"state", "query_action", "system_only", "discovery_result", "discovery_sha256", "provider", "fonts"}, "Emoji expectation")
    require(type(emoji["state"]) is str and emoji["state"] in EMOJI_STATES and emoji["query_action"] == QUERY
            and emoji["system_only"] is True, "Wrong Emoji discovery scope/state")
    require(emoji["discovery_result"] == EMOJI_DISCOVERY[emoji["state"]], "Query errors are not verified Emoji absence")
    sha(emoji["discovery_sha256"])
    require(type(emoji["fonts"]) is list, "Font list required")
    if emoji["state"] != "IDENTIFIED_ADMITTED":
        require(emoji["provider"] is None and emoji["fonts"] == [], "Unadmitted/absent branch cannot read fonts")
    else:
        _provider(emoji["provider"])
        require(1 <= len(emoji["fonts"]) <= 8, "Concrete bounded local fonts required")
        for font in emoji["fonts"]:
            _font(font, emoji["provider"]["authority"])
        require(len({f["uri"] for f in emoji["fonts"]}) == len(emoji["fonts"])
                and len({f["local_path"] for f in emoji["fonts"]}) == len(emoji["fonts"]), "Duplicate font identity")


def validate_expected(expected):
    exact(expected, {"schema", "authority_sha256", "run_id", "round", "qa_sha256", "runner_sha256",
                     "candidate_role", "avd", "target", "runner", "root_id", "art_resource", "emoji"}, "Expected run")
    require(expected["schema"] == "stage12-normal-bootstrap-expected-v1" and expected["authority_sha256"] == AUTHORITY,
            "Wrong normal-bootstrap authority/schema")
    run_id(expected["run_id"])
    integer(expected["round"], 1, 2)
    sha(expected["qa_sha256"]); sha(expected["runner_sha256"])
    require(expected["candidate_role"] == ROLE and expected["root_id"] == expected["run_id"], "Wrong candidate/root")
    exact(expected["avd"], {"name", "api", "serial"}, "Dedicated AVD")
    require(expected["avd"]["name"] == AVD and type(expected["avd"]["api"]) is int
            and expected["avd"]["api"] == 36 and expected["avd"]["serial"] in ("emulator-5588", "emulator-5590"),
            "Wrong dedicated AVD")
    _target(expected["target"], expected["qa_sha256"])
    runner = expected["runner"]
    exact(runner, {"package", "class", "certificate_sha256", "apk_sha256", "identity_observation_sha256"}, "Runner identity")
    require(runner["package"] == TEST and runner["class"] == RUNNER and runner["certificate_sha256"] == CERT
            and runner["apk_sha256"] == expected["runner_sha256"], "Wrong runner package/class/certificate/APK")
    sha(runner["identity_observation_sha256"])
    _art_resource(expected["art_resource"], expected["target"])
    _emoji_expected(expected["emoji"])
    if expected["emoji"]["state"] == "IDENTIFIED_ADMITTED":
        require(expected["emoji"]["provider"]["uid"] != expected["target"]["uid"], "Emoji is not a QA-owned UID")


def _metadata(metadata, uid):
    exact(metadata, {"regular", "symlink", "uid", "bytes"}, "ART metadata")
    require(metadata["regular"] is True and metadata["symlink"] is False
            and type(metadata["uid"]) is int and metadata["uid"] == uid, "ART owner/type mismatch")
    integer(metadata["bytes"])


def _snapshot(snapshot, uid):
    require(type(snapshot) is dict and type(snapshot.get("status")) is str
            and snapshot["status"] in {"HASHED", "ABSENT", "UNREADABLE"}, "Wrong ART observation state")
    if snapshot["status"] == "ABSENT":
        exact(snapshot, {"status", "exists", "absence_basis"}, "Absent ART")
        require(snapshot["exists"] is False and snapshot["absence_basis"] == "ENOENT_CONFIRMED",
                "File.exists false cannot distinguish EACCES/EPERM from confirmed ENOENT")
    elif snapshot["status"] == "HASHED":
        exact(snapshot, {"status", "exists", "metadata", "sha256"}, "Hashed ART")
        require(snapshot["exists"] is True, "Hashed ART must exist")
        _metadata(snapshot["metadata"], uid); sha(snapshot["sha256"])
    else:
        exact(snapshot, {"status", "metadata", "reason"}, "Unreadable ART")
        require(snapshot["reason"] in ("EACCES", "EPERM"), "Explicit permission limit required")
        if snapshot["metadata"] is not None:
            _metadata(snapshot["metadata"], uid)


def _case(value, subcase):
    require(value["case_id"] == "S12-B03" and value["subcase_id"] == subcase, "Wrong technical case ID")


def validate_testimony(observed, expected):
    """Validate exact declared witnesses. The returned status is never runtime/business acceptance."""
    validate_expected(expected)
    exact(observed, {"schema", "authority_sha256", "run_id", "round", "qa_sha256", "runner_sha256",
                     "candidate_role", "avd", "target", "runner", "root_id", "providers", "art", "emoji", "fileprovider", "business"},
          "Normal-bootstrap testimony")
    require(observed["schema"] == "stage12-normal-bootstrap-testimony-v1", "Wrong testimony schema")
    for key in ("authority_sha256", "run_id", "round", "qa_sha256", "runner_sha256", "candidate_role", "avd", "target", "runner", "root_id"):
        require(same(observed[key], expected[key]), "Expected identity mismatch: " + key)
    exact(observed["providers"], {"fileprovider_enabled", "startup_enabled"}, "Normal providers")
    require(observed["providers"]["fileprovider_enabled"] is True and observed["providers"]["startup_enabled"] is True,
            "Disabled providers are intermediate guards only")
    art = observed["art"]
    exact(art, {"case_id", "subcase_id", "resource", "shared_across_rounds", "before", "after", "operations"}, "ART testimony")
    _case(art, "normal-art")
    require(same(art["resource"], expected["art_resource"]) and art["shared_across_rounds"] is True,
            "ART is the exact package resource shared across rounds")
    _art_resource(art["resource"], expected["target"])
    require(art["operations"] == ["READ_METADATA_OR_HASH_ONLY"], "No root/chmod/delete/restore admitted")
    for phase in ("before", "after"):
        _snapshot(art[phase], expected["target"]["uid"])
    emoji = observed["emoji"]
    exact(emoji, {"case_id", "subcase_id", "state", "discovery_result", "discovery_sha256", "provider", "reads", "font_branch_executed", "operations"}, "Emoji testimony")
    _case(emoji, "normal-emoji")
    plan = expected["emoji"]
    require(emoji["state"] == plan["state"] and emoji["discovery_result"] == plan["discovery_result"]
            and emoji["discovery_sha256"] == plan["discovery_sha256"]
            and same(emoji["provider"], plan["provider"]), "Unpinned Emoji identity/state")
    if plan["state"] == "IDENTIFIED_ADMITTED":
        require(same(emoji["reads"], plan["fonts"]) and emoji["font_branch_executed"] is True,
                "Only exact pinned local fonts may be read")
        require(emoji["operations"] == ["SYSTEM_PROVIDER_DISCOVERY", "EXACT_CONTENT_URI_READ", "EXACT_LOCAL_FONT_READ"],
                "Generic resolver/network/download not admitted")
    else:
        require(emoji["reads"] == [] and emoji["font_branch_executed"] is False
                and emoji["operations"] == ["SYSTEM_PROVIDER_DISCOVERY"], "Unadmitted branch must not execute font I/O")
    fp = observed["fileprovider"]
    exact(fp, {"case_id", "subcase_id", "root_id", "events", "provider_read", "independent_read", "escape_checks", "canary"}, "FileProvider testimony")
    _case(fp, "normal-fileprovider")
    require(fp["root_id"] == expected["run_id"], "Wrong fresh FileProvider root")
    require(type(fp["events"]) is list and len(fp["events"]) == len(EVENTS), "Exact first-use event chain required")
    previous = -1
    for event, name in zip(fp["events"], EVENTS):
        exact(event, {"event", "elapsed_ns", "process_fresh"}, "First-use event")
        require(event["event"] == name and event["process_fresh"] is True, "Fresh process/admission before first URI required")
        integer(event["elapsed_ns"])
        require(event["elapsed_ns"] > previous, "First-use event ordering invalid")
        previous = event["elapsed_ns"]
    basename = "stage12-" + expected["run_id"] + "-readback.bin"
    uri = "content://" + QA + ".fileprovider/report_artifacts/" + basename
    relative = "files/reports/artifacts/" + basename
    require(same(fp["provider_read"], {"uri": uri, "bytes": len(READBACK_BYTES), "sha256": digest(READBACK_BYTES)}),
            "Wrong FileProvider URI/fixture readback")
    require(same(fp["independent_read"], {"reader": "OWN_FILE_INPUT", "relative_path": relative,
            "bytes": len(READBACK_BYTES), "sha256": digest(READBACK_BYTES)}), "Independent owned-file readback required")
    authority = "content://" + QA + ".fileprovider/report_artifacts/"
    probes = [("TRAVERSAL_URI", authority + "..%2F..%2F..%2Fcanaries%2Ffile.canary"),
              ("OWN_SYMLINK_OUTSIDE_PROVIDER_ROOT", authority + "stage12-" + expected["run_id"] + "-escape.bin")]
    # Source ordering + exact native exception, not invented counters for private/static I/O.
    require(same(fp["escape_checks"], [{"probe": name, "uri": uri, "outcome": "CANONICAL_ESCAPE_REJECTED",
            "exception_class": "java.lang.SecurityException", "guard_message": "Resolved path jumped beyond configured root",
            "core_classes_sha256": CORE_CLASSES_SHA256}
            for name, uri in probes]), "Both exact escape URIs must reject before I/O")
    require(same(fp["canary"], {"relative_path": "canaries/file.canary", "before_sha256": digest(CANARY_BYTES),
            "after_sha256": digest(CANARY_BYTES)}), "Own canary must remain unchanged")
    business = observed["business"]
    exact(business, {"tables", "claims"}, "No business promotion")
    exact(business["tables"], {"status", "reason"}, "Tables explicitly unmeasured")
    require(business["tables"]["status"] == "NOT_MEASURED" and type(business["tables"]["reason"]) is str
            and bool(business["tables"]["reason"].strip()) and business["claims"] == [],
            "Technical testimony cannot promote Room/business/normal/compilation/R4 acceptance")
    limits = []
    if any(art[phase]["status"] == "UNREADABLE" for phase in ("before", "after")):
        limits.append("ART_UNREADABLE_NO_PERMISSION_ESCALATION")
    if plan["state"] == "DISCOVERY_NOT_ADMITTED":
        limits.append("EMOJI_BRANCH_NOT_ADMITTED")
    # An unreadable diagnostic change is not a measured resource-byte change.
    art_change = None if "ART_UNREADABLE_NO_PERMISSION_ESCALATION" in limits else not same(art["before"], art["after"])
    return {"status": "LOCAL_RESOURCE_TESTIMONIES_VALID_WITH_LIMITS" if limits else "LOCAL_RESOURCE_TESTIMONIES_VALID",
            "limits": limits, "runtime_verified": False, "business_evidence": False,
            "normal_bootstrap_proven": False, "round_accepted": False, "compilation_state_equal": False,
            "r4_timing_evidence": False, "art_shared_across_rounds": True,
            "art_observed_change": art_change, "art_observation_changed": not same(art["before"], art["after"]),
            "emoji_state": plan["state"]}
