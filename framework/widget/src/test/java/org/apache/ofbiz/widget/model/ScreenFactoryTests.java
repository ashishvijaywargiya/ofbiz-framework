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
package org.apache.ofbiz.widget.model;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

/**
 * Covers the entry points ScreenFactory exposes for resolving a screen resource location.
 * {@link WidgetSecureLocationTests} covers the sanitizer itself; these tests confirm every
 * ScreenFactory entry point actually calls it, including the ones a request-controlled
 * {@code screenUri} (e.g. FoPrintServerEvents#getXslFo) or another request-influenced caller
 * would reach directly - not only the {@code <include-screen>}/{@code <decorator-screen>} path
 * that already went through ModelScreenWidget#renderReferencedScreen.
 */
public final class ScreenFactoryTests {

    @Test
    public void getScreensFromLocationRejectsFileSchemeDescriptorLocation() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ScreenFactory.getScreensFromLocation("file:/proc/self/fd/292"));
        assertTrue(e.getMessage().contains("unallowed"), "Unexpected message: " + e.getMessage());
    }

    @Test
    public void getScreenFromLocationRejectsFileSchemeDescriptorLocation() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ScreenFactory.getScreenFromLocation("file:/proc/self/fd/292", "main-decorator"));
        assertTrue(e.getMessage().contains("unallowed"), "Unexpected message: " + e.getMessage());
    }

    @Test
    public void getScreenFromWebappContextRejectsFileSchemeLocationWithoutTouchingServletContext() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);
        when(request.getContextPath()).thenReturn("/webtools");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ScreenFactory.getScreenFromWebappContext("file:/proc/self/fd/292", "main-decorator", request));
        assertTrue(e.getMessage().contains("unallowed"), "Unexpected message: " + e.getMessage());
        verify(servletContext, never()).getResource(any());
    }
}
