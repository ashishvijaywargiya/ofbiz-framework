/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ofbiz.testtools.report;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.Debug;

/**
 * Pure decision logic that turns a chronological list of one suite's archived
 * {@link TestRunManifest}s into a {@link TestTrendReport}: failure rate, current pass/fail streak,
 * duration-baseline deviation flags, and run-over-run count-drift flags. Mirrors
 * {@link TestReportPurgePlanner}'s split between filesystem discovery ({@link #analyze}) and pure
 * logic ({@link #analyzeManifests}) so the logic is unit-testable with synthetic manifest lists,
 * no filesystem involved.
 *
 * <p>Failure rate and streak are both computed from {@link TestRunManifest#isGreen()}, not from
 * the manifest's own recorded {@code outcome}: a zero-test run with {@code outcome="PASSED"} is
 * treated as non-passing by {@code isGreen()}, intentionally more conservative than what the
 * manifest itself reports. {@link TestTrendReport.Run#getOutcome()} still carries the manifest's
 * unmodified {@code outcome} string for display, so the rendered report and {@code trends-<suiteName>.json}
 * can legitimately show a run as its own recorded outcome even where the streak/failure-rate math
 * treated it as failing.</p>
 *
 * <p>{@link TestRunManifest#isFiltered() Filtered} runs (a {@code --tests SomeClass}-style narrowed
 * selection, not the whole suite) are still included in {@link TestTrendReport#getRuns()} - each
 * marked via {@link TestTrendReport.Run#isFiltered()} - but are excluded from every statistic:
 * failure rate, streak, the duration baseline, and the count/skipped drift comparisons all skip
 * over them entirely, and a filtered run itself is never flagged for duration deviation or count
 * drift. Without this, a single-class debug run sitting between two full runs would both throw off
 * the baseline those full runs are compared against and read as a wild, meaningless swing in its
 * own right.</p>
 */
public final class TestTrendAnalyzer {

    private TestTrendAnalyzer() {
    }

    private static final String MODULE = TestTrendAnalyzer.class.getName();

    /**
     * Loads every archived manifest for {@code suiteName} under {@code baseDir} (via
     * {@link TestReportPurgePlanner#discoverRunFolders}), sorts them chronologically by
     * {@code archivedAt}, and delegates to {@link #analyzeManifests}. Unreadable/corrupt
     * manifests are skipped with a logged warning, same defensive stance as the purge planner.
     */
    public static TestTrendReport analyze(File baseDir, String suiteName, int durationDeviationPercent) {
        List<TestRunManifest> manifests = new ArrayList<>();
        for (TestReportPurgePlanner.RunFolder runFolder : TestReportPurgePlanner.discoverRunFolders(baseDir)) {
            if (!suiteName.equals(runFolder.getSuiteName())) {
                continue;
            }
            File manifestFile = new File(runFolder.getDir(), "manifest.json");
            try (FileInputStream in = new FileInputStream(manifestFile)) {
                manifests.add(JSON.from(in).toObject(TestRunManifest.class));
            } catch (IOException e) {
                Debug.logWarning(e, "TestTrendAnalyzer: skipping unreadable " + manifestFile, MODULE);
            }
        }
        manifests.sort(Comparator.comparing(TestRunManifest::getArchivedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return analyzeManifests(suiteName, manifests, durationDeviationPercent);
    }

    /**
     * Pure logic: {@code manifestsChronological} must already be oldest-to-newest. No filesystem
     * access - unit-testable with a synthetic list.
     */
    public static TestTrendReport analyzeManifests(String suiteName, List<TestRunManifest> manifestsChronological,
            int durationDeviationPercent) {
        TestTrendReport report = new TestTrendReport();
        report.setSuiteName(suiteName);
        report.setRunCount(manifestsChronological.size());
        report.setNotEnoughHistory(manifestsChronological.size() < 2);

        if (manifestsChronological.isEmpty()) {
            report.setFailureRate(0.0);
            return report;
        }

        List<TestRunManifest> fullManifests = manifestsChronological.stream()
                .filter(manifest -> !manifest.isFiltered())
                .collect(Collectors.toList());
        report.setFilteredRunCount(manifestsChronological.size() - fullManifests.size());

        Double averageDuration = averageDuration(fullManifests);
        report.setAverageDurationSeconds(averageDuration);

        int failedCount = 0;
        List<TestTrendReport.Run> runs = new ArrayList<>();
        // Tracks the previous *full* manifest only, so a filtered run sitting in between two full
        // runs is never used as the count/skipped-drift baseline for the full run that follows it -
        // see the class javadoc.
        TestRunManifest previousFull = null;
        for (TestRunManifest manifest : manifestsChronological) {
            boolean filtered = manifest.isFiltered();
            boolean green = manifest.isGreen();
            if (!filtered && !green) {
                failedCount++;
            }

            TestTrendReport.Run run = new TestTrendReport.Run();
            run.setRunId(manifest.getRunId());
            run.setArchivedAt(manifest.getArchivedAt());
            run.setOutcome(manifest.getOutcome());
            run.setGreen(green);
            run.setFiltered(filtered);
            run.setFilterDetail(filtered && manifest.getParamsUsed() != null
                    ? manifest.getParamsUsed().get("testsFilter") : null);
            run.setCounts(manifest.getCounts());
            run.setDurationSeconds(manifest.getDurationSeconds());
            run.setDurationDeviationFlag(!filtered && isDurationDeviation(manifest.getDurationSeconds(),
                    averageDuration, durationDeviationPercent));

            if (!filtered) {
                if (previousFull != null && previousFull.getCounts() != null && manifest.getCounts() != null) {
                    run.setCountDecreasedFlag(manifest.getCounts().getTotal() < previousFull.getCounts().getTotal());
                    run.setSkippedIncreasedFlag(
                            manifest.getCounts().getSkipped() > previousFull.getCounts().getSkipped());
                }
                previousFull = manifest;
            }

            runs.add(run);
        }
        report.setRuns(runs);
        report.setFailureRate(fullManifests.isEmpty() ? 0.0 : (double) failedCount / fullManifests.size());

        if (fullManifests.isEmpty()) {
            report.setStreakDirection(null);
            report.setStreakLength(0);
        } else {
            List<TestRunManifest> reversedFull = new ArrayList<>(fullManifests);
            Collections.reverse(reversedFull);
            boolean streakGreen = reversedFull.get(0).isGreen();
            int streakLength = 0;
            for (TestRunManifest manifest : reversedFull) {
                if (manifest.isGreen() != streakGreen) {
                    break;
                }
                streakLength++;
            }
            report.setStreakDirection(streakGreen ? "PASSING" : "FAILING");
            report.setStreakLength(streakLength);
        }

        return report;
    }

    private static Double averageDuration(List<TestRunManifest> manifests) {
        long sum = 0;
        int count = 0;
        for (TestRunManifest manifest : manifests) {
            if (manifest.getDurationSeconds() != null) {
                sum += manifest.getDurationSeconds();
                count++;
            }
        }
        return count == 0 ? null : (double) sum / count;
    }

    private static boolean isDurationDeviation(Long durationSeconds, Double averageDuration, int deviationPercent) {
        if (durationSeconds == null || averageDuration == null || averageDuration == 0) {
            return false;
        }
        double deviation = Math.abs(durationSeconds - averageDuration) / averageDuration * 100.0;
        return deviation > deviationPercent;
    }
}
