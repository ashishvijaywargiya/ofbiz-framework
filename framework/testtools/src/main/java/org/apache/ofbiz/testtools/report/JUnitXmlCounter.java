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
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.util.Debug;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Sums pass/fail/skip counts (and total duration) across every {@code <testsuite>} JUnit XML file
 * under a directory, matching the {@code tests}/{@code failures}/{@code errors}/{@code skipped}/
 * {@code time} attributes {@code org.apache.ofbiz.testtools.SuiteXmlReportWriter} writes for
 * {@code testIntegration} runs, and that Gradle's own JUnit Platform listener writes for the
 * plain {@code test} task.
 */
public final class JUnitXmlCounter {

    private static final String MODULE = JUnitXmlCounter.class.getName();

    /**
     * Name of the directory {@code org.apache.ofbiz.testtools.TestRunServices} accumulates one
     * subdirectory per API-triggered run under (each already archived independently, under its
     * own runId). A gradle-triggered {@code test}/{@code testIntegration} run is handed the
     * shared parent directory (e.g. {@code runtime/logs/test-results}) as its own resultsDir,
     * which sits right next to this one - recursing into it here would keep summing every past
     * API-triggered run's counts into every future gradle run's manifest too. {@link
     * org.apache.ofbiz.testtools.report.TestReportArchiver#copyRecursive} skips it for the same
     * reason when copying resultsDir into the archived run folder.
     */
    static final String API_RUNS_DIR_NAME = "api-runs";

    private JUnitXmlCounter() {
    }

    /** Pass/fail/skip counts plus the summed {@code time} attribute, in whole seconds. */
    public static final class Result {
        private final TestRunManifest.Counts counts;
        private final long durationSeconds;

        public Result(TestRunManifest.Counts counts, long durationSeconds) {
            this.counts = counts;
            this.durationSeconds = durationSeconds;
        }

        public TestRunManifest.Counts getCounts() {
            return counts;
        }

        public long getDurationSeconds() {
            return durationSeconds;
        }
    }

    /** One file's tally: counts plus its unrounded duration sum, before the final rounding. */
    private static final class FileTally {
        private final TestRunManifest.Counts counts;
        private final double durationSeconds;

        FileTally(TestRunManifest.Counts counts, double durationSeconds) {
            this.counts = counts;
            this.durationSeconds = durationSeconds;
        }
    }

    /** Recursively walks {@code resultsDir} for {@code *.xml} files and sums their testsuite counts. */
    public static TestRunManifest.Counts count(File resultsDir) {
        return countWithDuration(resultsDir).getCounts();
    }

    /**
     * Recursively walks {@code resultsDir} for {@code *.xml} files and sums both their testsuite
     * counts and their {@code time} attributes (fractional seconds, per the JUnit XML schema),
     * rounded to the nearest whole second in the returned {@link Result}. Same single walk
     * {@link #count} delegates to - no separate parsing pass for duration.
     */
    public static Result countWithDuration(File resultsDir) {
        int total = 0;
        int failed = 0;
        int skipped = 0;
        double durationSeconds = 0;
        if (resultsDir != null && resultsDir.isDirectory()) {
            for (File xmlFile : listXmlFilesRecursively(resultsDir)) {
                try {
                    FileTally tally = countOneFile(xmlFile);
                    total += tally.counts.getTotal();
                    failed += tally.counts.getFailed();
                    skipped += tally.counts.getSkipped();
                    durationSeconds += tally.durationSeconds;
                } catch (Exception e) {
                    // Malformed/partial XML from an interrupted run - skip it, don't fail archiving.
                    Debug.logWarning(e, "JUnitXmlCounter: skipping unparsable file " + xmlFile, MODULE);
                }
            }
        }
        TestRunManifest.Counts counts = new TestRunManifest.Counts(total, total - failed - skipped, failed, skipped);
        return new Result(counts, Math.round(durationSeconds));
    }

    private static FileTally countOneFile(File xmlFile)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        NodeList suites = doc.getElementsByTagName("testsuite");
        int total = 0;
        int failed = 0;
        int skipped = 0;
        double durationSeconds = 0;
        for (int i = 0; i < suites.getLength(); i++) {
            Element suite = (Element) suites.item(i);
            total += parseIntAttribute(suite, "tests");
            failed += parseIntAttribute(suite, "failures") + parseIntAttribute(suite, "errors");
            skipped += parseIntAttribute(suite, "skipped");
            durationSeconds += parseDoubleAttribute(suite, "time");
        }
        TestRunManifest.Counts counts = new TestRunManifest.Counts(total, total - failed - skipped, failed, skipped);
        return new FileTally(counts, durationSeconds);
    }

    private static int parseIntAttribute(Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDoubleAttribute(Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<File> listXmlFilesRecursively(File dir) {
        List<File> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) {
            return result;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (API_RUNS_DIR_NAME.equals(child.getName())) {
                    continue;
                }
                result.addAll(listXmlFilesRecursively(child));
            } else if (child.getName().endsWith(".xml")) {
                result.add(child);
            }
        }
        return result;
    }
}
