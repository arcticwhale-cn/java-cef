// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.browser;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.handler.CefWindowHandler;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/**
 * This class represents a windowed rendered browser whose parent is an
 * explicitly injected native window handle (e.g. the HWND of an SWT Composite
 * on Windows or an NSView on macOS). It is the SWT-integration counterpart of
 * {@link CefBrowserWr}: no AWT component is created, the browser is created
 * directly as a child of the injected window and no re-parenting round-trip
 * through a hidden temporary window is performed.
 * <p>
 * The host (e.g. an SWT-based UI toolkit) is responsible for driving:
 * <ul>
 *   <li>{@link #resize(int,int)} whenever the parent's client area changes,
 *   <li>{@link #notifyWindowMoveOrResizeStarted()} when the top-level window
 *       is moved or resized (fixes positioning of select popups),
 *   <li>{@link #setFocus(boolean)} on focus changes,
 *   <li>{@link #setWindowVisibility(boolean)} on show/hide,
 *   <li>{@link #close(boolean)} on disposal (before CefShutdown).
 * </ul>
 * To create a new instance, please use
 * {@link CefClient#createBrowser(String, long, CefRequestContext, CefBrowserSettings)}.
 */
public class CefBrowserSw extends CefBrowser_N {
    private final long windowHandle_;
    private final Rectangle content_rect_ = new Rectangle(0, 0, 0, 0);

    private final CefWindowHandler win_handler_ = new CefWindowHandler() {
        @Override
        public Rectangle getRect(CefBrowser browser) {
            synchronized (content_rect_) {
                return new Rectangle(content_rect_);
            }
        }

        @Override
        public void onMouseEvent(CefBrowser browser, int event, int screenX, int screenY,
                int modifier, int button) {
            // Mouse events are delivered natively to the host window; no
            // forwarding to an AWT component tree is required.
        }
    };

    CefBrowserSw(CefClient client, String url, CefRequestContext context,
            CefBrowserSettings settings, long windowHandle) {
        super(client, url, context, null, null, settings);
        if (windowHandle == 0)
            throw new IllegalArgumentException("windowHandle must not be 0");
        windowHandle_ = windowHandle;
    }

    @Override
    public void createImmediately() {
        // The browser is created directly as a child of the injected native
        // window handle. Unlike the AWT flow, there is no hidden temp window
        // and no later re-parenting step.
        createBrowser(getClient(), windowHandle_, getUrl(), false, false, null,
                getRequestContext());
    }

    @Override
    public Component getUIComponent() {
        // There is no AWT component; the browser renders into the injected
        // native window.
        return null;
    }

    @Override
    public CefWindowHandler getWindowHandler() {
        return win_handler_;
    }

    /**
     * Resize the browser to the given client-area size. May be called before
     * the browser has been created; the size is then used as the initial
     * window rect on creation.
     */
    public void resize(int width, int height) {
        synchronized (content_rect_) {
            content_rect_.setBounds(0, 0, width, height);
        }
        if (getNativeRef("CefBrowser") != 0) {
            wasResized(width, height);
        }
    }

    /**
     * Call this method if the host window was moved or resized. This fixes
     * positioning of select popups and dismissal on window move/resize.
     */
    public void notifyWindowMoveOrResizeStarted() {
        notifyMoveOrResizeStarted();
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        throw new UnsupportedOperationException(
                "DevTools are not supported for handle-injected browsers");
    }

    @Override
    public synchronized boolean doClose() {
        // The host controls the browser lifetime explicitly (close(true) on
        // disposal); do not dispatch AWT window events.
        return false;
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
