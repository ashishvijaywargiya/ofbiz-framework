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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class TestTrendReportModelTest {

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
        first.setArchivedAt("2026-08-20T10:00:00Z");
        first.setOutcome("PASSED");
        first.setGreen(true);
        first.setDurationSeconds(40L);

        TestTrendReport.Run second = new TestTrendReport.Run();
        second.setArchivedAt("2026-08-21T10:00:00Z");
        second.setOutcome("FAILED");
        second.setGreen(false);
        second.setDurationSeconds(60L);
        second.setDurationDeviationFlag(true);
        second.setFiltered(true);

        report.setRuns(List.of(first, second));
        return report;
    }

    @Test
    void notEnoughHistoryStopsAtTheBasicFields() {
        TestTrendReport report = new TestTrendReport();
        report.setSuiteName("unit");
        report.setRunCount(1);
        report.setNotEnoughHistory(true);

        Map<String, Object> model = TestTrendReportModel.build(report);

        assertThat(model.get("suiteName"), is("unit"));
        assertThat(model.get("notEnoughHistory"), is(true));
        assertThat(model.get("passFailChart"), is(nullValue()));
        assertThat(model.containsKey("failureRateText"), is(false));
    }

    @Test
    void buildFormatsFailureRateStreakAndAverageDuration() {
        Map<String, Object> model = TestTrendReportModel.build(twoRunReport());

        assertThat(model.get("failureRateText"), is("50.0%"));
        assertThat(model.get("streakDirection"), is("FAILING"));
        assertThat(model.get("streakLength"), is(1));
        assertThat(model.get("averageDurationText"), is("50.0s"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildOrdersFlagTagsAndMutesOnlyFiltered() {
        Map<String, Object> model = TestTrendReportModel.build(twoRunReport());

        List<TestTrendReportModel.RunRow> runRows = (List<TestTrendReportModel.RunRow>) model.get("runRows");
        // newest first: the second/filtered/flagged run is row 0.
        TestTrendReportModel.RunRow flaggedRow = runRows.get(0);
        assertThat(flaggedRow.getRun().getArchivedAt(), is("2026-08-21T10:00:00Z"));
        assertThat(flaggedRow.getFlags().size(), is(2));
        assertThat(flaggedRow.getFlags().get(0).getLabel(), is("filtered"));
        assertThat(flaggedRow.getFlags().get(0).isMuted(), is(true));
        assertThat(flaggedRow.getFlags().get(1).getLabel(), is("duration"));
        assertThat(flaggedRow.getFlags().get(1).isMuted(), is(false));
        assertThat(flaggedRow.getDurationText(), is("60.0s"));

        TestTrendReportModel.RunRow plainRow = runRows.get(1);
        assertThat(plainRow.getFlags(), is(List.of()));
        assertThat(plainRow.getDurationText(), is("40.0s"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildExcludesFilteredRunsFromTheChartsButKeepsThemInRunRows() {
        Map<String, Object> model = TestTrendReportModel.build(twoRunReport());

        TestTrendChartData.PassFailChart passFailChart = (TestTrendChartData.PassFailChart) model.get("passFailChart");
        // The second run is filtered - only the first (unfiltered) run should reach the chart.
        assertThat(passFailChart.getPoints().size(), is(1));

        List<TestTrendReportModel.RunRow> runRows = (List<TestTrendReportModel.RunRow>) model.get("runRows");
        assertThat(runRows.size(), is(2));
    }

    @Test
    void newestFirstReversesAndCapsAtOneHundred() {
        TestTrendReport report = new TestTrendReport();
        java.util.List<TestTrendReport.Run> runs = new java.util.ArrayList<>();
        for (int i = 0; i < 105; i++) {
            TestTrendReport.Run run = new TestTrendReport.Run();
            run.setArchivedAt("run-" + i);
            runs.add(run);
        }

        List<TestTrendReport.Run> newestFirst = TestTrendReportModel.newestFirst(runs);

        assertThat(newestFirst.size(), is(100));
        assertThat(newestFirst.get(0).getArchivedAt(), is("run-104"));
    }
}
