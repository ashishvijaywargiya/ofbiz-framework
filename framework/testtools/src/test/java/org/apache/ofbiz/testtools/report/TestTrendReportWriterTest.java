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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.ofbiz.base.lang.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

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
    void consoleSummaryListsRunsNewestFirst() {
        String summary = TestTrendReportWriter.toConsoleSummary(twoRunReport());

        assertThat(summary, containsString("Runs (newest to oldest):"));
        int newestIndex = summary.indexOf("2026-08-21T10:00:00Z");
        int oldestIndex = summary.indexOf("2026-08-20T10:00:00Z");
        assertThat(newestIndex, is(not(-1)));
        assertThat(oldestIndex, is(not(-1)));
        assertThat(newestIndex < oldestIndex, is(true));
    }

    @Test
    void htmlListsRunsNewestFirstInTheTableButChartsStayOldestToNewest() {
        String html = TestTrendReportWriter.toHtml(twoRunReport());

        int tableStart = html.indexOf("<h2>Runs");
        int newestRowIndex = html.indexOf("2026-08-21T10:00:00Z", tableStart);
        int oldestRowIndex = html.indexOf("2026-08-20T10:00:00Z", tableStart);
        assertThat(newestRowIndex, is(not(-1)));
        assertThat(oldestRowIndex, is(not(-1)));
        assertThat(newestRowIndex < oldestRowIndex, is(true));

        // The pass/fail chart itself must stay oldest-to-newest, left to right - only the table's
        // display order changes, not the underlying chronological data the charts plot: the older,
        // passed run's dot must be drawn (and so appear in the markup) before the newer, failed run's.
        // Dots are now colored via CSS classes (trend-dot-pass/trend-dot-fail), not raw hex fills -
        // see test-report.css's javadoc for why.
        String chart = extractChart(html, "Pass/fail");
        int passedDot = chart.indexOf("trend-dot-pass");
        int failedDot = chart.indexOf("trend-dot-fail");
        assertThat(passedDot, is(not(-1)));
        assertThat(failedDot, is(not(-1)));
        assertThat(passedDot < failedDot, is(true));
    }

    /** A minimal Run carrying just an archivedAt, for building large synthetic run lists. */
    private static TestTrendReport.Run runAt(String archivedAt) {
        TestTrendReport.Run run = new TestTrendReport.Run();
        run.setRunId(archivedAt);
        run.setArchivedAt(archivedAt);
        run.setOutcome("PASSED");
        run.setGreen(true);
        run.setCounts(new TestRunManifest.Counts(1, 1, 0, 0));
        return run;
    }

    private static TestTrendReport manyRunsReport(int count) {
        TestTrendReport report = new TestTrendReport();
        report.setSuiteName("unit");
        report.setRunCount(count);
        report.setNotEnoughHistory(false);
        report.setFailureRate(0.0);
        report.setStreakDirection("PASSING");
        report.setStreakLength(count);
        List<TestTrendReport.Run> runs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Chronological, oldest first: run 0 is the oldest, run (count-1) the newest.
            runs.add(runAt(String.format(Locale.ROOT, "2026-01-%02dT00:00:00Z", (i % 28) + 1)));
        }
        report.setRuns(runs);
        return report;
    }

    @Test
    void consoleCapsRunsListAt100() {
        TestTrendReport report = manyRunsReport(105);
        // Runs are chronological oldest-first: index 0 is the oldest of 105, outside the latest-100
        // window; index 104 is the newest, always shown.
        report.getRuns().get(0).setArchivedAt("1999-01-01T00:00:00Z");
        report.getRuns().get(104).setArchivedAt("2099-01-01T00:00:00Z");

        String summary = TestTrendReportWriter.toConsoleSummary(report);

        assertThat(summary, containsString("showing latest 100 of 105"));
        assertThat(summary, containsString("2099-01-01T00:00:00Z"));
        assertThat(summary, is(not(containsString("1999-01-01T00:00:00Z"))));
    }

    @Test
    void htmlCapsRunsTableAt100() {
        TestTrendReport report = manyRunsReport(105);
        report.getRuns().get(0).setArchivedAt("1999-01-01T00:00:00Z");
        report.getRuns().get(104).setArchivedAt("2099-01-01T00:00:00Z");

        String html = TestTrendReportWriter.toHtml(report);
        // Scoped to the table's <tbody> only: the charts intentionally still plot every archived run
        // (only the table/list is capped - see TestTrendReportModel.newestFirst's javadoc), so the
        // oldest run's timestamp legitimately still appears earlier in the page, in a chart tooltip.
        // Counted by "<tr" occurrences rather than assuming any particular whitespace/attribute
        // layout around each row's opening tag.
        String table = html.substring(html.indexOf("<h2>Runs"));
        String tbody = table.substring(table.indexOf("<tbody>"));
        int rowCount = tbody.split("<tr").length - 1;

        assertThat(html, containsString("showing latest 100 of 105"));
        assertThat(rowCount, is(100));
        assertThat(tbody, containsString("2099-01-01T00:00:00Z"));
        assertThat(tbody, is(not(containsString("1999-01-01T00:00:00Z"))));
    }

    @Test
    void passFailChartHasALegendAndPerDotTooltipsInsteadOfBareHeightEncoding() {
        String html = TestTrendReportWriter.toHtml(twoRunReport());
        String chart = extractChart(html, "Pass/fail");

        // Legend: color is never the only identity cue (dataviz skill's status-color rule).
        assertThat(html, containsString("Passed"));
        assertThat(html, containsString("Failed"));
        // Every dot carries a native tooltip naming its date and outcome - no more guessing what a
        // dot means from its position alone.
        assertThat(chart, containsString("<title>2026-08-20T10:00:00Z - PASSED"));
        assertThat(chart, containsString("<title>2026-08-21T10:00:00Z - FAILED"));
        // An x-axis date tick, so a reader can tell *when* without opening the table.
        assertThat(chart, containsString("08-20"));
    }

    @Test
    void durationChartHasYAxisLabelsAnAverageReferenceLineAndPerDotTooltips() {
        String html = TestTrendReportWriter.toHtml(twoRunReport());
        String chart = extractChart(html, "Duration");

        // Actual seconds on the y-axis, not just relative dot height.
        assertThat(chart, containsString("40.0s"));
        assertThat(chart, containsString("60.0s"));
        // The average reference line, labeled with the same average the summary/table report.
        assertThat(chart, containsString("avg 50.0s"));
        // Tooltip per dot, including the deviation-flagged one.
        assertThat(chart, containsString("<title>2026-08-20T10:00:00Z - 40.0s"));
        assertThat(chart, containsString("<title>2026-08-21T10:00:00Z - 60.0s (duration deviation)"));
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
    void consoleSummaryFormatsFailureRateStreakDurationAndPerRunLines() {
        String summary = TestTrendReportWriter.toConsoleSummary(twoRunReport());

        assertThat(summary, containsString("testIntegration"));
        assertThat(summary, containsString("Failure rate: 50.0%"));
        assertThat(summary, containsString("Streak: FAILING x1"));
        assertThat(summary, containsString("Average duration: 50.0s"));
        assertThat(summary, containsString("2026-08-20T10:00:00Z"));
        assertThat(summary, containsString("PASSED"));
        assertThat(summary, containsString("2026-08-21T10:00:00Z"));
        assertThat(summary, containsString("FAILED"));
        assertThat(summary, containsString("[duration deviation]"));
    }

    @Test
    void writeCreatesBothFilesInOutputDir(@TempDir File tmp) throws IOException {
        TestTrendReportWriter.write(twoRunReport(), tmp);

        assertThat(new File(tmp, "trends-testIntegration.json").exists(), is(true));
        assertThat(new File(tmp, "trends-testIntegration.html").exists(), is(true));
        assertThat(Files.readString(new File(tmp, "trends-testIntegration.json").toPath()),
                containsString("testIntegration"));
        // write() also copies the shared stylesheet alongside the report - same mechanism
        // TestReportCssTest already exercises directly.
        assertThat(new File(tmp, "test-report.css").exists(), is(true));
    }

    @Test
    void consoleAndHtmlRenderTheManifestsOwnOutcomeNotTheDerivedGreenFlag() {
        // A zero-test "PASSED" run: isGreen() is conservatively false (see TestRunManifest#isGreen),
        // but the manifest's own recorded outcome is still "PASSED" - the rendered text must match
        // that recorded outcome, not the derived green boolean, or trends-<suiteName>.json (serialized
        // straight from outcome) and the console/HTML text would visibly disagree for this exact run.
        TestTrendReport report = new TestTrendReport();
        report.setSuiteName("testIntegration");
        report.setRunCount(1);
        report.setNotEnoughHistory(false);
        report.setFailureRate(1.0);
        report.setStreakDirection("FAILING");
        report.setStreakLength(1);

        TestTrendReport.Run run = new TestTrendReport.Run();
        run.setRunId("2026-08-22_10h00m00s_testIntegration");
        run.setArchivedAt("2026-08-22T10:00:00Z");
        run.setOutcome("PASSED");
        run.setGreen(false);
        run.setCounts(new TestRunManifest.Counts(0, 0, 0, 0));
        report.setRuns(List.of(run));

        String summary = TestTrendReportWriter.toConsoleSummary(report);
        String html = TestTrendReportWriter.toHtml(report);

        assertThat(summary, containsString("PASSED"));
        assertThat(html, containsString("<td>PASSED</td>"));
    }

    @Test
    void durationChartFallsBackToNoDataMessageWhenNoRunHasADuration() {
        TestTrendReport report = twoRunReport();
        report.getRuns().forEach(run -> run.setDurationSeconds(null));

        String html = TestTrendReportWriter.toHtml(report);

        assertThat(html, containsString("No duration data recorded"));
    }

    @Test
    void consoleSummaryIncludesCountDecreasedAndSkippedIncreasedSuffixes() {
        TestTrendReport report = twoRunReport();
        TestTrendReport.Run run = report.getRuns().get(1);
        run.setDurationDeviationFlag(false); // isolate this test to just the two flags under test
        run.setCountDecreasedFlag(true);
        run.setSkippedIncreasedFlag(true);

        String summary = TestTrendReportWriter.toConsoleSummary(report);

        assertThat(summary, containsString("[test count decreased, skipped increased]"));
    }

    @Test
    void consoleSummaryJoinsMultipleFlagsOnOneRunWithCommaInsideASingleBracket() {
        TestTrendReport report = twoRunReport();
        TestTrendReport.Run run = report.getRuns().get(1);
        run.setDurationDeviationFlag(true);
        run.setCountDecreasedFlag(true);
        run.setSkippedIncreasedFlag(true);

        String summary = TestTrendReportWriter.toConsoleSummary(report);

        assertThat(summary, containsString("[duration deviation, test count decreased, skipped increased]"));
    }

    @Test
    void consoleSummaryIncludesAFlagLegendExplainingEachFlag() {
        String summary = TestTrendReportWriter.toConsoleSummary(twoRunReport());

        assertThat(summary, containsString("Flag legend:"));
        assertThat(summary, containsString("filtered"));
        assertThat(summary, containsString("duration deviation"));
        assertThat(summary, containsString("test count decreased"));
        assertThat(summary, containsString("skipped increased"));
    }

    @Test
    void consoleSummaryNotesFilteredRunCountAndTagsFilteredRunLines() {
        TestTrendReport report = twoRunReport();
        report.setFilteredRunCount(1);
        TestTrendReport.Run run = report.getRuns().get(1);
        run.setDurationDeviationFlag(false); // isolate this test to just the filtered flag
        run.setFiltered(true);

        String summary = TestTrendReportWriter.toConsoleSummary(report);

        assertThat(summary, containsString("1 filtered/partial"));
        assertThat(summary, containsString("[filtered run]")); // no filterDetail set - generic fallback
    }

    @Test
    void consoleSummaryTagsFilteredRunLineWithItsFilterDetailWhenPresent() {
        TestTrendReport report = twoRunReport();
        TestTrendReport.Run run = report.getRuns().get(1);
        run.setDurationDeviationFlag(false); // isolate this test to just the filtered flag
        run.setFiltered(true);
        run.setFilterDetail("org.example.SomeTest");

        String summary = TestTrendReportWriter.toConsoleSummary(report);

        assertThat(summary, containsString("[filtered: org.example.SomeTest]"));
    }

    @Test
    void htmlNotesFilteredRunCountAndMarksFilteredRows() {
        TestTrendReport report = twoRunReport();
        report.setFilteredRunCount(1);
        report.getRuns().get(1).setFiltered(true);

        String html = TestTrendReportWriter.toHtml(report);

        assertThat(html, containsString("1 filtered/partial"));
        assertThat(html, containsString("row-filtered"));
        assertThat(html, containsString("filtered"));
    }

    @Test
    void htmlFlagsCellJoinsMultipleFlagsWithComma() {
        TestTrendReport report = twoRunReport();
        TestTrendReport.Run run = report.getRuns().get(1);
        run.setFiltered(true);
        run.setDurationDeviationFlag(true);
        run.setCountDecreasedFlag(true);
        run.setSkippedIncreasedFlag(true);

        String html = TestTrendReportWriter.toHtml(report);

        assertThat(html, containsString("filtered</span>, duration, count-decrease, skipped-increase"));
    }

    @Test
    void htmlIncludesAFlagLegendExplainingEachFlag() {
        String html = TestTrendReportWriter.toHtml(twoRunReport());

        assertThat(html, containsString("Flag legend"));
        assertThat(html, containsString("filtered"));
        assertThat(html, containsString("duration"));
        assertThat(html, containsString("count-decrease"));
        assertThat(html, containsString("skipped-increase"));
    }

    @Test
    void htmlHasAFilterColumnShowingDetailOnlyForFilteredRows() {
        TestTrendReport report = twoRunReport();
        report.getRuns().get(1).setFiltered(true);
        report.getRuns().get(1).setFilterDetail("org.apache.ofbiz.base.conversion.DateTimeTests");

        String html = TestTrendReportWriter.toHtml(report);

        assertThat(html, containsString("<th>Filter</th>"));
        assertThat(html, containsString("org.apache.ofbiz.base.conversion.DateTimeTests"));
        // The full run's own row must not carry the other row's filter detail. Found by locating the
        // <tr> immediately before this run's archivedAt text, without assuming any particular
        // whitespace between the <tr> tag and its first <td> - its row may be the last one in the
        // table (rows display newest-first), so the row's end is whichever comes first: the next
        // <tr>, or (if it's the last row) the closing </tbody>.
        // Scoped to the table (search starts at "<h2>Runs"), not the whole page: the same archivedAt
        // timestamp also appears earlier, in this run's pass/fail and duration chart tooltips - an
        // unscoped search would find that chart occurrence instead of the table row.
        int tableStart = html.indexOf("<h2>Runs");
        int anchor = html.indexOf("2026-08-20T10:00:00Z", tableStart);
        int rowStart = html.lastIndexOf("<tr", anchor);
        int nextTr = html.indexOf("<tr", anchor);
        int tbodyEnd = html.indexOf("</tbody>", anchor);
        int rowEnd = (nextTr == -1 || nextTr > tbodyEnd) ? tbodyEnd : nextTr;
        String row = html.substring(rowStart, rowEnd);
        assertThat(row, is(not(containsString("DateTimeTests"))));
    }

    @Test
    void chartsExcludeFilteredRunsButTheRunsTableStillListsThem() {
        TestTrendReport report = twoRunReport();
        // A filtered run with a wildly different outcome/duration than the full run beside it - if
        // it leaked into the charts, the pass/fail chart would gain a 3rd point and the duration
        // chart's range would need to stretch to fit 5s.
        TestTrendReport.Run filtered = new TestTrendReport.Run();
        filtered.setRunId("2026-08-20_12h00m00s_testIntegration");
        filtered.setArchivedAt("2026-08-20T12:00:00Z");
        filtered.setOutcome("FAILED");
        filtered.setGreen(false);
        filtered.setFiltered(true);
        filtered.setCounts(new TestRunManifest.Counts(1, 0, 1, 0));
        filtered.setDurationSeconds(5L);
        report.setRuns(List.of(report.getRuns().get(0), filtered, report.getRuns().get(1)));
        report.setFilteredRunCount(1);

        String html = TestTrendReportWriter.toHtml(report);
        String unfilteredHtml = TestTrendReportWriter.toHtml(twoRunReport());

        // Same two full runs plotted either way - the filtered run in between contributes no extra
        // chart point, so both charts render byte-for-byte the same as the no-filtered-run report.
        assertThat(extractChart(html, "Pass/fail"), is(extractChart(unfilteredHtml, "Pass/fail")));
        assertThat(extractChart(html, "Duration"), is(extractChart(unfilteredHtml, "Duration")));
        // But the filtered run is still listed in the table.
        assertThat(html, containsString("2026-08-20T12:00:00Z"));
    }

    @Test
    void htmlLinksTheSharedStylesheetInsteadOfInliningCss() {
        String html = TestTrendReportWriter.toHtml(twoRunReport());

        assertThat(html, containsString("<link rel=\"stylesheet\" href=\"test-report.css\">"));
        assertThat(html, is(not(containsString("<style>"))));
    }

    @Test
    void htmlUsesTheSharedBootstrapStyleStatCardsAndTableClasses() {
        String html = TestTrendReportWriter.toHtml(twoRunReport());

        assertThat(html, containsString("class=\"stat-cards\""));
        assertThat(html, containsString("class=\"bs-table\""));
    }

    private static String extractChart(String html, String heading) {
        int start = html.indexOf("<h2>" + heading);
        int svgStart = html.indexOf("<svg", start);
        int svgEnd = html.indexOf("</svg>", svgStart) + "</svg>".length();
        return html.substring(svgStart, svgEnd);
    }
}
