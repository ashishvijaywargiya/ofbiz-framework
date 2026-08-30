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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.ofbiz.base.lang.JSON;

/**
 * Renders a {@link TestTrendReport} three ways: a console-friendly summary (printed by
 * {@link TestTrendReportCli}), {@code trends-<suiteName>.json} (the report serialized via the same
 * Jackson-backed {@code JSON} helper {@code manifest.json} uses), and {@code trends-<suiteName>.html} -
 * a self-contained page with two hand-rolled inline-SVG line charts (pass/fail over time, duration
 * over time) plus the underlying per-run table. No external chart library. The suite name is part
 * of the filename so that two suites configured to share the same output directory don't
 * overwrite each other's trend report.
 */
public final class TestTrendReportWriter {

    private static final int CHART_WIDTH_PER_RUN = 40;
    private static final int CHART_MIN_WIDTH = 400;
    private static final int PASS_FAIL_CHART_HEIGHT = 60;
    private static final int DURATION_CHART_HEIGHT = 140;
    private static final int MAX_DATE_TICKS = 8;
    // Left inset for the duration chart's plotted line/points, wide enough to clear its y-axis value
    // labels (which sit at x=0) - the pass/fail chart carries no such labels, so it keeps a bare 10px.
    private static final int DURATION_PLOT_LEFT = 40;

    // Fixed status/sequential colors (dataviz skill's reference palette - never re-themed):
    // PASSED/FAILED are a status (state) job, never plain categorical hues; the duration line is a
    // magnitude trend, so it gets the single default sequential hue instead of an arbitrary color.
    private static final String COLOR_GOOD = "#0ca30c";
    private static final String COLOR_CRITICAL = "#d03b3b";
    private static final String COLOR_WARNING = "#fab219";
    private static final String COLOR_SEQUENTIAL = "#2a78d6";
    private static final String COLOR_SURFACE = "#fcfcfb";
    private static final String COLOR_MUTED = "#898781";
    private static final String COLOR_GRIDLINE = "#e1e0d9";
    private static final String COLOR_BASELINE = "#c3c2b7";
    // Temporary cap on the console/HTML run-by-run list/table - a suite with months of history can
    // run to hundreds of archived rows, unwieldy to scroll through. Charts and trends-<suiteName>.json
    // are unaffected: both still cover every archived run, this only trims what's rendered as a list.
    private static final int MAX_DISPLAYED_RUNS = 100;

    private TestTrendReportWriter() {
    }

    /** Writes {@code trends-<suiteName>.json} and {@code trends-<suiteName>.html} into {@code outputDir}. */
    public static void write(TestTrendReport report, File outputDir) throws IOException {
        Files.createDirectories(outputDir.toPath());
        String baseName = "trends-" + report.getSuiteName();
        Files.writeString(new File(outputDir, baseName + ".json").toPath(), toJson(report));
        Files.writeString(new File(outputDir, baseName + ".html").toPath(), toHtml(report));
    }

    public static String toJson(TestTrendReport report) throws IOException {
        return JSON.from(report).toString();
    }

    public static String toConsoleSummary(TestTrendReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trend report for '").append(report.getSuiteName()).append("' (")
                .append(report.getRunCount()).append(" run(s) archived");
        if (report.getFilteredRunCount() > 0) {
            sb.append(", ").append(report.getFilteredRunCount())
                    .append(" filtered/partial - excluded from the stats below");
        }
        sb.append(")\n");
        if (report.isNotEnoughHistory()) {
            sb.append("  Not enough history yet - need at least 2 archived runs.\n");
            return sb.toString();
        }
        sb.append(String.format(Locale.ROOT, "  Failure rate: %.1f%%%n", report.getFailureRate() * 100.0));
        sb.append("  Streak: ").append(report.getStreakDirection()).append(" x")
                .append(report.getStreakLength()).append('\n');
        sb.append("  Average duration: ").append(formatDuration(report.getAverageDurationSeconds())).append('\n');
        List<TestTrendReport.Run> displayedRuns = newestFirst(report.getRuns());
        sb.append("  Runs (newest to oldest");
        if (displayedRuns.size() < report.getRuns().size()) {
            sb.append(", showing latest ").append(displayedRuns.size())
                    .append(" of ").append(report.getRuns().size());
        }
        sb.append("):\n");
        for (TestTrendReport.Run run : displayedRuns) {
            sb.append("    ").append(run.getArchivedAt()).append("  ")
                    .append(run.getOutcome()).append("  ")
                    .append(formatDuration(run.getDurationSeconds() == null ? null
                            : run.getDurationSeconds().doubleValue()));
            List<String> flags = consoleFlagDescriptors(run);
            if (!flags.isEmpty()) {
                sb.append("  [").append(String.join(", ", flags)).append(']');
            }
            sb.append('\n');
        }
        sb.append('\n').append(CONSOLE_FLAG_LEGEND);
        return sb.toString();
    }

    /**
     * One run's active flags as human-readable phrases, in a fixed order - joined with a comma by
     * {@link #toConsoleSummary} into a single bracketed group (e.g. {@code [duration deviation,
     * test count decreased]}) rather than one bracket per flag, so multiple flags on the same run
     * read as one list instead of a run-together wall of brackets.
     */
    private static List<String> consoleFlagDescriptors(TestTrendReport.Run run) {
        List<String> flags = new ArrayList<>();
        if (run.isFiltered()) {
            flags.add(run.getFilterDetail() == null || run.getFilterDetail().isBlank()
                    ? "filtered run" : "filtered: " + run.getFilterDetail());
        }
        if (run.isDurationDeviationFlag()) {
            flags.add("duration deviation");
        }
        if (run.isCountDecreasedFlag()) {
            flags.add("test count decreased");
        }
        if (run.isSkippedIncreasedFlag()) {
            flags.add("skipped increased");
        }
        return flags;
    }

    // Same four flags explained in toHtml()'s "Flag legend" section below, worded to match this
    // console rendering's own flag phrases (see consoleFlagDescriptors) rather than the HTML table's
    // shorter tags.
    private static final String CONSOLE_FLAG_LEGEND = "Flag legend:\n"
            + "  filtered / filtered: <detail> - this run only executed a narrowed-down subset of "
            + "the suite (a --tests filter, or suitename=/component=/case=/method= for "
            + "testIntegration), not the whole suite. Excluded from failure rate, streak, the "
            + "duration baseline, and the two drift flags below.\n"
            + "  duration deviation - this run's duration differed from the average duration of "
            + "full runs by more than the configured threshold (test.trend.duration.deviation."
            + "percent, default 25%), either slower or faster.\n"
            + "  test count decreased - this run executed fewer tests in total than the previous "
            + "full run - possibly tests were skipped, removed, or the run didn't complete.\n"
            + "  skipped increased - this run had more skipped tests than the previous full run - "
            + "possibly new @Disabled/@Ignore tests or an environment issue.\n";

    public static String toHtml(TestTrendReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Test Trend Report - ")
                .append(escapeXml(report.getSuiteName())).append("</title>")
                .append("<style>")
                .append("body{font-family:sans-serif;margin:2em;color:#222}")
                .append("h1{font-size:1.4em}table{border-collapse:collapse;margin-top:1em}")
                .append("th,td{border:1px solid #ccc;padding:4px 8px;text-align:left;font-size:0.9em}")
                .append("th{background:#f0f0f0}.flag{color:#b45309;font-weight:bold}")
                .append(".summary{margin:1em 0}")
                .append(".row-filtered{color:#94a3b8;font-style:italic}")
                .append(".flag-filtered{color:#64748b;font-weight:normal}")
                .append(".chart-caption{margin:0.25em 0 0.75em;font-size:0.85em;color:#52514e}")
                .append(".legend{display:flex;gap:1.25em;margin:0.5em 0;font-size:0.85em;color:#52514e}")
                .append(".legend-item{display:flex;align-items:center;gap:0.4em}")
                .append(".legend-dot{width:10px;height:10px;border-radius:50%;display:inline-block}")
                .append(".legend-ring{width:10px;height:10px;border-radius:50%;display:inline-block;"
                        + "background:" + COLOR_SEQUENTIAL + ";box-shadow:0 0 0 2px " + COLOR_WARNING + "}")
                .append(".legend-line{width:14px;height:2px;background:" + COLOR_SEQUENTIAL + ";display:inline-block}")
                .append("</style></head><body>");
        html.append("<h1>Trend report: ").append(escapeXml(report.getSuiteName())).append("</h1>");
        html.append("<div class=\"summary\">").append(report.getRunCount()).append(" run(s) archived");
        if (report.getFilteredRunCount() > 0) {
            html.append(", ").append(report.getFilteredRunCount())
                    .append(" filtered/partial (excluded from the stats below)");
        }
        html.append("</div>");

        if (report.isNotEnoughHistory()) {
            html.append("<p>Not enough history yet - need at least 2 archived runs.</p></body></html>");
            return html.toString();
        }

        html.append("<div class=\"summary\">Failure rate: ")
                .append(String.format(Locale.ROOT, "%.1f%%", report.getFailureRate() * 100.0))
                .append(" | Streak: ").append(report.getStreakDirection()).append(" x")
                .append(report.getStreakLength())
                .append(" | Average duration: ")
                .append(escapeXml(formatDuration(report.getAverageDurationSeconds())))
                .append("</div>");

        // Filtered runs are excluded here (not just left unflagged) so the charts plot only the
        // full runs the trend stats above are actually computed from - see the class javadoc.
        List<TestTrendReport.Run> chartRuns = report.getRuns().stream()
                .filter(run -> !run.isFiltered())
                .collect(Collectors.toList());
        html.append("<h2>Pass/fail over time</h2>")
                .append("<p class=\"chart-caption\">Each dot is one archived run, oldest to newest, "
                        + "left to right. Hover a dot for its date and outcome.</p>")
                .append("<div class=\"legend\">")
                .append("<span class=\"legend-item\"><span class=\"legend-dot\" style=\"background:")
                .append(COLOR_GOOD).append("\"></span>Passed</span>")
                .append("<span class=\"legend-item\"><span class=\"legend-dot\" style=\"background:")
                .append(COLOR_CRITICAL).append("\"></span>Failed</span>")
                .append("</div>")
                .append(buildPassFailChart(chartRuns));
        html.append("<h2>Duration over time</h2>")
                .append("<p class=\"chart-caption\">Each dot is one archived run's duration, oldest to "
                        + "newest, left to right. The muted horizontal line marks the average across all "
                        + "full runs; amber-ringed dots deviated from it by more than the configured "
                        + "threshold. Hover a dot for its exact value.</p>")
                .append("<div class=\"legend\">")
                .append("<span class=\"legend-item\"><span class=\"legend-line\"></span>Duration</span>")
                .append("<span class=\"legend-item\"><span class=\"legend-ring\"></span>Deviation flagged</span>")
                .append("</div>")
                .append(buildDurationChart(chartRuns, report.getAverageDurationSeconds()));

        List<TestTrendReport.Run> displayedRuns = newestFirst(report.getRuns());
        html.append("<h2>Runs (newest first");
        if (displayedRuns.size() < report.getRuns().size()) {
            html.append(", showing latest ").append(displayedRuns.size())
                    .append(" of ").append(report.getRuns().size());
        }
        html.append(")</h2><table><tr><th>Archived at</th><th>Outcome</th><th>Total</th>"
                + "<th>Failed</th><th>Skipped</th><th>Duration</th><th>Flags</th><th>Filter</th></tr>");
        for (TestTrendReport.Run run : displayedRuns) {
            html.append("<tr").append(run.isFiltered() ? " class=\"row-filtered\"" : "").append("><td>")
                    .append(escapeXml(run.getArchivedAt())).append("</td><td>")
                    .append(escapeXml(run.getOutcome())).append("</td><td>")
                    .append(run.getCounts() == null ? "-" : run.getCounts().getTotal()).append("</td><td>")
                    .append(run.getCounts() == null ? "-" : run.getCounts().getFailed()).append("</td><td>")
                    .append(run.getCounts() == null ? "-" : run.getCounts().getSkipped()).append("</td><td>")
                    .append(escapeXml(formatDuration(run.getDurationSeconds() == null ? null
                            : run.getDurationSeconds().doubleValue())))
                    .append("</td><td class=\"flag\">");
            html.append(String.join(", ", htmlFlagDescriptors(run)));
            // Blank for a full run, or a filtered run whose manifest predates this feature (and so
            // has no recorded testsFilter to show) - see TestTrendReport.Run#getFilterDetail.
            html.append("</td><td>").append(run.getFilterDetail() == null ? "" : escapeXml(run.getFilterDetail()))
                    .append("</td></tr>");
        }
        html.append("</table>");
        html.append(HTML_FLAG_LEGEND);
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Same flags as {@link #consoleFlagDescriptors}, but rendered as the table's own short tags
     * (matching the {@code Flags} column header's established wording) instead of the console's
     * longer phrases, and with the {@code filtered} tag kept muted via {@code flag-filtered} - the
     * detail itself lives in the separate {@code Filter} column, not repeated here.
     */
    private static List<String> htmlFlagDescriptors(TestTrendReport.Run run) {
        List<String> flags = new ArrayList<>();
        if (run.isFiltered()) {
            flags.add("<span class=\"flag-filtered\">filtered</span>");
        }
        if (run.isDurationDeviationFlag()) {
            flags.add("duration");
        }
        if (run.isCountDecreasedFlag()) {
            flags.add("count-decrease");
        }
        if (run.isSkippedIncreasedFlag()) {
            flags.add("skipped-increase");
        }
        return flags;
    }

    // Same four flags explained in CONSOLE_FLAG_LEGEND, worded to match this table's own shorter
    // tags (see htmlFlagDescriptors) rather than the console's longer phrases.
    private static final String HTML_FLAG_LEGEND = "<h2>Flag legend</h2><ul>"
            + "<li><strong>filtered</strong> - this run only executed a narrowed-down subset of the "
            + "suite (a --tests filter, or suitename=/component=/case=/method= for testIntegration), "
            + "not the whole suite. See its own Filter column for which one. Excluded from the "
            + "failure rate, streak, duration baseline, and the two drift flags below.</li>"
            + "<li><strong>duration</strong> - this run's duration differed from the average duration "
            + "of full runs by more than the configured threshold "
            + "(test.trend.duration.deviation.percent, default 25%), either slower or faster.</li>"
            + "<li><strong>count-decrease</strong> - this run executed fewer tests in total than the "
            + "previous full run - possibly tests were skipped, removed, or the run didn't "
            + "complete.</li>"
            + "<li><strong>skipped-increase</strong> - this run had more skipped tests than the "
            + "previous full run - possibly new @Disabled/@Ignore tests or an environment issue.</li>"
            + "</ul>";

    /**
     * A pass/fail "status strip": every run sits on ONE baseline, colored by outcome only - not the
     * previous design's two-height scatter (top=fail, bottom=pass) connected by a zig-zag line, which
     * read as a meaningless up-down trend rather than a status. Each dot carries a native
     * {@code <title>} tooltip (date + outcome + duration) inside an enlarged, invisible hit circle so
     * hovering it is easy without any JS. X-axis date ticks and the Passed/Failed legend (added by the
     * caller in {@link #toHtml}) supply the axis/identity context a bare dot strip would otherwise lack.
     */
    private static String buildPassFailChart(List<TestTrendReport.Run> runs) {
        if (runs.isEmpty()) {
            return "<p>No runs to chart yet.</p>";
        }
        int width = Math.max(CHART_MIN_WIDTH, runs.size() * CHART_WIDTH_PER_RUN);
        int height = PASS_FAIL_CHART_HEIGHT;
        double stepX = runs.size() <= 1 ? 0 : (double) (width - 20) / (runs.size() - 1);
        double baselineY = 22;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg width=\"").append(width).append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" xmlns=\"http://www.w3.org/2000/svg\" style=\"background:").append(COLOR_SURFACE)
                .append("\">");
        svg.append("<line x1=\"10\" y1=\"").append(fmt(baselineY)).append("\" x2=\"").append(width - 10)
                .append("\" y2=\"").append(fmt(baselineY)).append("\" stroke=\"").append(COLOR_BASELINE)
                .append("\" stroke-width=\"1\"/>");

        for (int i = 0; i < runs.size(); i++) {
            TestTrendReport.Run run = runs.get(i);
            double x = 10 + i * stepX;
            String color = run.isGreen() ? COLOR_GOOD : COLOR_CRITICAL;
            String tooltip = escapeXml(run.getArchivedAt()) + " - " + escapeXml(run.getOutcome())
                    + (run.getDurationSeconds() == null ? ""
                            : " (" + formatDuration(run.getDurationSeconds().doubleValue()) + ")");
            // A transparent, oversized circle around the visible dot only enlarges the area a hover
            // needs to land in to trigger the shared <title> tooltip - see class javadoc.
            svg.append("<circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(baselineY))
                    .append("\" r=\"12\" fill=\"transparent\"><title>").append(tooltip).append("</title></circle>");
            svg.append("<circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(baselineY))
                    .append("\" r=\"6\" fill=\"").append(color).append("\" stroke=\"").append(COLOR_SURFACE)
                    .append("\" stroke-width=\"2\"><title>").append(tooltip).append("</title></circle>");
        }
        svg.append(buildDateTicks(runs, width, baselineY + 20, 10));
        svg.append("</svg>");
        return svg.toString();
    }

    /**
     * A duration line chart: y-axis min/max labels (actual seconds, not just relative shape), an
     * {@code averageDurationSeconds} reference line (the same baseline the analyzer's deviation flags
     * are measured against - passed in rather than recomputed, so the line and the flags can never
     * disagree), the line itself in the default sequential hue, and an amber ring (plus a larger
     * radius) on deviation-flagged points as a redundant size+color cue. Native {@code <title>}
     * tooltips give the exact value and flag status per point, same rationale as the pass/fail chart.
     */
    private static String buildDurationChart(List<TestTrendReport.Run> runs, Double averageDurationSeconds) {
        if (runs.isEmpty()) {
            return "<p>No runs to chart yet.</p>";
        }
        int width = Math.max(CHART_MIN_WIDTH, runs.size() * CHART_WIDTH_PER_RUN);
        int height = DURATION_CHART_HEIGHT;
        // Unlike the pass/fail chart, this one carries y-axis value labels down the left edge, so the
        // plotted line/points need a real left margin (DURATION_PLOT_LEFT) to start clear of that text
        // instead of the pass/fail chart's bare 10px inset - otherwise an early point can render right
        // on top of its own axis label.
        double stepX = runs.size() <= 1 ? 0 : (double) (width - DURATION_PLOT_LEFT - 10) / (runs.size() - 1);

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        boolean any = false;
        for (TestTrendReport.Run run : runs) {
            if (run.getDurationSeconds() != null) {
                any = true;
                min = Math.min(min, run.getDurationSeconds());
                max = Math.max(max, run.getDurationSeconds());
            }
        }
        if (!any) {
            return "<p>No duration data recorded for any archived run yet.</p>";
        }
        double range = max - min;
        if (range == 0) {
            range = 1;
        }
        double plotTop = 18;
        double plotBottom = height - 28;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg width=\"").append(width).append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" xmlns=\"http://www.w3.org/2000/svg\" style=\"background:").append(COLOR_SURFACE)
                .append("\">");

        // Y-axis: hairline gridlines at the min and max, each labeled with the real duration - lets a
        // reader read absolute seconds instead of only inferring shape from dot height.
        double minY = mapDurationToY(min, min, range, plotTop, plotBottom);
        double maxY = mapDurationToY(max, min, range, plotTop, plotBottom);
        svg.append("<line x1=\"").append(DURATION_PLOT_LEFT).append("\" y1=\"").append(fmt(minY))
                .append("\" x2=\"").append(width - 10).append("\" y2=\"").append(fmt(minY))
                .append("\" stroke=\"").append(COLOR_GRIDLINE).append("\" stroke-width=\"1\"/>");
        svg.append("<text x=\"0\" y=\"").append(fmt(minY + 3)).append("\" font-size=\"9\" fill=\"")
                .append(COLOR_MUTED).append("\">").append(escapeXml(formatDuration(min))).append("</text>");
        svg.append("<text x=\"0\" y=\"").append(fmt(maxY + 3)).append("\" font-size=\"9\" fill=\"")
                .append(COLOR_MUTED).append("\">").append(escapeXml(formatDuration(max))).append("</text>");

        // Average reference line: the same baseline TestTrendAnalyzer's duration-deviation flags are
        // measured against, so a reader can see at a glance how far a flagged dot actually sits from it.
        if (averageDurationSeconds != null && averageDurationSeconds >= min && averageDurationSeconds <= max) {
            double avgY = mapDurationToY(averageDurationSeconds, min, range, plotTop, plotBottom);
            svg.append("<line x1=\"").append(DURATION_PLOT_LEFT).append("\" y1=\"").append(fmt(avgY))
                    .append("\" x2=\"").append(width - 10).append("\" y2=\"").append(fmt(avgY))
                    .append("\" stroke=\"").append(COLOR_MUTED).append("\" stroke-width=\"1\" stroke-dasharray=\"3,3\"/>");
            svg.append("<text x=\"").append(width - 8).append("\" y=\"").append(fmt(avgY - 3))
                    .append("\" font-size=\"9\" fill=\"").append(COLOR_MUTED)
                    .append("\" text-anchor=\"end\">avg ").append(escapeXml(formatDuration(averageDurationSeconds)))
                    .append("</text>");
        }

        StringBuilder points = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < runs.size(); i++) {
            TestTrendReport.Run run = runs.get(i);
            if (run.getDurationSeconds() == null) {
                continue;
            }
            double x = DURATION_PLOT_LEFT + i * stepX;
            double y = mapDurationToY(run.getDurationSeconds(), min, range, plotTop, plotBottom);
            points.append(first ? "" : " ").append(fmt(x)).append(',').append(fmt(y));
            first = false;
            boolean flagged = run.isDurationDeviationFlag();
            String tooltip = escapeXml(run.getArchivedAt()) + " - "
                    + escapeXml(formatDuration(run.getDurationSeconds().doubleValue()))
                    + (flagged ? " (duration deviation)" : "");
            marks.append("<circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(y)).append("\" r=\"12\" "
                    + "fill=\"transparent\"><title>").append(tooltip).append("</title></circle>");
            marks.append("<circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(y)).append("\" r=\"")
                    .append(flagged ? "6" : "4").append("\" fill=\"").append(COLOR_SEQUENTIAL)
                    .append("\" stroke=\"").append(flagged ? COLOR_WARNING : COLOR_SURFACE)
                    .append("\" stroke-width=\"").append(flagged ? "2.5" : "2")
                    .append("\"><title>").append(tooltip).append("</title></circle>");
        }
        svg.append("<polyline points=\"").append(points).append("\" fill=\"none\" stroke=\"")
                .append(COLOR_SEQUENTIAL).append("\" stroke-width=\"2\"/>");
        svg.append(marks);
        svg.append(buildDateTicks(runs, width, height - 6, DURATION_PLOT_LEFT));
        svg.append("</svg>");
        return svg.toString();
    }

    private static double mapDurationToY(double value, double min, double range, double plotTop,
            double plotBottom) {
        return plotBottom - ((value - min) / range) * (plotBottom - plotTop);
    }

    /**
     * Muted x-axis date labels shared by both charts, at up to {@value #MAX_DATE_TICKS} evenly spaced
     * points (always including the last/newest run) so a wide chart with many runs doesn't collide
     * labels into an unreadable smear. {@code plotLeft} must match the caller's own data-point x
     * formula (10 for the pass/fail chart, {@link #DURATION_PLOT_LEFT} for the duration chart) or
     * the ticks drift out from under the points they're meant to label.
     */
    private static String buildDateTicks(List<TestTrendReport.Run> runs, int width, double textY, int plotLeft) {
        double stepX = runs.size() <= 1 ? 0 : (double) (width - plotLeft - 10) / (runs.size() - 1);
        int tickEvery = Math.max(1, (int) Math.ceil(runs.size() / (double) MAX_DATE_TICKS));
        StringBuilder ticks = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            boolean isLast = i == runs.size() - 1;
            if (i % tickEvery != 0 && !isLast) {
                continue;
            }
            double x = plotLeft + i * stepX;
            ticks.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(textY))
                    .append("\" font-size=\"9\" fill=\"").append(COLOR_MUTED).append("\" text-anchor=\"middle\">")
                    .append(escapeXml(formatTickDate(runs.get(i).getArchivedAt()))).append("</text>");
        }
        return ticks.toString();
    }

    private static String formatTickDate(String archivedAtIso) {
        try {
            // Includes time-of-day, not just the date: a suite this frequently archived (several
            // runs an hour during active dev) would otherwise show the same "08-29" on most of its
            // (at most MAX_DATE_TICKS) ticks, defeating the point of labeling them individually.
            return DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)
                    .format(Instant.parse(archivedAtIso));
        } catch (Exception e) {
            // Malformed/missing timestamp (shouldn't happen for a real archived manifest) - fall back
            // to whatever's there rather than let one bad tick take down the whole chart.
            return archivedAtIso == null ? "" : archivedAtIso;
        }
    }

    /**
     * {@code report.getRuns()} in reverse (newest archived-at first), capped to
     * {@value #MAX_DISPLAYED_RUNS} - used only for the human-facing run-by-run lists (console and
     * the HTML table) so the most recent result is the first thing a reader sees and a
     * long-running suite's history doesn't dump hundreds of rows into one page, without touching
     * {@code report.getRuns()}'s own chronological order: the charts
     * ({@link #buildPassFailChart}/{@link #buildDurationChart}) and {@code trends-<suiteName>.json}
     * both still need oldest-to-newest, left-to-right/first-to-last, and both still cover every
     * archived run - only this rendered list/table is trimmed.
     */
    private static List<TestTrendReport.Run> newestFirst(List<TestTrendReport.Run> runsChronological) {
        List<TestTrendReport.Run> reversed = new ArrayList<>(runsChronological);
        Collections.reverse(reversed);
        return reversed.size() > MAX_DISPLAYED_RUNS ? reversed.subList(0, MAX_DISPLAYED_RUNS) : reversed;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatDuration(Double seconds) {
        return seconds == null ? "-" : String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
