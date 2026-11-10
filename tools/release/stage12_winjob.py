"""Stage12 owned Windows process lifetime, not a network or security sandbox.

Import is portable and performs no native call. Only the default OwnedJob
constructor or file_identity invokes Win32. Callers own exact binary/argv pins,
reviewed roots and endpoint policy. No SDK, shell, process-name lookup or fallback.
"""
from __future__ import annotations

import copy
import ctypes as C
import math
import ntpath
import os
from pathlib import Path
import re
import subprocess
import time

KILL_ON_JOB_CLOSE = 0x00002000
CREATE_SUSPENDED = 0x00000004
CREATE_NO_WINDOW = 0x08000000
CREATE_UNICODE_ENVIRONMENT = 0x00000400
EXTENDED_STARTUPINFO_PRESENT = 0x00080000
CREATION_FLAGS = (CREATE_SUSPENDED | CREATE_NO_WINDOW |
                  CREATE_UNICODE_ENVIRONMENT | EXTENDED_STARTUPINFO_PRESENT)
HANDLE_LIST = 0x00020002
WAIT_OBJECT_0, WAIT_TIMEOUT = 0, 258
DWORD, WORD, BOOL, HANDLE = C.c_uint32, C.c_uint16, C.c_int32, C.c_void_p
SIZE_T = C.c_size_t


class JobError(RuntimeError):
    def __init__(self, code: str, *, win32_error=None, receipt=None, created=None):
        super().__init__(code)
        self.code, self.win32_error, self.receipt = code, win32_error, receipt
        self._created = created  # original native capability, never serialized as identity


def require(value, code):
    if not value:
        raise JobError(code)


def _windows_path(value):
    value = os.fspath(value)
    require(type(value) is str and 0 < len(value) < 32768 and
            not any(ord(c) < 32 for c in value), "INVALID_LOCAL_PATH")
    value = value.replace("/", "\\")
    require(re.match(r"^[A-Za-z]:\\", value) and ":" not in value[2:] and
            not value.startswith("\\\\"), "ABSOLUTE_LOCAL_DOS_PATH_REQUIRED")
    for part in value[3:].split("\\"):
        require(part and part not in (".", "..") and not part.endswith((" ", "."))
                and not any(c in part for c in '<>"|?*'), "AMBIGUOUS_LOCAL_PATH")
        require(not re.fullmatch(r"CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]",
                                 part.split(".", 1)[0], re.I), "DOS_DEVICE_PATH_REFUSED")
    return value


def _timeout(value):
    require(type(value) in (int, float) and math.isfinite(value) and
            0 <= value <= 3600, "FINITE_BOUNDED_TIMEOUT_REQUIRED")
    return float(value)


def _launch_spec(argv, environment, cwd, stdout, stderr):
    require(type(argv) is list and argv and all(type(x) is str and
            "\0" not in x for x in argv), "ARGV_LIST_REQUIRED")
    executable = _windows_path(argv[0])
    require(executable.lower().endswith(".exe"), "EXPLICIT_EXE_REQUIRED")
    require(type(environment) is dict and environment, "EXPLICIT_ENVIRONMENT_REQUIRED")
    folded = set()
    for key, value in environment.items():
        require(type(key) is str and key and "=" not in key and "\0" not in key and
                type(value) is str and "\0" not in value, "INVALID_ENVIRONMENT")
        require(key.casefold() not in folded, "DUPLICATE_ENVIRONMENT_KEY")
        folded.add(key.casefold())
    paths = tuple(_windows_path(p) for p in (cwd, stdout, stderr))
    require(ntpath.normcase(paths[1]) != ntpath.normcase(paths[2]), "DISTINCT_LOG_FILES_REQUIRED")
    command = subprocess.list2cmdline(argv)
    require(len(command) < 32767, "COMMAND_LINE_TOO_LONG")
    block = "\0".join(k + "=" + environment[k]
                      for k in sorted(environment, key=str.casefold)) + "\0\0"
    return executable, command, block, paths


class FILETIME(C.Structure):
    _fields_ = [("low", DWORD), ("high", DWORD)]


class SECURITY_ATTRIBUTES(C.Structure):
    _fields_ = [("length", DWORD), ("descriptor", C.c_void_p), ("inherit", BOOL)]


class STARTUPINFO(C.Structure):
    _fields_ = [("cb", DWORD), ("reserved", C.c_wchar_p),
        ("desktop", C.c_wchar_p), ("title", C.c_wchar_p),
        ("x", DWORD), ("y", DWORD), ("xsize", DWORD), ("ysize", DWORD),
        ("xchars", DWORD), ("ychars", DWORD), ("fill", DWORD), ("flags", DWORD),
        ("show", WORD), ("reserved_count", WORD), ("reserved_bytes", C.c_void_p),
        ("stdin", HANDLE), ("stdout", HANDLE), ("stderr", HANDLE)]


class STARTUPINFOEX(C.Structure):
    _fields_ = [("startup", STARTUPINFO), ("attributes", C.c_void_p)]


class PROCESS_INFORMATION(C.Structure):
    _fields_ = [("process", HANDLE), ("thread", HANDLE), ("pid", DWORD), ("tid", DWORD)]


class BASIC_LIMITS(C.Structure):
    _fields_ = [("process_time", C.c_int64), ("job_time", C.c_int64),
        ("flags", DWORD), ("min_working_set", SIZE_T), ("max_working_set", SIZE_T),
        ("active_limit", DWORD), ("affinity", SIZE_T), ("priority", DWORD), ("scheduling", DWORD)]


class IO_COUNTERS(C.Structure):
    _fields_ = [(name, C.c_uint64) for name in
               ("reads", "writes", "other", "read_bytes", "write_bytes", "other_bytes")]


class EXTENDED_LIMITS(C.Structure):
    _fields_ = [("basic", BASIC_LIMITS), ("io", IO_COUNTERS),
        ("process_memory", SIZE_T), ("job_memory", SIZE_T),
        ("peak_process_memory", SIZE_T), ("peak_job_memory", SIZE_T)]


class FILE_INFO(C.Structure):
    _fields_ = [("attributes", DWORD), ("created", FILETIME), ("accessed", FILETIME),
        ("written", FILETIME), ("volume", DWORD), ("size_high", DWORD),
        ("size_low", DWORD), ("links", DWORD), ("index_high", DWORD), ("index_low", DWORD)]


def _no_reparse(path, *, missing_leaf=False):
    # Caller has already reviewed its roots. Refuse links again at the boundary.
    path = Path(path)
    for item in reversed((path, *path.parents)):
        try:
            info = item.lstat()
        except FileNotFoundError:
            require(missing_leaf and item == path, "PATH_PARENT_MISSING")
            continue
        require(not item.is_symlink() and not getattr(info, "st_file_attributes", 0) & 0x400,
                "REPARSE_PATH_REFUSED")


class _NativeBackend:
    """Win32 adapter. kernel injection is exclusively for pure tests."""
    def __init__(self, kernel=None, last_error=None):
        if kernel is None:
            require(os.name == "nt", "WINDOWS_REQUIRED")
            kernel = C.WinDLL("kernel32", use_last_error=True)
        self.k = kernel
        self.last_error = last_error or getattr(C, "get_last_error", lambda: 0)
        self._stdio_handles = set()
        definitions = {
            "CreateJobObjectW": ([C.c_void_p, C.c_wchar_p], HANDLE),
            "SetInformationJobObject": ([HANDLE, C.c_int, C.c_void_p, DWORD], BOOL),
            "QueryInformationJobObject": ([HANDLE, C.c_int, C.c_void_p, DWORD, C.POINTER(DWORD)], BOOL),
            "AssignProcessToJobObject": ([HANDLE, HANDLE], BOOL),
            "IsProcessInJob": ([HANDLE, HANDLE, C.POINTER(BOOL)], BOOL),
            "TerminateJobObject": ([HANDLE, C.c_uint], BOOL),
            "CloseHandle": ([HANDLE], BOOL),
            "CreateFileW": ([C.c_wchar_p, DWORD, DWORD, C.c_void_p, DWORD, DWORD, HANDLE], HANDLE),
            "InitializeProcThreadAttributeList": ([C.c_void_p, DWORD, DWORD, C.POINTER(SIZE_T)], BOOL),
            "UpdateProcThreadAttribute": ([C.c_void_p, DWORD, SIZE_T, C.c_void_p, SIZE_T, C.c_void_p, C.c_void_p], BOOL),
            "DeleteProcThreadAttributeList": ([C.c_void_p], None),
            "CreateProcessW": ([C.c_wchar_p, C.c_wchar_p, C.c_void_p, C.c_void_p,
                BOOL, DWORD, C.c_void_p, C.c_wchar_p, C.c_void_p, C.POINTER(PROCESS_INFORMATION)], BOOL),
            "ResumeThread": ([HANDLE], DWORD),
            "TerminateProcess": ([HANDLE, C.c_uint], BOOL),
            "WaitForSingleObject": ([HANDLE, DWORD], DWORD),
            "GetExitCodeProcess": ([HANDLE, C.POINTER(DWORD)], BOOL),
            "GetProcessId": ([HANDLE], DWORD),
            "GetProcessTimes": ([HANDLE] + [C.POINTER(FILETIME)] * 4, BOOL),
            "QueryFullProcessImageNameW": ([HANDLE, DWORD, C.c_wchar_p, C.POINTER(DWORD)], BOOL),
            "OpenProcess": ([DWORD, BOOL, DWORD], HANDLE),
            "GetFileInformationByHandle": ([HANDLE, C.POINTER(FILE_INFO)], BOOL),
            "GetFinalPathNameByHandleW": ([HANDLE, C.c_wchar_p, DWORD, DWORD], DWORD),
        }
        for name, (args, result) in definitions.items():
            fn = getattr(self.k, name)
            fn.argtypes, fn.restype = args, result

    def checked(self, value, operation):
        if not value:
            raise JobError("WIN32_" + operation, win32_error=self.last_error())
        return value

    def close_handle(self, handle):
        self.checked(self.k.CloseHandle(handle), "CloseHandle")
        self._stdio_handles.discard(handle)

    def close_resources(self):
        for handle in list(self._stdio_handles):
            self.close_handle(handle)

    def create_job(self, flags):
        require(flags == KILL_ON_JOB_CLOSE, "EXACT_JOB_LIMITS_REQUIRED")
        handle = self.checked(self.k.CreateJobObjectW(None, None), "CreateJobObjectW")
        try:
            info = EXTENDED_LIMITS()
            info.basic.flags = flags  # no BREAKAWAY_OK/SILENT_BREAKAWAY_OK
            self.checked(self.k.SetInformationJobObject(handle, 9, C.byref(info), C.sizeof(info)),
                         "SetInformationJobObject")
            return handle
        except BaseException:
            self.close_handle(handle)
            raise

    def create_suspended(self, argv, environment, cwd, stdout, stderr, flags):
        require(flags == CREATION_FLAGS, "EXACT_CREATION_FLAGS_REQUIRED")
        exe, command, block, paths = _launch_spec(argv, environment, cwd, stdout, stderr)
        _no_reparse(exe)
        _no_reparse(paths[0])
        _no_reparse(paths[1], missing_leaf=True)
        _no_reparse(paths[2], missing_leaf=True)
        sa = SECURITY_ATTRIBUTES(C.sizeof(SECURITY_ATTRIBUTES), None, True)
        handles = []
        attributes = None
        created = None
        try:
            for path, access, share, disposition in (
                    ("NUL", 0x80000000, 3, 3), (paths[1], 0x40000000, 1, 1),
                    (paths[2], 0x40000000, 1, 1)):
                h = self.k.CreateFileW(path, access, share, C.byref(sa), disposition, 0x80, None)
                require(h not in (None, 0, C.c_void_p(-1).value), "STDIO_CREATE_NEW_FAILED")
                handles.append(h)
                self._stdio_handles.add(h)
            size = SIZE_T()
            self.k.InitializeProcThreadAttributeList(None, 1, 0, C.byref(size))
            require(size.value > 0, "ATTRIBUTE_LIST_SIZE_MISSING")
            buffer = C.create_string_buffer(size.value)
            self.checked(self.k.InitializeProcThreadAttributeList(buffer, 1, 0, C.byref(size)),
                         "InitializeProcThreadAttributeList")
            attributes = buffer
            inherited = (HANDLE * 3)(*handles)
            self.checked(self.k.UpdateProcThreadAttribute(buffer, 0, HANDLE_LIST, inherited,
                         C.sizeof(inherited), None, None), "UpdateProcThreadAttribute")
            startup = STARTUPINFOEX()
            startup.startup.cb = C.sizeof(startup)
            startup.startup.flags = 0x100 | 1  # USESTDHANDLES, USESHOWWINDOW; show=0
            startup.startup.stdin, startup.startup.stdout, startup.startup.stderr = handles
            startup.attributes = C.cast(buffer, C.c_void_p)
            pi = PROCESS_INFORMATION()
            cmd_buffer, env_buffer = C.create_unicode_buffer(command), C.create_unicode_buffer(block)
            self.checked(self.k.CreateProcessW(exe, cmd_buffer, None, None, True, flags,
                         C.cast(env_buffer, C.c_void_p), paths[0], C.byref(startup), C.byref(pi)),
                         "CreateProcessW")
            created = (pi.process, pi.thread, int(pi.pid))
            return created
        finally:
            cleanup_errors = []
            if attributes is not None:
                try:
                    self.k.DeleteProcThreadAttributeList(attributes)
                except BaseException:
                    cleanup_errors.append("ATTRIBUTE_LIST_DELETE_FAILED")
            # Only the child's selected inherited handles remain after this point.
            for handle in handles:
                try:
                    self.close_handle(handle)
                except BaseException:
                    cleanup_errors.append("STDIO_HANDLE_CLOSE_FAILED")
            if cleanup_errors:
                # Transfer the original still-suspended capability to OwnedJob's
                # failure path even when post-creation resource cleanup raises.
                raise JobError("CREATE_PROCESS_RESOURCE_CLEANUP_FAILED",
                               receipt={"errors": cleanup_errors}, created=created)

    def assign(self, job, process):
        self.checked(self.k.AssignProcessToJobObject(job, process), "AssignProcessToJobObject")

    def in_job(self, process, job):
        inside = BOOL()
        self.checked(self.k.IsProcessInJob(process, job, C.byref(inside)), "IsProcessInJob")
        return bool(inside.value)

    def resume(self, thread):
        previous = self.k.ResumeThread(thread)
        require(previous == 1, "PRIMARY_THREAD_RESUME_NOT_EXACTLY_ONE")

    def poll(self, process):
        status = self.k.WaitForSingleObject(process, 0)
        if status == WAIT_TIMEOUT:
            return None
        require(status == WAIT_OBJECT_0, "PROCESS_WAIT_FAILED")
        code = DWORD()
        self.checked(self.k.GetExitCodeProcess(process, C.byref(code)), "GetExitCodeProcess")
        return int(code.value)  # even259 is a real exit when the handle is signalled

    def wait(self, process, timeout):
        status = self.k.WaitForSingleObject(process, math.ceil(_timeout(timeout) * 1000))
        if status == WAIT_TIMEOUT:
            raise TimeoutError("OWN_PROCESS_TIMEOUT")
        require(status == WAIT_OBJECT_0, "PROCESS_WAIT_FAILED")
        code = self.poll(process)
        require(type(code) is int, "OWN_EXIT_CODE_UNCONFIRMED")
        return code

    def identity(self, process):
        pid = int(self.checked(self.k.GetProcessId(process), "GetProcessId"))
        created, exited, kernel, user = (FILETIME() for _ in range(4))
        self.checked(self.k.GetProcessTimes(process, C.byref(created), C.byref(exited),
                     C.byref(kernel), C.byref(user)), "GetProcessTimes")
        birth = (created.high << 32) | created.low
        running = self.poll(process) is None
        image = None
        image_failure = None
        if running:
            size = DWORD(32768)
            text = C.create_unicode_buffer(size.value)
            queried = self.k.QueryFullProcessImageNameW(process, 0, text, C.byref(size))
            if queried:
                image = text.value  # raw OS spelling; caller proves exact file equivalence
            else:
                # Capture the original API error before poll() or any other Win32
                # call can replace the calling thread's last-error value.
                native_error = int(self.last_error())
                image_failure = {"pid": pid, "creation_filetime": birth,
                    "running_before": running, "running_after": None,
                    "requested_size": len(text), "queried_size": int(size.value),
                    "win32_error": native_error, "image": None,
                    "raw_status": "IMAGE_QUERY_FAILED_RUNNING_STATE_NOT_RECHECKED"}
                try:
                    running = self.poll(process) is None
                except BaseException as error:
                    image_failure["raw_status"] = "IMAGE_QUERY_FAILED_RUNNING_RECHECK_FAILED"
                    image_failure["recheck_error"] = (
                        error.code if isinstance(error, JobError) else type(error).__name__)
                    raise JobError("WIN32_QueryFullProcessImageNameW",
                                   win32_error=native_error, receipt=image_failure) from error
                image_failure["running_after"] = running
                if running:
                    image_failure["raw_status"] = "LIVE_MEMBER_IMAGE_UNIDENTIFIABLE"
                    raise JobError("WIN32_QueryFullProcessImageNameW",
                                   win32_error=native_error, receipt=image_failure)
                image_failure["raw_status"] = "PROCESS_ENDED_DURING_IMAGE_QUERY"
        identity = {"pid": pid, "parent_pid": None, "parent_pid_source": "NOT_MEASURED",
                    "creation_filetime": birth, "image": image, "running": running}
        if image_failure is not None:
            identity["image_query_failure"] = image_failure
        return identity

    def job_pids(self, job):
        for count in (16, 64, 256, 1024, 4096):
            buffer = C.create_string_buffer(8 + count * C.sizeof(SIZE_T))
            returned = DWORD()
            ok = self.k.QueryInformationJobObject(job, 3, buffer, len(buffer), C.byref(returned))
            if not ok:
                if self.last_error() == 234:  # ERROR_MORE_DATA: retry a bounded larger buffer
                    continue
                self.checked(ok, "QueryInformationJobObject")
            assigned, listed = (DWORD * 2).from_buffer(buffer)
            require(listed <= count and assigned == listed, "JOB_PID_LIST_PARTIAL")
            ids = (SIZE_T * listed).from_buffer(buffer, 8)
            pids = [int(pid) for pid in ids]
            require(all(pid > 0 for pid in pids) and len(pids) == len(set(pids)), "JOB_PID_LIST_INVALID")
            return pids
        raise JobError("JOB_PID_LIST_CAP_EXCEEDED")

    def open_member(self, pid):
        # Called only for a PID just enumerated from this retained job handle.
        return self.checked(self.k.OpenProcess(0x1000 | 0x100000, False, pid), "OpenProcessJobMember")

    def terminate_process(self, process):
        self.checked(self.k.TerminateProcess(process, 113), "TerminateOwnSuspendedProcess")

    def terminate_job(self, job):
        self.checked(self.k.TerminateJobObject(job, 113), "TerminateOwnJobObject")

    def wait_empty(self, job, timeout):
        deadline = time.monotonic() + _timeout(timeout)
        while self.job_pids(job):
            if time.monotonic() >= deadline:
                return False
            time.sleep(0.01)
        return True

    def file_identity(self, path):
        path = _windows_path(path)
        _no_reparse(path)
        handle = self.k.CreateFileW(path, 0x80, 7, None, 3, 0x00200000, None)
        require(handle not in (None, 0, C.c_void_p(-1).value), "FILE_METADATA_OPEN_FAILED")
        try:
            info = FILE_INFO()
            self.checked(self.k.GetFileInformationByHandle(handle, C.byref(info)), "GetFileInformationByHandle")
            require(not info.attributes & (0x400 | 0x10), "FILE_NOT_REGULAR_OR_REPARSE")
            text = C.create_unicode_buffer(32768)
            length = self.checked(self.k.GetFinalPathNameByHandleW(handle, text, len(text), 0),
                                  "GetFinalPathNameByHandleW")
            require(length < len(text), "FINAL_PATH_BUFFER_TOO_SMALL")
            return {"final_path": text.value, "volume_serial": int(info.volume),
                    "file_index": (info.index_high << 32) | info.index_low,
                    "bytes": (info.size_high << 32) | info.size_low,
                    "attributes": int(info.attributes), "links": int(info.links),
                    "last_write_filetime": (info.written.high << 32) | info.written.low}
        finally:
            self.close_handle(handle)


class NativeImageBackend(_NativeBackend):
    """Explicit native-format policy; never a fallback from the Win32 backend.

    Native image strings are observations only. No observed NT path is opened,
    converted to DOS, made relative or accepted as approved-file identity here.
    """
    def __init__(self, kernel=None, last_error=None):
        super().__init__(kernel=kernel, last_error=last_error)
        function = self.k.K32GetProcessImageFileNameW
        function.argtypes = [HANDLE, C.c_wchar_p, DWORD]
        function.restype = DWORD

    def identity(self, process):
        pid = int(self.checked(self.k.GetProcessId(process), "GetProcessId"))
        created, exited, kernel, user = (FILETIME() for _ in range(4))
        self.checked(self.k.GetProcessTimes(process, C.byref(created), C.byref(exited),
                     C.byref(kernel), C.byref(user)), "GetProcessTimes")
        birth = (created.high << 32) | created.low
        running = self.poll(process) is None
        identity = {"pid": pid, "parent_pid": None, "parent_pid_source": "NOT_MEASURED",
                    "creation_filetime": birth, "running": running, "image": None,
                    "image_format": "NATIVE_SYSTEM", "native_queries": [],
                    "image_source": "TERMINAL_NO_CURRENT_IMAGE"}
        if not running:
            return identity
        observations = identity["native_queries"]
        size = DWORD(32768)
        text = C.create_unicode_buffer(size.value)
        success = bool(self.k.QueryFullProcessImageNameW(process, 1, text, C.byref(size)))
        error = 0 if success else int(self.last_error())  # immediately, before another API
        observations.append({"api": "QueryFullProcessImageNameW", "flags": 1,
            "success": success, "win32_error": error, "characters": int(size.value) if success else 0,
            "raw_image": text.value if success else None})
        text = C.create_unicode_buffer(32768)
        characters = int(self.k.K32GetProcessImageFileNameW(process, text, len(text)))
        error = 0 if characters else int(self.last_error())  # same original process handle
        observations.append({"api": "K32GetProcessImageFileNameW", "flags": None,
            "success": characters > 0, "win32_error": error, "characters": characters,
            "raw_image": text.value if characters else None})
        partial = {"pid": pid, "creation_filetime": birth, "running_before": True,
                   "running_after": None, "image": None, "image_format": "NATIVE_SYSTEM",
                   "native_queries": copy.deepcopy(observations)}
        first_error = next((item["win32_error"] for item in observations if not item["success"]), 0)
        try:
            running = self.poll(process) is None
        except BaseException as error:
            partial["raw_status"] = "NATIVE_IMAGE_RUNNING_RECHECK_FAILED"
            partial["recheck_error"] = error.code if isinstance(error, JobError) else type(error).__name__
            raise JobError("NATIVE_IMAGE_IDENTITY_NOT_PROVEN", win32_error=first_error,
                           receipt=partial) from error
        partial["running_after"] = identity["running"] = running
        valid = all(item["success"] and item["raw_image"] and
                    0 < item["characters"] < 32768 and
                    item["characters"] == len(item["raw_image"]) for item in observations)
        valid = valid and observations[0]["raw_image"] == observations[1]["raw_image"]
        if not valid:
            partial["raw_status"] = ("LIVE_NATIVE_IMAGE_UNIDENTIFIABLE" if running else
                                     "PROCESS_ENDED_DURING_NATIVE_IMAGE_QUERY")
            if running:
                raise JobError("NATIVE_IMAGE_IDENTITY_NOT_PROVEN", win32_error=first_error,
                               receipt=partial)
            identity["image_query_failure"] = partial
            return identity
        if running:
            identity["image"] = observations[0]["raw_image"]
            identity["image_source"] = "CURRENT_NATIVE_OBSERVATION"
        return identity

    def native_file_identity(self, approved_local_DOS_path):
        """Measure NT final name only from a caller-approved local DOS file handle."""
        path = _windows_path(approved_local_DOS_path)
        _no_reparse(path)
        handle = self.k.CreateFileW(path, 0x80, 7, None, 3, 0x00200000, None)
        require(handle not in (None, 0, C.c_void_p(-1).value), "FILE_METADATA_OPEN_FAILED")
        try:
            def information():
                info = FILE_INFO()
                self.checked(self.k.GetFileInformationByHandle(handle, C.byref(info)),
                             "GetFileInformationByHandle")
                require(not info.attributes & (0x400 | 0x10), "FILE_NOT_REGULAR_OR_REPARSE")
                return {"volume_serial": int(info.volume),
                    "file_index": (info.index_high << 32) | info.index_low,
                    "bytes": (info.size_high << 32) | info.size_low,
                    "attributes": int(info.attributes), "links": int(info.links),
                    "last_write_filetime": (info.written.high << 32) | info.written.low}

            measured = information()

            def final_name(flags):
                text = C.create_unicode_buffer(32768)
                length = int(self.k.GetFinalPathNameByHandleW(handle, text, len(text), flags))
                error = 0 if length else int(self.last_error())
                partial = {**measured, "flags": flags, "characters": length,
                           "win32_error": error, "raw_status": "APPROVED_DOS_FINAL_NAME_NOT_PROVEN"}
                if not length:
                    raise JobError("WIN32_GetFinalPathNameByHandleW", win32_error=error, receipt=partial)
                require(0 < length < len(text) and length == len(text.value), "FINAL_PATH_BUFFER_INVALID")
                return text.value, length

            final_path, _ = final_name(0)
            native_final_path, native_length = final_name(2)  # documented VOLUME_NAME_NT
            require(native_final_path.startswith("\\Device\\"), "NATIVE_FINAL_NAME_NOT_DEVICE_FORM")
            require(information() == measured, "APPROVED_DOS_METADATA_CHANGED_DURING_NATIVE_QUERY")
            return {**measured, "final_path": final_path,
                    "native_final_path": native_final_path, "native_final_path_flags": 2,
                    "native_final_path_length": native_length, "metadata_stable": True,
                    "proof_kind": "NO_OBSERVED_NATIVE_PATH_OPEN"}
        finally:
            self.close_handle(handle)


class OwnedChild:
    def __init__(self, job, process, pid, initial):
        self._job, self._process, self.pid = job, process, pid
        self._initial, self._last = copy.deepcopy(initial), copy.deepcopy(initial)
        self._code, self._closed = None, False

    def identity(self):
        if self._closed:
            return {**copy.deepcopy(self._last), "status": "RETAINED_RECORD_HANDLE_CLOSED"}
        value = self._job._backend.identity(self._process)
        require(value["pid"] == self.pid and value["creation_filetime"] == self._initial["creation_filetime"],
                "ORIGINAL_HANDLE_IDENTITY_CHANGED")
        if value["image"] is None:
            require(not value["running"], "LIVE_PROCESS_IMAGE_MISSING")
            value["image"] = self._initial["image"]
            value["image_source"] = "RETAINED_PRE_EXIT_OBSERVATION"
        else:
            value["image_source"] = "CURRENT_NATIVE_OBSERVATION"
        self._last = copy.deepcopy(value)
        return value

    def poll(self):
        if not self._closed:
            self._code = self._job._backend.poll(self._process)
        require(not self._closed or type(self._code) is int, "CLOSED_CHILD_EXIT_NOT_CONFIRMED")
        return self._code

    def wait(self, timeout):
        if self._closed:
            return self.poll()
        self._code = self._job._backend.wait(self._process, _timeout(timeout))
        return self._code


class OwnedJob:
    def __init__(self, *, backend=None, close_timeout=10.0):
        self._timeout = _timeout(close_timeout)
        self._backend = backend if backend is not None else _NativeBackend()
        self._handle = self._backend.create_job(KILL_ON_JOB_CLOSE)
        self._children = {}
        self._member_handles = {}
        self._pending_handles = set()
        self._closed = False
        self._job_closed = False
        self.close_receipt = None
        self.launch_failures = []

    @property
    def roots(self):
        return [copy.deepcopy(child._last) for child in self._children.values()]

    def launch(self, argv: list[str], environment: dict[str, str], cwd: Path,
               stdout: Path, stderr: Path) -> OwnedChild:
        require(not self._closed, "JOB_CLOSED")
        _launch_spec(argv, environment, cwd, stdout, stderr)
        process = thread = pid = None
        initial = None
        try:
            process, thread, pid = self._backend.create_suspended(
                argv, environment, cwd, stdout, stderr, CREATION_FLAGS)
            require(type(pid) is int and pid > 0 and pid not in self._children,
                    "OWN_ROOT_PID_INVALID_OR_REUSED")
            self._backend.assign(self._handle, process)
            require(self._backend.in_job(process, self._handle), "ASSIGNED_JOB_NOT_OBSERVED")
            initial = self._backend.identity(process)
            require(initial["pid"] == pid and type(initial["creation_filetime"]) is int and
                    initial["creation_filetime"] > 0 and initial["image"] and initial["running"] is True,
                    "SUSPENDED_OWN_IDENTITY_MISSING")
            child = OwnedChild(self, process, pid, initial)
            self._backend.resume(thread)
            self._children[pid] = child
            return child
        except BaseException as error:
            if process is None and isinstance(error, JobError) and error._created is not None:
                process, thread, pid = error._created
            cleanup = {"error": error.code if isinstance(error, JobError) else type(error).__name__,
                       "own_process_created": process is not None, "pid": pid,
                       "initial_identity": copy.deepcopy(initial), "exit_confirmed": process is None}
            if process is not None:
                try:
                    if self._backend.poll(process) is None:
                        self._backend.terminate_process(process)
                    cleanup["exit_code"] = self._backend.wait(process, self._timeout)
                    cleanup["exit_confirmed"] = type(cleanup["exit_code"]) is int
                except BaseException as stopped:
                    cleanup["cleanup_error"] = stopped.code if isinstance(stopped, JobError) else type(stopped).__name__
                finally:
                    if cleanup["exit_confirmed"]:
                        self._backend.close_handle(process)
                    else:
                        # Retain the original capability for close() to try again;
                        # never reopen an unassigned failed child by PID.
                        self._pending_handles.add(process)
            self.launch_failures.append(cleanup)
            if not cleanup["exit_confirmed"]:
                raise JobError("OWN_LAUNCH_FAILURE_STOP_UNCONFIRMED", receipt=cleanup) from error
            raise
        finally:
            if thread is not None:
                self._backend.close_handle(thread)

    def members(self):
        require(not self._closed, "JOB_CLOSED")
        values = []
        for pid in self._backend.job_pids(self._handle):
            child = self._children.get(pid)
            process = child._process if child else self._member_handles.get(pid)
            opened = False
            if process is None:
                process = self._backend.open_member(pid)
                opened = True
            in_job = None
            retained = not opened
            try:
                in_job = self._backend.in_job(process, self._handle)
                require(in_job, "ENUMERATED_HANDLE_NOT_IN_OWN_JOB")
                if opened:
                    # A proved own member remains bound to this exact handle even
                    # when its image query fails. close() owns its final release.
                    self._member_handles[pid] = process
                    retained = True
                value = child.identity() if child else self._backend.identity(process)
                require(value["pid"] == pid, "JOB_MEMBER_PID_CHANGED")
                if value["running"]:
                    value["in_owned_job"] = True
                    values.append(value)
            except BaseException as error:
                if isinstance(error, JobError):
                    partial = copy.deepcopy(error.receipt) if isinstance(error.receipt, dict) else {}
                    partial["member_context"] = {"enumerated_pid": pid,
                        "in_owned_job": in_job, "retained_handle": retained,
                        "job_handle_retained": not self._job_closed,
                        "handle_source": ("OWNED_ROOT_ORIGINAL" if child else
                                          "NEW_JOB_ENUMERATED_MEMBER" if opened else
                                          "RETAINED_JOB_MEMBER")}
                    error.receipt = partial
                if opened and not retained:
                    self._backend.close_handle(process)
                raise
        return values

    def close(self):
        if self._closed and self.close_receipt["handles_closed"] and self.close_receipt["empty_confirmed"]:
            return copy.deepcopy(self.close_receipt)
        require(not self._job_closed or self._pending_handles, "PREVIOUS_CLOSE_UNCONFIRMED")
        receipt = {"empty_confirmed": False, "terminate_job_requested": False,
                   "handles_closed": False, "errors": [], "root_records": []}
        try:
            if not self._job_closed:
                active = self._backend.job_pids(self._handle)
                receipt["active_before_close"] = list(active)
                if active:
                    self._backend.terminate_job(self._handle)
                    receipt["terminate_job_requested"] = True
                receipt["empty_confirmed"] = self._backend.wait_empty(self._handle, self._timeout)
            else:
                receipt["empty_confirmed"] = self.close_receipt["empty_confirmed"]
            require(receipt["empty_confirmed"], "OWN_JOB_EMPTY_NOT_CONFIRMED")
            for process in list(self._pending_handles):
                if self._backend.poll(process) is None:
                    self._backend.terminate_process(process)
                self._backend.wait(process, self._timeout)
                self._backend.close_handle(process)
                self._pending_handles.remove(process)
            for child in self._children.values():
                if not child._closed:
                    child._code = self._backend.wait(child._process, self._timeout)
                    child.identity()  # terminated handle: PID/time plus retained image
            receipt["root_records"] = self.roots
        except BaseException as error:
            receipt["errors"].append(error.code if isinstance(error, JobError) else type(error).__name__)
        finally:
            # Last own job handle close is a safety fallback, not proof of emptiness.
            # A failed *unassigned* suspended child cannot be killed by job close.
            # Keep that original handle until its exit is confirmed; never reopen it.
            handles = set(self._member_handles.values()) | {
                c._process for c in self._children.values() if not c._closed}
            for handle in handles:
                try:
                    self._backend.close_handle(handle)
                except BaseException as error:
                    receipt["errors"].append(error.code if isinstance(error, JobError) else type(error).__name__)
            close_resources = getattr(self._backend, "close_resources", None)
            if close_resources is not None:
                try:
                    close_resources()
                except BaseException as error:
                    receipt["errors"].append(error.code if isinstance(error, JobError) else type(error).__name__)
            try:
                if not self._job_closed:
                    self._backend.close_handle(self._handle)
                    self._job_closed = True
                receipt["retained_unconfirmed_process_handles"] = len(self._pending_handles)
                receipt["handles_closed"] = not receipt["errors"] and not self._pending_handles
            except BaseException as error:
                receipt["errors"].append(error.code if isinstance(error, JobError) else type(error).__name__)
            self._closed = True
            self._member_handles.clear()
            for child in self._children.values():
                child._closed = True
            receipt["root_records"] = self.roots
            self.close_receipt = receipt
        if receipt["errors"] or not receipt["empty_confirmed"]:
            raise JobError("OWN_JOB_CLOSE_UNCONFIRMED", receipt=copy.deepcopy(receipt))
        return copy.deepcopy(receipt)


def file_identity(path, *, backend=None):
    """Metadata-only; no binary content/hash read or path-equivalence decision."""
    path = _windows_path(path)
    return (backend if backend is not None else _NativeBackend()).file_identity(path)


def native_file_identity(approved_local_DOS_path, *, backend=None):
    """No observed/native path is accepted as input or opened by this API."""
    path = _windows_path(approved_local_DOS_path)
    return (backend if backend is not None else NativeImageBackend()).native_file_identity(path)
