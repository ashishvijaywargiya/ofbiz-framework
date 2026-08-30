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
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.lang.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class TestTrendAnalyzerTest {

    private static TestRunManifest manifest(String archivedAt, String outcome, int total, int failed, int skipped,
            Long durationSeconds) {
        TestRunManifest manifest = new TestRunManifest();
        manifest.setRunId(archivedAt);
        manifest.setSuiteName("testIntegration");
        manifest.setArchivedAt(archivedAt);
        manifest.setOutcome(outcome);
        manifest.setCounts(new TestRunManifest.Counts(total, total - failed - skipped, failed, skipped));
        manifest.setDurationSeconds(durationSeconds);
        return manifest;
    }

    /** Same as {@link #manifest}, but stamped as a narrowed-down (filtered) run - see
     * {@link TestRunManifest#isFiltered()}. */
    private static TestRunManifest filteredManifest(String archivedAt, String outcome, int total, int failed,
            int skipped, Long durationSeconds) {
        TestRunManifest manifest = manifest(archivedAt, outcome, total, failed, skipped, durationSeconds);
        manifest.setParamsUsed(Map.of("testsFilter", "org.example.SomeTest"));
        return manifest;
    }

    @Test
    void reportsNotEnoughHistoryForZeroOrOneRun() {
        TestTrendReport zero = TestTrendAnalyzer.analyzeManifests("unit", List.of(), 25);
        assertThat(zero.isNotEnoughHistory(), is(true));
        assertThat(zero.getRunCount(), is(0));

        TestTrendReport one = TestTrendAnalyzer.analyzeManifests("unit",
                List.of(manifest("2026-08-20T00:00:00Z", "PASSED", 10, 0, 0, 30L)), 25);
        assertThat(one.isNotEnoughHistory(), is(true));
        assertThat(one.getRunCount(), is(1));
    }

    @Test
    void computesFailureRateAcrossAllRuns() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-21T00:00:00Z", "FAILED", 10, 2, 0, 30L),
                manifest("2026-08-22T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-23T00:00:00Z", "PASSED", 10, 0, 0, 30L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("testIntegration", manifests, 25);

        assertThat(report.getFailureRate(), is(0.25));
    }

    @Test
    void streakCountsOnlyTheMostRecentRunOfIdenticalOutcomes() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "FAILED", 10, 1, 0, 30L),
                manifest("2026-08-21T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-22T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-23T00:00:00Z", "PASSED", 10, 0, 0, 30L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("testIntegration", manifests, 25);

        assertThat(report.getStreakDirection(), is("PASSING"));
        assertThat(report.getStreakLength(), is(3));
    }

    @Test
    void flagsARunWhoseDurationDeviatesPastTheThreshold() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-21T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-22T00:00:00Z", "PASSED", 10, 0, 0, 45L)); // average 35, +28.6% deviation

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("testIntegration", manifests, 25);

        assertThat(report.getRuns().get(0).isDurationDeviationFlag(), is(false));
        assertThat(report.getRuns().get(2).isDurationDeviationFlag(), is(true));
    }

    @Test
    void flagsARunThatFinishedSignificantlyFasterThanTheBaseline() {
        // Discriminates the bidirectional Math.abs(...) formula from the previously-reverted
        // signed (slow-only) formula: mean = (40+40+20)/3 = 33.33.
        // run0 (40s): |40-33.33|/33.33 = 20.0% either way - under threshold, never flagged.
        // run2 (20s): bidirectional |20-33.33|/33.33 = 40.0% - past the 25% threshold, flagged.
        //             signed-only  (20-33.33)/33.33  = -40.0% - not > 25%, would NOT be flagged.
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 10, 0, 0, 40L),
                manifest("2026-08-21T00:00:00Z", "PASSED", 10, 0, 0, 40L),
                manifest("2026-08-22T00:00:00Z", "PASSED", 10, 0, 0, 20L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("testIntegration", manifests, 25);

        assertThat(report.getRuns().get(0).isDurationDeviationFlag(), is(false));
        assertThat(report.getRuns().get(2).isDurationDeviationFlag(), is(true));
    }

    @Test
    void excludesRunsWithNullDurationFromTheBaselineAndNeverFlagsThem() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 10, 0, 0, null),
                manifest("2026-08-21T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-22T00:00:00Z", "PASSED", 10, 0, 0, 30L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("testIntegration", manifests, 25);

        assertThat(report.getAverageDurationSeconds(), is(30.0));
        assertThat(report.getRuns().get(0).isDurationDeviationFlag(), is(false));
        assertThat(report.getRuns().get(0).getDurationSeconds(), nullValue());
    }

    @Test
    void flagsCountDecreaseAndSkippedIncreaseAgainstThePreviousRunOnly() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 10, 0, 0, 30L),
                manifest("2026-08-21T00:00:00Z", "PASSED", 8, 0, 1, 30L), // total decreased, skipped increased
                manifest("2026-08-22T00:00:00Z", "PASSED", 12, 0, 1, 30L)); // total increased vs previous - not flagged

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("testIntegration", manifests, 25);

        assertThat(report.getRuns().get(0).isCountDecreasedFlag(), is(false)); // no previous run
        assertThat(report.getRuns().get(1).isCountDecreasedFlag(), is(true));
        assertThat(report.getRuns().get(1).isSkippedIncreasedFlag(), is(true));
        assertThat(report.getRuns().get(2).isCountDecreasedFlag(), is(false));
        assertThat(report.getRuns().get(2).isSkippedIncreasedFlag(), is(false));
    }

    @Test
    void filteredRunsAreExcludedFromFailureRateStreakAndDurationBaseline() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 800, 0, 0, 50L),
                manifest("2026-08-21T00:00:00Z", "PASSED", 800, 0, 0, 50L),
                // A single-class debug run: FAILED, tiny count, tiny duration - if this leaked into
                // the baseline/streak/failure-rate math it would tank all three.
                filteredManifest("2026-08-22T00:00:00Z", "FAILED", 1, 1, 0, 1L),
                manifest("2026-08-23T00:00:00Z", "PASSED", 800, 0, 0, 50L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("unit", manifests, 25);

        assertThat(report.getFailureRate(), is(0.0));
        assertThat(report.getStreakDirection(), is("PASSING"));
        assertThat(report.getStreakLength(), is(3));
        assertThat(report.getAverageDurationSeconds(), is(50.0));
        assertThat(report.getFilteredRunCount(), is(1));
        assertThat(report.getRuns(), hasSize(4));
        assertThat(report.getRuns().get(2).isFiltered(), is(true));
        assertThat(report.getRuns().get(0).isFiltered(), is(false));
    }

    @Test
    void filteredRunsAreNeverFlaggedForDurationDeviation() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 800, 0, 0, 50L),
                manifest("2026-08-21T00:00:00Z", "PASSED", 800, 0, 0, 50L),
                // 1s vs. a ~50s baseline would be a huge deviation if computed at all.
                filteredManifest("2026-08-22T00:00:00Z", "PASSED", 1, 0, 0, 1L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("unit", manifests, 25);

        assertThat(report.getRuns().get(2).isDurationDeviationFlag(), is(false));
    }

    @Test
    void countDriftComparisonSkipsFilteredRunsAndComparesAgainstThePreviousFullRunOnly() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 5, 0, 5, 30L), // baseline: total 5, skipped 5
                filteredManifest("2026-08-21T00:00:00Z", "PASSED", 20, 0, 0, 1L), // total 20, skipped 0
                manifest("2026-08-22T00:00:00Z", "PASSED", 10, 0, 1, 30L)); // total 10, skipped 1

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("unit", manifests, 25);

        // Comparing the last run (10, skipped 1) against the filtered run in between (20, skipped 0)
        // would flag count-decreased and never flag skipped-increased; comparing against the actual
        // previous full run (5, skipped 5) does the opposite of both.
        assertThat(report.getRuns().get(2).isCountDecreasedFlag(), is(false));
        assertThat(report.getRuns().get(2).isSkippedIncreasedFlag(), is(false));
        // The filtered run itself is never flagged either way.
        assertThat(report.getRuns().get(1).isCountDecreasedFlag(), is(false));
        assertThat(report.getRuns().get(1).isSkippedIncreasedFlag(), is(false));
    }

    @Test
    void filteredRunCarriesItsFilterDetailFromParamsUsedButAFullRunDoesNot() {
        List<TestRunManifest> manifests = List.of(
                manifest("2026-08-20T00:00:00Z", "PASSED", 800, 0, 0, 50L),
                filteredManifest("2026-08-21T00:00:00Z", "PASSED", 1, 0, 0, 1L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("unit", manifests, 25);

        assertThat(report.getRuns().get(0).getFilterDetail(), nullValue());
        assertThat(report.getRuns().get(1).getFilterDetail(), is("org.example.SomeTest"));
    }

    @Test
    void reportsZeroFailureRateAndNullStreakWhenEveryArchivedRunIsFiltered() {
        List<TestRunManifest> manifests = List.of(
                filteredManifest("2026-08-20T00:00:00Z", "FAILED", 1, 1, 0, 1L),
                filteredManifest("2026-08-21T00:00:00Z", "FAILED", 1, 1, 0, 1L));

        TestTrendReport report = TestTrendAnalyzer.analyzeManifests("unit", manifests, 25);

        assertThat(report.isNotEnoughHistory(), is(false)); // 2 runs archived, just none of them full
        assertThat(report.getFilteredRunCount(), is(2));
        assertThat(report.getFailureRate(), is(0.0));
        assertThat(report.getStreakDirection(), nullValue());
        assertThat(report.getStreakLength(), is(0));
        assertThat(report.getAverageDurationSeconds(), nullValue());
    }

    @Test
    void analyzeFiltersBySuiteNameAndSortsChronologicallyFromDisk(@TempDir File baseDir) throws IOException {
        writeManifest(new File(baseDir, "2026-08-21/10h00m00s_testIntegration"), "testIntegration",
                "2026-08-21T10:00:00Z", "PASSED", 10, 0, 0, 30L);
        writeManifest(new File(baseDir, "2026-08-20/09h00m00s_testIntegration"), "testIntegration",
                "2026-08-20T09:00:00Z", "FAILED", 10, 1, 0, 30L);
        writeManifest(new File(baseDir, "2026-08-20/08h00m00s_unit"), "unit",
                "2026-08-20T08:00:00Z", "PASSED", 5, 0, 0, 10L); // different suite, must be excluded

        TestTrendReport report = TestTrendAnalyzer.analyze(baseDir, "testIntegration", 25);

        assertThat(report.getRunCount(), is(2));
        assertThat(report.getRuns(), hasSize(2));
        assertThat(report.getRuns().get(0).getArchivedAt(), is("2026-08-20T09:00:00Z"));
        assertThat(report.getRuns().get(1).getArchivedAt(), is("2026-08-21T10:00:00Z"));
    }

    private static void writeManifest(File runDir, String suiteName, String archivedAt, String outcome, int total,
            int failed, int skipped, Long durationSeconds) throws IOException {
        Files.createDirectories(runDir.toPath());
        TestRunManifest manifest = manifest(archivedAt, outcome, total, failed, skipped, durationSeconds);
        manifest.setSuiteName(suiteName);
        Files.writeString(new File(runDir, "manifest.json").toPath(), JSON.from(manifest).toString());
    }
}
