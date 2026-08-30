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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TestReportCssTest {

    @Test
    void copyToWritesTheStylesheetIntoANewTargetDir(@TempDir File tmp) throws IOException {
        File targetDir = new File(tmp, "nested/output-dir");

        TestReportCss.copyTo(targetDir);

        File cssFile = new File(targetDir, "test-report.css");
        assertThat(cssFile.exists(), is(true));
        assertThat(Files.readString(cssFile.toPath()), containsString("--bs-success"));
    }

    @Test
    void copyToOverwritesAnExistingCopy(@TempDir File tmp) throws IOException {
        File cssFile = new File(tmp, "test-report.css");
        Files.writeString(cssFile.toPath(), "stale content");

        TestReportCss.copyTo(tmp);

        assertThat(Files.readString(cssFile.toPath()), containsString("--bs-success"));
        assertThat(Files.readString(cssFile.toPath()), is(not(containsString("stale content"))));
    }

    @Test
    void fileNameConstantMatchesWhatCopyToWrites(@TempDir File tmp) throws IOException {
        TestReportCss.copyTo(tmp);

        assertThat(new File(tmp, TestReportCss.FILE_NAME).exists(), is(true));
    }
}
