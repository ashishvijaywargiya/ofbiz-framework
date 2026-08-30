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
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import org.apache.ofbiz.base.lang.JSON;

/**
 * Renders a {@link TestTrendReport} three ways: a console-friendly summary (printed by
 * {@link TestTrendReportCli}), {@code trends-<suiteName>.json} (the report serialized via the same
 * Jackson-backed {@code JSON} helper {@code manifest.json} uses), and {@code trends-<suiteName>.html} -
 * a self-contained page (Bootstrap-style shared CSS, see {@link TestReportCss}) with two hand-rolled
 * inline-SVG line charts (pass/fail over time, duration over time) plus the underlying per-run
 * table, rendered from FreeMarker templates under {@code templates/testtrends/} using data prepared
 * by {@link TestTrendReportModel} and {@link TestTrendChartData}. No external chart library. The
 * suite name is part of the filename so that two suites configured to share the same output
 * directory don't overwrite each other's trend report.
 */
public final class TestTrendReportWriter {

    private static final Configuration FREEMARKER_CONFIG = buildFreemarkerConfig();

    private TestTrendReportWriter() {
    }

    private static Configuration buildFreemarkerConfig() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
        // Standalone and independent of OFBiz's screen-widget/webapp FreeMarker setup - these CLIs
        // already run outside any OFBiz container. Absolute-from-classpath-root ("/...") path so it
        // resolves regardless of this class's own package.
        cfg.setTemplateLoader(new ClassTemplateLoader(TestTrendReportWriter.class, "/templates/testtrends"));
        cfg.setDefaultEncoding("UTF-8");
        // ROOT locale so ?string('0.0') always renders a plain period-decimal number (no
        // locale-dependent grouping separator) for chart point coordinates.
        cfg.setLocale(Locale.ROOT);
        return cfg;
    }

    /**
     * Writes {@code trends-<suiteName>.json}, {@code trends-<suiteName>.html}, and a copy of the
     * shared report stylesheet ({@link TestReportCss}) into {@code outputDir}.
     */
    public static void write(TestTrendReport report, File outputDir) throws IOException {
        Files.createDirectories(outputDir.toPath());
        String baseName = "trends-" + report.getSuiteName();
        Files.writeString(new File(outputDir, baseName + ".json").toPath(), toJson(report));
        Files.writeString(new File(outputDir, baseName + ".html").toPath(), toHtml(report));
        TestReportCss.copyTo(outputDir);
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
        sb.append("  Average duration: ")
                .append(TestTrendChartData.formatDuration(report.getAverageDurationSeconds())).append('\n');
        List<TestTrendReport.Run> displayedRuns = TestTrendReportModel.newestFirst(report.getRuns());
        sb.append("  Runs (newest to oldest");
        if (displayedRuns.size() < report.getRuns().size()) {
            sb.append(", showing latest ").append(displayedRuns.size())
                    .append(" of ").append(report.getRuns().size());
        }
        sb.append("):\n");
        for (TestTrendReport.Run run : displayedRuns) {
            sb.append("    ").append(run.getArchivedAt()).append("  ")
                    .append(run.getOutcome()).append("  ")
                    .append(TestTrendChartData.formatDuration(run.getDurationSeconds() == null ? null
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

    // Same four flags explained in trends-report.ftl's "Flag legend" section, worded to match this
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
        Map<String, Object> model = TestTrendReportModel.build(report);
        try {
            Template template = FREEMARKER_CONFIG.getTemplate("trends-report.ftl");
            StringWriter out = new StringWriter();
            template.process(model, out);
            return out.toString();
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException(
                    "Failed to render trends-" + report.getSuiteName() + ".html", e);
        }
    }
}
