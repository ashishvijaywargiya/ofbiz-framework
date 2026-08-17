/*******************************************************************************
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
 *******************************************************************************/
package org.apache.ofbiz.testtools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.ofbiz.base.util.Debug;

/**
 * Writes one Allure result JSON file per test-case - Allure's own {@code <uuid>-result.json} schema
 * (https://allurereport.org/docs/how-it-works-test-result-file/), hand-rolled rather than depending on
 * allure-java-commons the way {@link SuiteXmlReportWriter} already hand-rolls JUnit-style XML instead
 * of depending on a library: this class lives in {@code src/main} (TestRunContainer needs it to run
 * inside a deployed instance's {@code ofbiz --test}/webtools "Run Test"), so a new dependency here
 * would ship in the release distribution. See the 2026-08-17 Allure design doc
 * (plugins/supporting-docs/specs/2026-08-17-allure-test-reporting-design.md) for the full rationale.
 *
 * <p>Only fields this codebase actually uses are written: {@code uuid}, {@code name}, {@code fullName},
 * {@code status}, {@code statusDetails} (omitted entirely for a passed test), {@code stage} (always
 * {@code "finished"}), {@code start}/{@code stop}, and {@code labels} ({@code suite}, {@code testClass},
 * {@code package}). No {@code historyId}, no {@code steps} - deliberately out of scope, see the design
 * doc's Out of scope section.
 *
 * <p>{@link TestRunContainer} only constructs this sink when the {@code allure.results.directory}
 * system property is set - see that class for the opt-in wiring.
 */
class AllureSuiteReportSink implements SuiteReportSink {

    private static final String MODULE = AllureSuiteReportSink.class.getName();

    private final File resultsDir;
    private String suiteName;
    private long currentTestStartMillis;

    AllureSuiteReportSink(File resultsDir) {
        this.resultsDir = resultsDir;
        resultsDir.mkdirs();
    }

    @Override
    public void startSuite(String suiteName) {
        this.suiteName = suiteName;
    }

    @Override
    public void testStarted(String classname, String name) {
        currentTestStartMillis = System.currentTimeMillis();
    }

    @Override
    public void testFinished(String classname, String name, long elapsedMillis, Outcome outcome) {
        String uuid = UUID.randomUUID().toString();
        long stop = currentTestStartMillis + elapsedMillis;
        writeResultFile(uuid, toResultJson(uuid, classname, name, currentTestStartMillis, stop, outcome));
    }

    @Override
    public void endSuite() {
        // Nothing to do: unlike SuiteXmlReportWriter's single per-suite file, Allure results are one
        // file per test-case, already written by testFinished() as each test completes.
    }

    private String toResultJson(String uuid, String classname, String name, long start, long stop, Outcome outcome) {
        int lastDot = classname.lastIndexOf('.');
        String simpleClassName = lastDot >= 0 ? classname.substring(lastDot + 1) : classname;
        String packageName = lastDot >= 0 ? classname.substring(0, lastDot) : "";

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"uuid\": \"").append(uuid).append("\",\n");
        json.append("  \"name\": \"").append(escapeJson(name)).append("\",\n");
        json.append("  \"fullName\": \"").append(escapeJson(classname + "." + name)).append("\",\n");
        json.append("  \"status\": \"").append(statusOf(outcome)).append("\",\n");
        appendStatusDetails(json, outcome);
        json.append("  \"stage\": \"finished\",\n");
        json.append("  \"start\": ").append(start).append(",\n");
        json.append("  \"stop\": ").append(stop).append(",\n");
        json.append("  \"labels\": [\n");
        json.append("    {\"name\": \"suite\", \"value\": \"").append(escapeJson(suiteName)).append("\"},\n");
        json.append("    {\"name\": \"testClass\", \"value\": \"").append(escapeJson(simpleClassName)).append("\"},\n");
        json.append("    {\"name\": \"package\", \"value\": \"").append(escapeJson(packageName)).append("\"}\n");
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String statusOf(Outcome outcome) {
        if (outcome instanceof Outcome.Failure) {
            return "failed";
        } else if (outcome instanceof Outcome.Error) {
            return "broken";
        }
        return "passed";
    }

    private static void appendStatusDetails(StringBuilder json, Outcome outcome) {
        String message = null;
        String trace = null;
        if (outcome instanceof Outcome.Failure failure) {
            message = failure.message();
            trace = failure.stackTrace();
        } else if (outcome instanceof Outcome.Error error) {
            message = error.throwable().getMessage();
            trace = ReportingSupport.stackTraceOf(error.throwable());
        }
        if (trace == null) {
            return;
        }
        json.append("  \"statusDetails\": {\n");
        if (message != null) {
            json.append("    \"message\": \"").append(escapeJson(message)).append("\",\n");
        }
        json.append("    \"trace\": \"").append(escapeJson(trace)).append("\"\n");
        json.append("  },\n");
    }

    private void writeResultFile(String uuid, String json) {
        File resultFile = new File(resultsDir, uuid + "-result.json");
        try (FileOutputStream out = new FileOutputStream(resultFile)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Debug.logError(e, "Unable to write Allure result file '" + resultFile + "' for suite '" + suiteName + "'", MODULE);
        }
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
