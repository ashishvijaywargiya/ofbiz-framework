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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the {@code Map<String, Object>} FreeMarker data model {@code trends-report.ftl} (and its
 * chart/table partials) render from - every value a template shouldn't compute itself: formatted
 * percentages/durations, per-run flag tags in display order, and (via {@link TestTrendChartData})
 * the two charts' prepared point geometry. {@link TestTrendReportWriter#toHtml} is the only caller.
 */
public final class TestTrendReportModel {

    // Temporary cap on the HTML run-by-run table - a suite with months of history can run to
    // hundreds of archived rows, unwieldy to scroll through. Both charts and trends-<suiteName>.json
    // are unaffected: they still cover every archived run - only this rendered table is trimmed.
    private static final int MAX_DISPLAYED_RUNS = 100;

    private TestTrendReportModel() {
    }

    /** One flag chip on a run's table row - {@code muted} is true only for the "filtered" tag. */
    public static final class FlagTag {
        private final String label;
        private final boolean muted;

        FlagTag(String label, boolean muted) {
            this.label = label;
            this.muted = muted;
        }

        public String getLabel() {
            return label;
        }

        public boolean isMuted() {
            return muted;
        }
    }

    /** One run-table row: the run bean itself, its flag tags in display order, and its formatted duration. */
    public static final class RunRow {
        private final TestTrendReport.Run run;
        private final List<FlagTag> flags;
        private final String durationText;

        RunRow(TestTrendReport.Run run, List<FlagTag> flags, String durationText) {
            this.run = run;
            this.flags = flags;
            this.durationText = durationText;
        }

        public TestTrendReport.Run getRun() {
            return run;
        }

        public List<FlagTag> getFlags() {
            return flags;
        }

        public String getDurationText() {
            return durationText;
        }
    }

    public static Map<String, Object> build(TestTrendReport report) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("suiteName", report.getSuiteName());
        model.put("runCount", report.getRunCount());
        model.put("filteredRunCount", report.getFilteredRunCount());
        model.put("notEnoughHistory", report.isNotEnoughHistory());
        if (report.isNotEnoughHistory()) {
            return model;
        }
        model.put("failureRateText", String.format(Locale.ROOT, "%.1f%%", report.getFailureRate() * 100.0));
        model.put("streakDirection", report.getStreakDirection());
        model.put("streakLength", report.getStreakLength());
        model.put("averageDurationText", TestTrendChartData.formatDuration(report.getAverageDurationSeconds()));

        // Filtered runs are excluded here (not just left unflagged) so the charts plot only the full
        // runs the trend stats above are actually computed from.
        List<TestTrendReport.Run> chartRuns = report.getRuns().stream()
                .filter(run -> !run.isFiltered())
                .collect(Collectors.toList());
        model.put("passFailChart", TestTrendChartData.buildPassFailChart(chartRuns));
        model.put("durationChart",
                TestTrendChartData.buildDurationChart(chartRuns, report.getAverageDurationSeconds()));

        List<TestTrendReport.Run> displayedRuns = newestFirst(report.getRuns());
        model.put("displayedRunCount", displayedRuns.size());
        model.put("totalRunCount", report.getRuns().size());
        model.put("runRows",
                displayedRuns.stream().map(TestTrendReportModel::buildRunRow).collect(Collectors.toList()));
        return model;
    }

    private static RunRow buildRunRow(TestTrendReport.Run run) {
        List<FlagTag> flags = new ArrayList<>();
        if (run.isFiltered()) {
            flags.add(new FlagTag("filtered", true));
        }
        if (run.isDurationDeviationFlag()) {
            flags.add(new FlagTag("duration", false));
        }
        if (run.isCountDecreasedFlag()) {
            flags.add(new FlagTag("count-decrease", false));
        }
        if (run.isSkippedIncreasedFlag()) {
            flags.add(new FlagTag("skipped-increase", false));
        }
        String durationText = run.getDurationSeconds() == null ? "-"
                : TestTrendChartData.formatDuration(run.getDurationSeconds().doubleValue());
        return new RunRow(run, flags, durationText);
    }

    /**
     * {@code runsChronological} in reverse (newest archived-at first), capped to
     * {@value #MAX_DISPLAYED_RUNS} - used for human-facing run-by-run lists (the console summary and
     * the HTML table) so the most recent result is the first thing a reader sees and a long-running
     * suite's history doesn't dump hundreds of rows into one page, without touching the charts' or
     * trends-<suiteName>.json's own chronological order - both still cover every archived run, only
     * this list is trimmed.
     */
    public static List<TestTrendReport.Run> newestFirst(List<TestTrendReport.Run> runsChronological) {
        List<TestTrendReport.Run> reversed = new ArrayList<>(runsChronological);
        Collections.reverse(reversed);
        return reversed.size() > MAX_DISPLAYED_RUNS ? reversed.subList(0, MAX_DISPLAYED_RUNS) : reversed;
    }
}
