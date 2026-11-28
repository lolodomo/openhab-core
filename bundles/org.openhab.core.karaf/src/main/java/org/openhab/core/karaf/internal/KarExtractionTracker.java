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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks whether the .kar files in the add-ons folder are installed and completely extracted.
 *
 * Karaf lists a KAR as installed as soon as its extraction starts, and a KAR whose extraction was interrupted, e.g.
 * because openHAB was stopped, is never completed. A listed KAR is therefore only considered as installed once the
 * features repository list exists, which Karaf writes when the extraction is complete. If its directory does not
 * change for {@link #STALLED_CHECKS} consecutive checks, the extraction is considered as interrupted and the KAR is
 * installed again once. This can be removed once Karaf contains https://github.com/apache/karaf/pull/3164.
 *
 * Not thread-safe, {@link #allKarsInstalled} must not be called concurrently.
 *
 * @author Holger Friedrich - Initial contribution
 */
@NonNullByDefault
class KarExtractionTracker {

    static final int STALLED_CHECKS = 4;
    static final String KAR_FEATURES_FILE = "features.cfg";

    @FunctionalInterface
    interface KarInstaller {
        void install(Path karFile) throws Exception;
    }

    private final Logger logger = LoggerFactory.getLogger(KarExtractionTracker.class);

    private final KarInstaller karInstaller;
    private final Map<String, Long> lastSizes = new HashMap<>();
    private final Map<String, Integer> unchangedChecks = new HashMap<>();
    private final Set<String> reinstalledKars = new HashSet<>();

    KarExtractionTracker(KarInstaller karInstaller) {
        this.karInstaller = karInstaller;
    }

    /**
     * Checks whether all .kar files in the add-ons folder are installed and completely extracted.
     *
     * @param addonsDirectory the folder containing the .kar files
     * @param karStorage the folder Karaf extracts the KARs to
     * @param karRepos the KARs listed by Karaf
     * @return {@code true} if all KARs are installed
     * @throws IOException if a folder cannot be read
     */
    boolean allKarsInstalled(Path addonsDirectory, Path karStorage, List<String> karRepos) throws IOException {
        List<Path> karFiles;
        try (Stream<Path> files = Files.list(addonsDirectory)) {
            karFiles = files.filter(file -> file.getFileName().toString().endsWith(".kar")).toList();
        }
        boolean allInstalled = true;
        for (Path karFile : karFiles) {
            if (!isInstalled(karFile, karStorage, karRepos)) {
                allInstalled = false;
            }
        }
        return allInstalled;
    }

    private boolean isInstalled(Path karFile, Path karStorage, List<String> karRepos) throws IOException {
        String karFileName = karFile.getFileName().toString();
        String karName = karFileName.substring(0, karFileName.lastIndexOf("."));
        if (reinstalledKars.contains(karName)) {
            // installed again or failed to, use the result as it is instead of waiting forever
            return true;
        }
        if (!karRepos.contains(karName)) {
            return false;
        }
        Path karDirectory = karStorage.resolve(karName);
        if (Files.isRegularFile(karDirectory.resolve(KAR_FEATURES_FILE))) {
            lastSizes.remove(karName);
            unchangedChecks.remove(karName);
            return true;
        }
        if (!Files.isDirectory(karDirectory)) {
            return false;
        }

        long size = directorySize(karDirectory);
        Long lastSize = lastSizes.put(karName, size);
        if (lastSize == null || lastSize != size) {
            // extraction is in progress
            unchangedChecks.remove(karName);
            return false;
        }
        int unchanged = unchangedChecks.getOrDefault(karName, 0) + 1;
        unchangedChecks.put(karName, unchanged);
        if (unchanged < STALLED_CHECKS) {
            return false;
        }

        reinstalledKars.add(karName);
        lastSizes.remove(karName);
        unchangedChecks.remove(karName);
        logger.warn("The extraction of '{}' did not complete, probably because openHAB was stopped while it was "
                + "extracted. Installing it again.", karFileName);
        try {
            karInstaller.install(karFile);
        } catch (Exception e) {
            logger.warn("Installing '{}' again failed, add-ons may fail to install: {}", karFileName, e.getMessage());
        }
        return true;
    }

    private static long directorySize(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> file.toFile().length()).sum();
        }
    }
}
