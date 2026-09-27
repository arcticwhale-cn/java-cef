// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "life_span_handler.h"

#include "client_handler.h"
#include "jni_util.h"
#include "util.h"

LifeSpanHandler::LifeSpanHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

// TODO(JCEF): Expose all parameters.
bool LifeSpanHandler::OnBeforePopup(CefRefPtr<CefBrowser> browser,
                                    CefRefPtr<CefFrame> frame,
                                    int popup_id,
                                    const CefString& target_url,
                                    const CefString& target_frame_name,
                                    WindowOpenDisposition target_disposition,
                                    bool user_gesture,
                                    const CefPopupFeatures& popupFeatures,
                                    CefWindowInfo& windowInfo,
                                    CefRefPtr<CefClient>& client,
                                    CefBrowserSettings& settings,
                                    CefRefPtr<CefDictionaryValue>& extra_info,
                                    bool* no_javascript_access) {
  if (browser->GetHost()->IsWindowRenderingDisabled()) {
    // Cancel popups in off-screen rendering mode.
    return true;
  }

  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();
  ScopedJNIString jtargetUrl(env, target_url);
  ScopedJNIString jtargetFrameName(env, target_frame_name);
  jboolean jreturn = JNI_FALSE;

  // [SWT integration] Expose popupFeatures: the geometry requested by the
  // page (window.open features). Each value has a *Set flag that is false
  // when the page did not specify it (0 in that case). GetMethodID resolves
  // the signature at runtime, so this stays in sync with the Java interface.
  JNI_CALL_METHOD(env, handle_, "onBeforePopup",
                  "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/"
                  "CefFrame;Ljava/lang/String;Ljava/lang/String;IZIZIZIZ)Z",
                  Boolean, jreturn, jbrowser.get(), jframe.get(),
                  jtargetUrl.get(), jtargetFrameName.get(),
                  (jint)popupFeatures.x, (jboolean)popupFeatures.xSet,
                  (jint)popupFeatures.y, (jboolean)popupFeatures.ySet,
                  (jint)popupFeatures.width, (jboolean)popupFeatures.widthSet,
                  (jint)popupFeatures.height, (jboolean)popupFeatures.heightSet);

  return (jreturn != JNI_FALSE);
}

void LifeSpanHandler::OnAfterCreated(CefRefPtr<CefBrowser> browser) {
  ScopedJNIEnv env;
  if (!env)
    return;

  jobject jbrowser = nullptr;
  bool is_local_ref = false;
  if (!jbrowsers_.empty()) {
    // Browser created from Java (see CefBrowser_N::create).
    util::AddCefBrowser(browser);

    jbrowser = jbrowsers_.front();
    jbrowsers_.pop_front();

    CefRefPtr<ClientHandler> client =
        (ClientHandler*)browser->GetHost()->GetClient().get();
    client->OnAfterCreated();
  } else if (browser->IsPopup()) {
    // [SWT integration] Browser created natively by CEF itself
    // (OnBeforePopup returned false). Upstream jcef pairs such browsers
    // with no Java object, so every subsequent callback
    // (onFaviconURLChange, onTitleChange, ...) would be delivered with a
    // null browser parameter. Create the Java wrapper here and pair it so
    // that callbacks carry a real browser object. The wrapper is kept
    // alive by CefClient's browser map (populated by the onAfterCreated
    // call below) and removed again in CefClient.onBeforeClose.
    //
    // util::AddCefBrowser is intentionally NOT called: native popups have
    // their own top-level window and receive input directly. Routing them
    // through the global mouse monitor hook (util_win) would forward
    // popup clicks into the hosting toolkit's component tree.
    jbrowser = NewJNIObject(env, "org/cef/browser/CefBrowserNativePopup",
                            "(Lorg/cef/CefClient;Ljava/lang/String;)V",
                            handle_.get(), NewJNIString(env, ""));
    if (!jbrowser) {
      // Class missing (e.g. an older jar without the fork's popup
      // wrapper). Fall back to upstream behavior (unpaired popup).
      return;
    }
    is_local_ref = true;
  } else {
    // Other browsers without a queued Java wrapper (e.g. DevTools) keep
    // upstream behavior and stay unpaired.
    return;
  }

  // Add a reference to |browser| that will be released in
  // LifeSpanHandler::OnBeforeClose.
  if (SetCefForJNIObject(env, jbrowser, browser.get(), "CefBrowser")) {
    JNI_CALL_VOID_METHOD(env, handle_, "onAfterCreated",
                         "(Lorg/cef/browser/CefBrowser;)V", jbrowser);
  }

  if (is_local_ref) {
    // The wrapper is owned by CefClient's browser map now; release the
    // local reference created by NewJNIObject.
    env->DeleteLocalRef(jbrowser);
  } else {
    // Release the global ref added in CefBrowser_N::create.
    env->DeleteGlobalRef(jbrowser);
  }
}

bool LifeSpanHandler::DoClose(CefRefPtr<CefBrowser> browser) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jreturn = JNI_FALSE;

  JNI_CALL_METHOD(env, handle_, "doClose", "(Lorg/cef/browser/CefBrowser;)Z",
                  Boolean, jreturn, jbrowser.get());

  return (jreturn != JNI_FALSE);
}

void LifeSpanHandler::OnBeforeClose(CefRefPtr<CefBrowser> browser) {
  REQUIRE_UI_THREAD();
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);

  JNI_CALL_VOID_METHOD(env, handle_, "onBeforeClose",
                       "(Lorg/cef/browser/CefBrowser;)V", jbrowser.get());

  // Clear the browser pointer member of the Java object. This will
  // release the browser reference that was added in
  // LifeSpanHandler::OnAfterCreated.
  SetCefForJNIObject<CefBrowser>(env, jbrowser, nullptr, "CefBrowser");

  CefRefPtr<ClientHandler> client =
      (ClientHandler*)browser->GetHost()->GetClient().get();
  client->OnBeforeClose(browser);
}

void LifeSpanHandler::OnAfterParentChanged(CefRefPtr<CefBrowser> browser) {
  REQUIRE_UI_THREAD();
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);

  JNI_CALL_VOID_METHOD(env, handle_, "onAfterParentChanged",
                       "(Lorg/cef/browser/CefBrowser;)V", jbrowser.get());
}

void LifeSpanHandler::registerJBrowser(jobject browser) {
  jbrowsers_.push_back(browser);
}

void LifeSpanHandler::unregisterJBrowser(jobject browser) {
  jbrowsers_.remove(browser);
}
