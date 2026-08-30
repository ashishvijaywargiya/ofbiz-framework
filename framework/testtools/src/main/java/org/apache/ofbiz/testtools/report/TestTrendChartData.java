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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-point pixel geometry and formatted labels for the trend report's two inline-SVG charts
 * (pass/fail status strip, duration line chart) - pure data, no markup. Extracted from what used to
 * be {@code TestTrendReportWriter}'s {@code buildPassFailChart}/{@code buildDurationChart}
 * markup-string builders so the same math can be asserted on directly (actual x/y/labels) instead of
 * scraped out of generated HTML, and so {@code trends-report.ftl}'s chart partials have nothing left
 * to compute themselves. Plain classes with JavaBean getters, not records - FreeMarker's default
 * bean introspection maps {@code getX()} to bare {@code .x} property access in templates the way a
 * record's {@code x()} accessor is not.
 */
public final class TestTrendChartData {

    public static final int CHART_WIDTH_PER_RUN = 40;
    public static final int CHART_MIN_WIDTH = 400;
    public static final int PASS_FAIL_CHART_HEIGHT = 60;
    public static final int DURATION_CHART_HEIGHT = 140;
    public static final int MAX_DATE_TICKS = 8;
    // Left inset for the duration chart's plotted line/points, wide enough to clear its y-axis value
    // labels (which sit at x=0) - the pass/fail chart carries no such labels, so it keeps a bare 10px
    // (see buildPassFailChart).
    public static final int DURATION_PLOT_LEFT = 40;
    private static final double PASS_FAIL_BASELINE_Y = 22;

    private TestTrendChartData() {
    }

    /** One dot on the pass/fail status strip. {@code status} is {@code "pass"} or {@code "fail"}. */
    public static final class PassFailPoint {
        private final double x;
        private final double y;
        private final String status;
        private final String tooltip;

        PassFailPoint(double x, double y, String status, String tooltip) {
            this.x = x;
            this.y = y;
            this.status = status;
            this.tooltip = tooltip;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public String getStatus() {
            return status;
        }

        public String getTooltip() {
            return tooltip;
        }
    }

    /** One dot on the duration line chart. */
    public static final class DurationPoint {
        private final double x;
        private final double y;
        private final boolean flagged;
        private final String tooltip;

        DurationPoint(double x, double y, boolean flagged, String tooltip) {
            this.x = x;
            this.y = y;
            this.flagged = flagged;
            this.tooltip = tooltip;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public boolean isFlagged() {
            return flagged;
        }

        public String getTooltip() {
            return tooltip;
        }
    }

    /** One x-axis date label, shared by both charts. */
    public static final class DateTick {
        private final double x;
        private final String label;

        DateTick(double x, String label) {
            this.x = x;
            this.label = label;
        }

        public double getX() {
            return x;
        }

        public String getLabel() {
            return label;
        }
    }

    /** A y-axis value label (min/max/average reference line), positioned at pixel {@code y}. */
    public static final class AxisLabel {
        private final double y;
        private final String text;

        AxisLabel(double y, String text) {
            this.y = y;
            this.text = text;
        }

        public double getY() {
            return y;
        }

        public String getText() {
            return text;
        }
    }

    /** Prepared data for the pass/fail status strip. A {@code null} return from
     *  {@link #buildPassFailChart} means there are no runs to chart. */
    public static final class PassFailChart {
        private final int width;
        private final int height;
        private final double baselineY;
        private final double ticksY;
        private final List<PassFailPoint> points;
        private final List<DateTick> ticks;
        private final int plotLeft;
        private final int plotRight;

        PassFailChart(int width, int height, double baselineY, double ticksY, List<PassFailPoint> points,
                List<DateTick> ticks, int plotLeft, int plotRight) {
            this.width = width;
            this.height = height;
            this.baselineY = baselineY;
            this.ticksY = ticksY;
            this.points = points;
            this.ticks = ticks;
            this.plotLeft = plotLeft;
            this.plotRight = plotRight;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public double getBaselineY() {
            return baselineY;
        }

        public double getTicksY() {
            return ticksY;
        }

        public List<PassFailPoint> getPoints() {
            return points;
        }

        public List<DateTick> getTicks() {
            return ticks;
        }

        /** Left/right x pixel bounds of the baseline and axis - {@code pass-fail-chart.ftl} draws its
         *  axis line and tick math from these instead of its own hardcoded literals, so they can never
         *  drift from what {@link #buildPassFailChart} actually plotted points/ticks against. */
        public int getPlotLeft() {
            return plotLeft;
        }

        public int getPlotRight() {
            return plotRight;
        }
    }

    /**
     * Prepared data for the duration line chart. {@code hasData} is false when there are runs but
     * none of them recorded a duration - the caller distinguishes that ("No duration data recorded
     * for any archived run yet.") from "no runs at all" (a {@code null} {@link DurationChart}, same
     * convention as {@link PassFailChart}, meaning "No runs to chart yet.").
     */
    public static final class DurationChart {
        private final int width;
        private final int height;
        private final boolean hasData;
        private final List<DurationPoint> points;
        private final AxisLabel minLabel;
        private final AxisLabel maxLabel;
        private final AxisLabel avgLine;
        private final double ticksY;
        private final List<DateTick> ticks;
        private final int plotLeft;
        private final int plotRight;

        DurationChart(int width, int height, boolean hasData, List<DurationPoint> points, AxisLabel minLabel,
                AxisLabel maxLabel, AxisLabel avgLine, double ticksY, List<DateTick> ticks, int plotLeft,
                int plotRight) {
            this.width = width;
            this.height = height;
            this.hasData = hasData;
            this.points = points;
            this.minLabel = minLabel;
            this.maxLabel = maxLabel;
            this.avgLine = avgLine;
            this.ticksY = ticksY;
            this.ticks = ticks;
            this.plotLeft = plotLeft;
            this.plotRight = plotRight;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public boolean isHasData() {
            return hasData;
        }

        public List<DurationPoint> getPoints() {
            return points;
        }

        public AxisLabel getMinLabel() {
            return minLabel;
        }

        public AxisLabel getMaxLabel() {
            return maxLabel;
        }

        public AxisLabel getAvgLine() {
            return avgLine;
        }

        public double getTicksY() {
            return ticksY;
        }

        public List<DateTick> getTicks() {
            return ticks;
        }

        /** Left/right x pixel bounds of the gridlines/baseline and axis - {@code duration-chart.ftl}
         *  draws its lines and tick math from these instead of its own hardcoded literals, so they can
         *  never drift from what {@link #buildDurationChart} actually plotted points/ticks against. */
        public int getPlotLeft() {
            return plotLeft;
        }

        public int getPlotRight() {
            return plotRight;
        }
    }

    public static PassFailChart buildPassFailChart(List<TestTrendReport.Run> runs) {
        if (runs.isEmpty()) {
            return null;
        }
        int width = Math.max(CHART_MIN_WIDTH, runs.size() * CHART_WIDTH_PER_RUN);
        int plotLeft = 10;
        int plotRight = width - 10;
        double stepX = runs.size() <= 1 ? 0 : (double) (plotRight - plotLeft) / (runs.size() - 1);

        List<PassFailPoint> points = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            TestTrendReport.Run run = runs.get(i);
            double x = plotLeft + i * stepX;
            String tooltip = orEmpty(run.getArchivedAt()) + " - " + orEmpty(run.getOutcome())
                    + (run.getDurationSeconds() == null ? ""
                            : " (" + formatDuration(run.getDurationSeconds().doubleValue()) + ")");
            points.add(new PassFailPoint(x, PASS_FAIL_BASELINE_Y, run.isGreen() ? "pass" : "fail", tooltip));
        }
        List<DateTick> ticks = buildDateTicks(runs, width, plotLeft);
        return new PassFailChart(width, PASS_FAIL_CHART_HEIGHT, PASS_FAIL_BASELINE_Y, PASS_FAIL_BASELINE_Y + 20,
                points, ticks, plotLeft, plotRight);
    }

    public static DurationChart buildDurationChart(List<TestTrendReport.Run> runs, Double averageDurationSeconds) {
        if (runs.isEmpty()) {
            return null;
        }
        int width = Math.max(CHART_MIN_WIDTH, runs.size() * CHART_WIDTH_PER_RUN);
        int height = DURATION_CHART_HEIGHT;
        double ticksY = height - 6;
        int plotLeft = DURATION_PLOT_LEFT;
        int plotRight = width - 10;

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
            return new DurationChart(width, height, false, List.of(), null, null, null, ticksY, List.of(),
                    plotLeft, plotRight);
        }
        double range = max - min;
        if (range == 0) {
            range = 1;
        }
        double plotTop = 18;
        double plotBottom = height - 28;
        // Unlike the pass/fail chart, this one carries y-axis value labels down the left edge, so the
        // plotted line/points need a real left margin (DURATION_PLOT_LEFT) to start clear of that text.
        double stepX = runs.size() <= 1 ? 0 : (double) (plotRight - plotLeft) / (runs.size() - 1);

        double minY = mapDurationToY(min, min, range, plotTop, plotBottom);
        double maxY = mapDurationToY(max, min, range, plotTop, plotBottom);
        AxisLabel minLabel = new AxisLabel(minY, formatDuration(min));
        AxisLabel maxLabel = new AxisLabel(maxY, formatDuration(max));

        // Average reference line: the same baseline TestTrendAnalyzer's duration-deviation flags are
        // measured against, so a reader can see at a glance how far a flagged dot actually sits from it.
        AxisLabel avgLine = null;
        if (averageDurationSeconds != null && averageDurationSeconds >= min && averageDurationSeconds <= max) {
            double avgY = mapDurationToY(averageDurationSeconds, min, range, plotTop, plotBottom);
            avgLine = new AxisLabel(avgY, "avg " + formatDuration(averageDurationSeconds));
        }

        List<DurationPoint> points = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            TestTrendReport.Run run = runs.get(i);
            if (run.getDurationSeconds() == null) {
                continue;
            }
            double x = plotLeft + i * stepX;
            double y = mapDurationToY(run.getDurationSeconds(), min, range, plotTop, plotBottom);
            boolean flagged = run.isDurationDeviationFlag();
            String tooltip = orEmpty(run.getArchivedAt()) + " - "
                    + formatDuration(run.getDurationSeconds().doubleValue())
                    + (flagged ? " (duration deviation)" : "");
            points.add(new DurationPoint(x, y, flagged, tooltip));
        }
        List<DateTick> ticks = buildDateTicks(runs, width, plotLeft);
        return new DurationChart(width, height, true, points, minLabel, maxLabel, avgLine, ticksY, ticks,
                plotLeft, plotRight);
    }

    private static double mapDurationToY(double value, double min, double range, double plotTop, double plotBottom) {
        return plotBottom - ((value - min) / range) * (plotBottom - plotTop);
    }

    /**
     * Muted x-axis date labels shared by both charts, at up to {@value #MAX_DATE_TICKS} evenly spaced
     * points (always including the last/newest run) so a wide chart with many runs doesn't collide
     * labels into an unreadable smear. {@code plotLeft} must match the caller's own data-point x
     * formula (10 for the pass/fail chart, {@link #DURATION_PLOT_LEFT} for the duration chart) or the
     * ticks drift out from under the points they're meant to label.
     */
    static List<DateTick> buildDateTicks(List<TestTrendReport.Run> runs, int width, int plotLeft) {
        double stepX = runs.size() <= 1 ? 0 : (double) (width - plotLeft - 10) / (runs.size() - 1);
        int tickEvery = Math.max(1, (int) Math.ceil(runs.size() / (double) MAX_DATE_TICKS));
        List<DateTick> ticks = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            boolean isLast = i == runs.size() - 1;
            if (i % tickEvery != 0 && !isLast) {
                continue;
            }
            double x = plotLeft + i * stepX;
            ticks.add(new DateTick(x, formatTickDate(runs.get(i).getArchivedAt())));
        }
        return ticks;
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

    public static String formatDuration(Double seconds) {
        return seconds == null ? "-" : String.format(Locale.ROOT, "%.1fs", seconds);
    }

    /** {@code value}, or {@code ""} for a {@code null} - both tooltip builders above concatenate
     *  {@code archivedAt}/{@code outcome} straight from a deserialized manifest.json, which carries no
     *  defaulting, so a plain {@code +} would otherwise render the literal text "null" into the page. */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
