package com.facebook.android;

import android.os.Bundle;

public final class LoginDialogListener implements Facebook.DialogListener {
    public void onComplete(Bundle values) {
        SessionEvents.onLoginSuccess();
    }

    public void onFacebookError(FacebookError error) {
        SessionEvents.onLoginError(error.getMessage());
    }

    public void onError(DialogError error) {
        SessionEvents.onLoginError(error.getMessage());
    }

    public void onCancel() {
        SessionEvents.onLoginError("Action Canceled");
    }
}
