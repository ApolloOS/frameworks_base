/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apkverity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.platform.test.annotations.RootPermissionTest;

import com.android.blockdevicewriter.BlockDeviceWriter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This test makes sure app installs with fs-verity signature, and on-access verification works.
 *
 * <p>When an app is installed, all or none of the files should have their corresponding .fsv_sig
 * signature file. Otherwise, install will fail.
 *
 * <p>Once installed, file protected by fs-verity is verified by kernel every time a block is loaded
 * from disk to memory. The file is immutable by design, enforced by filesystem.
 *
 * <p>In order to make sure a block of the file is readable only if the underlying block on disk
 * stay intact, the test needs to bypass the filesystem and tampers with the corresponding physical
 * address against the block device.
 *
 * <p>Requirements to run this test:
 * <ul>
 *   <li>Device is rootable</li>
 *   <li>The filesystem supports fs-verity</li>
 *   <li>The feature flag is enabled</li>
 * </ul>
 */
@RootPermissionTest
@RunWith(DeviceJUnit4ClassRunner.class)
public class ApkVerityTest extends BaseHostJUnit4Test {
    private static final String TARGET_PACKAGE = "com.android.apkverity";

    private static final String BASE_APK = "ApkVerityTestApp.apk";
    private static final String BASE_APK_DM = "ApkVerityTestApp.dm";
    private static final String SPLIT_APK = "ApkVerityTestAppSplit.apk";
    private static final String SPLIT_APK_DM = "ApkVerityTestAppSplit.dm";

    private static final String INSTALLED_BASE_APK = "base.apk";
    private static final String INSTALLED_BASE_DM = "base.dm";
    private static final String INSTALLED_SPLIT_APK = "split_feature_x.apk";
    private static final String INSTALLED_SPLIT_DM = "split_feature_x.dm";
    private static final String INSTALLED_BASE_APK_FSV_SIG = "base.apk.fsv_sig";
    private static final String INSTALLED_BASE_DM_FSV_SIG = "base.dm.fsv_sig";
    private static final String INSTALLED_SPLIT_APK_FSV_SIG = "split_feature_x.apk.fsv_sig";
    private static final String INSTALLED_SPLIT_DM_FSV_SIG = "split_feature_x.dm.fsv_sig";

    private static final String DAMAGING_EXECUTABLE = "/data/local/tmp/block_device_writer";
    private static final String CERT_PATH = "/data/local/tmp/ApkVerityTestCert.der";

    /** Only 4K page is supported by fs-verity currently. */
    private static final int FSVERITY_PAGE_SIZE = 4096;

    private ITestDevice mDevice;
    private boolean mDmRequireFsVerity;

    @Before
    public void setUp() throws DeviceNotAvailableException {
        mDevice = getDevice();
        mDmRequireFsVerity = "true".equals(
                mDevice.getProperty("pm.dexopt.dm.require_fsverity"));

        expectRemoteCommandToSucceed("cmd file_integrity append-cert " + CERT_PATH);
        uninstallPackage(TARGET_PACKAGE);
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        expectRemoteCommandToSucceed("cmd file_integrity remove-last-cert");
        uninstallPackage(TARGET_PACKAGE);
    }

    @Test
    public void testFsverityKernelSupports() throws DeviceNotAvailableException {
        ITestDevice.MountPointInfo mountPoint = mDevice.getMountPointInfo("/data");
        expectRemoteCommandToSucceed("test -f /sys/fs/" + mountPoint.type + "/features/verity");
    }

    @Test
    public void testInstallBase() throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));

        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG);
        verifyInstalledFilesHaveFsverity(INSTALLED_BASE_APK);
    }

    @Test
    public void testInstallBaseWithWrongSignature()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(SPLIT_APK_DM + ".fsv_sig",
                        BASE_APK + ".fsv_sig")
                .runExpectingFailure();
    }

    @Test
    public void testInstallBaseWithSplit()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .addFileAndSignature(SPLIT_APK)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));

        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG,
                INSTALLED_SPLIT_APK,
                INSTALLED_SPLIT_APK_FSV_SIG);
        verifyInstalledFilesHaveFsverity(
                INSTALLED_BASE_APK,
                INSTALLED_SPLIT_APK);
    }

    @Test
    public void testInstallBaseWithDm() throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .addFileAndSignature(BASE_APK_DM)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));

        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG,
                INSTALLED_BASE_DM,
                INSTALLED_BASE_DM_FSV_SIG);
        verifyInstalledFilesHaveFsverity(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_DM);
    }

    @Test
    public void testInstallEverything() throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .addFileAndSignature(BASE_APK_DM)
                .addFileAndSignature(SPLIT_APK)
                .addFileAndSignature(SPLIT_APK_DM)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));

        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG,
                INSTALLED_BASE_DM,
                INSTALLED_BASE_DM_FSV_SIG,
                INSTALLED_SPLIT_APK,
                INSTALLED_SPLIT_APK_FSV_SIG,
                INSTALLED_SPLIT_DM,
                INSTALLED_SPLIT_DM_FSV_SIG);
        verifyInstalledFilesHaveFsverity(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_DM,
                INSTALLED_SPLIT_APK,
                INSTALLED_SPLIT_DM);
    }

    @Test
    public void testInstallSplitOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));
        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG);

        new InstallMultiple()
                .inheritFrom(TARGET_PACKAGE)
                .addFileAndSignature(SPLIT_APK)
                .run();

        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG,
                INSTALLED_SPLIT_APK,
                INSTALLED_SPLIT_APK_FSV_SIG);
        verifyInstalledFilesHaveFsverity(
                INSTALLED_BASE_APK,
                INSTALLED_SPLIT_APK);
    }

    @Test
    public void testInstallSplitOnlyMissingSignature()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));
        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG);

        new InstallMultiple()
                .inheritFrom(TARGET_PACKAGE)
                .addFile(SPLIT_APK)
                .runExpectingFailure();
    }

    @Test
    public void testInstallSplitOnlyWithoutBaseSignature()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));
        verifyInstalledFiles(INSTALLED_BASE_APK);

        new InstallMultiple()
                .inheritFrom(TARGET_PACKAGE)
                .addFileAndSignature(SPLIT_APK)
                .run();
        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_SPLIT_APK,
                INSTALLED_SPLIT_APK_FSV_SIG);
    }

    @Test
    public void testInstallOnlyDmHasFsvSig()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFileAndSignature(BASE_APK_DM)
                .addFile(SPLIT_APK)
                .addFileAndSignature(SPLIT_APK_DM)
                .run();
        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_DM,
                INSTALLED_BASE_DM_FSV_SIG,
                INSTALLED_SPLIT_APK,
                INSTALLED_SPLIT_DM,
                INSTALLED_SPLIT_DM_FSV_SIG);
        verifyInstalledFilesHaveFsverity(
                INSTALLED_BASE_DM,
                INSTALLED_SPLIT_DM);
    }

    @Test
    public void testInstallDmWithoutFsvSig_Base()
            throws DeviceNotAvailableException, FileNotFoundException {
        InstallMultiple installer = new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(SPLIT_APK)
                .addFileAndSignature(SPLIT_APK_DM);
        if (mDmRequireFsVerity) {
            installer.runExpectingFailure();
        } else {
            installer.run();
            verifyInstalledFiles(
                    INSTALLED_BASE_APK,
                    INSTALLED_BASE_DM,
                    INSTALLED_SPLIT_APK,
                    INSTALLED_SPLIT_DM,
                    INSTALLED_SPLIT_DM_FSV_SIG);
            verifyInstalledFilesHaveFsverity(INSTALLED_SPLIT_DM);
        }
    }

    @Test
    public void testInstallDmWithoutFsvSig_Split()
            throws DeviceNotAvailableException, FileNotFoundException {
        InstallMultiple installer = new InstallMultiple()
                .addFile(BASE_APK)
                .addFileAndSignature(BASE_APK_DM)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM);
        if (mDmRequireFsVerity) {
            installer.runExpectingFailure();
        } else {
            installer.run();
            verifyInstalledFiles(
                    INSTALLED_BASE_APK,
                    INSTALLED_BASE_DM,
                    INSTALLED_BASE_DM_FSV_SIG,
                    INSTALLED_SPLIT_APK,
                    INSTALLED_SPLIT_DM);
            verifyInstalledFilesHaveFsverity(INSTALLED_BASE_DM);
        }
    }

    @Test
    public void testInstallSomeApkIsMissingFsvSig_Base()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .addFileAndSignature(BASE_APK_DM)
                .addFile(SPLIT_APK)
                .addFileAndSignature(SPLIT_APK_DM)
                .runExpectingFailure();
    }

    @Test
    public void testInstallSomeApkIsMissingFsvSig_Split()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFileAndSignature(BASE_APK_DM)
                .addFileAndSignature(SPLIT_APK)
                .addFileAndSignature(SPLIT_APK_DM)
                .runExpectingFailure();
    }

    @Test
    public void testInstallBaseWithFsvSigThenSplitWithout()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFileAndSignature(BASE_APK)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));
        verifyInstalledFiles(
                INSTALLED_BASE_APK,
                INSTALLED_BASE_APK_FSV_SIG);

        new InstallMultiple()
                .addFile(SPLIT_APK)
                .runExpectingFailure();
    }

    @Test
    public void testInstallBaseWithoutFsvSigThenSplitWith()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .run();
        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));
        verifyInstalledFiles(INSTALLED_BASE_APK);

        new InstallMultiple()
                .addFileAndSignature(SPLIT_APK)
                .runExpectingFailure();
    }

    @Test
    public void testFsverityFileIsImmutableAndReadable() throws DeviceNotAvailableException {
        new InstallMultiple().addFileAndSignature(BASE_APK).run();
        String apkPath = getApkPath(TARGET_PACKAGE);

        assertNotNull(getDevice().getAppPackageInfo(TARGET_PACKAGE));
        expectRemoteCommandToFail("echo -n '' >> " + apkPath);
        expectRemoteCommandToSucceed("cat " + apkPath + " > /dev/null");
    }

    @Test
    public void testFsverityFailToReadModifiedBlockAtFront() throws DeviceNotAvailableException {
        new InstallMultiple().addFileAndSignature(BASE_APK).run();
        String apkPath = getApkPath(TARGET_PACKAGE);

        long apkSize = getFileSizeInBytes(apkPath);
        long offsetFirstByte = 0;

        // The first two pages should be both readable at first.
        assertTrue(BlockDeviceWriter.canReadByte(mDevice, apkPath, offsetFirstByte));
        if (apkSize > offsetFirstByte + FSVERITY_PAGE_SIZE) {
            assertTrue(BlockDeviceWriter.canReadByte(mDevice, apkPath,
                    offsetFirstByte + FSVERITY_PAGE_SIZE));
        }

        // Damage the file directly against the block device.
        damageFileAgainstBlockDevice(apkPath, offsetFirstByte);

        // Expect actual read from disk to fail but only at damaged page.
        BlockDeviceWriter.dropCaches(mDevice);
        assertFalse(BlockDeviceWriter.canReadByte(mDevice, apkPath, offsetFirstByte));
        if (apkSize > offsetFirstByte + FSVERITY_PAGE_SIZE) {
            long lastByteOfTheSamePage =
                    offsetFirstByte % FSVERITY_PAGE_SIZE + FSVERITY_PAGE_SIZE - 1;
            assertFalse(BlockDeviceWriter.canReadByte(mDevice, apkPath, lastByteOfTheSamePage));
            assertTrue(BlockDeviceWriter.canReadByte(mDevice, apkPath, lastByteOfTheSamePage + 1));
        }
    }

    @Test
    public void testFsverityFailToReadModifiedBlockAtBack() throws DeviceNotAvailableException {
        new InstallMultiple().addFileAndSignature(BASE_APK).run();
        String apkPath = getApkPath(TARGET_PACKAGE);

        long apkSize = getFileSizeInBytes(apkPath);
        long offsetOfLastByte = apkSize - 1;

        // The first two pages should be both readable at first.
        assertTrue(BlockDeviceWriter.canReadByte(mDevice, apkPath, offsetOfLastByte));
        if (offsetOfLastByte - FSVERITY_PAGE_SIZE > 0) {
            assertTrue(BlockDeviceWriter.canReadByte(mDevice, apkPath,
                    offsetOfLastByte - FSVERITY_PAGE_SIZE));
        }

        // Damage the file directly against the block device.
        damageFileAgainstBlockDevice(apkPath, offsetOfLastByte);

        // Expect actual read from disk to fail but only at damaged page.
        BlockDeviceWriter.dropCaches(mDevice);
        assertFalse(BlockDeviceWriter.canReadByte(mDevice, apkPath, offsetOfLastByte));
        if (offsetOfLastByte - FSVERITY_PAGE_SIZE > 0) {
            long firstByteOfTheSamePage = offsetOfLastByte - offsetOfLastByte % FSVERITY_PAGE_SIZE;
            assertFalse(BlockDeviceWriter.canReadByte(mDevice, apkPath, firstByteOfTheSamePage));
            assertTrue(BlockDeviceWriter.canReadByte(mDevice, apkPath, firstByteOfTheSamePage - 1));
        }
    }

    private void verifyInstalledFilesHaveFsverity(String... filenames)
            throws DeviceNotAvailableException {
        // Verify that all files are protected by fs-verity
        String apkPath = getApkPath(TARGET_PACKAGE);
        String appDir = apkPath.substring(0, apkPath.lastIndexOf("/"));
        long kTargetOffset = 0;
        for (String basename : filenames) {
            String path = appDir + "/" + basename;
            damageFileAgainstBlockDevice(path, kTargetOffset);

            // Retry is sometimes needed to pass the test. Package manager may have FD leaks
            // (see b/122744005 as example) that prevents the file in question to be evicted
            // from filesystem cache. Forcing GC workarounds the problem.
            int retry = 5;
            for (; retry > 0; retry--) {
                BlockDeviceWriter.dropCaches(mDevice);
                if (!BlockDeviceWriter.canReadByte(mDevice, path, kTargetOffset)) {
                    break;
                }
                try {
                    String openFiles = expectRemoteCommandToSucceed("lsof " + apkPath);
                    CLog.d("lsof: " + openFiles);
                    Thread.sleep(1000);
                    forceGCOnOpenFilesProcess(getOpenFilesPIDs(openFiles));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            assertTrue("Read from " + path + " should fail", retry > 0);
        }
    }

    /**
     * This is a helper method that parses the lsof output to get PIDs of process holding FD.
     * Here is an example output of lsof. This method extracts the second columns(PID).
     *
     * Example lsof output:
     *  COMMAND      PID     USER    FD     TYPE   DEVICE   SIZE/OFF  NODE   NAME
     * .example.app  1063    u0_a38  mem    REG    253,6    8599      12826  example.apk
     * .example.app  1063    u0_a38  99r    REG    253,6    8599      12826  example.apk
     */
    private Set<String> getOpenFilesPIDs(String lsof) {
        Set<String> openFilesPIDs = new HashSet<>();
        String[] lines = lsof.split("\n");
        for (int i = 1; i < lines.length; i++) {
            openFilesPIDs.add(lines[i].split("\\s+")[1]);
        }
        return openFilesPIDs;
    }

    /**
     * This is a helper method that forces GC on processes given their PIDs.
     * That is to execute shell command "kill -10" on PIDs.
     */
    private void forceGCOnOpenFilesProcess(Set<String> openFilesPIDs)
            throws DeviceNotAvailableException {
        for (String openFilePID : openFilesPIDs) {
            mDevice.executeShellV2Command("kill -10 " + openFilePID);
        }
    }

    private void verifyInstalledFiles(String... filenames) throws DeviceNotAvailableException {
        String apkPath = getApkPath(TARGET_PACKAGE);
        String appDir = apkPath.substring(0, apkPath.lastIndexOf("/"));
        // Exclude directories since we only care about files.
        HashSet<String> actualFiles = new HashSet<>(Arrays.asList(
                expectRemoteCommandToSucceed("ls -p " + appDir + " | grep -v '/'").split("\n")));

        HashSet<String> expectedFiles = new HashSet<>(Arrays.asList(filenames));
        assertEquals(expectedFiles, actualFiles);
    }

    private void damageFileAgainstBlockDevice(String path, long offsetOfTargetingByte)
            throws DeviceNotAvailableException {
        assertTrue(path.startsWith("/data/"));
        ITestDevice.MountPointInfo mountPoint = mDevice.getMountPointInfo("/data");
        ArrayList<String> args = new ArrayList<>();
        args.add(DAMAGING_EXECUTABLE);
        if ("f2fs".equals(mountPoint.type)) {
            args.add("--use-f2fs-pinning");
        }
        args.add(mountPoint.filesystem);
        args.add(path);
        args.add(Long.toString(offsetOfTargetingByte));
        expectRemoteCommandToSucceed(String.join(" ", args));
    }

    private String getApkPath(String packageName) throws DeviceNotAvailableException {
        String line = expectRemoteCommandToSucceed("pm path " + packageName + " | grep base.apk");
        int index = line.trim().indexOf(":");
        assertTrue(index >= 0);
        return line.substring(index + 1);
    }

    private long getFileSizeInBytes(String packageName) throws DeviceNotAvailableException {
        return Long.parseLong(expectRemoteCommandToSucceed("stat -c '%s' " + packageName).trim());
    }

    private String expectRemoteCommandToSucceed(String cmd) throws DeviceNotAvailableException {
        CommandResult result = mDevice.executeShellV2Command(cmd);
        assertEquals("`" + cmd + "` failed: " + result.getStderr(), CommandStatus.SUCCESS,
                result.getStatus());
        return result.getStdout();
    }

    private void expectRemoteCommandToFail(String cmd) throws DeviceNotAvailableException {
        CommandResult result = mDevice.executeShellV2Command(cmd);
        assertTrue("Unexpected success from `" + cmd + "`: " + result.getStderr(),
                result.getStatus() != CommandStatus.SUCCESS);
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        InstallMultiple() {
            super(getDevice(), getBuild());
        }

        InstallMultiple addFileAndSignature(String filename) {
            try {
                addFile(filename);
                addFile(filename + ".fsv_sig");
            } catch (FileNotFoundException e) {
                fail("Missing test file: " + e);
            }
            return this;
        }
    }
}
