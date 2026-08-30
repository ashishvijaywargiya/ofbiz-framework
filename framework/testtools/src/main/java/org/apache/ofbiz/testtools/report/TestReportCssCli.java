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
 * Command-line entry point for copying {@link TestReportCss}'s canonical stylesheet next to a
 * generated report - usable via a {@code javaexec} subprocess on
 * {@code sourceSets.main.runtimeClasspath}, the same mechanism {@code TestReportArchiverCli}/
 * {@code TestTrendReportCli} use to call this project's Java classes from a Gradle script, since a
 * plain script-level {@code import} can't see classes this same build compiles. Not currently called
 * that way: {@code test-reports.gradle}'s own {@code copyTestReportCss} does a plain Groovy file copy
 * instead (a standalone-runnable task forking a JVM against possibly-uncompiled classes would silently
 * degrade rather than fail loudly - see that helper's own comment). Kept as a directly testable,
 * reusable entry point for the same copy, and never fails its caller - same swallow-and-continue
 * stance as those two CLIs.
 *
 * <p>Usage: {@code TestReportCssCli <targetDir>}.
 */
public final class TestReportCssCli {

    private TestReportCssCli() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException("usage: TestReportCssCli <targetDir>");
            }
            TestReportCss.copyTo(new File(args[0]));
        } catch (Exception e) {
            System.err.println("TestReportCssCli: failed to copy " + TestReportCss.FILE_NAME + ": " + e.getMessage());
        }
    }
}
