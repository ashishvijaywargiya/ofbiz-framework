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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure decision logic that turns a chronological list of one suite's archived
 * {@link TestRunManifest}s into a {@link TestTrendReport}: failure rate, current pass/fail streak,
 * duration-baseline deviation flags, and run-over-run count-drift flags. Mirrors
 * {@link TestReportPurgePlanner}'s split between filesystem discovery ({@link #analyze}) and pure
 * logic ({@link #analyzeManifests}) so the logic is unit-testable with synthetic manifest lists,
 * no filesystem involved.
 */
public final class TestTrendAnalyzer {

    private TestTrendAnalyzer() {
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

        Double averageDuration = averageDuration(manifestsChronological);
        report.setAverageDurationSeconds(averageDuration);

        int failedCount = 0;
        List<TestTrendReport.Run> runs = new ArrayList<>();
        TestRunManifest previous = null;
        for (TestRunManifest manifest : manifestsChronological) {
            boolean green = manifest.isGreen();
            if (!green) {
                failedCount++;
            }

            TestTrendReport.Run run = new TestTrendReport.Run();
            run.setRunId(manifest.getRunId());
            run.setArchivedAt(manifest.getArchivedAt());
            run.setOutcome(manifest.getOutcome());
            run.setGreen(green);
            run.setCounts(manifest.getCounts());
            run.setDurationSeconds(manifest.getDurationSeconds());
            run.setDurationDeviationFlag(isDurationDeviation(manifest.getDurationSeconds(), averageDuration,
                    durationDeviationPercent));

            if (previous != null && previous.getCounts() != null && manifest.getCounts() != null) {
                run.setCountDecreasedFlag(manifest.getCounts().getTotal() < previous.getCounts().getTotal());
                run.setSkippedIncreasedFlag(manifest.getCounts().getSkipped() > previous.getCounts().getSkipped());
            }

            runs.add(run);
            previous = manifest;
        }
        report.setRuns(runs);
        report.setFailureRate((double) failedCount / manifestsChronological.size());

        List<TestTrendReport.Run> reversed = new ArrayList<>(runs);
        Collections.reverse(reversed);
        boolean streakGreen = reversed.get(0).isGreen();
        int streakLength = 0;
        for (TestTrendReport.Run run : reversed) {
            if (run.isGreen() != streakGreen) {
                break;
            }
            streakLength++;
        }
        report.setStreakDirection(streakGreen ? "PASSING" : "FAILING");
        report.setStreakLength(streakLength);

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
        // Signed, not absolute: only a run that took meaningfully *longer* than the baseline is a
        // regression worth flagging - a run that finished faster than average is not a problem.
        double deviation = (durationSeconds - averageDuration) / averageDuration * 100.0;
        return deviation > deviationPercent;
    }
}
