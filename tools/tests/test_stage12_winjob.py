"""Pure doubles only. No WinDLL, SDK, process, file creation or native call."""
import ctypes as C
import importlib.util
from pathlib import Path, PureWindowsPath
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1] / "release/stage12_winjob.py"
spec = importlib.util.spec_from_file_location("stage12_winjob_under_test", SOURCE)
w = importlib.util.module_from_spec(spec)
spec.loader.exec_module(w)

ARGV = ["C:/owned/sdk/emulator/emulator.exe", "-version"]
ENV = {"SystemRoot": "C:/Windows", "PATH": "C:/Windows/System32"}
CWD = PureWindowsPath("C:/owned/runtime")
OUT = PureWindowsPath("C:/owned/evidence/stdout.txt")
ERR = PureWindowsPath("C:/owned/evidence/stderr.txt")


class FakeBackend:
    def __init__(self, failure=None):
        self.events = []
        self.failure = failure
        self.states = {}
        self.assigned = set()
        self.closed = []
        self.next_handle = 100
        self.foreign = False
        self.empty_result = True

    def create_job(self, flags):
        self.events.append(("create_job", flags))
        return 70

    def create_suspended(self, *args):
        self.events.append(("create_suspended", args))
        if self.failure == "create":
            raise w.JobError("CREATE_FAILED")
        h = self.next_handle
        self.next_handle += 2
        self.states[h] = {"pid": h + 1000, "creation_filetime": 134372638894596925 + h,
            "image": ARGV[0], "parent_pid": None, "parent_pid_source": "NOT_MEASURED",
            "running": True, "code": None}
        if self.failure == "post_create":
            raise w.JobError("POST_CREATE_FAILED", created=(h, h + 1, h + 1000))
        return h, h + 1, h + 1000

    def assign(self, job, process):
        self.events.append(("assign", job, process))
        if self.failure == "assign":
            raise w.JobError("ASSIGN_FAILED")
        self.assigned.add(process)

    def in_job(self, process, job):
        self.events.append(("in_job", process, job))
        return process not in (self.foreign or set()) and process in self.assigned

    def identity(self, process):
        self.events.append(("identity", process))
        value = {k: v for k, v in self.states[process].items() if k != "code"}
        if not value["running"]:
            value["image"] = None
        return value

    def resume(self, thread):
        self.events.append(("resume", thread))
        if self.failure == "resume":
            raise w.JobError("RESUME_FAILED")

    def poll(self, process):
        return self.states[process]["code"]

    def wait(self, process, timeout):
        self.events.append(("wait", process, timeout))
        code = self.poll(process)
        if code is None:
            raise TimeoutError("FAKE_OWN_TIMEOUT")
        return code

    def job_pids(self, job):
        self.events.append(("job_pids", job))
        if self.failure == "query":
            raise w.JobError("QUERY_FAILED")
        return [self.states[h]["pid"] for h in sorted(self.assigned) if self.states[h]["running"]]

    def open_member(self, pid):
        self.events.append(("open_member", pid))
        return next(h for h, state in self.states.items() if state["pid"] == pid)

    def terminate_process(self, process):
        self.events.append(("terminate_process", process))
        if self.failure == "terminate":
            raise w.JobError("TERMINATE_FAILED")
        self.finish(process, 113)

    def terminate_job(self, job):
        self.events.append(("terminate_job", job))
        for h in self.assigned:
            self.finish(h, 113)

    def finish(self, handle, code):
        self.states[handle].update(running=False, code=code)

    def wait_empty(self, job, timeout):
        self.events.append(("wait_empty", job, timeout))
        return self.empty_result

    def close_handle(self, handle):
        self.events.append(("close", handle))
        self.closed.append(handle)

    def file_identity(self, path):
        self.events.append(("file_identity", path))
        return {"final_path": path, "volume_serial": 1, "file_index": 2,
                "bytes": 3, "attributes": 32, "links": 1, "last_write_filetime": 134370241337968358}


class Fn:
    def __init__(self, call=lambda *args: 1):
        self.call = call

    def __call__(self, *args):
        return self.call(*args)


class FakeKernel:
    def __init__(self, fail_create=False):
        self.fail_create = fail_create
        self.opens, self.closed = [], []
        self.next_handle = 30
        self.inherited = None
        self.flags = None
        self.created = None
        self.job_flags = None
        for name in ["CreateJobObjectW", "SetInformationJobObject", "QueryInformationJobObject",
            "AssignProcessToJobObject", "IsProcessInJob", "TerminateJobObject", "CloseHandle",
            "CreateFileW", "InitializeProcThreadAttributeList", "UpdateProcThreadAttribute",
            "DeleteProcThreadAttributeList", "CreateProcessW", "ResumeThread", "TerminateProcess",
            "WaitForSingleObject", "GetExitCodeProcess", "GetProcessId", "GetProcessTimes",
            "QueryFullProcessImageNameW", "OpenProcess", "GetFileInformationByHandle", "GetFinalPathNameByHandleW"]:
            setattr(self, name, Fn())
        self.CreateJobObjectW.call = lambda *a: 77
        self.SetInformationJobObject.call = self.set_limits
        self.CreateFileW.call = self.create_file
        self.CloseHandle.call = lambda h: self.closed.append(h) or 1
        self.InitializeProcThreadAttributeList.call = self.init_attributes
        self.UpdateProcThreadAttribute.call = self.update_attributes
        self.CreateProcessW.call = self.create_process

    def set_limits(self, job, kind, pointer, size):
        self.job_flags = C.cast(pointer, C.POINTER(w.EXTENDED_LIMITS)).contents.basic.flags
        return 1

    def create_file(self, path, access, share, attrs, disposition, flags, template):
        self.opens.append((path, access, disposition,
                           C.cast(attrs, C.POINTER(w.SECURITY_ATTRIBUTES)).contents.inherit if attrs else False))
        self.next_handle += 1
        return self.next_handle

    def init_attributes(self, target, count, flags, size):
        C.cast(size, C.POINTER(w.SIZE_T)).contents.value = 64
        return int(target is not None)

    def update_attributes(self, target, flags, key, handles, size, old, returned):
        self.inherited = list(C.cast(handles, C.POINTER(w.HANDLE * 3)).contents)
        self.attribute_key = key
        return 1

    def create_process(self, exe, command, pa, ta, inherit, flags, env, cwd, startup, pi):
        self.flags = flags
        start = C.cast(startup, C.POINTER(w.STARTUPINFOEX)).contents
        self.created = {"exe": exe, "command": command.value, "inherit": inherit,
                        "stdio": [start.startup.stdin, start.startup.stdout, start.startup.stderr],
                        "cb": start.startup.cb, "cwd": cwd}
        if self.fail_create:
            return 0
        info = C.cast(pi, C.POINTER(w.PROCESS_INFORMATION)).contents
        info.process, info.thread, info.pid, info.tid = 91, 92, 901, 902
        return 1


class OwnedJobTests(unittest.TestCase):
    def launch(self, backend=None):
        backend = backend or FakeBackend()
        job = w.OwnedJob(backend=backend)
        return backend, job, job.launch(ARGV, ENV, CWD, OUT, ERR)

    def test_suspend_assign_membership_identity_resume_order_and_retained_handle(self):
        backend, job, child = self.launch()
        operations = [e[0] for e in backend.events]
        self.assertEqual(operations[:6], ["create_job", "create_suspended", "assign", "in_job", "identity", "resume"])
        self.assertEqual(backend.events[0], ("create_job", w.KILL_ON_JOB_CLOSE))
        self.assertEqual(backend.events[1][1][-1], w.CREATION_FLAGS)
        self.assertEqual(backend.closed, [101])
        self.assertEqual(child.pid, 1100)
        self.assertIsNone(child.identity()["parent_pid"])
        self.assertEqual(child.identity()["parent_pid_source"], "NOT_MEASURED")
        job.close()
        self.assertIn(100, backend.closed)

    def test_assignment_failure_terminates_only_original_still_suspended_child(self):
        backend = FakeBackend("assign"); job = w.OwnedJob(backend=backend)
        with self.assertRaisesRegex(w.JobError, "ASSIGN_FAILED"):
            job.launch(ARGV, ENV, CWD, OUT, ERR)
        self.assertNotIn("resume", [e[0] for e in backend.events])
        self.assertEqual([e for e in backend.events if e[0] == "terminate_process"], [("terminate_process", 100)])
        self.assertEqual(sorted(backend.closed), [100, 101])
        self.assertTrue(job.launch_failures[0]["exit_confirmed"])
        job.close()

    def test_create_process_failure_has_no_fake_child_or_process_cleanup(self):
        backend = FakeBackend("create"); job = w.OwnedJob(backend=backend)
        with self.assertRaisesRegex(w.JobError, "CREATE_FAILED"):
            job.launch(ARGV, ENV, CWD, OUT, ERR)
        self.assertFalse(any(e[0] in ("assign", "resume", "terminate_process") for e in backend.events))
        self.assertTrue(job.launch_failures[0]["exit_confirmed"])
        job.close()

    def test_post_create_resource_error_transfers_original_capability_for_cleanup(self):
        backend = FakeBackend("post_create"); job = w.OwnedJob(backend=backend)
        with self.assertRaisesRegex(w.JobError, "POST_CREATE_FAILED"):
            job.launch(ARGV, ENV, CWD, OUT, ERR)
        self.assertIn(("terminate_process", 100), backend.events)
        self.assertEqual(sorted(backend.closed), [100, 101])
        job.close()

    def test_resume_failure_preserves_initial_record_and_confirms_own_exit(self):
        backend = FakeBackend("resume"); job = w.OwnedJob(backend=backend)
        with self.assertRaisesRegex(w.JobError, "RESUME_FAILED"):
            job.launch(ARGV, ENV, CWD, OUT, ERR)
        self.assertEqual(job.launch_failures[0]["initial_identity"]["pid"], 1100)
        self.assertEqual(job.launch_failures[0]["exit_code"], 113)
        job.close()

    def test_timeout_does_not_close_handle_or_claim_exit(self):
        backend, job, child = self.launch()
        with self.assertRaises(TimeoutError): child.wait(0.01)
        self.assertIsNone(child.poll())
        self.assertNotIn(100, backend.closed)
        job.close()

    def test_identity_after_exit_uses_exact_retained_record_and_wait_accepts259(self):
        backend, job, child = self.launch()
        initial = child.identity(); backend.finish(100, 259)
        ended = child.identity()
        self.assertFalse(ended["running"])
        self.assertEqual(ended["image"], initial["image"])
        self.assertEqual(ended["creation_filetime"], initial["creation_filetime"])
        self.assertEqual(ended["image_source"], "RETAINED_PRE_EXIT_OBSERVATION")
        self.assertEqual(child.wait(0), 259)
        receipt = job.close()
        self.assertEqual(receipt["root_records"][0]["pid"], child.pid)
        self.assertEqual(child.identity()["status"], "RETAINED_RECORD_HANDLE_CLOSED")
        self.assertNotIn("terminate_job", [e[0] for e in backend.events])

    def test_membership_is_checked_before_unknown_member_identity(self):
        backend, job, _ = self.launch()
        backend.states[200] = {**backend.states[100], "pid": 1200}
        backend.assigned.add(200)
        identities = job.members()
        self.assertEqual([i["pid"] for i in identities], [1100, 1200])
        index = backend.events.index(("open_member", 1200))
        self.assertEqual([e[0] for e in backend.events[index:index+3]], ["open_member", "in_job", "identity"])
        self.assertNotIn(200, backend.closed)
        job.close(); self.assertIn(200, backend.closed)

    def test_foreign_or_reused_member_is_rejected_before_image_read(self):
        backend, job, _ = self.launch()
        backend.states[200] = {**backend.states[100], "pid": 1200}
        backend.assigned.add(200)
        backend.foreign = {200}; before = len(backend.events)
        with self.assertRaisesRegex(w.JobError, "ENUMERATED_HANDLE_NOT_IN_OWN_JOB"): job.members()
        self.assertNotIn(("identity", 200), backend.events[before:])
        self.assertIn(200, backend.closed)
        backend.foreign = False; job.close()

    def test_live_member_image_failure_preserves_context_and_handle_until_own_close(self):
        backend, job, _ = self.launch()
        backend.states[200] = {**backend.states[100], "pid": 1200}
        backend.assigned.add(200)
        observed = backend.identity

        def unidentified_member(process):
            if process == 200:
                raise w.JobError("WIN32_QueryFullProcessImageNameW", win32_error=5,
                    receipt={"pid": 1200, "creation_filetime": 134373026825971723,
                             "running_before": True, "running_after": True,
                             "queried_size": 32768, "win32_error": 5, "image": None,
                             "raw_status": "LIVE_MEMBER_IMAGE_UNIDENTIFIABLE"})
            return observed(process)

        backend.identity = unidentified_member
        with self.assertRaisesRegex(w.JobError, "WIN32_QueryFullProcessImageNameW") as caught:
            job.members()
        error = caught.exception
        self.assertEqual(error.win32_error, 5)
        self.assertIsNone(error.receipt["image"])
        self.assertEqual(error.receipt["member_context"], {
            "enumerated_pid": 1200, "in_owned_job": True, "retained_handle": True,
            "job_handle_retained": True, "handle_source": "NEW_JOB_ENUMERATED_MEMBER"})
        self.assertNotIn(200, backend.closed)
        self.assertEqual([e for e in backend.events if e[0] == "open_member"], [("open_member", 1200)])
        receipt = job.close()
        self.assertTrue(receipt["empty_confirmed"] and receipt["handles_closed"])
        self.assertIn(200, backend.closed)
        self.assertEqual([e for e in backend.events if e[0] == "terminate_job"], [("terminate_job", 70)])

    def test_unassigned_stop_failure_retains_original_capability_until_confirmed_cleanup(self):
        backend = FakeBackend("assign"); job = w.OwnedJob(backend=backend)
        original = backend.terminate_process
        backend.terminate_process = lambda process: (_ for _ in ()).throw(w.JobError("STOP_FAILED"))
        with self.assertRaisesRegex(w.JobError, "OWN_LAUNCH_FAILURE_STOP_UNCONFIRMED"):
            job.launch(ARGV, ENV, CWD, OUT, ERR)
        self.assertNotIn(100, backend.closed)
        with self.assertRaisesRegex(w.JobError, "OWN_JOB_CLOSE_UNCONFIRMED"): job.close()
        self.assertEqual(job.close_receipt["retained_unconfirmed_process_handles"], 1)
        self.assertNotIn(100, backend.closed)
        backend.terminate_process = original
        backend.failure = None
        receipt = job.close()
        self.assertTrue(receipt["empty_confirmed"] and receipt["handles_closed"])
        self.assertIn(100, backend.closed)

    def test_close_terminates_only_own_job_and_is_idempotent(self):
        backend, job, _ = self.launch()
        receipt = job.close()
        self.assertTrue(receipt["empty_confirmed"])
        self.assertTrue(receipt["handles_closed"])
        self.assertEqual([e for e in backend.events if e[0] == "terminate_job"], [("terminate_job", 70)])
        count = len(backend.events); self.assertEqual(job.close(), receipt)
        self.assertEqual(len(backend.events), count)

    def test_unconfirmed_empty_never_returns_success_but_closes_own_job(self):
        backend, job, _ = self.launch(); backend.empty_result = False
        with self.assertRaisesRegex(w.JobError, "OWN_JOB_CLOSE_UNCONFIRMED") as caught: job.close()
        self.assertFalse(caught.exception.receipt["empty_confirmed"])
        self.assertIn(70, backend.closed)
        with self.assertRaises(w.JobError): job.close()

    def test_query_failure_preserves_error_and_last_handle_cleanup(self):
        backend, job, _ = self.launch(); backend.failure = "query"
        with self.assertRaises(w.JobError) as caught: job.close()
        self.assertIn("QUERY_FAILED", caught.exception.receipt["errors"])
        self.assertIn(70, backend.closed)
        self.assertNotIn("terminate_job", [e[0] for e in backend.events])

    def test_invalid_input_rejected_before_create_process(self):
        for argv, env, cwd, out, err in [(["adb.exe"], ENV, CWD, OUT, ERR),
            (["C:/owned/run.cmd"], ENV, CWD, OUT, ERR), (ARGV, {"PATH": "x", "Path": "y"}, CWD, OUT, ERR),
            (ARGV, ENV, CWD, OUT, OUT), (ARGV, ENV, "//server/share", OUT, ERR),
            (ARGV, ENV, CWD, "C:/owned/NUL.txt", ERR), (ARGV, {"X": "a\0b"}, CWD, OUT, ERR)]:
            backend = FakeBackend(); job = w.OwnedJob(backend=backend)
            with self.assertRaises(w.JobError): job.launch(argv, env, cwd, out, err)
            self.assertFalse(any(e[0] == "create_suspended" for e in backend.events)); job.close()

    def test_file_identity_is_metadata_only_delegation_without_content_hash(self):
        backend = FakeBackend(); result = w.file_identity(PureWindowsPath(ARGV[0]), backend=backend)
        self.assertEqual(result["last_write_filetime"], 134370241337968358)
        self.assertNotIn("sha256", result)
        self.assertEqual([e[0] for e in backend.events], ["file_identity"])


class NativeAdapterDoubleTests(unittest.TestCase):
    def adapter(self, fail=False):
        kernel = FakeKernel(fail)
        return kernel, w._NativeBackend(kernel=kernel, last_error=lambda: 5)

    def image_failure_adapter(self, *, ended_after_query=False):
        kernel, backend = self.adapter()
        events, state = [], {"last_error": 0, "waits": 0}

        def pid(handle):
            events.append("pid")
            return 901

        def times(handle, created, exited, kernel_time, user_time):
            events.append("times")
            value = C.cast(created, C.POINTER(w.FILETIME)).contents
            stamp = 134373026825971723
            value.low, value.high = stamp & 0xffffffff, stamp >> 32
            return 1

        def wait(handle, timeout):
            events.append("wait")
            state["waits"] += 1
            state["last_error"] = 87  # would destroy the original query error
            return w.WAIT_OBJECT_0 if ended_after_query and state["waits"] > 1 else w.WAIT_TIMEOUT

        def query(handle, flags, text, size):
            events.append("query")
            state["last_error"] = 5
            C.cast(size, C.POINTER(w.DWORD)).contents.value = 4096
            return 0

        def last_error():
            events.append("last_error")
            return state["last_error"]

        def code(handle, pointer):
            events.append("exit_code")
            C.cast(pointer, C.POINTER(w.DWORD)).contents.value = 7
            state["last_error"] = 99
            return 1

        kernel.GetProcessId.call = pid
        kernel.GetProcessTimes.call = times
        kernel.WaitForSingleObject.call = wait
        kernel.QueryFullProcessImageNameW.call = query
        kernel.GetExitCodeProcess.call = code
        backend.last_error = last_error
        return backend, events

    def test_image_failure_captures_win32_error_before_poll_and_propagates_live_partial(self):
        backend, events = self.image_failure_adapter()
        with self.assertRaisesRegex(w.JobError, "WIN32_QueryFullProcessImageNameW") as caught:
            backend.identity(91)
        self.assertEqual(events, ["pid", "times", "wait", "query", "last_error", "wait"])
        self.assertEqual(caught.exception.win32_error, 5)
        self.assertEqual(caught.exception.receipt, {
            "pid": 901, "creation_filetime": 134373026825971723,
            "running_before": True, "running_after": True,
            "requested_size": 32768, "queried_size": 4096, "win32_error": 5,
            "image": None, "raw_status": "LIVE_MEMBER_IMAGE_UNIDENTIFIABLE"})

    def test_image_failure_then_exit_is_distinguished_without_retry_or_invented_image(self):
        backend, events = self.image_failure_adapter(ended_after_query=True)
        value = backend.identity(91)
        self.assertFalse(value["running"])
        self.assertIsNone(value["image"])
        partial = value["image_query_failure"]
        self.assertEqual(partial["win32_error"], 5)
        self.assertEqual(partial["creation_filetime"], 134373026825971723)
        self.assertTrue(partial["running_before"])
        self.assertFalse(partial["running_after"])
        self.assertEqual(partial["raw_status"], "PROCESS_ENDED_DURING_IMAGE_QUERY")
        self.assertIsNone(partial["image"])
        self.assertEqual(events, ["pid", "times", "wait", "query", "last_error", "wait", "exit_code"])

    def test_native_startup_exact_flags_handle_list_and_create_new_stdio(self):
        kernel, backend = self.adapter()
        with patch.object(w, "_no_reparse"):
            created = backend.create_suspended(ARGV, ENV, CWD, OUT, ERR, w.CREATION_FLAGS)
        self.assertEqual(created, (91, 92, 901))
        self.assertEqual(kernel.attribute_key, w.HANDLE_LIST)
        self.assertEqual(kernel.inherited, [31, 32, 33])
        self.assertEqual(kernel.created["stdio"], kernel.inherited)
        self.assertTrue(kernel.created["inherit"])
        self.assertEqual(kernel.created["cb"], C.sizeof(w.STARTUPINFOEX))
        self.assertEqual(kernel.flags, 0x08080404)
        self.assertFalse(kernel.flags & 0x01000000)  # no CREATE_BREAKAWAY_FROM_JOB
        self.assertEqual([x[2] for x in kernel.opens], [3, 1, 1])
        self.assertEqual([bool(x[3]) for x in kernel.opens], [True, True, True])
        self.assertEqual(kernel.closed, [31, 32, 33])

    def test_native_create_process_failure_closes_all_stdio_handles(self):
        kernel, backend = self.adapter(True)
        with patch.object(w, "_no_reparse"), self.assertRaisesRegex(w.JobError, "WIN32_CreateProcessW"):
            backend.create_suspended(ARGV, ENV, CWD, OUT, ERR, w.CREATION_FLAGS)
        self.assertEqual(kernel.closed, [31, 32, 33])

    def test_native_job_limit_is_exact_and_no_breakaway(self):
        kernel, backend = self.adapter()
        self.assertEqual(backend.create_job(w.KILL_ON_JOB_CLOSE), 77)
        self.assertEqual(kernel.job_flags, 0x2000)
        self.assertEqual(kernel.job_flags & (0x800 | 0x1000), 0)

    def test_structs_use_explicit32bit_win32_scalars_on_any_test_host(self):
        self.assertEqual(C.sizeof(w.DWORD), 4)
        self.assertEqual(C.sizeof(w.FILETIME), 8)
        if C.sizeof(C.c_void_p) == 8:
            self.assertEqual(C.sizeof(w.STARTUPINFOEX), 112)
            self.assertEqual(C.sizeof(w.EXTENDED_LIMITS), 144)

    def test_native_file_identity_requests_only_attributes_and_keeps_exact_filetime(self):
        kernel, backend = self.adapter()
        stamp = 134370241337968358

        def info(handle, pointer):
            result = C.cast(pointer, C.POINTER(w.FILE_INFO)).contents
            result.attributes, result.volume, result.links = 32, 19, 1
            result.index_low, result.index_high = 7, 2
            result.size_low = 50
            result.written.low, result.written.high = stamp & 0xffffffff, stamp >> 32
            return 1

        def finalpath(handle, buffer, length, flags):
            buffer.value = "\\\\?\\C:\\owned\\sdk\\emulator\\emulator.exe"
            return len(buffer.value)

        kernel.GetFileInformationByHandle.call = info
        kernel.GetFinalPathNameByHandleW.call = finalpath
        with patch.object(w, "_no_reparse"):
            result = backend.file_identity(ARGV[0])
        self.assertEqual(kernel.opens[0][1], 0x80)
        self.assertEqual(kernel.opens[0][2], 3)
        self.assertFalse(kernel.opens[0][3])
        self.assertEqual(result["last_write_filetime"], stamp)
        self.assertEqual(result["file_index"], (2 << 32) | 7)
        self.assertNotIn("sha256", result)
        self.assertEqual(kernel.closed, [31])

    def test_native_terminal_identity_never_queries_image_and_exit259_is_terminal(self):
        kernel, backend = self.adapter()
        kernel.WaitForSingleObject.call = lambda *args: w.WAIT_OBJECT_0
        kernel.GetProcessId.call = lambda handle: 901

        def times(handle, created, exited, kernel_time, user_time):
            value = C.cast(created, C.POINTER(w.FILETIME)).contents
            value.low, value.high = 0x12345678, 0x01abcdef
            return 1

        def code(handle, pointer):
            C.cast(pointer, C.POINTER(w.DWORD)).contents.value = 259
            return 1

        kernel.GetProcessTimes.call = times
        kernel.GetExitCodeProcess.call = code
        kernel.QueryFullProcessImageNameW.call = lambda *args: self.fail("image query after exit")
        result = backend.identity(91)
        self.assertFalse(result["running"])
        self.assertIsNone(result["image"])
        self.assertEqual(result["creation_filetime"], (0x01abcdef << 32) | 0x12345678)
        self.assertEqual(backend.poll(91), 259)


class NativeImagePolicyTests(unittest.TestCase):
    NATIVE = ("\\Device\\HarddiskVolume3\\Users\\User\\.cache\\"
              "miguardia-stage12-runtime\\20261024\\sdk\\emulator\\crashpad_handler.exe")

    def image_adapter(self, *, fail_first=False, fail_second=False, second=None,
                      ended_after=False, wrong_size=False):
        kernel = FakeKernel()
        kernel.K32GetProcessImageFileNameW = Fn()
        events, state = [], {"error": 0, "queries": 0}
        kernel.GetProcessId.call = lambda handle: 901

        def times(handle, created, exited, kernel_time, user_time):
            value = C.cast(created, C.POINTER(w.FILETIME)).contents
            stamp = 134373038754714188
            value.low, value.high = stamp & 0xffffffff, stamp >> 32
            return 1

        def wait(handle, timeout):
            state["error"] = 99
            return w.WAIT_OBJECT_0 if ended_after and state["queries"] == 2 else w.WAIT_TIMEOUT

        def query(handle, flags, text, size):
            events.append(("QueryFullProcessImageNameW", handle, flags))
            state["queries"] += 1
            state["error"] = 5 if fail_first else 0
            if fail_first:
                return 0
            text.value = self.NATIVE
            C.cast(size, C.POINTER(w.DWORD)).contents.value = len(self.NATIVE) + int(wrong_size)
            return 1

        def k32(handle, text, capacity):
            events.append(("K32GetProcessImageFileNameW", handle, None))
            state["queries"] += 1
            state["error"] = 31 if fail_second else 0
            if fail_second:
                return 0
            text.value = self.NATIVE if second is None else second
            return len(text.value)

        def last_error():
            events.append(("last_error", state["error"]))
            return state["error"]

        def exit_code(handle, pointer):
            C.cast(pointer, C.POINTER(w.DWORD)).contents.value = 0
            return 1

        kernel.GetProcessTimes.call = times
        kernel.WaitForSingleObject.call = wait
        kernel.QueryFullProcessImageNameW.call = query
        kernel.K32GetProcessImageFileNameW.call = k32
        kernel.GetExitCodeProcess.call = exit_code
        return kernel, w.NativeImageBackend(kernel=kernel, last_error=last_error), events

    def file_adapter(self, *, attributes=32, change_metadata=False, fail_native_name=False):
        kernel = FakeKernel()
        kernel.K32GetProcessImageFileNameW = Fn()
        events, state = [], {"calls": 0, "error": 0}

        def information(handle, pointer):
            events.append(("metadata", handle))
            state["calls"] += 1
            value = C.cast(pointer, C.POINTER(w.FILE_INFO)).contents
            value.attributes, value.volume, value.links = attributes, 3091422717, 1
            value.index_high, value.index_low, value.size_low = 2, 7, 8273560
            stamp = 134370241337968358 + int(change_metadata and state["calls"] > 1)
            value.written.low, value.written.high = stamp & 0xffffffff, stamp >> 32
            return 1

        def final_name(handle, text, capacity, flags):
            events.append(("final_name", handle, flags))
            if flags == 2 and fail_native_name:
                state["error"] = 5
                return 0
            text.value = self.NATIVE if flags == 2 else "\\\\?\\C:\\owned\\crashpad_handler.exe"
            return len(text.value)

        kernel.GetFileInformationByHandle.call = information
        kernel.GetFinalPathNameByHandleW.call = final_name
        return kernel, w.NativeImageBackend(kernel=kernel, last_error=lambda: state["error"]), events

    def test_native_policy_is_explicit_and_default_win32_does_not_fallback(self):
        kernel = FakeKernel()
        flags = []
        kernel.GetProcessId.call = lambda handle: 901
        kernel.WaitForSingleObject.call = lambda *a: w.WAIT_TIMEOUT

        def times(handle, created, *rest):
            C.cast(created, C.POINTER(w.FILETIME)).contents.low = 1
            return 1

        kernel.GetProcessTimes.call = times
        kernel.QueryFullProcessImageNameW.call = lambda handle, flag, *a: flags.append(flag) or 0
        kernel.K32GetProcessImageFileNameW = Fn(lambda *a: self.fail("default native fallback"))
        backend = w._NativeBackend(kernel=kernel, last_error=lambda: 5)
        with self.assertRaisesRegex(w.JobError, "WIN32_QueryFullProcessImageNameW"): backend.identity(91)
        self.assertEqual(flags, [0])
        self.assertTrue(issubclass(w.NativeImageBackend, w._NativeBackend))

    def test_native_two_queries_same_handle_exact_path_and_typed_fields(self):
        kernel, backend, events = self.image_adapter()
        value = backend.identity(91)
        self.assertEqual(events, [("QueryFullProcessImageNameW", 91, 1),
                                  ("K32GetProcessImageFileNameW", 91, None)])
        self.assertEqual(value["image"], self.NATIVE)
        self.assertEqual(value["image_format"], "NATIVE_SYSTEM")
        self.assertEqual(value["image_source"], "CURRENT_NATIVE_OBSERVATION")
        self.assertEqual(value["creation_filetime"], 134373038754714188)
        self.assertTrue(value["running"])
        for item in value["native_queries"]:
            self.assertEqual(set(item), {"api", "flags", "success", "win32_error", "characters", "raw_image"})
            self.assertIs(item["success"], True)
            self.assertEqual(item["win32_error"], 0)
            self.assertEqual(item["characters"], len(self.NATIVE))
            self.assertEqual(item["raw_image"], self.NATIVE)
        self.assertEqual(kernel.opens, [])

    def test_each_native_api_failure_preserves_immediate_error_and_rejects_live_process(self):
        for first, second, code in [(True, False, 5), (False, True, 31)]:
            kernel, backend, events = self.image_adapter(fail_first=first, fail_second=second)
            with self.assertRaisesRegex(w.JobError, "NATIVE_IMAGE_IDENTITY_NOT_PROVEN") as caught:
                backend.identity(91)
            self.assertEqual(caught.exception.win32_error, code)
            partial = caught.exception.receipt
            self.assertIsNone(partial["image"])
            self.assertTrue(partial["running_before"] and partial["running_after"])
            self.assertEqual(partial["raw_status"], "LIVE_NATIVE_IMAGE_UNIDENTIFIABLE")
            self.assertEqual(len([e for e in events if e[0] != "last_error"]), 2)
            self.assertIn(("last_error", code), events)
            failed = partial["native_queries"][0 if first else 1]
            self.assertFalse(failed["success"])
            self.assertEqual(failed["win32_error"], code)
            self.assertIsNone(failed["raw_image"])
            self.assertEqual(kernel.opens, [])

    def test_native_mismatch_empty_and_wrong_character_count_are_not_accepted(self):
        for options in [{"second": self.NATIVE.replace("Volume3", "Volume9")},
                        {"second": ""}, {"wrong_size": True}]:
            kernel, backend, events = self.image_adapter(**options)
            with self.assertRaisesRegex(w.JobError, "NATIVE_IMAGE_IDENTITY_NOT_PROVEN") as caught:
                backend.identity(91)
            self.assertIsNone(caught.exception.receipt["image"])
            self.assertEqual(len(caught.exception.receipt["native_queries"]), 2)
            self.assertEqual(kernel.opens, [])

    def test_native_failure_then_exit_keeps_partial_but_does_not_invent_image(self):
        kernel, backend, events = self.image_adapter(fail_first=True, ended_after=True)
        value = backend.identity(91)
        self.assertFalse(value["running"])
        self.assertIsNone(value["image"])
        self.assertEqual(value["image_source"], "TERMINAL_NO_CURRENT_IMAGE")
        self.assertEqual(value["image_query_failure"]["raw_status"], "PROCESS_ENDED_DURING_NATIVE_IMAGE_QUERY")
        self.assertEqual(value["image_query_failure"]["native_queries"][0]["win32_error"], 5)
        self.assertEqual(len([e for e in events if e[0] != "last_error"]), 2)

    def test_native_file_name_is_measured_from_same_approved_dos_metadata_handle_only(self):
        kernel, backend, events = self.file_adapter()
        path = "C:/owned/crashpad_handler.exe"
        with patch.object(w, "_no_reparse"):
            value = w.native_file_identity(path, backend=backend)
        self.assertEqual(kernel.opens, [(path.replace("/", "\\"), 0x80, 3, False)])
        self.assertEqual(events, [("metadata", 31), ("final_name", 31, 0),
                                  ("final_name", 31, 2), ("metadata", 31)])
        self.assertEqual(value["native_final_path"], self.NATIVE)
        self.assertEqual(value["native_final_path_flags"], 2)
        self.assertEqual(value["native_final_path_length"], len(self.NATIVE))
        self.assertEqual(value["proof_kind"], "NO_OBSERVED_NATIVE_PATH_OPEN")
        self.assertEqual(value["last_write_filetime"], 134370241337968358)
        self.assertTrue(value["metadata_stable"])
        self.assertNotIn("sha256", value)
        self.assertEqual(kernel.closed, [31])

    def test_native_file_api_refuses_nt_unc_ads_and_never_opens_observed_paths(self):
        kernel, backend, events = self.file_adapter()
        for path in [self.NATIVE, self.NATIVE.replace("Volume3", "Volume9"),
                     "\\\\server\\share\\crashpad_handler.exe", "C:/owned/crashpad_handler.exe:stream"]:
            with self.assertRaises(w.JobError): w.native_file_identity(path, backend=backend)
        self.assertEqual(kernel.opens, [])
        self.assertFalse(hasattr(w, "native_observed_file_identity"))

    def test_native_file_metadata_denies_reparse_directory_and_instability(self):
        for options in [{"attributes": 0x400}, {"attributes": 0x10}, {"change_metadata": True}]:
            kernel, backend, events = self.file_adapter(**options)
            with patch.object(w, "_no_reparse"), self.assertRaises(w.JobError):
                w.native_file_identity("C:/owned/crashpad_handler.exe", backend=backend)
            self.assertEqual(kernel.opens[0][1], 0x80)
            self.assertEqual(kernel.closed, [31])

    def test_native_final_name_failure_keeps_error_and_does_not_change_api_policy(self):
        kernel, backend, events = self.file_adapter(fail_native_name=True)
        with patch.object(w, "_no_reparse"), self.assertRaisesRegex(w.JobError, "WIN32_GetFinalPathNameByHandleW") as caught:
            w.native_file_identity("C:/owned/crashpad_handler.exe", backend=backend)
        self.assertEqual(caught.exception.win32_error, 5)
        self.assertEqual(caught.exception.receipt["flags"], 2)
        self.assertEqual(events, [("metadata", 31), ("final_name", 31, 0), ("final_name", 31, 2)])
        self.assertEqual(kernel.closed, [31])


if __name__ == "__main__":
    unittest.main()
