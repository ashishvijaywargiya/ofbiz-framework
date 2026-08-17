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
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Asserts AllureSuiteReportSink's written JSON via targeted field extraction rather than a real JSON
 * parse - this module has no JSON library on its test classpath, and hand-writing the JSON (see
 * AllureSuiteReportSink's own javadoc for why) means the schema is small and stable enough that
 * regex-per-field is sufficient, the same spirit as SuiteXmlReportWriterTest's structural (not
 * string-equality) assertions.
 */
class AllureSuiteReportSinkTest {

    @TempDir
    File resultsDir;

    @Test
    void writesAPassedResultWithNoStatusDetails() throws IOException {
        AllureSuiteReportSink sink = new AllureSuiteReportSink(resultsDir);
        sink.startSuite("mysuite");
        sink.testStarted("com.example.Foo", "testA");
        sink.testFinished("com.example.Foo", "testA", 18, SuiteReportSink.Outcome.passed());
        sink.endSuite();

        String json = onlyResultFile();

        assertThat(field(json, "name"), is("testA"));
        assertThat(field(json, "fullName"), is("com.example.Foo.testA"));
        assertThat(field(json, "status"), is("passed"));
        assertThat(field(json, "stage"), is("finished"));
        assertThat(json, not(containsString("\"statusDetails\"")));
        assertThat(json, containsString("{\"name\": \"suite\", \"value\": \"mysuite\"}"));
        assertThat(json, containsString("{\"name\": \"testClass\", \"value\": \"Foo\"}"));
        assertThat(json, containsString("{\"name\": \"package\", \"value\": \"com.example\"}"));
    }

    @Test
    void stopTimeIsStartTimePlusElapsedMillis() throws IOException {
        AllureSuiteReportSink sink = new AllureSuiteReportSink(resultsDir);
        sink.startSuite("mysuite");
        sink.testStarted("com.example.Foo", "testA");
        sink.testFinished("com.example.Foo", "testA", 250, SuiteReportSink.Outcome.passed());

        String json = onlyResultFile();

        long start = numericField(json, "start");
        long stop = numericField(json, "stop");
        assertThat(stop - start, is(250L));
    }

    @Test
    void writesAFailedResultWithMessageAndTrace() throws IOException {
        AllureSuiteReportSink sink = new AllureSuiteReportSink(resultsDir);
        sink.startSuite("mysuite");
        sink.testStarted("com.example.Foo", "testB");
        sink.testFinished("com.example.Foo", "testB", 5,
                SuiteReportSink.Outcome.failure("expected true", "java.lang.AssertionError", "stack trace text"));

        String json = onlyResultFile();

        assertThat(field(json, "status"), is("failed"));
        assertThat(json, containsString("\"message\": \"expected true\""));
        assertThat(json, containsString("\"trace\": \"stack trace text\""));
    }

    @Test
    void writesABrokenResultForAnUnexpectedException() throws IOException {
        AllureSuiteReportSink sink = new AllureSuiteReportSink(resultsDir);
        sink.startSuite("mysuite");
        sink.testStarted("com.example.Bar", "testC");
        sink.testFinished("com.example.Bar", "testC", 3, SuiteReportSink.Outcome.error(new RuntimeException("boom")));

        String json = onlyResultFile();

        assertThat(field(json, "status"), is("broken"));
        assertThat(json, containsString("\"message\": \"boom\""));
        assertThat(json, containsString("java.lang.RuntimeException: boom"));
    }

    @Test
    void escapesJsonSpecialCharactersInMessagesSoTheOutputStaysValid() throws IOException {
        AllureSuiteReportSink sink = new AllureSuiteReportSink(resultsDir);
        sink.startSuite("mysuite");
        sink.testStarted("com.example.Foo", "testA");
        sink.testFinished("com.example.Foo", "testA", 1,
                SuiteReportSink.Outcome.failure("bad \"quoted\"\nmessage", "java.lang.AssertionError", "trace"));

        String json = onlyResultFile();

        assertThat(json, containsString("bad \\\"quoted\\\"\\nmessage"));
    }

    @Test
    void writesOneFileForEachOfTwoTestCasesInTheSameSuite() {
        AllureSuiteReportSink sink = new AllureSuiteReportSink(resultsDir);
        sink.startSuite("mysuite");
        sink.testStarted("com.example.Foo", "testA");
        sink.testFinished("com.example.Foo", "testA", 1, SuiteReportSink.Outcome.passed());
        sink.testStarted("com.example.Foo", "testB");
        sink.testFinished("com.example.Foo", "testB", 1, SuiteReportSink.Outcome.passed());

        File[] files = resultsDir.listFiles();

        assertThat(files.length, is(2));
        assertThat(files[0].getName(), matchesPattern("[0-9a-f-]{36}-result\\.json"));
        assertThat(files[1].getName(), matchesPattern("[0-9a-f-]{36}-result\\.json"));
        assertThat(files[0].getName(), not(is(files[1].getName())));
    }

    @Test
    void constructorCreatesTheResultsDirectoryIfMissing() {
        File nested = new File(resultsDir, "nested/allure-results");

        new AllureSuiteReportSink(nested);

        assertThat(nested.isDirectory(), is(true));
    }

    private String onlyResultFile() throws IOException {
        File[] files = resultsDir.listFiles();
        assertThat(files, notNullValue());
        assertThat(files.length, is(1));
        return Files.readString(files[0].toPath());
    }

    private static String field(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\": \"([^\"]*)\"").matcher(json);
        assertThat(matcher.find(), is(true));
        return matcher.group(1);
    }

    private static long numericField(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\": (\\d+)").matcher(json);
        assertThat(matcher.find(), is(true));
        return Long.parseLong(matcher.group(1));
    }
}
