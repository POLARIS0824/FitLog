"""Regression checks for shared-device ownership and evidence preservation."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock

import verify


class DeviceLifecycleTest(unittest.TestCase):
    def setUp(self):
        test_root = verify.ROOT / 'build' / 'verification' / 'harness-tests'
        test_root.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=test_root)
        self.root = Path(self.temporary.name)
        self.root_patch = patch.object(verify, 'ROOT', self.root)
        self.root_patch.start()
        self.environment = patch.dict(os.environ, {'ANDROID_HOME': str(self.root / 'sdk'),
            'ANDROID_AVD_HOME': str(self.root / 'user-avds')})
        self.environment.start()

    def tearDown(self):
        self.environment.stop()
        self.root_patch.stop()
        self.temporary.cleanup()

    def session(self, name='daily', mode='shared', started=False):
        driver = verify.Driver(name)
        driver.evidence.mkdir(parents=True)
        driver.state = dict(mode=mode, avd='Pixel_10', serial='emulator-5580', started=started)
        driver.save()
        driver.lease()
        driver.log(checkpoint='before')
        (driver.evidence / 'proof.png').write_bytes(b'retained-image')
        return driver

    def test_shared_environment_keeps_user_avds(self):
        driver = self.session()
        reloaded = verify.Driver('daily')
        self.assertEqual(reloaded.env['ANDROID_AVD_HOME'], str(self.root / 'user-avds'))
        self.assertEqual(driver.state['mode'], 'shared')

    def test_isolated_environment_uses_run_scratch(self):
        driver = self.session(mode='isolated')
        reloaded = verify.Driver('daily')
        self.assertEqual(reloaded.env['ANDROID_AVD_HOME'], str(driver.scratch / 'avds'))

    def test_attached_cleanup_never_calls_device_and_preserves_data(self):
        driver = self.session()
        driver.scratch.mkdir()
        (driver.scratch / 'sentinel').write_text('keep')
        driver.command = Mock(side_effect=AssertionError('Attached cleanup must not call a device'))
        driver.cleanup()
        self.assertTrue(driver.state['cleaned'])
        self.assertTrue((driver.scratch / 'sentinel').is_file())
        self.assertEqual((driver.evidence / 'proof.png').read_bytes(), b'retained-image')
        self.assertFalse((driver.base.parent / 'emulator-5580.lock').exists())
        driver.cleanup()

    def test_owned_shared_cleanup_stops_exact_serial_but_keeps_avd(self):
        driver = self.session(started=True)
        driver.connected = Mock(side_effect=[['emulator-5580'], []])
        driver.owner = Mock()
        driver.adb_run = Mock()
        driver.command = Mock(return_value='')
        driver.scratch.mkdir()
        driver.cleanup()
        driver.adb_run.assert_called_once_with('emu', 'kill')
        self.assertTrue(driver.scratch.is_dir())
        self.assertTrue((driver.evidence / 'proof.png').exists())

    def test_isolated_cleanup_removes_only_scratch(self):
        driver = self.session(mode='isolated')
        driver.scratch.mkdir()
        (driver.scratch / 'temporary-avd').write_text('disposable')
        driver.cleanup()
        self.assertFalse(driver.scratch.exists())
        self.assertEqual((driver.evidence / 'proof.png').read_bytes(), b'retained-image')

    def test_failed_shared_boot_terminates_its_owned_process(self):
        driver = self.session(started=True)
        driver.shared_device = Mock(return_value=None)
        driver.command = Mock(return_value='Pixel_10')
        driver.check_port = Mock()
        driver.prepare = Mock()
        driver.connected = Mock(return_value=[])
        process = Mock()
        process.poll.return_value = None
        driver.start_emulator = Mock(return_value=process)
        driver.await_boot = Mock(side_effect=RuntimeError('boot failed'))
        driver.owner = Mock(side_effect=RuntimeError('not online'))
        driver.cleanup = Mock()
        with self.assertRaisesRegex(RuntimeError, 'boot failed'):
            driver.launch(5580)
        process.terminate.assert_called_once()
        process.wait.assert_called_once_with(timeout=30)
        driver.cleanup.assert_called_once()

    def test_disappearing_reused_device_never_starts_another_instance(self):
        driver = self.session()
        driver.shared_device = Mock(return_value='emulator-5554')
        driver.prepare = Mock()
        driver.connected = Mock(return_value=[])
        driver.start_emulator = Mock()
        driver.cleanup = Mock()
        with self.assertRaisesRegex(RuntimeError, 'disconnected'):
            driver.launch(5580)
        driver.start_emulator.assert_not_called()

    def test_wrong_avd_prevents_shutdown_and_keeps_lease(self):
        driver = self.session(started=True)
        driver.connected = Mock(return_value=['emulator-5580'])
        driver.adb_run = Mock(return_value='Another_AVD\nOK')
        with self.assertRaisesRegex(RuntimeError, 'Refusing to drive'):
            driver.cleanup()
        driver.adb_run.assert_called_once_with('emu', 'avd', 'name')
        self.assertFalse(driver.state.get('cleaned', False))
        self.assertTrue((driver.base.parent / 'emulator-5580.lock').exists())

    def test_second_run_cannot_lease_or_release_first_device(self):
        first = self.session()
        second = verify.Driver('second')
        second.evidence.mkdir(parents=True)
        second.state = dict(mode='shared', serial='emulator-5580', started=False)
        with self.assertRaisesRegex(RuntimeError, 'leased by another run'):
            second.lease()
        second.release()
        self.assertEqual((first.base.parent / 'emulator-5580.lock').read_text(), 'daily')
        second.state['avd'] = 'Pixel_10'
        second.adb_run = Mock()
        with self.assertRaisesRegex(RuntimeError, 'lease is missing'):
            second.owner()
        second.adb_run.assert_not_called()

    def test_discovery_logging_does_not_prevent_initialization(self):
        driver = verify.Driver('probe')
        driver.log(command=['adb', 'devices'])
        driver.command = Mock(return_value='head')
        driver.android_run = Mock(return_value='Android CLI test')
        driver.prepare('shared', 'Pixel_10', 'emulator-5580')
        self.assertEqual(driver.state['mode'], 'shared')
        self.assertTrue((driver.evidence / 'toolchain.json').is_file())

    def test_uncreated_database_is_empty_without_creating_sqlite(self):
        driver = self.session()
        driver.owner = Mock()
        driver.adb_run = Mock(side_effect=['', 'files\ncache'])
        rows = driver.database('empty')
        self.assertTrue(all(not values for values in rows.values()))
        self.assertFalse((driver.evidence / 'empty' / 'training-records.db').exists())
        self.assertTrue((driver.evidence / 'empty' / 'rows.json').is_file())
        with self.assertRaises(FileExistsError):
            driver.database('empty')
        self.assertEqual(driver.adb_run.call_count, 2)

    def test_default_database_snapshots_are_independent(self):
        driver = self.session()
        driver.owner = Mock()
        driver.adb_run = Mock(side_effect=['', 'files', '', 'files'])
        driver.database()
        driver.database()
        snapshots = list(driver.evidence.glob('database-*/rows.json'))
        self.assertEqual(len(snapshots), 2)
        self.assertEqual(snapshots[0].read_bytes(), snapshots[1].read_bytes())

    def test_smoke_rejects_shared_before_any_drive(self):
        driver = self.session()
        driver.doctor = Mock(side_effect=AssertionError('Must reject before health check/drive'))
        with self.assertRaisesRegex(RuntimeError, 'requires launch --isolated'):
            driver.smoke()
        driver.doctor.assert_not_called()

    def test_requested_serial_must_be_matching_emulator(self):
        driver = verify.Driver('selection')
        driver.connected = Mock(return_value=['emulator-5554', 'physical-phone'])
        driver.command = Mock(return_value='Wrong_AVD\nOK')
        for serial in ('emulator-5554', 'physical-phone'):
            with self.assertRaisesRegex(RuntimeError, 'not AVD'):
                driver.shared_device('Pixel_10', serial)
        self.assertEqual(driver.command.call_count, 1)

    def test_ambiguous_matching_devices_require_explicit_serial(self):
        driver = verify.Driver('selection')
        driver.connected = Mock(return_value=['emulator-5554', 'emulator-5556'])
        driver.command = Mock(return_value='Pixel_10\nOK')
        with self.assertRaisesRegex(RuntimeError, 'Multiple matching'):
            driver.shared_device('Pixel_10')
        self.assertEqual(driver.shared_device('Pixel_10', 'emulator-5556'), 'emulator-5556')

    def test_failed_doctor_does_not_accept_replaced_apk(self):
        driver = self.session()
        driver.state.update(apk_sha256='expected', head='test')
        driver.owner = Mock()
        driver.adb_run = Mock(side_effect=['1', '123', 'mResumedActivity com.example.fitlog/.app.MainActivity'])
        driver.installed_digest = Mock(return_value='replaced')
        with self.assertRaisesRegex(RuntimeError, 'Doctor failed'):
            driver.doctor()
        self.assertFalse((driver.evidence / 'doctor.json').exists())

    def test_picker_must_be_explicitly_allowed(self):
        driver = self.session()
        driver.state.update(apk_sha256='expected', head='test')
        driver.owner = Mock()
        driver.adb_run = Mock(side_effect=['1', '123', 'mResumedActivity com.google.android.documentsui/.picker.PickActivity'] * 2)
        driver.installed_digest = Mock(return_value='expected')
        with self.assertRaisesRegex(RuntimeError, 'Doctor failed'):
            driver.doctor()
        driver.doctor(allow_picker=True)
        self.assertTrue(json.loads((driver.evidence / 'doctor.json').read_text())['allow_picker'])


if __name__ == '__main__':
    unittest.main()
