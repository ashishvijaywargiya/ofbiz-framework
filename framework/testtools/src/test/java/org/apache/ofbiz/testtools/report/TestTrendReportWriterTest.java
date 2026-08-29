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

import org.apache.ofbiz.base.lang.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class TestTrendReportWriterTest {

    private static TestTrendReport twoRunReport() {
        TestTrendReport report = new TestTrendReport();
        report.setSuiteName("testIntegration");
        report.setRunCount(2);
        report.setNotEnoughHistory(false);
        report.setFailureRate(0.5);
        report.setStreakDirection("FAILING");
        report.setStreakLength(1);
        report.setAverageDurationSeconds(50.0);

        TestTrendReport.Run first = new TestTrendReport.Run();
        first.setRunId("2026-08-20_10h00m00s_testIntegration");
        first.setArchivedAt("2026-08-20T10:00:00Z");
        first.setOutcome("PASSED");
        first.setGreen(true);
        first.setCounts(new TestRunManifest.Counts(10, 10, 0, 0));
        first.setDurationSeconds(40L);

        TestTrendReport.Run second = new TestTrendReport.Run();
        second.setRunId("2026-08-21_10h00m00s_testIntegration");
        second.setArchivedAt("2026-08-21T10:00:00Z");
        second.setOutcome("FAILED");
        second.setGreen(false);
        second.setCounts(new TestRunManifest.Counts(10, 8, 2, 0));
        second.setDurationSeconds(60L);
        second.setDurationDeviationFlag(true);

        report.setRuns(List.of(first, second));
        return report;
    }

    @Test
    void jsonRoundTripsAllFields() throws IOException {
        TestTrendReport report = twoRunReport();

        String json = TestTrendReportWriter.toJson(report);
        TestTrendReport roundTripped = JSON.from(json).toObject(TestTrendReport.class);

        assertThat(roundTripped.getSuiteName(), is("testIntegration"));
        assertThat(roundTripped.getRunCount(), is(2));
        assertThat(roundTripped.getStreakDirection(), is("FAILING"));
        assertThat(roundTripped.getRuns().get(1).isDurationDeviationFlag(), is(true));
    }

    @Test
    void htmlContainsSuiteNameAndFlaggedRunData() {
        String html = TestTrendReportWriter.toHtml(twoRunReport());

        assertThat(html, containsString("testIntegration"));
        assertThat(html, containsString("FAILING"));
        assertThat(html, containsString("2026-08-21T10:00:00Z"));
        assertThat(html, containsString("<svg"));
    }

    @Test
    void htmlReportsNotEnoughHistoryForASingleRun() {
        TestTrendReport report = new TestTrendReport();
        report.setSuiteName("unit");
        report.setRunCount(1);
        report.setNotEnoughHistory(true);

        String html = TestTrendReportWriter.toHtml(report);

        assertThat(html, containsString("Not enough history"));
    }

    @Test
    void consoleSummaryReportsNotEnoughHistoryForZeroRuns() {
        TestTrendReport report = new TestTrendReport();
        report.setSuiteName("unit");
        report.setRunCount(0);
        report.setNotEnoughHistory(true);

        String summary = TestTrendReportWriter.toConsoleSummary(report);

        assertThat(summary, containsString("Not enough history"));
    }

    @Test
    void writeCreatesBothFilesInOutputDir(@TempDir File tmp) throws IOException {
        TestTrendReportWriter.write(twoRunReport(), tmp);

        assertThat(new File(tmp, "trends.json").exists(), is(true));
        assertThat(new File(tmp, "trends.html").exists(), is(true));
        assertThat(Files.readString(new File(tmp, "trends.json").toPath()), containsString("testIntegration"));
    }
}
