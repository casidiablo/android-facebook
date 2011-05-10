package com.facebook.android;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.FacebookError;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

public abstract class BaseRequestListener implements AsyncFacebookRunner.RequestListener{
    @Override
    public abstract void onComplete(String response, Object state);

    @Override
    public void onIOException(IOException e, Object state) {
    }

    @Override
    public void onFileNotFoundException(FileNotFoundException e, Object state) {
    }

    @Override
    public void onMalformedURLException(MalformedURLException e, Object state) {
    }

    @Override
    public void onFacebookError(FacebookError e, Object state){
    }
}
