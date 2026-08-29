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
import java.util.Locale;

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
    private static final int PASS_FAIL_CHART_HEIGHT = 80;
    private static final int DURATION_CHART_HEIGHT = 140;

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
                .append(report.getRunCount()).append(" run(s) archived)\n");
        if (report.isNotEnoughHistory()) {
            sb.append("  Not enough history yet - need at least 2 archived runs.\n");
            return sb.toString();
        }
        sb.append(String.format(Locale.ROOT, "  Failure rate: %.1f%%%n", report.getFailureRate() * 100.0));
        sb.append("  Streak: ").append(report.getStreakDirection()).append(" x")
                .append(report.getStreakLength()).append('\n');
        sb.append("  Average duration: ").append(formatDuration(report.getAverageDurationSeconds())).append('\n');
        sb.append("  Runs (oldest to newest):\n");
        for (TestTrendReport.Run run : report.getRuns()) {
            sb.append("    ").append(run.getArchivedAt()).append("  ")
                    .append(run.getOutcome()).append("  ")
                    .append(formatDuration(run.getDurationSeconds() == null ? null
                            : run.getDurationSeconds().doubleValue()));
            if (run.isDurationDeviationFlag()) {
                sb.append("  [duration deviation]");
            }
            if (run.isCountDecreasedFlag()) {
                sb.append("  [test count decreased]");
            }
            if (run.isSkippedIncreasedFlag()) {
                sb.append("  [skipped increased]");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

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
                .append("</style></head><body>");
        html.append("<h1>Trend report: ").append(escapeXml(report.getSuiteName())).append("</h1>");
        html.append("<div class=\"summary\">").append(report.getRunCount()).append(" run(s) archived</div>");

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

        html.append("<h2>Pass/fail over time</h2>").append(buildPassFailChart(report.getRuns()));
        html.append("<h2>Duration over time</h2>").append(buildDurationChart(report.getRuns()));

        html.append("<h2>Runs</h2><table><tr><th>Archived at</th><th>Outcome</th><th>Total</th>"
                + "<th>Failed</th><th>Skipped</th><th>Duration</th><th>Flags</th></tr>");
        for (TestTrendReport.Run run : report.getRuns()) {
            html.append("<tr><td>").append(escapeXml(run.getArchivedAt())).append("</td><td>")
                    .append(escapeXml(run.getOutcome())).append("</td><td>")
                    .append(run.getCounts() == null ? "-" : run.getCounts().getTotal()).append("</td><td>")
                    .append(run.getCounts() == null ? "-" : run.getCounts().getFailed()).append("</td><td>")
                    .append(run.getCounts() == null ? "-" : run.getCounts().getSkipped()).append("</td><td>")
                    .append(escapeXml(formatDuration(run.getDurationSeconds() == null ? null
                            : run.getDurationSeconds().doubleValue())))
                    .append("</td><td class=\"flag\">");
            if (run.isDurationDeviationFlag()) {
                html.append("duration ");
            }
            if (run.isCountDecreasedFlag()) {
                html.append("count-decrease ");
            }
            if (run.isSkippedIncreasedFlag()) {
                html.append("skipped-increase ");
            }
            html.append("</td></tr>");
        }
        html.append("</table></body></html>");
        return html.toString();
    }

    private static String buildPassFailChart(List<TestTrendReport.Run> runs) {
        int width = Math.max(CHART_MIN_WIDTH, runs.size() * CHART_WIDTH_PER_RUN);
        int height = PASS_FAIL_CHART_HEIGHT;
        double stepX = runs.size() <= 1 ? 0 : (double) (width - 20) / (runs.size() - 1);
        StringBuilder points = new StringBuilder();
        StringBuilder circles = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            TestTrendReport.Run run = runs.get(i);
            double x = 10 + i * stepX;
            double y = run.isGreen() ? height - 15 : 15;
            points.append(i == 0 ? "" : " ").append(fmt(x)).append(',').append(fmt(y));
            circles.append("<circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(y))
                    .append("\" r=\"4\" fill=\"").append(run.isGreen() ? "#16a34a" : "#dc2626").append("\"/>");
        }
        return "<svg width=\"" + width + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height
                + "\" xmlns=\"http://www.w3.org/2000/svg\">"
                + "<polyline points=\"" + points + "\" fill=\"none\" stroke=\"#94a3b8\" stroke-width=\"1\"/>"
                + circles + "</svg>";
    }

    private static String buildDurationChart(List<TestTrendReport.Run> runs) {
        int width = Math.max(CHART_MIN_WIDTH, runs.size() * CHART_WIDTH_PER_RUN);
        int height = DURATION_CHART_HEIGHT;
        double stepX = runs.size() <= 1 ? 0 : (double) (width - 20) / (runs.size() - 1);

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

        StringBuilder points = new StringBuilder();
        StringBuilder circles = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < runs.size(); i++) {
            TestTrendReport.Run run = runs.get(i);
            if (run.getDurationSeconds() == null) {
                continue;
            }
            double x = 10 + i * stepX;
            double y = height - 15 - ((run.getDurationSeconds() - min) / range) * (height - 30);
            points.append(first ? "" : " ").append(fmt(x)).append(',').append(fmt(y));
            first = false;
            circles.append("<circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(y))
                    .append("\" r=\"").append(run.isDurationDeviationFlag() ? "6" : "3")
                    .append("\" fill=\"").append(run.isDurationDeviationFlag() ? "#ea580c" : "#2563eb")
                    .append("\"/>");
        }
        return "<svg width=\"" + width + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height
                + "\" xmlns=\"http://www.w3.org/2000/svg\">"
                + "<polyline points=\"" + points + "\" fill=\"none\" stroke=\"#94a3b8\" stroke-width=\"1\"/>"
                + circles + "</svg>";
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
