package com.facebook.android;

import android.os.Bundle;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import com.facebook.android.SessionEvents;

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
