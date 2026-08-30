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

/**
 * Suite-level trend statistics computed by {@link TestTrendAnalyzer} from a chronological list of
 * archived {@link TestRunManifest}s: failure rate, current pass/fail streak, duration baseline +
 * per-run deviation flags, and per-run count-drift flags. Plain data holder, serialized to
 * {@code trends-<suiteName>.json} by {@link TestTrendReportWriter} via the same Jackson-backed {@code JSON}
 * helper {@code manifest.json} uses.
 */
public final class TestTrendReport {

    private String suiteName;
    private int runCount;
    private int filteredRunCount;
    private boolean notEnoughHistory;
    private double failureRate;
    private String streakDirection;
    private int streakLength;
    private Double averageDurationSeconds;
    private List<Run> runs = new ArrayList<>();

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public int getRunCount() {
        return runCount;
    }

    public void setRunCount(int runCount) {
        this.runCount = runCount;
    }

    /**
     * How many of {@link #getRunCount()}'s archived runs are {@link TestRunManifest#isFiltered()
     * filtered} (a narrowed-down subset run, not the whole suite) - included in {@link #getRuns()}
     * and marked there, but excluded from every trend statistic this report computes.
     */
    public int getFilteredRunCount() {
        return filteredRunCount;
    }

    public void setFilteredRunCount(int filteredRunCount) {
        this.filteredRunCount = filteredRunCount;
    }

    public boolean isNotEnoughHistory() {
        return notEnoughHistory;
    }

    public void setNotEnoughHistory(boolean notEnoughHistory) {
        this.notEnoughHistory = notEnoughHistory;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public String getStreakDirection() {
        return streakDirection;
    }

    public void setStreakDirection(String streakDirection) {
        this.streakDirection = streakDirection;
    }

    public int getStreakLength() {
        return streakLength;
    }

    public void setStreakLength(int streakLength) {
        this.streakLength = streakLength;
    }

    public Double getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public void setAverageDurationSeconds(Double averageDurationSeconds) {
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public List<Run> getRuns() {
        return runs;
    }

    public void setRuns(List<Run> runs) {
        this.runs = runs;
    }

    /** One archived run's row in the trend report, oldest-to-newest order in {@link #getRuns()}. */
    public static final class Run {
        private String runId;
        private String archivedAt;
        private String outcome;
        private boolean green;
        private boolean filtered;
        private String filterDetail;
        private TestRunManifest.Counts counts;
        private Long durationSeconds;
        private boolean durationDeviationFlag;
        private boolean countDecreasedFlag;
        private boolean skippedIncreasedFlag;

        public String getRunId() {
            return runId;
        }

        public void setRunId(String runId) {
            this.runId = runId;
        }

        public String getArchivedAt() {
            return archivedAt;
        }

        public void setArchivedAt(String archivedAt) {
            this.archivedAt = archivedAt;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public boolean isGreen() {
            return green;
        }

        public void setGreen(boolean green) {
            this.green = green;
        }

        /** Mirrors {@link TestRunManifest#isFiltered()} for this run - see that method's javadoc. */
        public boolean isFiltered() {
            return filtered;
        }

        public void setFiltered(boolean filtered) {
            this.filtered = filtered;
        }

        /**
         * The raw filter that narrowed this run - a Gradle {@code --tests} class name, or an
         * {@code ofbiz --test suitename=}/{@code case=}/{@code method=} arg - copied verbatim from
         * {@link TestRunManifest#getParamsUsed()}'s {@code testsFilter} entry. Null for a full run,
         * and may be null even when {@link #isFiltered()} is true if the manifest was constructed
         * without one (e.g. hand-built in a test).
         */
        public String getFilterDetail() {
            return filterDetail;
        }

        public void setFilterDetail(String filterDetail) {
            this.filterDetail = filterDetail;
        }

        public TestRunManifest.Counts getCounts() {
            return counts;
        }

        public void setCounts(TestRunManifest.Counts counts) {
            this.counts = counts;
        }

        public Long getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(Long durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public boolean isDurationDeviationFlag() {
            return durationDeviationFlag;
        }

        public void setDurationDeviationFlag(boolean durationDeviationFlag) {
            this.durationDeviationFlag = durationDeviationFlag;
        }

        public boolean isCountDecreasedFlag() {
            return countDecreasedFlag;
        }

        public void setCountDecreasedFlag(boolean countDecreasedFlag) {
            this.countDecreasedFlag = countDecreasedFlag;
        }

        public boolean isSkippedIncreasedFlag() {
            return skippedIncreasedFlag;
        }

        public void setSkippedIncreasedFlag(boolean skippedIncreasedFlag) {
            this.skippedIncreasedFlag = skippedIncreasedFlag;
        }
    }
}
