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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Copies the canonical {@code test-report.css} stylesheet (checked into source at
 * {@code framework/testtools/src/main/resources/templates/testreports/test-report.css}) next to a
 * generated report. Every HTML report this project generates - the trends report
 * ({@link TestTrendReportWriter}, which calls this class directly), and the single-page/framed
 * {@code test}/{@code testIntegration} reports ({@code test-reports.gradle}'s own
 * {@code copyTestReportCss}, a plain file copy of this same resource rather than a call into this
 * class) - links this same file with a
 * same-directory relative {@code <link href="test-report.css">} rather than inlining CSS text, so
 * one edit to the stylesheet reaches every report. A copy, not a shared link target, because the
 * report directories involved ({@code build/test-reports-history/},
 * {@code runtime/logs/test-reports-history/}, {@code runtime/logs/test-results/},
 * {@code runtime/logs/test-results/html/}, and every archived run's own {@code html-report/} copy)
 * don't sit under one common tree a relative {@code ../} link could reach.
 */
public final class TestReportCss {

    public static final String FILE_NAME = "test-report.css";
    private static final String RESOURCE_PATH = "/templates/testreports/" + FILE_NAME;

    private TestReportCss() {
    }

    /** Copies the stylesheet into {@code targetDir}, creating it first if necessary. */
    public static void copyTo(File targetDir) throws IOException {
        Files.createDirectories(targetDir.toPath());
        try (InputStream in = TestReportCss.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IOException("Classpath resource not found: " + RESOURCE_PATH);
            }
            Files.copy(in, new File(targetDir, FILE_NAME).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
