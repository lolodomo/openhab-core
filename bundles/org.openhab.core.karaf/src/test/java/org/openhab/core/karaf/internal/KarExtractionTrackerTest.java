/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.karaf.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link KarExtractionTracker}.
 *
 * @author Holger Friedrich - Initial contribution
 */
@NonNullByDefault
public class KarExtractionTrackerTest {

    private static final String KAR_NAME = "openhab-addons-5.3.0";

    private @TempDir @NonNullByDefault({}) Path tempDir;
    private @NonNullByDefault({}) Path addonsDirectory;
    private @NonNullByDefault({}) Path karStorage;
    private @NonNullByDefault({}) Path karFile;
    private @NonNullByDefault({}) Path karDirectory;

    private final List<Path> installedKars = new ArrayList<>();
    private final KarExtractionTracker tracker = new KarExtractionTracker(installedKars::add);

    @BeforeEach
    public void setUp() throws IOException {
        addonsDirectory = Files.createDirectory(tempDir.resolve("addons"));
        karStorage = Files.createDirectory(tempDir.resolve("kar"));
        karFile = addonsDirectory.resolve(KAR_NAME + ".kar");
        karDirectory = karStorage.resolve(KAR_NAME);
    }

    @Test
    public void noKarFiles() throws IOException {
        assertTrue(tracker.allKarsInstalled(addonsDirectory, karStorage, List.of()));
    }

    @Test
    public void karNotInstalled() throws IOException {
        Files.createFile(karFile);

        assertFalse(tracker.allKarsInstalled(addonsDirectory, karStorage, List.of()));
    }

    @Test
    public void karListedWhileBeingExtracted() throws IOException {
        Files.createFile(karFile);
        Files.createDirectories(karDirectory.resolve("repository"));

        for (int i = 0; i < 2 * KarExtractionTracker.STALLED_CHECKS; i++) {
            Files.writeString(karDirectory.resolve("repository").resolve("file" + i), "content");
            assertFalse(tracker.allKarsInstalled(addonsDirectory, karStorage, List.of(KAR_NAME)));
        }
        assertEquals(List.of(), installedKars);
    }

    @Test
    public void karCompletelyExtracted() throws IOException {
        Files.createFile(karFile);
        Files.createDirectories(karDirectory);
        Files.createFile(karDirectory.resolve(KarExtractionTracker.KAR_FEATURES_FILE));

        assertTrue(tracker.allKarsInstalled(addonsDirectory, karStorage, List.of(KAR_NAME)));
        assertEquals(List.of(), installedKars);
    }

    @Test
    public void interruptedExtractionIsInstalledAgainOnce() throws IOException {
        Files.createFile(karFile);
        Files.createDirectories(karDirectory.resolve("repository"));
        Files.writeString(karDirectory.resolve("repository").resolve("partial"), "content");

        // the first check records the size, it then needs to stay unchanged for STALLED_CHECKS checks
        for (int i = 0; i < KarExtractionTracker.STALLED_CHECKS; i++) {
            assertFalse(tracker.allKarsInstalled(addonsDirectory, karStorage, List.of(KAR_NAME)));
        }
        assertTrue(tracker.allKarsInstalled(addonsDirectory, karStorage, List.of(KAR_NAME)));
        assertEquals(List.of(karFile), installedKars);

        assertTrue(tracker.allKarsInstalled(addonsDirectory, karStorage, List.of(KAR_NAME)));
        assertEquals(List.of(karFile), installedKars);
    }

    @Test
    public void failedInstallationDoesNotBlock() throws IOException {
        KarExtractionTracker failingTracker = new KarExtractionTracker(file -> {
            throw new IOException("extraction failed");
        });
        Files.createFile(karFile);
        Files.createDirectories(karDirectory);

        for (int i = 0; i < KarExtractionTracker.STALLED_CHECKS; i++) {
            assertFalse(failingTracker.allKarsInstalled(addonsDirectory, karStorage, List.of(KAR_NAME)));
        }
        assertTrue(failingTracker.allKarsInstalled(addonsDirectory, karStorage, List.of(KAR_NAME)));
        // also when Karaf removed the KAR after the failure
        assertTrue(failingTracker.allKarsInstalled(addonsDirectory, karStorage, List.of()));
    }
}
