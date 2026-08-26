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
package org.apache.ofbiz.base.util;

import static org.apache.ofbiz.base.util.UtilHttp.getPathInfoOnlyParameterMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.HttpMethod;

import org.apache.ofbiz.entity.Delegator;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public final class UtilHttpTest {
    private HttpServletRequest req;

    @BeforeEach
    public void setup() {
        req = Mockito.mock(HttpServletRequest.class);
    }

    @Test
    public void basicGetPathInfoOnlyParameterMap() {
        assertThat(getPathInfoOnlyParameterMap("/~foo=1/~bar=2", x -> true),
                allOf(hasEntry("foo", "1"), hasEntry("bar", "2")));

        assertThat(getPathInfoOnlyParameterMap("/~foo=1/~foo=2", x -> true),
                hasEntry("foo", Arrays.asList("1", "2")));

        assertThat(getPathInfoOnlyParameterMap("/~foo=1/~foo=2/~foo=3/", x -> true),
                hasEntry("foo", Arrays.asList("1", "2", "3")));

        assertThat(getPathInfoOnlyParameterMap("/~foo=1/~bar=2/~foo=3/", x -> true),
                Matchers.<Map<String, Object>>allOf(
                        hasEntry("foo", Arrays.asList("1", "3")),
                        hasEntry("bar", "2")));
    }

    @Test
    public void emptyGetPathInfoOnlyParameterMap() {
        assertThat(getPathInfoOnlyParameterMap(null, x -> true), is(anEmptyMap()));
    }

    @Test
    public void filteredGetPathInfoOnlyParameterMap() {
        assertThat(getPathInfoOnlyParameterMap("/~foo=1/~bar=2", name -> !"foo".equals(name)),
                allOf(not(hasEntry("foo", "1")), hasEntry("bar", "2")));

        assertThat(getPathInfoOnlyParameterMap("/~foo=1/~bar=2", "foo"::equals),
                allOf(hasEntry("foo", "1"), not(hasEntry("bar", "2"))));
    }

    @Test
    public void basicGetParameterMap() {
        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "foo", new String[] {"1"},
                "bar", new String[] {"2", "3"}));
        when(req.getPathInfo()).thenReturn("/foo");
        assertThat(UtilHttp.getParameterMap(req), Matchers.<Map<String, Object>>allOf(
                hasEntry("foo", "1"),
                hasEntry("bar", Arrays.asList("2", "3"))));
    }

    @Test
    public void pathInfoOverrideGetParameterMap() {
        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "foo", new String[] {"1"},
                "bar", new String[] {"2"}));
        when(req.getPathInfo()).thenReturn("/foo/~bar=3");
        assertThat(UtilHttp.getParameterMap(req), Matchers.<Map<String, Object>>allOf(
                hasEntry("foo", "1"),
                hasEntry("bar", "3")));
    }

    @Test
    public void emptyParameterMap() {
        when(req.getParameterMap()).thenReturn(Collections.emptyMap());
        when(req.getPathInfo()).thenReturn("/foo/bar");
        when(req.getMethod()).thenReturn(HttpMethod.POST);
        UtilHttp.getParameterMap(req);
        // Check that multi-part arguments are looked up
        Mockito.verify(req).getContentType();
    }

    @Test
    public void filteredGetParameterMap() {
        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "foo", new String[] {"1"},
                "bar", new String[] {"2", "3"}));
        when(req.getPathInfo()).thenReturn("/foo");
        Predicate<String> equalsBar = "bar"::equals;
        assertThat(UtilHttp.getParameterMap(req, equalsBar.negate()), Matchers.<Map<String, Object>>allOf(
                hasEntry("foo", "1"),
                not(hasEntry("bar", Arrays.asList("2", "3")))));
        assertThat(UtilHttp.getParameterMap(req, equalsBar), Matchers.<Map<String, Object>>allOf(
                not(hasEntry("foo", "1")),
                hasEntry("bar", Arrays.asList("2", "3"))));
    }

    @Test
    public void basicMakeParamValueFromComposite() {
        when(req.getParameter("meetingDate_c_compositeType")).thenReturn("Timestamp");
        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "meetingDate_c_date", new String[] {"2019-07-14"},
                "meetingDate_c_hour", new String[] {"13"},
                "meetingDate_c_minutes", new String[] {"8"}));
        assertThat(UtilHttp.makeParamValueFromComposite(req, "meetingDate"),
                equalTo(Timestamp.valueOf(LocalDateTime.of(2019, Month.JULY, 14, 13, 8))));
    }

    @Test
    public void emptyTypeMakeParamValueFromComposite() {
        when(req.getParameter("meetingDate_c_compositeType")).thenReturn(null);
        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "meetingDate_c_date", new String[] {"2019-07-14"},
                "meetingDate_c_hour", new String[] {"13"},
                "meetingDate_c_minutes", new String[] {"8"}));
        assertNull(UtilHttp.makeParamValueFromComposite(req, "meetingDate"));
    }

    @Test
    public void getRelativeRequestPathIncludesQueryString() {
        when(req.getRequestURI()).thenReturn("/rest/items");
        when(req.getQueryString()).thenReturn("pageIndex=1&pageSize=1");

        assertThat(UtilHttp.getRelativeRequestPath(req), equalTo("/rest/items?pageIndex=1&pageSize=1"));
    }

    @Test
    public void getRelativeRequestPathReturnsNullWhenRequestMissing() {
        assertNull(UtilHttp.getRelativeRequestPath(null));
    }

    @Test
    public void ampmMakeParamValueFromComposite() {
        when(req.getParameter("meetingDate_c_compositeType")).thenReturn("Timestamp");

        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "meetingDate_c_date", new String[] {"2019-07-14"},
                "meetingDate_c_hour", new String[] {"12"},
                "meetingDate_c_minutes", new String[] {"8"},
                "meetingDate_c_ampm", new String[] {"AM"}));
        assertThat(UtilHttp.makeParamValueFromComposite(req, "meetingDate"),
                equalTo(Timestamp.valueOf(LocalDateTime.of(2019, Month.JULY, 14, 0, 8))));

        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "meetingDate_c_date", new String[] {"2019-07-14"},
                "meetingDate_c_hour", new String[] {"8"},
                "meetingDate_c_minutes", new String[] {"8"},
                "meetingDate_c_ampm", new String[] {"PM"}));
        assertThat(UtilHttp.makeParamValueFromComposite(req, "meetingDate"),
                equalTo(Timestamp.valueOf(LocalDateTime.of(2019, Month.JULY, 14, 20, 8))));

        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "meetingDate_c_date", new String[] {"2019-07-14"},
                "meetingDate_c_hour", new String[] {"18"},
                "meetingDate_c_minutes", new String[] {"8"},
                "meetingDate_c_ampm", new String[] {"PM"}));
        assertThat(UtilHttp.makeParamValueFromComposite(req, "meetingDate"),
                equalTo(Timestamp.valueOf(LocalDateTime.of(2019, Month.JULY, 14, 18, 8))));
    }

    @Test
    public void basicMakeParamListWithSuffix() {
        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "foo_suf", new String[] {"0"},
                "bar_suf", new String[] {"1"},
                "baz", new String[] {"2"}));
        assertThat(UtilHttp.makeParamListWithSuffix(req, "_suf", null), containsInAnyOrder("0", "1"));
        assertThat(UtilHttp.makeParamListWithSuffix(req, "_suf", "b"), contains("1"));
    }

    @Test
    public void additionalParamsMakeParamListWithSuffix() {
        when(req.getParameterMap()).thenReturn(UtilMisc.toMap(
                "foo_suf", new String[] {"0"},
                "bar_suf", new String[] {"1"},
                "baz", new String[] {"2"}));
        Map<String, Object> extra = UtilMisc.toMap("baz_suf", "3");
        assertThat(UtilHttp.makeParamListWithSuffix(req, extra, "_suf", null), containsInAnyOrder("0", "1", "3"));
        assertThat(UtilHttp.makeParamListWithSuffix(req, extra, "_suf", "b"), containsInAnyOrder("1", "3"));
    }

    @Test
    public void missingRequestMakeParamListWithSuffix() {
        assertThrows(NullPointerException.class, () -> UtilHttp.makeParamListWithSuffix(null, "suffix", "prefix"));
    }

    // Covers the multipart half of the reported anonymous mainDecoratorLocation override (the
    // request body did not need to be JSON - see WebAppUtilTests for the JSON half of the same
    // fix). A multipart form field must never be able to shadow a name the webapp already
    // exposes as a trusted, application-owned ServletContext attribute.
    @Test
    public void multiPartFieldDoesNotOverrideAnExistingServletContextAttribute() throws Exception {
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getAttribute("mainDecoratorLocation"))
                .thenReturn("component://order/widget/ordermgr/CommonScreens.xml");
        when(req.getServletContext()).thenReturn(servletContext);
        when(req.getSession()).thenReturn(mock(HttpSession.class));
        stubMultipartBody(req, "mainDecoratorLocation", "file:/dev/fd/292");

        UtilHttp.getMultiPartParameterMap(req);

        verify(req, never()).setAttribute("mainDecoratorLocation", "file:/dev/fd/292");
    }

    @Test
    public void multiPartFieldStillSetsAttributesThatDoNotShadowContextConfig() throws Exception {
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getAttribute("searchString")).thenReturn(null);
        when(req.getServletContext()).thenReturn(servletContext);
        when(req.getSession()).thenReturn(mock(HttpSession.class));
        stubMultipartBody(req, "searchString", "widgets");

        UtilHttp.getMultiPartParameterMap(req);

        verify(req).setAttribute("searchString", "widgets");
    }

    private static void stubMultipartBody(HttpServletRequest request, String fieldName, String fieldValue) {
        String boundary = "----UtilHttpTestBoundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n"
                + "\r\n"
                + fieldValue + "\r\n"
                + "--" + boundary + "--\r\n";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=" + boundary);
        when(request.getContentLengthLong()).thenReturn((long) bodyBytes.length);
        when(request.getCharacterEncoding()).thenReturn(null);
        Delegator delegator = mock(Delegator.class);
        when(delegator.getDelegator()).thenReturn(delegator);
        when(request.getAttribute("delegator")).thenReturn(delegator);
        try {
            when(request.getInputStream()).thenReturn(inputStreamOf(bodyBytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServletInputStream inputStreamOf(byte[] content) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(content);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bytes.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return bytes.read();
            }
        };
    }
}
