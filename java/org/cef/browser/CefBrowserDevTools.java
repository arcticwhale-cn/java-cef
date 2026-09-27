// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.browser;

import org.cef.CefClient;

import java.awt.Component;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/**
 * [SWT integration] This class represents the DevTools window created by CEF
 * itself ({@code CefBrowserHost::ShowDevTools} with an empty windowInfo - a
 * native top-level window, same pattern as native popups). The upstream
 * {@link CefBrowserSw#createDevToolsBrowser} threw
 * {@code UnsupportedOperationException} because an Alloy-style DevTools window
 * cannot be embedded into the host window; the fork's native create() DevTools
 * branch opens it as a native pop-up window instead, so a pure callback-handle
 * wrapper suffices (same pattern as {@code CefBrowserNativePopup}).
 * <p>
 * The window is fully owned and managed by CEF (title, size, lifecycle - it is
 * closed by CEF when the parent browser closes). The Java wrapper exists only
 * as the DevTools create-path handle: it must not create any UI component and
 * must never interfere with the close flow (see {@link #doClose()}).
 */
class CefBrowserDevTools extends CefBrowser_N {
    CefBrowserDevTools(CefClient client, String url, CefRequestContext context,
            CefBrowser_N parent, Point inspectAt) {
        super(client, url, context, parent, inspectAt, null);
    }

    @Override
    public void createImmediately() {
        // windowHandle 0 + canvas null: the native DevTools branch calls
        // ShowDevTools with an empty windowInfo, i.e. CEF creates and owns
        // the top-level DevTools window.
        createDevTools(getParentBrowser(), getClient(), 0, false, false, null,
                getInspectAt());
    }

    @Override
    public Component getUIComponent() {
        // CEF owns the native top-level window; there is no component to
        // embed into a UI toolkit.
        return null;
    }

    @Override
    public synchronized boolean doClose() {
        // DevTools is closed by its own system menu or cascaded by CEF when
        // the parent browser closes. Never cancel the close from Java.
        return false;
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        throw new UnsupportedOperationException("Nested DevTools are not supported");
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        throw new UnsupportedOperationException("Unsupported for windowed rendering");
    }

    @Override
    public void setWindowlessFrameRate(int frameRate) {
        throw new UnsupportedOperationException(
                "You can only set windowless framerate on OSR browser");
    }

    @Override
    public CompletableFuture<Integer> getWindowlessFrameRate() {
        throw new UnsupportedOperationException(
                "You can only get windowless framerate on OSR browser");
    }
}