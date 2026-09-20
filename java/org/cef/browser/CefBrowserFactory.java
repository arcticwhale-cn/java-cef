// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.browser;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;

/**
 * Creates a new instance of CefBrowser according the passed values
 */
public class CefBrowserFactory {
    public static CefBrowser create(CefClient client, String url, boolean isOffscreenRendered,
            boolean isTransparent, CefRequestContext context, CefBrowserSettings settings) {
        if (isOffscreenRendered)
            return new CefBrowserOsr(client, url, isTransparent, context, settings);
        return new CefBrowserWr(client, url, context, settings);
    }

    /**
     * Creates a windowed browser which is parented to an explicitly injected
     * native window handle (e.g. the HWND of an SWT Composite on Windows or an
     * NSView on macOS). No AWT component is involved.
     */
    public static CefBrowser create(CefClient client, String url, long windowHandle,
            CefRequestContext context, CefBrowserSettings settings) {
        return new CefBrowserSw(client, url, context, settings, windowHandle);
    }
}
