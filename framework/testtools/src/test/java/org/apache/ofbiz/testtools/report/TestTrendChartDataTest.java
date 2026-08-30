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
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

class TestTrendChartDataTest {

    private static TestTrendReport.Run run(String archivedAt, boolean green, Long durationSeconds) {
        TestTrendReport.Run run = new TestTrendReport.Run();
        run.setArchivedAt(archivedAt);
        run.setOutcome(green ? "PASSED" : "FAILED");
        run.setGreen(green);
        run.setDurationSeconds(durationSeconds);
        return run;
    }

    @Test
    void buildPassFailChartReturnsNullForNoRuns() {
        assertThat(TestTrendChartData.buildPassFailChart(List.of()), is(nullValue()));
    }

    @Test
    void buildPassFailChartPlacesTwoPointsAtTheClampedMinWidth() {
        List<TestTrendReport.Run> runs = List.of(
                run("2026-08-20T10:00:00Z", true, 40L),
                run("2026-08-21T10:00:00Z", false, 60L));

        TestTrendChartData.PassFailChart chart = TestTrendChartData.buildPassFailChart(runs);

        // 2 runs * 40px/run = 80, below CHART_MIN_WIDTH (400), so width clamps to 400.
        assertThat(chart.getWidth(), is(400));
        assertThat(chart.getPoints().size(), is(2));
        assertThat(chart.getPoints().get(0).getX(), closeTo(10.0, 0.01));
        assertThat(chart.getPoints().get(0).getStatus(), is("pass"));
        assertThat(chart.getPoints().get(1).getX(), closeTo(390.0, 0.01));
        assertThat(chart.getPoints().get(1).getStatus(), is("fail"));
        assertThat(chart.getPoints().get(0).getTooltip(),
                is(TestTrendChartData.formatDisplayDateTime("2026-08-20T10:00:00Z") + " - PASSED (40.0s)"));
    }

    @Test
    void buildDurationChartReturnsNullForNoRuns() {
        assertThat(TestTrendChartData.buildDurationChart(List.of(), 50.0), is(nullValue()));
    }

    @Test
    void buildDurationChartHasNoDataWhenNoRunRecordedADuration() {
        List<TestTrendReport.Run> runs = List.of(run("2026-08-20T10:00:00Z", true, null));

        TestTrendChartData.DurationChart chart = TestTrendChartData.buildDurationChart(runs, null);

        assertThat(chart.isHasData(), is(false));
        assertThat(chart.getPoints(), is(empty()));
    }

    @Test
    void buildDurationChartOmitsTheAverageLineWhenOutOfRange() {
        List<TestTrendReport.Run> runs = List.of(
                run("2026-08-20T10:00:00Z", true, 40L),
                run("2026-08-21T10:00:00Z", false, 60L));

        TestTrendChartData.DurationChart chart = TestTrendChartData.buildDurationChart(runs, 1000.0);

        assertThat(chart.getAvgLine(), is(nullValue()));
    }

    @Test
    void buildDurationChartIncludesTheAverageLineWhenInRangeAndFlagsDeviatedPoints() {
        List<TestTrendReport.Run> runs = List.of(
                run("2026-08-20T10:00:00Z", true, 40L),
                run("2026-08-21T10:00:00Z", false, 60L));
        runs.get(1).setDurationDeviationFlag(true);

        TestTrendChartData.DurationChart chart = TestTrendChartData.buildDurationChart(runs, 50.0);

        assertThat(chart.getAvgLine().getText(), is("avg 50.0s"));
        assertThat(chart.getMinLabel().getText(), is("40.0s"));
        assertThat(chart.getMaxLabel().getText(), is("60.0s"));
        assertThat(chart.getPoints().get(1).isFlagged(), is(true));
        assertThat(chart.getPoints().get(1).getTooltip(), is(TestTrendChartData.formatDisplayDateTime("2026-08-21T10:00:00Z")
                + " - 60.0s (duration deviation)"));
    }

    @Test
    void passFailChartExposesThePlotLeftAndPlotRightTickGenerationActuallyUsed() {
        List<TestTrendReport.Run> runs = List.of(
                run("2026-08-20T10:00:00Z", true, 40L),
                run("2026-08-21T10:00:00Z", false, 60L));

        TestTrendChartData.PassFailChart chart = TestTrendChartData.buildPassFailChart(runs);

        // buildDateTicks places its first tick at exactly plotLeft (i == 0, so x = plotLeft + 0 *
        // stepX) - if the bean's exposed plotLeft ever drifts from the value tick generation actually
        // used, this fails instead of only drifting visually (see buildDateTicks's javadoc warning).
        assertThat((double) chart.getPlotLeft(), closeTo(chart.getTicks().get(0).getX(), 0.01));
        assertThat(chart.getPlotRight(), is(chart.getWidth() - 10));
    }

    @Test
    void durationChartExposesThePlotLeftAndPlotRightTickGenerationActuallyUsed() {
        List<TestTrendReport.Run> runs = List.of(
                run("2026-08-20T10:00:00Z", true, 40L),
                run("2026-08-21T10:00:00Z", false, 60L));

        TestTrendChartData.DurationChart chart = TestTrendChartData.buildDurationChart(runs, 50.0);

        assertThat(chart.getPlotLeft(), is(TestTrendChartData.DURATION_PLOT_LEFT));
        assertThat((double) chart.getPlotLeft(), closeTo(chart.getTicks().get(0).getX(), 0.01));
        assertThat(chart.getPlotRight(), is(chart.getWidth() - 10));
    }

    @Test
    void dateTicksCapAtEightAndAlwaysIncludeTheLastRun() {
        List<TestTrendReport.Run> runs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            runs.add(run(String.format(Locale.ROOT, "2026-01-%02dT00:00:00Z", i + 1), true, null));
        }

        List<TestTrendChartData.DateTick> ticks = TestTrendChartData.buildDateTicks(runs, 800, 10);

        assertThat(ticks.size() <= TestTrendChartData.MAX_DATE_TICKS, is(true));
        assertThat(ticks.get(ticks.size() - 1).getLabel(),
                is(TestTrendChartData.formatTickDate("2026-01-20T00:00:00Z")));
    }

    @Test
    void buildPassFailChartTooltipHasNoLiteralNullForMissingArchivedAtOrOutcome() {
        TestTrendReport.Run runWithNulls = new TestTrendReport.Run();
        runWithNulls.setGreen(true);
        // archivedAt and outcome left null, as a deserialized manifest.json can leave them.

        TestTrendChartData.PassFailChart chart = TestTrendChartData.buildPassFailChart(List.of(runWithNulls));

        assertThat(chart.getPoints().get(0).getTooltip(), is(not(containsString("null"))));
    }

    @Test
    void buildDurationChartTooltipHasNoLiteralNullForMissingArchivedAt() {
        TestTrendReport.Run runWithNullArchivedAt = new TestTrendReport.Run();
        runWithNullArchivedAt.setDurationSeconds(40L);

        TestTrendChartData.DurationChart chart =
                TestTrendChartData.buildDurationChart(List.of(runWithNullArchivedAt), null);

        assertThat(chart.getPoints().get(0).getTooltip(), is(not(containsString("null"))));
    }

    @Test
    void formatDurationHandlesNullAndNumericValues() {
        assertThat(TestTrendChartData.formatDuration(null), is("-"));
        assertThat(TestTrendChartData.formatDuration(12.34), is("12.3s"));
    }

    @Test
    void formatDisplayDateTimeUsesTwentyFourHourYyyyMmDdHhMmSsAndHandlesNullAndMalformedInput() {
        String formatted = TestTrendChartData.formatDisplayDateTime("2026-08-20T10:00:00Z");

        assertThat(formatted.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), is(true));
        // No "T"/"Z" ISO-8601 punctuation survives into the display form.
        assertThat(formatted, is(not(containsString("T"))));
        assertThat(formatted, is(not(containsString("Z"))));
        // Null and malformed input fall back gracefully instead of throwing (a deserialized
        // manifest.json can leave archivedAt null - same contract as formatTickDate).
        assertThat(TestTrendChartData.formatDisplayDateTime(null), is(""));
        assertThat(TestTrendChartData.formatDisplayDateTime("not-a-date"), is("not-a-date"));
    }
}
