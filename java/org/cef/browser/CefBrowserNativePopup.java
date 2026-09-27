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
 * [SWT integration] This class represents a native popup window created by
 * CEF itself (i.e. {@code CefLifeSpanHandler.onBeforePopup} returned false).
 * Upstream jcef pairs such browsers with no Java object at all, so every
 * subsequent callback ({@code onFaviconURLChange}, {@code onTitleChange}, ...)
 * was delivered with a null browser parameter.
 * <p>
 * The fork's {@code LifeSpanHandler::OnAfterCreated} instantiates this class
 * and pairs it with the native popup, so that all callbacks carry a real
 * browser object and the application can e.g. apply the favicon as window
 * icon via {@code setWindowIcon}.
 * <p>
 * The window is fully owned and managed by CEF (position, size, title). The
 * Java wrapper exists only as a callback handle: it must not create any UI
 * component and must never interfere with the close flow (see
 * {@link #doClose()}). It is kept alive by {@link CefClient}'s internal
 * browser map and removed again on {@code onBeforeClose}.
 */
class CefBrowserNativePopup extends CefBrowser_N {
    CefBrowserNativePopup(CefClient client, String url) {
        super(client, url, null, null, null, null);
    }

    @Override
    public void createImmediately() {
        // The native popup window already exists; nothing to create. The
        // browser is never created from Java.
    }

    @Override
    public Component getUIComponent() {
        // CEF owns the native top-level window; there is no component to
        // embed into a UI toolkit.
        return null;
    }

    @Override
    public synchronized boolean doClose() {
        // The user closes a native popup via its system menu / close button.
        // Never cancel the close from the Java side.
        return false;
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        throw new UnsupportedOperationException("DevTools are not supported for native popups");
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