#!/usr/bin/env python3
"""Verify FitLog on Pixel_10 by default, or an explicitly isolated test AVD."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import sqlite3
import subprocess
import sys
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[4]
PACKAGE = 'com.example.fitlog'
COMPONENT = PACKAGE + '/.app.MainActivity'


class Driver:
    def __init__(self, run):
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,63}', run):
            raise ValueError('Run name must be 1-64 letters, digits, underscores or hyphens.')
        self.base = ROOT / 'build' / 'verification' / run
        self.base_existed = self.base.exists()
        self.evidence = self.base / 'evidence'
        self.scratch = self.base / 'scratch'
        verification_root = ROOT / 'build' / 'verification'
        if not verification_root.resolve().is_relative_to(ROOT.resolve()) or not self.base.resolve().is_relative_to(verification_root.resolve()):
            raise RuntimeError('Verification output must stay inside this checkout.')
        self.state_path = self.base / 'run.json'
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else {}
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if not sdk:
            match = re.search(r'^sdk.dir=(.+)$', (ROOT / 'local.properties').read_text(), re.M)
            sdk = match[1].strip().replace('\\:', ':').replace('\\\\', '\\') if match else None
        if not sdk:
            raise RuntimeError('Set ANDROID_HOME to the SDK used by this checkout.')
        self.sdk = Path(sdk)
        self.adb = str(self.sdk / 'platform-tools' / 'adb.exe')
        self.android = shutil.which('android')
        self.env = dict(os.environ, ANDROID_HOME=str(self.sdk))
        if self.state and self.state.get('mode', 'isolated') == 'isolated':
            self.env['ANDROID_AVD_HOME'] = str(self.scratch / 'avds')

    def save(self):
        self.state_path.write_text(json.dumps(self.state, indent=2), encoding='utf-8')

    def log(self, **event):
        self.evidence.mkdir(parents=True, exist_ok=True)
        with (self.evidence / 'actions.jsonl').open('a', encoding='utf-8') as stream:
            stream.write(json.dumps(dict(time=datetime.now(timezone.utc).isoformat(), **event), ensure_ascii=False) + '\n')

    def command(self, args, timeout=60, check=True, stdin=None, env=None):
        result = subprocess.run([str(a) for a in args], cwd=ROOT, env=env or self.env,
                                input=stdin, capture_output=True, text=True, encoding='utf-8',
                                errors='replace', timeout=timeout)
        self.log(command=[str(a) for a in args], exit_code=result.returncode,
                 stdout=result.stdout, stderr=result.stderr)
        if check and result.returncode:
            raise RuntimeError(f'{args[0]} exited {result.returncode}: {result.stdout}\n{result.stderr}')
        return result.stdout.strip()

    def adb_run(self, *args, **kw):
        return self.command([self.adb, '-s', self.state['serial'], *args], **kw)

    def android_run(self, *args, **kw):
        if not self.android:
            raise RuntimeError('android CLI must be installed and on PATH.')
        return self.command([self.android, '--sdk', self.sdk, *args], **kw)

    def lease(self):
        path = self.base.parent / (self.state['serial'] + '.lock')
        try:
            with path.open('x', encoding='utf-8') as stream:
                stream.write(self.base.name)
        except FileExistsError:
            raise RuntimeError(f'Device is leased by another run; inspect {path} and clean up that run first.') from None
        self.state['lease'] = str(path)
        self.save()

    def release(self):
        path = self.base.parent / (self.state['serial'] + '.lock')
        if path.is_file() and path.read_text(encoding='utf-8') == self.base.name:
            path.unlink()

    def prepare(self, mode, avd, serial):
        if self.state or self.base_existed:
            raise RuntimeError('Run directory exists. Clean it up, then choose a fresh --run name.')
        if not re.fullmatch(r'emulator-\d+', serial):
            raise ValueError('This helper accepts emulator serials only; physical phones are not implicit targets.')
        self.evidence.mkdir(parents=True, exist_ok=True)
        self.state = dict(mode=mode, avd=avd, serial=serial, started=False,
                          apk=str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk'))
        self.save()
        self.lease()
        self.state['head'] = self.command(['git', 'rev-parse', 'HEAD'])
        (self.evidence / 'dirty-paths.txt').write_text(self.command(['git', 'status', '--short']), encoding='utf-8')
        version = self.android_run('--version')
        (self.evidence / 'toolchain.json').write_text(json.dumps(dict(android=version, sdk=str(self.sdk),
            adb=self.adb, input_backend='adb'), indent=2), encoding='utf-8')
        self.save()

    def connected(self):
        output = self.command([self.adb, 'devices'])
        return [line.split()[0] for line in output.splitlines()
                if len(line.split()) >= 2 and line.split()[1] == 'device']

    def shared_device(self, avd, device=None):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+', avd):
            raise ValueError('AVD name must contain only letters, digits, underscore, dot or hyphen.')
        devices = self.connected()
        if device and device not in devices:
            raise RuntimeError(f'Requested device {device!r} is not online.')
        matches = []
        for serial in ([device] if device else devices):
            if re.fullmatch(r'emulator-\d+', serial):
                name = self.command([self.adb, '-s', serial, 'emu', 'avd', 'name']).splitlines()[0]
                if name == avd:
                    matches.append(serial)
        if device and not matches:
            raise RuntimeError(f'Requested device is not AVD {avd!r}.')
        if len(matches) > 1:
            raise RuntimeError('Multiple matching AVDs; select the observed --device serial explicitly.')
        return matches[0] if matches else None

    def installed_digest(self):
        paths = self.adb_run('shell', 'pm', 'path', PACKAGE).splitlines()
        if len(paths) != 1 or not paths[0].startswith('package:'):
            raise RuntimeError('Expected one installed FitLog debug APK. Use launch --install to deploy this checkout.')
        return self.adb_run('shell', 'sha256sum', paths[0].removeprefix('package:')).split()[0]

    def attach(self, avd, device):
        serial = self.shared_device(avd, device)
        if not serial:
            raise RuntimeError(f'{avd} is not running; use launch to start it.')
        self.prepare('shared', avd, serial)
        self.owner()
        self.state['apk_sha256'] = self.installed_digest()
        self.state['build_source'] = 'existing-installation'
        self.save()
        self.doctor()
        print(f'Attached: {serial}\nEvidence: {self.evidence}')

    def launch(self, port, avd='Pixel_10', device=None, install=False):
        serial = self.shared_device(avd, device)
        reused = serial is not None
        process = None
        if not serial:
            available = self.command([self.sdk / 'emulator/emulator.exe', '-list-avds']).splitlines()
            if avd not in available:
                raise RuntimeError(f'Existing AVD {avd!r} was not found. Check ANDROID_AVD_HOME; no replacement is created.')
            self.check_port(port)
            serial = f'emulator-{port}'
        self.prepare('shared', avd, serial)
        try:
            if serial not in self.connected():
                if reused:
                    raise RuntimeError('The selected shared emulator disconnected; rediscover it before another launch.')
                process = self.start_emulator(port, isolated=False)
            self.await_boot(process)
            if install:
                self.build()
                self.android_run('run', '--device', serial, '--apks', self.state['apk'],
                                 '--activity', PACKAGE + '.app.MainActivity', timeout=120)
                if self.installed_digest() != self.state['apk_sha256']:
                    raise RuntimeError('Installed APK does not match the built artifact.')
            else:
                self.state['apk_sha256'] = self.installed_digest()
                self.state['build_source'] = 'existing-installation'
                self.adb_run('shell', 'am', 'start', '-W', '-n', COMPONENT)
            self.save()
            self.doctor()
            print(f'Ready: {serial}\nEvidence: {self.evidence}')
        except BaseException:
            if process and process.poll() is None:
                try:
                    self.owner()
                    self.adb_run('emu', 'kill')
                except Exception:
                    process.terminate()
                try:
                    process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill()
            self.cleanup()
            raise

    @staticmethod
    def check_port(port):
        if port % 2 or not 5554 <= port <= 5682:
            raise ValueError('Emulator port must be even, between 5554 and 5682.')
        for candidate in (port, port + 1):
            with socket.socket() as sock:
                sock.bind(('127.0.0.1', candidate))

    def build(self):
        output = self.command(['cmd.exe', '/d', '/c', 'gradlew.bat :app:assembleDebug --console=plain'], timeout=600)
        (self.evidence / 'build.txt').write_text(output, encoding='utf-8')
        self.state['apk_sha256'] = hashlib.sha256(Path(self.state['apk']).read_bytes()).hexdigest()
        self.state['build_source'] = 'checkout-build'
        self.save()

    def start_emulator(self, port, isolated):
        args = [str(self.sdk / 'emulator/emulator.exe'), '-avd', self.state['avd'], '-port', str(port),
                '-no-window', '-no-audio', '-no-boot-anim']
        if isolated:
            args += ['-no-snapshot', '-gpu', 'software', '-camera-back', 'none', '-camera-front', 'none']
        with (self.evidence / 'emulator.stdout.txt').open('w') as stdout, (self.evidence / 'emulator.stderr.txt').open('w') as stderr:
            process = subprocess.Popen(args, env=self.env, cwd=ROOT, stdout=stdout, stderr=stderr,
                                       creationflags=subprocess.CREATE_NO_WINDOW)
        self.state.update(started=True, emulator_pid=process.pid)
        self.save()
        return process

    def await_boot(self, process):
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if process and process.poll() is not None:
                raise RuntimeError('Emulator exited. Inspect emulator stdout/stderr evidence.')
            boot = self.adb_run('shell', 'getprop', 'sys.boot_completed', timeout=10, check=False)
            if boot == '1':
                self.owner()
                return
            time.sleep(2)
        raise RuntimeError('Android did not boot within 180 seconds.')

    def owner(self):
        if not self.state or self.state.get('cleaned'):
            raise RuntimeError('No live instance owned by this run. Launch a fresh run.')
        if 'mode' in self.state:
            lease = self.base.parent / (self.state['serial'] + '.lock')
            if not lease.is_file() or lease.read_text(encoding='utf-8') != self.base.name:
                raise RuntimeError('Device lease is missing or belongs to another run.')
        name = self.adb_run('emu', 'avd', 'name').splitlines()[0]
        if name != self.state['avd']:
            raise RuntimeError(f'Refusing to drive {name!r}; expected {self.state["avd"]!r}.')

    def launch_isolated(self, port):
        if self.state or self.base.exists():
            raise RuntimeError('Run directory exists. Clean it up, then choose a fresh --run name.')
        if not self.android:
            raise RuntimeError('android CLI must be installed and on PATH.')
        image = self.sdk / 'system-images' / 'android-36' / 'default' / 'x86_64'
        if not (image / 'system.img').exists():
            raise RuntimeError('Missing system image. Run: android sdk install system-images/android-36/default/x86_64')
        self.check_port(port)
        self.prepare('isolated', 'FitLogVerify_' + self.base.name, f'emulator-{port}')
        self.env['ANDROID_AVD_HOME'] = str(self.scratch / 'avds')
        (self.scratch / 'avds').mkdir(parents=True)
        process = None
        try:
            self.build()
            avdmanager = self.sdk / 'cmdline-tools/latest/bin/avdmanager.bat'
            args = [str(avdmanager), 'create', 'avd', '--name', self.state['avd'], '--package',
                    'system-images;android-36;default;x86_64', '--device', 'pixel_7',
                    '--path', str(self.scratch / 'avds' / (self.state['avd'] + '.avd'))]
            self.command(['cmd.exe', '/d', '/c', subprocess.list2cmdline(args)], stdin='no\n', timeout=90)
            config = self.scratch / 'avds' / (self.state['avd'] + '.avd') / 'config.ini'
            # These are private AVD settings, not modifications of a shared device.
            with config.open('a', encoding='utf-8') as out:
                out.write('\nhw.keyboard=yes\nhw.ramSize=2048\ndisk.dataPartition.size=2G\n')
            process = self.start_emulator(port, isolated=True)
            self.await_boot(process)
            self.owner()
            self.adb_run('shell', 'input', 'keyevent', '82')
            self.android_run('run', '--device', self.state['serial'], '--apks', self.state['apk'],
                             '--activity', PACKAGE + '.app.MainActivity', timeout=120)
            self.wait('Record training', 60)
            self.doctor()
            self.log(checkpoint='launch-ready', result='PASS')
            print(f'Ready: {self.state["serial"]}\nEvidence: {self.evidence}')
        except BaseException:
            if process and process.poll() is None:
                try:
                    self.owner()
                    self.adb_run('emu', 'kill')
                except Exception:
                    # The live Popen handle belongs to this launch, never a discovered shared PID.
                    process.terminate()
                try:
                    process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill()
            self.cleanup()
            raise

    def doctor(self, allow_picker=False):
        self.owner()
        boot = self.adb_run('shell', 'getprop', 'sys.boot_completed')
        pid = self.adb_run('shell', 'pidof', PACKAGE)
        activity = self.adb_run('shell', 'dumpsys', 'activity', 'activities')
        resumed = [line.strip() for line in activity.splitlines() if 'ResumedActivity' in line]
        digest = self.installed_digest()
        foreground = (PACKAGE, 'com.google.android.documentsui', 'com.android.documentsui') if allow_picker else (PACKAGE,)
        if boot != '1' or not pid or not any(package + '/' in line for package in foreground for line in resumed) or digest != self.state['apk_sha256']:
            raise RuntimeError('Doctor failed: boot, live foreground activity, or exact APK identity differs.')
        report = dict(result='PASS', serial=self.state['serial'], avd=self.state['avd'],
                      pid=pid, resumed=resumed, apk_sha256=digest, head=self.state['head'],
                      mode=self.state.get('mode', 'isolated'), build_source=self.state.get('build_source'), allow_picker=allow_picker)
        (self.evidence / 'doctor.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        self.state['needs_doctor'] = False
        self.save()
        print(json.dumps(report, indent=2))

    def layout(self, quiet=False):
        self.owner()
        path = self.evidence / 'latest-layout.json'
        self.android_run('layout', '--device', self.state['serial'], '--full', '--flat', '--output', path, timeout=45)
        data = json.loads(path.read_text(encoding='utf-8-sig'))
        archive = self.evidence / f'layout-{time.time_ns()}.json'
        shutil.copyfile(path, archive)
        self.log(layout=str(archive.relative_to(self.base)))
        # Exclude status/navigation bars and IME. The active application can also be SAF.
        if isinstance(data, list):
            windows = [w for w in data if isinstance(w, dict) and w.get('type') == 'APPLICATION']
            if windows:
                data = [w for w in windows if w.get('focused')] or windows
        nodes = []
        def walk(value):
            if isinstance(value, dict):
                if 'bounds' in value:
                    nodes.append(value)
                for child in value.values():
                    if isinstance(child, (dict, list)):
                        walk(child)
            elif isinstance(value, list):
                for child in value:
                    walk(child)
        walk(data)
        if not nodes:
            raise RuntimeError('Android layout returned no bounded elements. Inspect latest-layout.json.')
        if not quiet:
            print(json.dumps(nodes, indent=2, ensure_ascii=False))
        return nodes

    @staticmethod
    def labels(node):
        return [str(node[key]) for key in ('text', 'content-desc', 'contentDesc', 'contentDescription') if node.get(key)]

    def matches(self, text, nodes=None):
        return [n for n in (nodes if nodes is not None else self.layout(True))
                if not n.get('off-screen') and any(re.fullmatch(text, label, re.S) for label in self.labels(n))]

    def wait(self, text, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.matches(text):
                self.log(assert_visible=text, result='PASS')
                return
            time.sleep(0.5)
        raise RuntimeError(f'Timed out waiting for {text!r}.')

    @staticmethod
    def bounds(node):
        values = [int(v) for v in re.findall(r'-?\d+', str(node['bounds']))]
        if len(values) != 4 or values[2] <= values[0] or values[3] <= values[1]:
            raise RuntimeError(f'Invalid/off-screen bounds: {node["bounds"]}')
        return values

    def tap(self, text, index=None):
        nodes = self.matches(text)
        described = [n for n in nodes if any(re.fullmatch(text, str(n.get(key, '')), re.S)
                     for key in ('content-desc', 'contentDesc', 'contentDescription'))]
        if described:
            nodes = described
        if not nodes or (len(nodes) != 1 and index is None):
            raise RuntimeError(f'{text!r} matched {len(nodes)} elements; inspect layout and choose --index if needed.')
        node = nodes[index or 0]
        x1, y1, x2, y2 = self.bounds(node)
        self.log(action='tap', selector=text, index=index, node=node)
        self.adb_run('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))

    def input(self, value):
        nodes = self.layout(True)
        focused = [n for n in nodes if ('FOCUSED' in [s.upper() for s in n.get('state', [])] or n.get('focused'))
                   and ('EditText' in n.get('class', '') or 'EDITABLE' in [s.upper() for s in n.get('interactions', [])])]
        if not focused:
            raise RuntimeError('No editable field is focused. Inspect layout and tap the field first.')
        if not re.fullmatch(r'[A-Za-z0-9 ._-]+', value):
            raise ValueError('Use ASCII alphanumeric fixture text (spaces, dot, underscore, hyphen allowed).')
        self.adb_run('shell', 'input', 'text', value.replace(' ', '%s'))

    def back(self):
        self.owner()
        self.adb_run('shell', 'input', 'keyevent', '4')

    def scroll(self, direction):
        nodes = [n for n in self.layout(True) if 'SCROLLABLE' in [s.upper() for s in n.get('interactions', [])] and not n.get('off-screen')]
        if len(nodes) != 1:
            raise RuntimeError(f'Expected one visible scroll container, found {len(nodes)}. Inspect layout.')
        x1, y1, x2, y2 = self.bounds(nodes[0])
        x = (x1 + x2) // 2
        upper, lower = y1 + (y2-y1)//4, y2 - (y2-y1)//4
        start, end = (lower, upper) if direction == 'down' else (upper, lower)
        self.adb_run('shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '600')

    def capture(self, name):
        if not re.fullmatch(r'[A-Za-z0-9_-]+', name):
            raise ValueError('Checkpoint name must be letters, digits, underscores or hyphens.')
        self.layout(True)
        shutil.copyfile(self.evidence / 'latest-layout.json', self.evidence / (name + '.json'))
        path = self.evidence / (name + '.png')
        self.android_run('screen', 'capture', '--device', self.state['serial'], '-o', path)
        print(f'Visually inspect {path} before the next UI action.')

    def restart(self):
        self.owner()
        self.adb_run('shell', 'am', 'force-stop', PACKAGE)
        self.adb_run('shell', 'am', 'start', '-W', '-n', COMPONENT)
        self.wait('Record training', 30)

    def database(self, name=None):
        name = name or f'database-{time.time_ns()}'
        if not re.fullmatch(r'[A-Za-z0-9_-]+', name):
            raise ValueError('Snapshot name must be letters, digits, underscores or hyphens.')
        self.owner()
        target = self.evidence / name
        target.mkdir()
        self.adb_run('shell', 'am', 'force-stop', PACKAGE)
        entries = self.adb_run('shell', 'run-as', PACKAGE, 'ls').splitlines()
        files = self.adb_run('shell', 'run-as', PACKAGE, 'ls', 'databases').splitlines() if 'databases' in entries else []
        for filename in files:
            if filename in ('training-records.db', 'training-records.db-wal', 'training-records.db-shm'):
                args = [self.adb, '-s', self.state['serial'], 'exec-out', 'run-as', PACKAGE, 'cat', 'databases/' + filename]
                result = subprocess.run(args, capture_output=True, timeout=30)
                if result.returncode:
                    raise RuntimeError(result.stderr.decode(errors='replace'))
                (target / filename).write_bytes(result.stdout)
                self.log(command=args, bytes=len(result.stdout), sha256=hashlib.sha256(result.stdout).hexdigest())
        db = target / 'training-records.db'
        if not db.exists():
            rows = {table: [] for table in ('confirmed_diary', 'confirmed_session', 'confirmed_exercise', 'confirmed_set')}
            (target / 'rows.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
            self.log(checkpoint='database-not-created', result='PASS', rows=rows)
            return rows
        with sqlite3.connect(db.as_uri() + '?mode=ro', uri=True) as connection:
            connection.row_factory = sqlite3.Row
            rows = {table: [dict(row) for row in connection.execute('SELECT * FROM ' + table)]
                    for table in ('confirmed_diary', 'confirmed_session', 'confirmed_exercise', 'confirmed_set')}
        (target / 'rows.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
        print(json.dumps(rows, indent=2))
        return rows

    def smoke(self):
        if self.state.get('mode', 'isolated') != 'isolated':
            raise RuntimeError('smoke requires launch --isolated; shared app data must be preserved.')
        self.doctor()
        self.wait('Record training')
        self.tap('Record training')
        self.wait('Saved automatically')
        self.tap('Back')
        self.wait('Record training')
        self.tap('Log')
        self.wait('No local training records yet\\.')
        self.log(feature='local-records.blank', result='PASS')
        self.tap('Today')
        self.tap('Record training')
        self.tap('Add exercise')
        self.tap('Exercise name')
        name = 'VerifySquat'
        self.input(name)
        self.back()  # Dismiss IME, preserving the editor.
        self.tap('Repetitions')
        self.input('8')
        self.back()
        self.wait('Saved locally')
        self.tap('Back')
        self.tap('Log')
        self.wait(name)
        self.wait('1 completed set')
        self.tap(name)
        self.wait(name)
        self.wait('8')
        self.restart()
        self.tap('Log')
        self.wait(name)
        self.tap(name)
        self.wait(name)
        self.wait('8')
        rows = self.database('persisted-database')
        assert len(rows['confirmed_diary']) == 1, 'Expected exactly one local record.'
        assert rows['confirmed_diary'][0]['origin'] == 'NATIVE'
        assert len(rows['confirmed_exercise']) == 1 and rows['confirmed_exercise'][0]['rawName'] == name
        assert len(rows['confirmed_set']) == 1 and rows['confirmed_set'][0]['reps'] == 8
        self.log(feature='local-records.today-create-restart', result='PASS')
        self.restart()
        self.tap('Log')
        self.tap(name)
        self.wait('8')
        self.capture('local-reopened')
        print('PASS: blank editor, auto-save, Log reopen, process restart, and persisted SQLite rows.')

    def cleanup(self):
        if not self.state:
            return
        if self.state.get('cleaned'):
            print(f'Already cleaned. Evidence retained: {self.evidence}')
            return
        retained = {str(path.relative_to(self.evidence)): path.stat().st_size
                    for path in self.evidence.rglob('*') if path.is_file()}
        isolated = self.state.get('mode', 'isolated') == 'isolated'
        should_stop = self.state.get('started', isolated)
        devices = self.connected() if should_stop else []
        if self.state['serial'] in devices:
            self.owner()
            self.adb_run('emu', 'kill')
            deadline = time.monotonic() + 30
            while self.state['serial'] in self.connected():
                if time.monotonic() > deadline:
                    raise RuntimeError('Owned emulator still present; retain scratch and investigate.')
                time.sleep(1)
        # ADB absence alone does not mean an emulator has exited. Inspect only our unique AVD.
        script = ("Get-CimInstance Win32_Process | Where-Object { "
                  "$_.Name -in @('emulator.exe','qemu-system-x86_64.exe') -and "
                  f"$_.CommandLine -like '*{self.state['avd']}*' "
                  "} | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress")
        deadline = time.monotonic() + 30
        while should_stop and self.command(['powershell.exe', '-NoProfile', '-Command', script]):
            if time.monotonic() > deadline:
                raise RuntimeError('Owned emulator process still exists; retaining its scratch directory.')
            time.sleep(1)
        # Resolve and check the complete recursive deletion target before deleting anything.
        target = self.scratch.resolve()
        expected = (ROOT / 'build' / 'verification' / self.base.name / 'scratch').resolve()
        if target != expected or self.scratch.is_symlink() or self.base.is_symlink():
            raise RuntimeError('Refusing unexpected scratch path.')
        if isolated and self.scratch.exists():
            shutil.rmtree(target)
        for name, size in retained.items():
            path = self.evidence / name
            if not path.is_file() or path.stat().st_size < size:
                raise RuntimeError(f'Evidence lost during cleanup: {name}')
        self.release()
        self.state['cleaned'] = True
        self.save()
        assert (self.evidence / 'actions.jsonl').is_file(), 'Evidence was lost during cleanup.'
        self.log(action='cleanup', result='PASS', evidence_preserved=True)
        print(f'Cleaned {self.state.get("mode", "isolated")} run; existing AVD/data preserved. Evidence retained: {self.evidence}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    for command in ('launch', 'attach', 'doctor', 'layout', 'tap', 'input', 'back', 'scroll', 'wait', 'capture', 'restart', 'database', 'smoke', 'cleanup'):
        p = sub.add_parser(command)
        p.add_argument('--run', required=True)
        if command == 'launch':
            p.add_argument('--port', type=int, default=5580)
            p.add_argument('--isolated', action='store_true', help='Create a disposable empty-data AVD instead of Pixel_10.')
            p.add_argument('--install', action='store_true', help='Build this checkout and deploy via android run, preserving shared app data.')
        if command in ('launch', 'attach'):
            p.add_argument('--avd', default='Pixel_10')
            p.add_argument('--device', help='Observed serial of an already running emulator.')
        if command == 'doctor':
            p.add_argument('--allow-picker', action='store_true', help='Allow the real SAF picker in the foreground.')
        if command in ('tap', 'wait'):
            p.add_argument('--text', required=True)
        if command == 'tap':
            p.add_argument('--index', type=int)
        if command == 'wait':
            p.add_argument('--timeout', type=int, default=30)
        if command == 'input':
            p.add_argument('--value', required=True)
        if command == 'capture':
            p.add_argument('--name', required=True)
        if command == 'database':
            p.add_argument('--name', help='Fresh snapshot directory name; defaults to a unique timestamp.')
        if command == 'scroll':
            p.add_argument('--direction', choices=('up', 'down'), required=True)
    args = parser.parse_args()
    driver = Driver(args.run)
    try:
        if driver.state.get('needs_doctor') and args.action not in ('doctor', 'cleanup', 'restart'):
            raise RuntimeError('Previous action failed. Run doctor before driving again, or restart to a known state.')
        if args.action == 'launch':
            if args.isolated:
                if args.device or args.avd != 'Pixel_10' or args.install:
                    raise ValueError('--isolated builds/installs its own AVD; do not combine with --device, --avd or --install.')
                driver.launch_isolated(args.port)
            else:
                driver.launch(args.port, args.avd, args.device, args.install)
        elif args.action == 'attach':
            driver.attach(args.avd, args.device)
        elif args.action == 'doctor':
            driver.doctor(args.allow_picker)
        elif args.action == 'restart':
            driver.restart()
            driver.doctor()
        elif args.action == 'tap':
            driver.owner()
            driver.tap(args.text, args.index)
        elif args.action == 'input':
            driver.input(args.value)
        elif args.action == 'wait':
            driver.wait(args.text, args.timeout)
        elif args.action == 'capture':
            driver.capture(args.name)
        elif args.action == 'database':
            driver.database(args.name)
        elif args.action == 'scroll':
            driver.scroll(args.direction)
        else:
            getattr(driver, args.action)()
    except Exception as error:
        if driver.state and not driver.state.get('cleaned'):
            driver.state['needs_doctor'] = True
            driver.save()
        driver.log(error=str(error), action=args.action, result='FAIL')
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
