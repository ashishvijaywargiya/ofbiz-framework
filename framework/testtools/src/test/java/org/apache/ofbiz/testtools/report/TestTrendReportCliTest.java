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
import java.time.Instant;

import org.apache.ofbiz.base.lang.JSON;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestTrendReportCliTest {

    private static final String[] PROPERTY_NAMES = {
            "test.trend.unit.dir", "test.trend.integration.dir", "test.trend.duration.deviation.percent"
    };

    @AfterEach
    void clearSystemProperties() {
        for (String name : PROPERTY_NAMES) {
            System.clearProperty(name);
        }
    }

    @Test
    void writesTrendsFilesWhenHistoryExists(@TempDir File tmp) throws IOException {
        File unitDir = new File(tmp, "unit-history");
        writeManifest(new File(unitDir, "2026-08-20/10h00m00s_unit"), "unit");
        writeManifest(new File(unitDir, "2026-08-21/10h00m00s_unit"), "unit");
        File integrationDir = new File(tmp, "no-such-dir");

        System.setProperty("test.trend.unit.dir", unitDir.getAbsolutePath());
        System.setProperty("test.trend.integration.dir", integrationDir.getAbsolutePath());

        TestTrendReportCli.main(new String[0]);

        assertThat(new File(unitDir, "trends.json").exists(), is(true));
        assertThat(new File(unitDir, "trends.html").exists(), is(true));
    }

    @Test
    void doesNotThrowWhenNoBaseDirsAreConfigured() {
        TestTrendReportCli.main(new String[0]); // must not throw
    }

    private static void writeManifest(File runDir, String suiteName) throws IOException {
        Files.createDirectories(runDir.toPath());
        TestRunManifest manifest = new TestRunManifest();
        manifest.setRunId(runDir.getName());
        manifest.setSuiteName(suiteName);
        manifest.setOutcome("PASSED");
        manifest.setArchivedAt(Instant.now().toString());
        manifest.setCounts(new TestRunManifest.Counts(10, 10, 0, 0));
        manifest.setDurationSeconds(30L);
        Files.writeString(new File(runDir, "manifest.json").toPath(), JSON.from(manifest).toString());
    }
}
