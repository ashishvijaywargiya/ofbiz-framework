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

/**
 * Entry point invoked by the Gradle {@code testReportTrends} task (see
 * {@code test-report-trends.gradle}). Standalone and on-demand only - not wired into
 * {@code test}/{@code testIntegration} via {@code finalizedBy}, and not gated by
 * {@code test.history}; it just reads whatever history is already archived under the same
 * directories {@code TestReportArchiverCli}/{@code test-report-archive.gradle} write to. Loops
 * over both suites ("unit" and "testIntegration"), analyzing and writing a trend report for each.
 * Never throws past {@code main} and always exits 0, consistent with the archiver's own
 * defensive stance: this is a passive report, not a build gate.
 *
 * <p>System properties consumed:
 * <ul>
 *   <li>{@code test.trend.unit.dir} - base dir archiveUnitTestReport writes to, required</li>
 *   <li>{@code test.trend.integration.dir} - base dir archiveIntegrationTestReport writes to,
 *       required</li>
 *   <li>{@code test.trend.duration.deviation.percent} - percent deviation threshold, default
 *       25</li>
 * </ul>
 */
public final class TestTrendReportCli {

    private TestTrendReportCli() {
    }

    public static void main(String[] args) {
        int deviationPercent = parseDeviationPercent(System.getProperty("test.trend.duration.deviation.percent"));
        reportOneSuite("unit", System.getProperty("test.trend.unit.dir"), deviationPercent);
        reportOneSuite("testIntegration", System.getProperty("test.trend.integration.dir"), deviationPercent);
    }

    private static void reportOneSuite(String suiteName, String baseDirPath, int deviationPercent) {
        try {
            if (baseDirPath == null || baseDirPath.isBlank()) {
                System.out.println("TestTrendReportCli: no base dir configured for '" + suiteName + "', skipping");
                return;
            }
            File baseDir = new File(baseDirPath);
            if (!baseDir.isDirectory() || isEmpty(baseDir)) {
                System.out.println("TestTrendReportCli: no archived runs found for '" + suiteName
                        + "' - enable test.history first");
                return;
            }
            TestTrendReport report = TestTrendAnalyzer.analyze(baseDir, suiteName, deviationPercent);
            TestTrendReportWriter.write(report, baseDir);
            System.out.println(TestTrendReportWriter.toConsoleSummary(report));
        } catch (Exception e) {
            System.err.println("TestTrendReportCli: failed to compute trend report for '" + suiteName + "': "
                    + e.getMessage());
        }
    }

    private static boolean isEmpty(File dir) {
        File[] children = dir.listFiles();
        return children == null || children.length == 0;
    }

    private static int parseDeviationPercent(String value) {
        if (value == null || value.isBlank()) {
            return 25;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 25;
        }
    }
}
