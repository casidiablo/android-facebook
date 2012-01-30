/*
 * Copyright 2010 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.android;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.facebook.android.Facebook.DialogListener;

public class FbDialog extends Dialog {

    static final FrameLayout.LayoutParams FILL =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                         ViewGroup.LayoutParams.FILL_PARENT);
    static final String DISPLAY_STRING = "touch";

    private String mUrl;
    private DialogListener mListener;
    private ProgressDialog mSpinner;
    private ImageView mCrossImage;
    private WebView mWebView;
    private FrameLayout mContent;

    public FbDialog(Context context, String url, DialogListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        mUrl = url;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSpinner = new ProgressDialog(getContext());
        mSpinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mSpinner.setMessage("Loading...");

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mContent = new FrameLayout(getContext());

        /* Create the 'x' image, but don't add to the mContent layout yet
         * at this point, we only need to know its drawable width and height 
         * to place the webview
         */
        createCrossImage();

        /* Now we know 'x' drawable width and height, 
         * layout the webivew and add it the mContent layout
         */
        int crossWidth = mCrossImage.getDrawable().getIntrinsicWidth();
        setUpWebView(crossWidth / 2);

        /* Finally add the 'x' image to the mContent layout and
         * add mContent to the Dialog view
         */
        mContent.addView(mCrossImage, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addContentView(mContent, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
    }

    private void setUpWebView(int margin) {
        LinearLayout webViewContainer = new LinearLayout(getContext());
        mWebView = new WebView(getContext());
        mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.setWebViewClient(new FbDialog.FbWebViewClient());
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.loadUrl(mUrl);
        mWebView.setLayoutParams(FILL);
        mWebView.setVisibility(View.INVISIBLE);

        webViewContainer.setPadding(margin, margin, margin, margin);
        webViewContainer.addView(mWebView);
        mContent.addView(webViewContainer);
    }

    private void createCrossImage() {
        mCrossImage = new ImageView(getContext());
        // Dismiss the dialog when user click on the 'x'
        mCrossImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onCancel();
                FbDialog.this.dismiss();
            }
        });

        Drawable crossDrawable;

        DisplayMetrics dm;
        // if it is a tablet
        if ((getContext().getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
            crossDrawable = getDefaultDrawable(CLOSE_BUTTON_XLARGE);
        } else {
            dm = getContext().getResources().getDisplayMetrics();
            switch (dm.densityDpi) {
                case DisplayMetrics.DENSITY_HIGH:
                    crossDrawable = getDefaultDrawable(CLOSE_BUTTON_HD);
                    break;
                case DisplayMetrics.DENSITY_LOW:
                    crossDrawable = getDefaultDrawable(CLOSE_BUTTON_LD);
                    break;
                default:
                    crossDrawable = getDefaultDrawable(CLOSE_BUTTON_DEFAULT);
                    break;
            }
        }

        mCrossImage.setImageDrawable(crossDrawable);
        /* 'x' should not be visible while webview is loading
         * make it visible only after webview has fully loaded
        */
        mCrossImage.setVisibility(View.INVISIBLE);
    }

    private class FbWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d("Facebook-WebView", "Redirect URL: " + url);
            if (url.startsWith(Facebook.REDIRECT_URI)) {
                Bundle values = Util.parseUrl(url);

                String error = values.getString("error");
                if (error == null) {
                    error = values.getString("error_type");
                }

                if (error == null) {
                    mListener.onComplete(values);
                } else if (error.equals("access_denied") ||
                           error.equals("OAuthAccessDeniedException")) {
                    mListener.onCancel();
                } else {
                    mListener.onFacebookError(new FacebookError(error));
                }

                FbDialog.this.dismiss();
                return true;
            } else if (url.startsWith(Facebook.CANCEL_URI)) {
                mListener.onCancel();
                FbDialog.this.dismiss();
                return true;
            } else if (url.contains(DISPLAY_STRING)) {
                return false;
            }
            // launch non-dialog URLs in a full browser
            getContext().startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        }

        @Override
        public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            mListener.onError(
                    new DialogError(description, errorCode, failingUrl));
            FbDialog.this.dismiss();
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d("Facebook-WebView", "Webview loading URL: " + url);
            super.onPageStarted(view, url, favicon);
            try {
                mSpinner.show();
            } catch (Exception ignore) {}
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mSpinner.dismiss();
            /* 
             * Once webview is fully loaded, set the mContent background to be transparent
             * and make visible the 'x' image. 
             */
            mContent.setBackgroundColor(Color.TRANSPARENT);
            mWebView.setVisibility(View.VISIBLE);
            mCrossImage.setVisibility(View.VISIBLE);
        }
    }

    private static Drawable getDefaultDrawable(String nextImage) {
        byte[] decode = Base64.decode(nextImage, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decode, 0, decode.length);
        return new BitmapDrawable(resize(bitmap, 1.5f));
    }

    private static Bitmap resize(Bitmap img, float factor) {
        //actual width of the image (img is a Bitmap object)
        int width = img.getWidth();
        int height = img.getHeight();

        //new width / height
        int newWidth = (int) (width * factor);
        int newHeight = (int) (height * factor);

        // calculate the scale
        float scaleWidth = (float) newWidth / width;
        float scaleHeight = (float) newHeight / height;

        // create a matrix for the manipulation
        Matrix matrix = new Matrix();

        // resize the bit map
        matrix.postScale(scaleWidth, scaleHeight);

        // recreate the new Bitmap and set it back
        Bitmap result = Bitmap.createBitmap(img, 0, 0, width, height, matrix, true);
        img.recycle();
        return result;
    }

    private static final String CLOSE_BUTTON_DEFAULT =
            "iVBORw0KGgoAAAANSUhEUgAAAB0AAAAdCAYAAABWk2cPAAAKRGlDQ1BJQ0MgUHJvZmlsZQAAeAGd\n" +
            "lndUFNcXx9/MbC+0XZYiZem9twWkLr1IlSYKy+4CS1nWZRewN0QFIoqICFYkKGLAaCgSK6JYCAgW\n" +
            "7AEJIkoMRhEVlczGHPX3Oyf5/U7eH3c+8333nnfn3vvOGQAoASECYQ6sAEC2UCKO9PdmxsUnMPG9\n" +
            "AAZEgAM2AHC4uaLQKL9ogK5AXzYzF3WS8V8LAuD1LYBaAK5bBIQzmX/p/+9DkSsSSwCAwtEAOx4/\n" +
            "l4tyIcpZ+RKRTJ9EmZ6SKWMYI2MxmiDKqjJO+8Tmf/p8Yk8Z87KFPNRHlrOIl82TcRfKG/OkfJSR\n" +
            "EJSL8gT8fJRvoKyfJc0WoPwGZXo2n5MLAIYi0yV8bjrK1ihTxNGRbJTnAkCgpH3FKV+xhF+A5gkA\n" +
            "O0e0RCxIS5cwjbkmTBtnZxYzgJ+fxZdILMI53EyOmMdk52SLOMIlAHz6ZlkUUJLVlokW2dHG2dHR\n" +
            "wtYSLf/n9Y+bn73+GWS9/eTxMuLPnkGMni/al9gvWk4tAKwptDZbvmgpOwFoWw+A6t0vmv4+AOQL\n" +
            "AWjt++p7GLJ5SZdIRC5WVvn5+ZYCPtdSVtDP6386fPb8e/jqPEvZeZ9rx/Thp3KkWRKmrKjcnKwc\n" +
            "qZiZK+Jw+UyL/x7ifx34VVpf5WEeyU/li/lC9KgYdMoEwjS03UKeQCLIETIFwr/r8L8M+yoHGX6a\n" +
            "axRodR8BPckSKPTRAfJrD8DQyABJ3IPuQJ/7FkKMAbKbF6s99mnuUUb3/7T/YeAy9BXOFaQxZTI7\n" +
            "MprJlYrzZIzeCZnBAhKQB3SgBrSAHjAGFsAWOAFX4Al8QRAIA9EgHiwCXJAOsoEY5IPlYA0oAiVg\n" +
            "C9gOqsFeUAcaQBM4BtrASXAOXARXwTVwE9wDQ2AUPAOT4DWYgSAID1EhGqQGaUMGkBlkC7Egd8gX\n" +
            "CoEioXgoGUqDhJAUWg6tg0qgcqga2g81QN9DJ6Bz0GWoH7oDDUPj0O/QOxiBKTAd1oQNYSuYBXvB\n" +
            "wXA0vBBOgxfDS+FCeDNcBdfCR+BW+Bx8Fb4JD8HP4CkEIGSEgeggFggLYSNhSAKSioiRlUgxUonU\n" +
            "Ik1IB9KNXEeGkAnkLQaHoWGYGAuMKyYAMx/DxSzGrMSUYqoxhzCtmC7MdcwwZhLzEUvFamDNsC7Y\n" +
            "QGwcNg2bjy3CVmLrsS3YC9ib2FHsaxwOx8AZ4ZxwAbh4XAZuGa4UtxvXjDuL68eN4KbweLwa3gzv\n" +
            "hg/Dc/ASfBF+J/4I/gx+AD+Kf0MgE7QJtgQ/QgJBSFhLqCQcJpwmDBDGCDNEBaIB0YUYRuQRlxDL\n" +
            "iHXEDmIfcZQ4Q1IkGZHcSNGkDNIaUhWpiXSBdJ/0kkwm65KdyRFkAXk1uYp8lHyJPEx+S1GimFLY\n" +
            "lESKlLKZcpBylnKH8pJKpRpSPakJVAl1M7WBep76kPpGjiZnKRcox5NbJVcj1yo3IPdcnihvIO8l\n" +
            "v0h+qXyl/HH5PvkJBaKCoQJbgaOwUqFG4YTCoMKUIk3RRjFMMVuxVPGw4mXFJ0p4JUMlXyWeUqHS\n" +
            "AaXzSiM0hKZHY9O4tHW0OtoF2igdRzeiB9Iz6CX07+i99EllJWV75RjlAuUa5VPKQwyEYcgIZGQx\n" +
            "yhjHGLcY71Q0VbxU+CqbVJpUBlSmVeeoeqryVYtVm1Vvqr5TY6r5qmWqbVVrU3ugjlE3VY9Qz1ff\n" +
            "o35BfWIOfY7rHO6c4jnH5tzVgDVMNSI1lmkc0OjRmNLU0vTXFGnu1DyvOaHF0PLUytCq0DqtNa5N\n" +
            "03bXFmhXaJ/RfspUZnoxs5hVzC7mpI6GToCOVGe/Tq/OjK6R7nzdtbrNug/0SHosvVS9Cr1OvUl9\n" +
            "bf1Q/eX6jfp3DYgGLIN0gx0G3QbThkaGsYYbDNsMnxipGgUaLTVqNLpvTDX2MF5sXGt8wwRnwjLJ\n" +
            "NNltcs0UNnUwTTetMe0zg80czQRmu836zbHmzuZC81rzQQuKhZdFnkWjxbAlwzLEcq1lm+VzK32r\n" +
            "BKutVt1WH60drLOs66zv2SjZBNmstemw+d3W1JZrW2N7w45q52e3yq7d7oW9mT3ffo/9bQeaQ6jD\n" +
            "BodOhw+OTo5ixybHcSd9p2SnXU6DLDornFXKuuSMdfZ2XuV80vmti6OLxOWYy2+uFq6Zroddn8w1\n" +
            "msufWzd3xE3XjeO2323Ineme7L7PfchDx4PjUevxyFPPk+dZ7znmZeKV4XXE67m3tbfYu8V7mu3C\n" +
            "XsE+64P4+PsU+/T6KvnO9632fein65fm1+g36e/gv8z/bAA2IDhga8BgoGYgN7AhcDLIKWhFUFcw\n" +
            "JTgquDr4UYhpiDikIxQODQrdFnp/nsE84by2MBAWGLYt7EG4Ufji8B8jcBHhETURjyNtIpdHdkfR\n" +
            "opKiDke9jvaOLou+N994vnR+Z4x8TGJMQ8x0rE9seexQnFXcirir8erxgvj2BHxCTEJ9wtQC3wXb\n" +
            "F4wmOiQWJd5aaLSwYOHlReqLshadSpJP4iQdT8YmxyYfTn7PCePUcqZSAlN2pUxy2dwd3Gc8T14F\n" +
            "b5zvxi/nj6W6pZanPklzS9uWNp7ukV6ZPiFgC6oFLzICMvZmTGeGZR7MnM2KzWrOJmQnZ58QKgkz\n" +
            "hV05WjkFOf0iM1GRaGixy+LtiyfFweL6XCh3YW67hI7+TPVIjaXrpcN57nk1eW/yY/KPFygWCAt6\n" +
            "lpgu2bRkbKnf0m+XYZZxl3Uu11m+ZvnwCq8V+1dCK1NWdq7SW1W4anS1/+pDa0hrMtf8tNZ6bfna\n" +
            "V+ti13UUahauLhxZ77++sUiuSFw0uMF1w96NmI2Cjb2b7Dbt3PSxmFd8pcS6pLLkfSm39Mo3Nt9U\n" +
            "fTO7OXVzb5lj2Z4tuC3CLbe2emw9VK5YvrR8ZFvottYKZkVxxavtSdsvV9pX7t1B2iHdMVQVUtW+\n" +
            "U3/nlp3vq9Orb9Z41zTv0ti1adf0bt7ugT2ee5r2au4t2ftun2Df7f3++1trDWsrD+AO5B14XBdT\n" +
            "1/0t69uGevX6kvoPB4UHhw5FHupqcGpoOKxxuKwRbpQ2jh9JPHLtO5/v2pssmvY3M5pLjoKj0qNP\n" +
            "v0/+/tax4GOdx1nHm34w+GFXC62luBVqXdI62ZbeNtQe395/IuhEZ4drR8uPlj8ePKlzsuaU8qmy\n" +
            "06TThadnzyw9M3VWdHbiXNq5kc6kznvn487f6Iro6r0QfOHSRb+L57u9us9ccrt08rLL5RNXWFfa\n" +
            "rjpebe1x6Gn5yeGnll7H3tY+p772a87XOvrn9p8e8Bg4d93n+sUbgTeu3px3s//W/Fu3BxMHh27z\n" +
            "bj+5k3Xnxd28uzP3Vt/H3i9+oPCg8qHGw9qfTX5uHnIcOjXsM9zzKOrRvRHuyLNfcn95P1r4mPq4\n" +
            "ckx7rOGJ7ZOT437j154ueDr6TPRsZqLoV8Vfdz03fv7Db56/9UzGTY6+EL+Y/b30pdrLg6/sX3VO\n" +
            "hU89fJ39ema6+I3am0NvWW+738W+G5vJf49/X/XB5EPHx+CP92ezZ2f/AAOY8/xJsCmYAAAACXBI\n" +
            "WXMAAAsTAAALEwEAmpwYAAAGX0lEQVRIDYVWS0yUSRDufx4wwAxPA4jyViQxmI3sRTbhmWWDm/UC\n" +
            "usaY3bCnPUDkyNWDm6AevXg07sEHjyyEDRDEYORlxBCIukpQjEsiGnnDDDPM9H5fMf+fQVispP/u\n" +
            "6qr6qruruvo3tNbqK2RAzhb6ih7FNjQC7gvqoOY+ZIcsiKYNw/AMDAwU5OTkfBMXF5fhcrkSfT7f\n" +
            "8vr6+tzs7OxEZWXla2xgFXoGetNub2ju9MsGTePp06dOzoMOvH379nev1zsIfg0thBZJ5Ncopx71\n" +
            "wSvaE4fjL9uuCSgY9+7di6LikydPqrCTEYyFlpeX9eDgoG5vb9d37twJtrW1Cc95k6hPO/AqjLPL\n" +
            "8Q6nXBkV0TtevHjxSyAQ+Eywubm54PXr14PFxcWMq9nM2IU4Tzn1qE+7ly9f/kqcMN4Ox5bTSIfP\n" +
            "nz+vD4VCWwTo7e0NQGY60G63W2dmZurs7GzpEV9LRr2enp4A7WgPnN/2cmw5vXnzJmPgvHv37ndI\n" +
            "kE80vH///haBkDT64MGDOjExUaekpIgT5gtl5BMSEkQeExMjc7SjPXGIR1ziY0r8mU6NxsbGaAgP\n" +
            "zM/P/0WDsbExcXjo0KEdO6Gj5ORkyyF5NqfTKb2pT3vifPz4sZO4NTU10WDlmMVpeJeurq6uOhyL\n" +
            "D8mgL168yNgJUEFBgUaMNRJEeM7n5+dbY85TTj3T5sKFC6G1tTUdDAY3iYt5l7lbcYpgx2DSg0T4\n" +
            "k6vr6OiQu5mRkaFTU1P15OQkp4WGhoYsYDoYHh42RaJHfdpRRhwKiUt8+gGrbJcvX7bdvn2bFSfT\n" +
            "4/EUoVejo6M0UjgyheNRMzMzZIVKSkrU48ePFeKrsAB16tQpUyR61KcdycSJj48nblZLS4tBf+rh\n" +
            "w4esSnFXr1497ff7P4HoUI6WCYKxNKzS2hEHiP0OHslj6UbYhbAIXqFP165d+5F+6M82NTXFkmXH\n" +
            "NUh0OBzuzc1NsEphdQoxUbgiCiegzp07p1AMFBYmchyj9OQ5f/bsWdGjPu1oT6Lcbre7Dx8+nADW\n" +
            "Tn82APJo7Vi2E2WdYyFcCWWz2XYA1NXVyXFTAasXPR4n50nmQmlHe5MwZtbyzB30Z3v37h1l9jdv\n" +
            "3vgD/oDXjAdKmxgydkgEsUfSKKxYxqYe+ZGREZmjXlJSktjRnkQ97NZLfLDb/nAsUbm5uWmY+H5p\n" +
            "aWkaKa7r6+ut7MW8xArJY8Vwa2tLo9po9iZFZrWZvcQhHnGJj2uWSn8YK8elS5fotOj9+/cDBLl1\n" +
            "65Y4PXbs2C6HlLPQQ1+3traStYjXh/NHjx6VnjgUEpf4YT8OeXQReAbIj/dSzqmqqspWWFioX716\n" +
            "xSSwkgc6CndP1dbWStIwlnDMaSHGmcc7PT2taE8cCsK4/rAfLkgZ5eXlbvR5aD8hLnyMrQKBmMmq\n" +
            "+/r6dHd3t4yRMFYtho3u7OzU/f39IktPT5c+ojC8Ji7xw36M7QKsVNT58+czIPj2xo0bf2DFEq/m\n" +
            "5ma5r1i9ANEBG18a9l++MOb9pB3jTRziETeML++06ZR3NaG6uroQ/Q94qDu5W7wS+sqVK1YNPnLk\n" +
            "iGZBZ6njjtiT5zwXwUZ92pGIQ7wwrtxTTEOLHxwxWvTx48fTs7KyijH+Ga+EJBWNkblBvBKWc8gt\n" +
            "J+aYcupRn0R74hCPuBjzFZNXZvupIWcY3K0LfwEp4+PjvIzZiFUdkuF0bGysC/9ACokVevTokcG7\n" +
            "jd0ovLMKj7kqLS3VyHQb3lO1sbHhe/Dgwd9nzpxpA8Ys8P4F3meMuX3eiu2dguGQu2Udji8qKsoD\n" +
            "WAnGtU1NTS0TExPjAPNyB/9HlFOP+rSj/YkTJ3Ix9qARVzYI++0BJoRYrjCgQkxeXl4i6nEa4pIM\n" +
            "Pq2hoaEQv5n5aWlpOahSCUgiF95dHy7+Mor/LK7FDJLmH+jOl5WVLeBuzqMKLYL3oUkVQS9kHa81\n" +
            "se2YR80YeE6ePJmErEzG6xBHHo2VnO8vdXhcXrQVtNWKiop1lL+FZ8+e0dkqGl8PiTN6i3Y5pSS8\n" +
            "Y15s7prO4xA3N+5n7IcPH6LxvjoXFxcNXiWUtgAyeXNlZWUD8V6D7joanfF3R/6R0e+gPZ2aGhHO\n" +
            "uSvWTCeO14l/JHtUVJQNhTy0sLAQxPGyoklVQ8/d7+kM80L7OjWV2EcsgHE3W+TV2ddRJNZ/lLRQ\n" +
            "OVlezJYAAAAASUVORK5CYII=";

    public static final String CLOSE_BUTTON_HD =
            "iVBORw0KGgoAAAANSUhEUgAAACsAAAArCAYAAADhXXHAAAAACXBIWXMAAAsTAAALEwEAmpwYAAAK\n" +
            "T2lDQ1BQaG90b3Nob3AgSUNDIHByb2ZpbGUAAHjanVNnVFPpFj333vRCS4iAlEtvUhUIIFJCi4AU\n" +
            "kSYqIQkQSoghodkVUcERRUUEG8igiAOOjoCMFVEsDIoK2AfkIaKOg6OIisr74Xuja9a89+bN/rXX\n" +
            "Pues852zzwfACAyWSDNRNYAMqUIeEeCDx8TG4eQuQIEKJHAAEAizZCFz/SMBAPh+PDwrIsAHvgAB\n" +
            "eNMLCADATZvAMByH/w/qQplcAYCEAcB0kThLCIAUAEB6jkKmAEBGAYCdmCZTAKAEAGDLY2LjAFAt\n" +
            "AGAnf+bTAICd+Jl7AQBblCEVAaCRACATZYhEAGg7AKzPVopFAFgwABRmS8Q5ANgtADBJV2ZIALC3\n" +
            "AMDOEAuyAAgMADBRiIUpAAR7AGDIIyN4AISZABRG8lc88SuuEOcqAAB4mbI8uSQ5RYFbCC1xB1dX\n" +
            "Lh4ozkkXKxQ2YQJhmkAuwnmZGTKBNA/g88wAAKCRFRHgg/P9eM4Ors7ONo62Dl8t6r8G/yJiYuP+\n" +
            "5c+rcEAAAOF0ftH+LC+zGoA7BoBt/qIl7gRoXgugdfeLZrIPQLUAoOnaV/Nw+H48PEWhkLnZ2eXk\n" +
            "5NhKxEJbYcpXff5nwl/AV/1s+X48/Pf14L7iJIEyXYFHBPjgwsz0TKUcz5IJhGLc5o9H/LcL//wd\n" +
            "0yLESWK5WCoU41EScY5EmozzMqUiiUKSKcUl0v9k4t8s+wM+3zUAsGo+AXuRLahdYwP2SycQWHTA\n" +
            "4vcAAPK7b8HUKAgDgGiD4c93/+8//UegJQCAZkmScQAAXkQkLlTKsz/HCAAARKCBKrBBG/TBGCzA\n" +
            "BhzBBdzBC/xgNoRCJMTCQhBCCmSAHHJgKayCQiiGzbAdKmAv1EAdNMBRaIaTcA4uwlW4Dj1wD/ph\n" +
            "CJ7BKLyBCQRByAgTYSHaiAFiilgjjggXmYX4IcFIBBKLJCDJiBRRIkuRNUgxUopUIFVIHfI9cgI5\n" +
            "h1xGupE7yAAygvyGvEcxlIGyUT3UDLVDuag3GoRGogvQZHQxmo8WoJvQcrQaPYw2oefQq2gP2o8+\n" +
            "Q8cwwOgYBzPEbDAuxsNCsTgsCZNjy7EirAyrxhqwVqwDu4n1Y8+xdwQSgUXACTYEd0IgYR5BSFhM\n" +
            "WE7YSKggHCQ0EdoJNwkDhFHCJyKTqEu0JroR+cQYYjIxh1hILCPWEo8TLxB7iEPENyQSiUMyJ7mQ\n" +
            "AkmxpFTSEtJG0m5SI+ksqZs0SBojk8naZGuyBzmULCAryIXkneTD5DPkG+Qh8lsKnWJAcaT4U+Io\n" +
            "UspqShnlEOU05QZlmDJBVaOaUt2ooVQRNY9aQq2htlKvUYeoEzR1mjnNgxZJS6WtopXTGmgXaPdp\n" +
            "r+h0uhHdlR5Ol9BX0svpR+iX6AP0dwwNhhWDx4hnKBmbGAcYZxl3GK+YTKYZ04sZx1QwNzHrmOeZ\n" +
            "D5lvVVgqtip8FZHKCpVKlSaVGyovVKmqpqreqgtV81XLVI+pXlN9rkZVM1PjqQnUlqtVqp1Q61Mb\n" +
            "U2epO6iHqmeob1Q/pH5Z/YkGWcNMw09DpFGgsV/jvMYgC2MZs3gsIWsNq4Z1gTXEJrHN2Xx2KruY\n" +
            "/R27iz2qqaE5QzNKM1ezUvOUZj8H45hx+Jx0TgnnKKeX836K3hTvKeIpG6Y0TLkxZVxrqpaXllir\n" +
            "SKtRq0frvTau7aedpr1Fu1n7gQ5Bx0onXCdHZ4/OBZ3nU9lT3acKpxZNPTr1ri6qa6UbobtEd79u\n" +
            "p+6Ynr5egJ5Mb6feeb3n+hx9L/1U/W36p/VHDFgGswwkBtsMzhg8xTVxbzwdL8fb8VFDXcNAQ6Vh\n" +
            "lWGX4YSRudE8o9VGjUYPjGnGXOMk423GbcajJgYmISZLTepN7ppSTbmmKaY7TDtMx83MzaLN1pk1\n" +
            "mz0x1zLnm+eb15vft2BaeFostqi2uGVJsuRaplnutrxuhVo5WaVYVVpds0atna0l1rutu6cRp7lO\n" +
            "k06rntZnw7Dxtsm2qbcZsOXYBtuutm22fWFnYhdnt8Wuw+6TvZN9un2N/T0HDYfZDqsdWh1+c7Ry\n" +
            "FDpWOt6azpzuP33F9JbpL2dYzxDP2DPjthPLKcRpnVOb00dnF2e5c4PziIuJS4LLLpc+Lpsbxt3I\n" +
            "veRKdPVxXeF60vWdm7Obwu2o26/uNu5p7ofcn8w0nymeWTNz0MPIQ+BR5dE/C5+VMGvfrH5PQ0+B\n" +
            "Z7XnIy9jL5FXrdewt6V3qvdh7xc+9j5yn+M+4zw33jLeWV/MN8C3yLfLT8Nvnl+F30N/I/9k/3r/\n" +
            "0QCngCUBZwOJgUGBWwL7+Hp8Ib+OPzrbZfay2e1BjKC5QRVBj4KtguXBrSFoyOyQrSH355jOkc5p\n" +
            "DoVQfujW0Adh5mGLw34MJ4WHhVeGP45wiFga0TGXNXfR3ENz30T6RJZE3ptnMU85ry1KNSo+qi5q\n" +
            "PNo3ujS6P8YuZlnM1VidWElsSxw5LiquNm5svt/87fOH4p3iC+N7F5gvyF1weaHOwvSFpxapLhIs\n" +
            "OpZATIhOOJTwQRAqqBaMJfITdyWOCnnCHcJnIi/RNtGI2ENcKh5O8kgqTXqS7JG8NXkkxTOlLOW5\n" +
            "hCepkLxMDUzdmzqeFpp2IG0yPTq9MYOSkZBxQqohTZO2Z+pn5mZ2y6xlhbL+xW6Lty8elQfJa7OQ\n" +
            "rAVZLQq2QqboVFoo1yoHsmdlV2a/zYnKOZarnivN7cyzytuQN5zvn//tEsIS4ZK2pYZLVy0dWOa9\n" +
            "rGo5sjxxedsK4xUFK4ZWBqw8uIq2Km3VT6vtV5eufr0mek1rgV7ByoLBtQFr6wtVCuWFfevc1+1d\n" +
            "T1gvWd+1YfqGnRs+FYmKrhTbF5cVf9go3HjlG4dvyr+Z3JS0qavEuWTPZtJm6ebeLZ5bDpaql+aX\n" +
            "Dm4N2dq0Dd9WtO319kXbL5fNKNu7g7ZDuaO/PLi8ZafJzs07P1SkVPRU+lQ27tLdtWHX+G7R7ht7\n" +
            "vPY07NXbW7z3/T7JvttVAVVN1WbVZftJ+7P3P66Jqun4lvttXa1ObXHtxwPSA/0HIw6217nU1R3S\n" +
            "PVRSj9Yr60cOxx++/p3vdy0NNg1VjZzG4iNwRHnk6fcJ3/ceDTradox7rOEH0x92HWcdL2pCmvKa\n" +
            "RptTmvtbYlu6T8w+0dbq3nr8R9sfD5w0PFl5SvNUyWna6YLTk2fyz4ydlZ19fi753GDborZ752PO\n" +
            "32oPb++6EHTh0kX/i+c7vDvOXPK4dPKy2+UTV7hXmq86X23qdOo8/pPTT8e7nLuarrlca7nuer21\n" +
            "e2b36RueN87d9L158Rb/1tWeOT3dvfN6b/fF9/XfFt1+cif9zsu72Xcn7q28T7xf9EDtQdlD3YfV\n" +
            "P1v+3Njv3H9qwHeg89HcR/cGhYPP/pH1jw9DBY+Zj8uGDYbrnjg+OTniP3L96fynQ89kzyaeF/6i\n" +
            "/suuFxYvfvjV69fO0ZjRoZfyl5O/bXyl/erA6xmv28bCxh6+yXgzMV70VvvtwXfcdx3vo98PT+R8\n" +
            "IH8o/2j5sfVT0Kf7kxmTk/8EA5jz/GMzLdsAAAAgY0hSTQAAeiUAAICDAAD5/wAAgOkAAHUwAADq\n" +
            "YAAAOpgAABdvkl/FRgAAB9hJREFUeNq8mUFMG2cWx//2GOw4bB2g0y0hIXjj1iosMSVqpSS7W5Sk\n" +
            "rQ9R0i0uEASHRBxAqzhC4hZV4uBVriSVIrHaFCUCJeHACpUDhyqKKmIWidSbIgXoGsxu7A1aFkxK\n" +
            "PQQ8+NvL+9A34zE2gd1Pehp5xt97v3nzzfvee2PC7oZJOJp05wCACUemO/faxnY6h4uZRKKjeI0J\n" +
            "kgKwSccUP88YYyaT6X8CyyE4mAWA5dSpU/tv3rz5G1mWj9ntdmdeXt4Bq9V6eH19/V/JZHJJUZTI\n" +
            "4uLiD36/f/Tx48cJACoJvwG2G28bQXI4K4ACAEWjo6Ofx+Pxb1Kp1BrLYaRSqbV4PP7N6Ojo5wCK\n" +
            "SI+V9Jpf8ymngUoA8gHYARQNDQ19nEgkvme7GIlE4vuhoaGPCdpO+qVswKYcPZoPYN/z58+vHTp0\n" +
            "yK//45MnT/Dw4UOEw2EsLy8jEomgrKwMsizD5XLh9OnTOH78eJqBaDR68/Dhw38E8ArAurA82Ot4\n" +
            "1AbAcfbs2V8tLS2NiN5RVZUFAgFWWlrKdC+ToZSWlrJAIMBUVdV4eWlpaaSuru4dAA6yJ+1kSYig\n" +
            "B1paWt5TFGVKNDAwMMAqKipygtRLRUUFGxgY0AArijLV0tLyHoADOwHmoFYAB0pKSo68fPnyr6Li\n" +
            "rq6u14LUS1dXlwZ4ZWVl/NixY04CtuYCbKb1+QaAg5FI5GtRYXNz856AcmlubtYARyKRrwEcJPv5\n" +
            "xJPRqxYA+wG81dPT81kqldrkivx+/56CcvH7/WJ42+zp6fkMwC+Jw5LJu2ZaL0UAnPF4/G9cSTAY\n" +
            "NDRUVFTEvF5vTlDnz59nsiwbXgsGg+Jy+AGAkzhsRt41AcijQH2wr6/vsvh4PB6PIWgoFGKqqjKf\n" +
            "z7ctaENDA1NVlYVCIUNgj8ejiRJ9fX2XaTkUEJdJ71Ur3Y0rGo1+yycGAoE05bIss1AopAljmYA5\n" +
            "KB+ZgAOBwNZ/otHotwBcxGPVe1eiNVJSWVn5gaqq63yiURwdHh5O25WMgPWgfAwPDxvGYUHXemVl\n" +
            "5QcASohL0i8BB4Dy27dv/4FPmpiYMPSWy+Vi0Wh0W+BMoNFolLlcLkO9ExMTW/+7c+fOFQDlFMq2\n" +
            "loKJXP0mgHfHxsb+xCdcv3494zrcDvjWrVs7BtUvhfHx8T8DeJe4rABMZmEjkADkFxcXl3OXz8zM\n" +
            "ZAzI4XAYtbW1iMVimvOSJKG9vR2SJGnOx2IxnDlzBuFweFudfBQWFpYJWZkkwvKEJc9msxXyCYuL\n" +
            "i9tud5mA9YODbnfzenvEYRETe7MQDSQAFovFYuUT1tbWsiYSHHhhYcHw+sLCQk6genvEIcJqkl4T\n" +
            "APOaMMPhcOSU+VRXV0OWZcNrsiyjuro6Jz2iPeIwC2Fry7NbtdPq6uqqaCjb8Pl8uH//ftoaFddw\n" +
            "f38/mpqasuoS7RGHpgg166pPFolE/sEnVFVV7QpUBL57925WYNEecYjRAmZdBZp69OjRNJ9w4sSJ\n" +
            "HYPGYjGcO3fOMEpkAxbtEYemGhZ3r1IA79vt9gvJZHKDxzujJNvn8xnG0RcvXjC32501Djc1NRkm\n" +
            "5Xwkk8kNu91+AcD7xLUfgGQWvKoCSCqK8ioUCo3xO2xvb0/zgNfrTfPowsICamtrt9767eKw1+tN\n" +
            "0ynaCYVCY4qivAKQ1NdlfAcrph3jo9bW1muiN2pqatI80dvba+jRbDtdf38/kyRJ85+amhqN91tb\n" +
            "W68B+Ih4ivkOJuYGbwA4AuBDAHWzs7M/ClufIUhvb++2oHpgI1AAbHx8fAt0dnb2RwB1xHGEuDRp\n" +
            "okT1+9sAfg3g0/r6+i9VYWF2dnZmrFpzrW6NQDs7O8X1rNbX138J4FPieJu4JH3yzfPZowBOAvhi\n" +
            "cHBQkwu2tbXtaUnT1tamefyDg4PDAL4g+0eFfNZkVH8VUA5ZBeATh8PROjU1NSMq7Ojo2BPQjo4O\n" +
            "DejU1NSMzWa7DOATsl9CPIZ1GK8WCqkG+hDABVmWr8zNzc2LioPBoGGpk4t4PB5NzcUYY3Nzc/Oy\n" +
            "LF8BcIHsOonDmqnC5d61A3gLgBvAbwH4nE5n5/T0dFgfM7u7u7O+XFzcbjfr7u5Oi7vT09Nhp9PZ\n" +
            "CcBH9txk3673qsnAu5puIQCZJsv37t37fX19/Wmz2ayZ9+zZM4yMjGBmZgYbGxuYnJxEVVUV8vPz\n" +
            "4Xa74fV6UVFRoTGUSqXYwMDAw4sXL/4FwCKAf9NxGcDPut5X1o6MA8Aheitr6c7bGhsbv5qcnJzd\n" +
            "TRdxcnJytrGx8SsAbaS3luyUkl3Djoxpm+5hHtXtBaSgiKQQgKOhocF99erV33k8nqN2u92WLaNS\n" +
            "FOXV06dPZ2/cuPHdgwcPZgC8BBAnTy4DWAGQoI5i0qibaMrS7syju7RTcHZQAeeg3wUA9l26dOmd\n" +
            "kydPljmdzjctFoulvLxcnp+fX1RVVY1EIv8JBoP/7O3t/TuANXrEPxHsCh1/AqDQo09manvuqD9L\n" +
            "CcUvSApI7HQtj0QM4JtkPEmgCsH+DGCVJEHXNrL1Z7O1FU1iyUNethEgh9xH5/IFWP4BhMNu0ONd\n" +
            "E6AVXRN5M1sjOZemrUkHnUdg+QRvFUAturi4lc0R8DrJBklSB8n2+muNWSjdOaBFADULnk0JwKoA\n" +
            "vrkTyN1+B4MAphf9R7tUBtnxB7zdfs75v35h/O8A3ZZHRXtYwqsAAAAASUVORK5CYII=";

    public static final String CLOSE_BUTTON_XLARGE =
            "iVBORw0KGgoAAAANSUhEUgAAADoAAAA7CAYAAAAq55mNAAAACXBIWXMAAAsTAAALEwEAmpwYAAAK\n" +
            "T2lDQ1BQaG90b3Nob3AgSUNDIHByb2ZpbGUAAHjanVNnVFPpFj333vRCS4iAlEtvUhUIIFJCi4AU\n" +
            "kSYqIQkQSoghodkVUcERRUUEG8igiAOOjoCMFVEsDIoK2AfkIaKOg6OIisr74Xuja9a89+bN/rXX\n" +
            "Pues852zzwfACAyWSDNRNYAMqUIeEeCDx8TG4eQuQIEKJHAAEAizZCFz/SMBAPh+PDwrIsAHvgAB\n" +
            "eNMLCADATZvAMByH/w/qQplcAYCEAcB0kThLCIAUAEB6jkKmAEBGAYCdmCZTAKAEAGDLY2LjAFAt\n" +
            "AGAnf+bTAICd+Jl7AQBblCEVAaCRACATZYhEAGg7AKzPVopFAFgwABRmS8Q5ANgtADBJV2ZIALC3\n" +
            "AMDOEAuyAAgMADBRiIUpAAR7AGDIIyN4AISZABRG8lc88SuuEOcqAAB4mbI8uSQ5RYFbCC1xB1dX\n" +
            "Lh4ozkkXKxQ2YQJhmkAuwnmZGTKBNA/g88wAAKCRFRHgg/P9eM4Ors7ONo62Dl8t6r8G/yJiYuP+\n" +
            "5c+rcEAAAOF0ftH+LC+zGoA7BoBt/qIl7gRoXgugdfeLZrIPQLUAoOnaV/Nw+H48PEWhkLnZ2eXk\n" +
            "5NhKxEJbYcpXff5nwl/AV/1s+X48/Pf14L7iJIEyXYFHBPjgwsz0TKUcz5IJhGLc5o9H/LcL//wd\n" +
            "0yLESWK5WCoU41EScY5EmozzMqUiiUKSKcUl0v9k4t8s+wM+3zUAsGo+AXuRLahdYwP2SycQWHTA\n" +
            "4vcAAPK7b8HUKAgDgGiD4c93/+8//UegJQCAZkmScQAAXkQkLlTKsz/HCAAARKCBKrBBG/TBGCzA\n" +
            "BhzBBdzBC/xgNoRCJMTCQhBCCmSAHHJgKayCQiiGzbAdKmAv1EAdNMBRaIaTcA4uwlW4Dj1wD/ph\n" +
            "CJ7BKLyBCQRByAgTYSHaiAFiilgjjggXmYX4IcFIBBKLJCDJiBRRIkuRNUgxUopUIFVIHfI9cgI5\n" +
            "h1xGupE7yAAygvyGvEcxlIGyUT3UDLVDuag3GoRGogvQZHQxmo8WoJvQcrQaPYw2oefQq2gP2o8+\n" +
            "Q8cwwOgYBzPEbDAuxsNCsTgsCZNjy7EirAyrxhqwVqwDu4n1Y8+xdwQSgUXACTYEd0IgYR5BSFhM\n" +
            "WE7YSKggHCQ0EdoJNwkDhFHCJyKTqEu0JroR+cQYYjIxh1hILCPWEo8TLxB7iEPENyQSiUMyJ7mQ\n" +
            "AkmxpFTSEtJG0m5SI+ksqZs0SBojk8naZGuyBzmULCAryIXkneTD5DPkG+Qh8lsKnWJAcaT4U+Io\n" +
            "UspqShnlEOU05QZlmDJBVaOaUt2ooVQRNY9aQq2htlKvUYeoEzR1mjnNgxZJS6WtopXTGmgXaPdp\n" +
            "r+h0uhHdlR5Ol9BX0svpR+iX6AP0dwwNhhWDx4hnKBmbGAcYZxl3GK+YTKYZ04sZx1QwNzHrmOeZ\n" +
            "D5lvVVgqtip8FZHKCpVKlSaVGyovVKmqpqreqgtV81XLVI+pXlN9rkZVM1PjqQnUlqtVqp1Q61Mb\n" +
            "U2epO6iHqmeob1Q/pH5Z/YkGWcNMw09DpFGgsV/jvMYgC2MZs3gsIWsNq4Z1gTXEJrHN2Xx2KruY\n" +
            "/R27iz2qqaE5QzNKM1ezUvOUZj8H45hx+Jx0TgnnKKeX836K3hTvKeIpG6Y0TLkxZVxrqpaXllir\n" +
            "SKtRq0frvTau7aedpr1Fu1n7gQ5Bx0onXCdHZ4/OBZ3nU9lT3acKpxZNPTr1ri6qa6UbobtEd79u\n" +
            "p+6Ynr5egJ5Mb6feeb3n+hx9L/1U/W36p/VHDFgGswwkBtsMzhg8xTVxbzwdL8fb8VFDXcNAQ6Vh\n" +
            "lWGX4YSRudE8o9VGjUYPjGnGXOMk423GbcajJgYmISZLTepN7ppSTbmmKaY7TDtMx83MzaLN1pk1\n" +
            "mz0x1zLnm+eb15vft2BaeFostqi2uGVJsuRaplnutrxuhVo5WaVYVVpds0atna0l1rutu6cRp7lO\n" +
            "k06rntZnw7Dxtsm2qbcZsOXYBtuutm22fWFnYhdnt8Wuw+6TvZN9un2N/T0HDYfZDqsdWh1+c7Ry\n" +
            "FDpWOt6azpzuP33F9JbpL2dYzxDP2DPjthPLKcRpnVOb00dnF2e5c4PziIuJS4LLLpc+Lpsbxt3I\n" +
            "veRKdPVxXeF60vWdm7Obwu2o26/uNu5p7ofcn8w0nymeWTNz0MPIQ+BR5dE/C5+VMGvfrH5PQ0+B\n" +
            "Z7XnIy9jL5FXrdewt6V3qvdh7xc+9j5yn+M+4zw33jLeWV/MN8C3yLfLT8Nvnl+F30N/I/9k/3r/\n" +
            "0QCngCUBZwOJgUGBWwL7+Hp8Ib+OPzrbZfay2e1BjKC5QRVBj4KtguXBrSFoyOyQrSH355jOkc5p\n" +
            "DoVQfujW0Adh5mGLw34MJ4WHhVeGP45wiFga0TGXNXfR3ENz30T6RJZE3ptnMU85ry1KNSo+qi5q\n" +
            "PNo3ujS6P8YuZlnM1VidWElsSxw5LiquNm5svt/87fOH4p3iC+N7F5gvyF1weaHOwvSFpxapLhIs\n" +
            "OpZATIhOOJTwQRAqqBaMJfITdyWOCnnCHcJnIi/RNtGI2ENcKh5O8kgqTXqS7JG8NXkkxTOlLOW5\n" +
            "hCepkLxMDUzdmzqeFpp2IG0yPTq9MYOSkZBxQqohTZO2Z+pn5mZ2y6xlhbL+xW6Lty8elQfJa7OQ\n" +
            "rAVZLQq2QqboVFoo1yoHsmdlV2a/zYnKOZarnivN7cyzytuQN5zvn//tEsIS4ZK2pYZLVy0dWOa9\n" +
            "rGo5sjxxedsK4xUFK4ZWBqw8uIq2Km3VT6vtV5eufr0mek1rgV7ByoLBtQFr6wtVCuWFfevc1+1d\n" +
            "T1gvWd+1YfqGnRs+FYmKrhTbF5cVf9go3HjlG4dvyr+Z3JS0qavEuWTPZtJm6ebeLZ5bDpaql+aX\n" +
            "Dm4N2dq0Dd9WtO319kXbL5fNKNu7g7ZDuaO/PLi8ZafJzs07P1SkVPRU+lQ27tLdtWHX+G7R7ht7\n" +
            "vPY07NXbW7z3/T7JvttVAVVN1WbVZftJ+7P3P66Jqun4lvttXa1ObXHtxwPSA/0HIw6217nU1R3S\n" +
            "PVRSj9Yr60cOxx++/p3vdy0NNg1VjZzG4iNwRHnk6fcJ3/ceDTradox7rOEH0x92HWcdL2pCmvKa\n" +
            "RptTmvtbYlu6T8w+0dbq3nr8R9sfD5w0PFl5SvNUyWna6YLTk2fyz4ydlZ19fi753GDborZ752PO\n" +
            "32oPb++6EHTh0kX/i+c7vDvOXPK4dPKy2+UTV7hXmq86X23qdOo8/pPTT8e7nLuarrlca7nuer21\n" +
            "e2b36RueN87d9L158Rb/1tWeOT3dvfN6b/fF9/XfFt1+cif9zsu72Xcn7q28T7xf9EDtQdlD3YfV\n" +
            "P1v+3Njv3H9qwHeg89HcR/cGhYPP/pH1jw9DBY+Zj8uGDYbrnjg+OTniP3L96fynQ89kzyaeF/6i\n" +
            "/suuFxYvfvjV69fO0ZjRoZfyl5O/bXyl/erA6xmv28bCxh6+yXgzMV70VvvtwXfcdx3vo98PT+R8\n" +
            "IH8o/2j5sfVT0Kf7kxmTk/8EA5jz/GMzLdsAAAAgY0hSTQAAeiUAAICDAAD5/wAAgOkAAHUwAADq\n" +
            "YAAAOpgAABdvkl/FRgAACt1JREFUeNrMm39MU1kWx7+vLY8WCsWKCitiFpg1jsPqjDMa3FWjMYZg\n" +
            "yPgD44TlVzoJGZzVyZjJ7h8mMwGjrtnEmLiRRIwxQf5QBxCDkkUx2WVggiGiC/gLywanq0aQ1Cpt\n" +
            "X2l795/7yO3lvfa1FDMvuSmvfb3vfDjnnnPPeacC5v8QNF5Hfg1CxDKfwJwLYe5FGEjCAZNfEygP\n" +
            "Iw+dwrnSEWQAicI5AQBCiCK0IGhDMMQRUMcMPft669at3wMw5ObmfiQIgp4KHrDb7UMA/Nu2bfsP\n" +
            "BQxwr/IgMpAa8HyZuwxmACACMAJIBpAKwHru3LmPHQ7HEbfb/TPReLjd7p8dDseRc+fOfQzASudK\n" +
            "pnOL9F4zVkIIwXwxs4B6AAlUCDOANADpdrv9kCRJD8kcD0mSHtrt9kMA0uncZnqvBMZahHnwMTOT\n" +
            "yoCJAJIAWACkDwwMVEmS9ITE+ZAk6cnAwEAVBbbQeyYywJphhSiu0XHaTDh9+nSOzWb7R1JS0vpw\n" +
            "E9y/fx+BQAD37t1DIBAAAOj1eqxZswZ6vR6rV68OK4Db7e47f/78nw8cODAKYJoOdh3P2UOzmhTp\n" +
            "fzQVwKKbN2/unp6eHldZb6SpqYnU1NQQq9VKFLxoyLBaraSmpoY0NTURt9utqN3p6enxrq6uvQAW\n" +
            "URmSqEz6uZqxEmQagCWDg4PfBYNBn5JAx48fJ3l5eRHh1EZeXh45fvy4ImwwGPQNDg5+B2AJlWXO\n" +
            "sGqQmSMjI39TEuLixYukoKAgZkB+FBQUkIsXLyoCP3369O8AMucKqwo5PDz8vdKNjx07FjdAfhw5\n" +
            "ckQRdnh4+Pu5wsohJAGAiU6U0dzcXMKbqyRJxGazzRukPGw2G5EkaZYZNzc3lwDIoDKaqMw6LaDs\n" +
            "RsBIF/2SsrKyT7xe7wv2Rk6nkxQXF887pDyKioqI0+kMgfV6vS/Kyso+oWs2lcps0AIrm2wiDdLp\n" +
            "AJY/e/bsEm86VVVV7w1SHlVVVbNM+NmzZ5cALKeymqns+nCgrMkm0W3Y0vr6+l3BYDDATn7ixImw\n" +
            "AlksFmIymaIGEUWRWCyWsNecOHGCN+FAfX39LgBLqcxJkUyY1WYqNYe8V69e3WYn7u3tjQjZ29tL\n" +
            "urq6ooIVRZF0dHSQ/v7+iLG3t7c3BPbVq1e3AeQxJqyqVV6bCwFk19bWFvPaLC8vjwgpH52dnZpg\n" +
            "ZUj5iARbXl4+y4Rra2uLAWRT2VW1ymszA8AHjx49atSqTR5SKywPqRX2ypUrIdc/evSoEcAHVHZV\n" +
            "rQrUW8lrMxtAvsfjeanFAalBRoJVg9QCyzsmj8czDiCfyi6vVQMPqqMB1wxgMYC8urq6L9iJxsbG\n" +
            "wmqlvb09bCbCw0aCJISQjo6OsNYwOjoacn1dXd0XdK0upiwiW9mQzdZIA+9SAB/29PScYic5c+ZM\n" +
            "xHWmFTYekADImTNnQr7T09NzCsCHlCGNMs2YL2u2C2lMWj06OtrJTlJRUaHJqWiBjQckAFJRURHy\n" +
            "vdHR0U4AqynDQt58BeqhzDQFygXw6cTExAN2kg0bNmgOE5Fg4wEJgGzYsCHkuxMTEw8AfEoZFlGm\n" +
            "BBZUZLzt7wAUeL3eSXaSzMzMqGJiLLDRQAIgWVlZfB48AaCAMsjeV5RBddQVW+T1CWBjIBCYZidJ\n" +
            "TEyMepcTDWxHRwcRRTGqe6SkpPDJuQfARmadWiibTgaVHdEyAB8B2MwLEsveVBRF0tbWNi+Q8lCQ\n" +
            "czNlWMY4JJ2OqwnN1GclSXKx8Wfx4sVRlyj0ej1EUdR8bbRHSkpKyLnf7/cytS22aC7oVJJuwePx\n" +
            "TLEfWK3WqIQwmUxoaWlBYWFhxGsLCwvR0tICk8kU1T0WLFgQcu71eqe4pwMhGwXFCoPH4wnRaHp6\n" +
            "+rxAzgU2Kysr5HxqampSAVJQAyUAyMuXL//Hvrlq1ap5g4wVNj8/P+T8+fPnv6g9qNKpPNkiT548\n" +
            "GWU/WLt2bVwgr1+/jmvXrsUFlpeJykwUYKHqdSsrK7/VutcFQEwmU8QdT3t7OxFFUZM31hJTx8bG\n" +
            "Qr5TWVn5rZrXVYujfwSwy+VyvdaSvUQDGU3oCRd2+OzF5XK9BrCbyj4rjuq455IBAH553L179w6r\n" +
            "+urqatXQYLFYwprr7t274fP5Zt7z+XzYu3dvWDPOyMiA2WxW/IyXhco6zcgf4J61zux1k5m97loA\n" +
            "hSUlJX/RWmEwm82KOSmvSa2binC5KF9hCAaDgZKSkr8CKKSyy3vdZH6vq6d1UaucvQDYCmDf0NDQ\n" +
            "Xa1VBh62ra1N046Hh+3v7w9bJOP/oUNDQ3cB7KMyy9mLlTIZeNBEJh9dCeAPAD4vLS39gddquMq8\n" +
            "XG3QCsnD9vX1hYU8duzYLG2Wlpb+AOBzKvNKJh+dVU7hKwy5NN0pBFDW3d39UzR1XbPZHNPeVRRF\n" +
            "Yjabo6rrdnd3/wSgjMoqp2iKFQalmtEyWn/ZBGBPTk7ON5OTkyFp29u3b8mWLVveW/F6y5Ytsyr1\n" +
            "k5OTkzk5Od8A2ENlzaeyq9aMFKuAAD6Ttbp///5Tfr/fz97I4XC8l8cSRUVFs2Km3+/379+//xSj\n" +
            "zc+0VgFn1XVp4N0EYBeAL+vr639UincHDx6cN8iDBw8qxtiGhoZmAF9S2TZRWSPWdVUr9dRdb6Ne\n" +
            "7avW1tZbKjcm2dnZcQPMzs4mDQ0NipCtra23AHxFZdpGZdRUqVfS6gLGAxcAKALwJwBfNzY2Km6D\n" +
            "nE4nOXz4MMnIyIgZMCMjgxw+fHjWepSPpqamfwL4mspSRGWTPe0CNW3qo+gGm0lmW1tbf9HpdM6C\n" +
            "goIVeiZjNhqN2Lp1KyorK2G1WmE0GjEyMqIpE9mxYwdsNhsaGhpQXFwMo9EY8rnP5/MdPXr08oED\n" +
            "B24DcAF4Q19dAN4B8ADwMbuikI29oKFZysQ0S6XS+JQGwLJ9+/bfnj17dt/y5ct/owbw5s0b2O12\n" +
            "vHv3Do8fPw7pSlmxYgXMZjNyc3PDbiHHxsaeV1dXX+rs7PwvBXTSIYNOcaDBSKD8o30DtXcZNoXC\n" +
            "WmRwURRTT548ubG8vHxzampqcjybm1wu11RjY+O/Dh061O3z+VwMmKzNtwykpKZNNdMVVNpLldZU\n" +
            "MBAIBG/cuOG4cOHCvdTUVCk7O3thcnKycS6A4+Pjzqampn/v2bPnx8uXLz8IBAIs3BvGXKcAeKkm\n" +
            "2U285oYqtmDGatZIF7us3RS6AzHT90wAjFVVVbk7d+5cuW7durzMzMyFWuBevHjx+s6dO0+vXr36\n" +
            "8MKFC3YK4KEw7+h4y2jRTa+RmKxLtblK0NgxZmC6VIwUSAaWIZPpe0amUTEhKysref369elpaWmJ\n" +
            "q1atSjcYDAKt2JHh4eEJp9Mp9fX1TTgcjimaZvkogJfCTDGwMqCH06Q/UgdZpO4NQWHNJjDalTUs\n" +
            "DxPTr5fIdGUaFEqQQSYPlnNIH9WQxAB5OA3KWpxWWJMk1n5dorJO5SRd7suTqEAmKpSsURZUz8U2\n" +
            "pWTfx2nUw/wta3CagdTcC6i1y4pvfNQx2k2gQIncq0g/Y0EFDpRwoNMMrMS9KgFqbniMpndOUAg/\n" +
            "OgYkQWGwZhsONMjB8sPPAPLhg0QjfCwt5jquxdzArGP5b73K+oTKOg1wGg5wGiSxtq3G2vopcOVS\n" +
            "vqee76sPeQ7CCRrkNMv30/OAMfXmvo9fSfBt4UIYB6f6K4l4NB7H61DbNwOx/+5lzoDzARqP+eft\n" +
            "px7/HwDmccW4HdyWsQAAAABJRU5ErkJggg==";

    private static final String CLOSE_BUTTON_LD =
            "iVBORw0KGgoAAAANSUhEUgAAADoAAAA7CAYAAAAq55mNAAAACXBIWXMAAAsTAAALEwEAmpwYAAAK\n" +
            "T2lDQ1BQaG90b3Nob3AgSUNDIHByb2ZpbGUAAHjanVNnVFPpFj333vRCS4iAlEtvUhUIIFJCi4AU\n" +
            "kSYqIQkQSoghodkVUcERRUUEG8igiAOOjoCMFVEsDIoK2AfkIaKOg6OIisr74Xuja9a89+bN/rXX\n" +
            "Pues852zzwfACAyWSDNRNYAMqUIeEeCDx8TG4eQuQIEKJHAAEAizZCFz/SMBAPh+PDwrIsAHvgAB\n" +
            "eNMLCADATZvAMByH/w/qQplcAYCEAcB0kThLCIAUAEB6jkKmAEBGAYCdmCZTAKAEAGDLY2LjAFAt\n" +
            "AGAnf+bTAICd+Jl7AQBblCEVAaCRACATZYhEAGg7AKzPVopFAFgwABRmS8Q5ANgtADBJV2ZIALC3\n" +
            "AMDOEAuyAAgMADBRiIUpAAR7AGDIIyN4AISZABRG8lc88SuuEOcqAAB4mbI8uSQ5RYFbCC1xB1dX\n" +
            "Lh4ozkkXKxQ2YQJhmkAuwnmZGTKBNA/g88wAAKCRFRHgg/P9eM4Ors7ONo62Dl8t6r8G/yJiYuP+\n" +
            "5c+rcEAAAOF0ftH+LC+zGoA7BoBt/qIl7gRoXgugdfeLZrIPQLUAoOnaV/Nw+H48PEWhkLnZ2eXk\n" +
            "5NhKxEJbYcpXff5nwl/AV/1s+X48/Pf14L7iJIEyXYFHBPjgwsz0TKUcz5IJhGLc5o9H/LcL//wd\n" +
            "0yLESWK5WCoU41EScY5EmozzMqUiiUKSKcUl0v9k4t8s+wM+3zUAsGo+AXuRLahdYwP2SycQWHTA\n" +
            "4vcAAPK7b8HUKAgDgGiD4c93/+8//UegJQCAZkmScQAAXkQkLlTKsz/HCAAARKCBKrBBG/TBGCzA\n" +
            "BhzBBdzBC/xgNoRCJMTCQhBCCmSAHHJgKayCQiiGzbAdKmAv1EAdNMBRaIaTcA4uwlW4Dj1wD/ph\n" +
            "CJ7BKLyBCQRByAgTYSHaiAFiilgjjggXmYX4IcFIBBKLJCDJiBRRIkuRNUgxUopUIFVIHfI9cgI5\n" +
            "h1xGupE7yAAygvyGvEcxlIGyUT3UDLVDuag3GoRGogvQZHQxmo8WoJvQcrQaPYw2oefQq2gP2o8+\n" +
            "Q8cwwOgYBzPEbDAuxsNCsTgsCZNjy7EirAyrxhqwVqwDu4n1Y8+xdwQSgUXACTYEd0IgYR5BSFhM\n" +
            "WE7YSKggHCQ0EdoJNwkDhFHCJyKTqEu0JroR+cQYYjIxh1hILCPWEo8TLxB7iEPENyQSiUMyJ7mQ\n" +
            "AkmxpFTSEtJG0m5SI+ksqZs0SBojk8naZGuyBzmULCAryIXkneTD5DPkG+Qh8lsKnWJAcaT4U+Io\n" +
            "UspqShnlEOU05QZlmDJBVaOaUt2ooVQRNY9aQq2htlKvUYeoEzR1mjnNgxZJS6WtopXTGmgXaPdp\n" +
            "r+h0uhHdlR5Ol9BX0svpR+iX6AP0dwwNhhWDx4hnKBmbGAcYZxl3GK+YTKYZ04sZx1QwNzHrmOeZ\n" +
            "D5lvVVgqtip8FZHKCpVKlSaVGyovVKmqpqreqgtV81XLVI+pXlN9rkZVM1PjqQnUlqtVqp1Q61Mb\n" +
            "U2epO6iHqmeob1Q/pH5Z/YkGWcNMw09DpFGgsV/jvMYgC2MZs3gsIWsNq4Z1gTXEJrHN2Xx2KruY\n" +
            "/R27iz2qqaE5QzNKM1ezUvOUZj8H45hx+Jx0TgnnKKeX836K3hTvKeIpG6Y0TLkxZVxrqpaXllir\n" +
            "SKtRq0frvTau7aedpr1Fu1n7gQ5Bx0onXCdHZ4/OBZ3nU9lT3acKpxZNPTr1ri6qa6UbobtEd79u\n" +
            "p+6Ynr5egJ5Mb6feeb3n+hx9L/1U/W36p/VHDFgGswwkBtsMzhg8xTVxbzwdL8fb8VFDXcNAQ6Vh\n" +
            "lWGX4YSRudE8o9VGjUYPjGnGXOMk423GbcajJgYmISZLTepN7ppSTbmmKaY7TDtMx83MzaLN1pk1\n" +
            "mz0x1zLnm+eb15vft2BaeFostqi2uGVJsuRaplnutrxuhVo5WaVYVVpds0atna0l1rutu6cRp7lO\n" +
            "k06rntZnw7Dxtsm2qbcZsOXYBtuutm22fWFnYhdnt8Wuw+6TvZN9un2N/T0HDYfZDqsdWh1+c7Ry\n" +
            "FDpWOt6azpzuP33F9JbpL2dYzxDP2DPjthPLKcRpnVOb00dnF2e5c4PziIuJS4LLLpc+Lpsbxt3I\n" +
            "veRKdPVxXeF60vWdm7Obwu2o26/uNu5p7ofcn8w0nymeWTNz0MPIQ+BR5dE/C5+VMGvfrH5PQ0+B\n" +
            "Z7XnIy9jL5FXrdewt6V3qvdh7xc+9j5yn+M+4zw33jLeWV/MN8C3yLfLT8Nvnl+F30N/I/9k/3r/\n" +
            "0QCngCUBZwOJgUGBWwL7+Hp8Ib+OPzrbZfay2e1BjKC5QRVBj4KtguXBrSFoyOyQrSH355jOkc5p\n" +
            "DoVQfujW0Adh5mGLw34MJ4WHhVeGP45wiFga0TGXNXfR3ENz30T6RJZE3ptnMU85ry1KNSo+qi5q\n" +
            "PNo3ujS6P8YuZlnM1VidWElsSxw5LiquNm5svt/87fOH4p3iC+N7F5gvyF1weaHOwvSFpxapLhIs\n" +
            "OpZATIhOOJTwQRAqqBaMJfITdyWOCnnCHcJnIi/RNtGI2ENcKh5O8kgqTXqS7JG8NXkkxTOlLOW5\n" +
            "hCepkLxMDUzdmzqeFpp2IG0yPTq9MYOSkZBxQqohTZO2Z+pn5mZ2y6xlhbL+xW6Lty8elQfJa7OQ\n" +
            "rAVZLQq2QqboVFoo1yoHsmdlV2a/zYnKOZarnivN7cyzytuQN5zvn//tEsIS4ZK2pYZLVy0dWOa9\n" +
            "rGo5sjxxedsK4xUFK4ZWBqw8uIq2Km3VT6vtV5eufr0mek1rgV7ByoLBtQFr6wtVCuWFfevc1+1d\n" +
            "T1gvWd+1YfqGnRs+FYmKrhTbF5cVf9go3HjlG4dvyr+Z3JS0qavEuWTPZtJm6ebeLZ5bDpaql+aX\n" +
            "Dm4N2dq0Dd9WtO319kXbL5fNKNu7g7ZDuaO/PLi8ZafJzs07P1SkVPRU+lQ27tLdtWHX+G7R7ht7\n" +
            "vPY07NXbW7z3/T7JvttVAVVN1WbVZftJ+7P3P66Jqun4lvttXa1ObXHtxwPSA/0HIw6217nU1R3S\n" +
            "PVRSj9Yr60cOxx++/p3vdy0NNg1VjZzG4iNwRHnk6fcJ3/ceDTradox7rOEH0x92HWcdL2pCmvKa\n" +
            "RptTmvtbYlu6T8w+0dbq3nr8R9sfD5w0PFl5SvNUyWna6YLTk2fyz4ydlZ19fi753GDborZ752PO\n" +
            "32oPb++6EHTh0kX/i+c7vDvOXPK4dPKy2+UTV7hXmq86X23qdOo8/pPTT8e7nLuarrlca7nuer21\n" +
            "e2b36RueN87d9L158Rb/1tWeOT3dvfN6b/fF9/XfFt1+cif9zsu72Xcn7q28T7xf9EDtQdlD3YfV\n" +
            "P1v+3Njv3H9qwHeg89HcR/cGhYPP/pH1jw9DBY+Zj8uGDYbrnjg+OTniP3L96fynQ89kzyaeF/6i\n" +
            "/suuFxYvfvjV69fO0ZjRoZfyl5O/bXyl/erA6xmv28bCxh6+yXgzMV70VvvtwXfcdx3vo98PT+R8\n" +
            "IH8o/2j5sfVT0Kf7kxmTk/8EA5jz/GMzLdsAAAAgY0hSTQAAeiUAAICDAAD5/wAAgOkAAHUwAADq\n" +
            "YAAAOpgAABdvkl/FRgAACt1JREFUeNrMm39MU1kWx7+vLY8WCsWKCitiFpg1jsPqjDMa3FWjMYZg\n" +
            "yPgD44TlVzoJGZzVyZjJ7h8mMwGjrtnEmLiRRIwxQf5QBxCDkkUx2WVggiGiC/gLywanq0aQ1Cpt\n" +
            "X2l795/7yO3lvfa1FDMvuSmvfb3vfDjnnnPPeacC5v8QNF5Hfg1CxDKfwJwLYe5FGEjCAZNfEygP\n" +
            "Iw+dwrnSEWQAicI5AQBCiCK0IGhDMMQRUMcMPft669at3wMw5ObmfiQIgp4KHrDb7UMA/Nu2bfsP\n" +
            "BQxwr/IgMpAa8HyZuwxmACACMAJIBpAKwHru3LmPHQ7HEbfb/TPReLjd7p8dDseRc+fOfQzASudK\n" +
            "pnOL9F4zVkIIwXwxs4B6AAlUCDOANADpdrv9kCRJD8kcD0mSHtrt9kMA0uncZnqvBMZahHnwMTOT\n" +
            "yoCJAJIAWACkDwwMVEmS9ITE+ZAk6cnAwEAVBbbQeyYywJphhSiu0XHaTDh9+nSOzWb7R1JS0vpw\n" +
            "E9y/fx+BQAD37t1DIBAAAOj1eqxZswZ6vR6rV68OK4Db7e47f/78nw8cODAKYJoOdh3P2UOzmhTp\n" +
            "fzQVwKKbN2/unp6eHldZb6SpqYnU1NQQq9VKFLxoyLBaraSmpoY0NTURt9utqN3p6enxrq6uvQAW\n" +
            "URmSqEz6uZqxEmQagCWDg4PfBYNBn5JAx48fJ3l5eRHh1EZeXh45fvy4ImwwGPQNDg5+B2AJlWXO\n" +
            "sGqQmSMjI39TEuLixYukoKAgZkB+FBQUkIsXLyoCP3369O8AMucKqwo5PDz8vdKNjx07FjdAfhw5\n" +
            "ckQRdnh4+Pu5wsohJAGAiU6U0dzcXMKbqyRJxGazzRukPGw2G5EkaZYZNzc3lwDIoDKaqMw6LaDs\n" +
            "RsBIF/2SsrKyT7xe7wv2Rk6nkxQXF887pDyKioqI0+kMgfV6vS/Kyso+oWs2lcps0AIrm2wiDdLp\n" +
            "AJY/e/bsEm86VVVV7w1SHlVVVbNM+NmzZ5cALKeymqns+nCgrMkm0W3Y0vr6+l3BYDDATn7ixImw\n" +
            "AlksFmIymaIGEUWRWCyWsNecOHGCN+FAfX39LgBLqcxJkUyY1WYqNYe8V69e3WYn7u3tjQjZ29tL\n" +
            "urq6ooIVRZF0dHSQ/v7+iLG3t7c3BPbVq1e3AeQxJqyqVV6bCwFk19bWFvPaLC8vjwgpH52dnZpg\n" +
            "ZUj5iARbXl4+y4Rra2uLAWRT2VW1ymszA8AHjx49atSqTR5SKywPqRX2ypUrIdc/evSoEcAHVHZV\n" +
            "rQrUW8lrMxtAvsfjeanFAalBRoJVg9QCyzsmj8czDiCfyi6vVQMPqqMB1wxgMYC8urq6L9iJxsbG\n" +
            "wmqlvb09bCbCw0aCJISQjo6OsNYwOjoacn1dXd0XdK0upiwiW9mQzdZIA+9SAB/29PScYic5c+ZM\n" +
            "xHWmFTYekADImTNnQr7T09NzCsCHlCGNMs2YL2u2C2lMWj06OtrJTlJRUaHJqWiBjQckAFJRURHy\n" +
            "vdHR0U4AqynDQt58BeqhzDQFygXw6cTExAN2kg0bNmgOE5Fg4wEJgGzYsCHkuxMTEw8AfEoZFlGm\n" +
            "BBZUZLzt7wAUeL3eSXaSzMzMqGJiLLDRQAIgWVlZfB48AaCAMsjeV5RBddQVW+T1CWBjIBCYZidJ\n" +
            "TEyMepcTDWxHRwcRRTGqe6SkpPDJuQfARmadWiibTgaVHdEyAB8B2MwLEsveVBRF0tbWNi+Q8lCQ\n" +
            "czNlWMY4JJ2OqwnN1GclSXKx8Wfx4sVRlyj0ej1EUdR8bbRHSkpKyLnf7/cytS22aC7oVJJuwePx\n" +
            "TLEfWK3WqIQwmUxoaWlBYWFhxGsLCwvR0tICk8kU1T0WLFgQcu71eqe4pwMhGwXFCoPH4wnRaHp6\n" +
            "+rxAzgU2Kysr5HxqampSAVJQAyUAyMuXL//Hvrlq1ap5g4wVNj8/P+T8+fPnv6g9qNKpPNkiT548\n" +
            "GWU/WLt2bVwgr1+/jmvXrsUFlpeJykwUYKHqdSsrK7/VutcFQEwmU8QdT3t7OxFFUZM31hJTx8bG\n" +
            "Qr5TWVn5rZrXVYujfwSwy+VyvdaSvUQDGU3oCRd2+OzF5XK9BrCbyj4rjuq455IBAH553L179w6r\n" +
            "+urqatXQYLFYwprr7t274fP5Zt7z+XzYu3dvWDPOyMiA2WxW/IyXhco6zcgf4J61zux1k5m97loA\n" +
            "hSUlJX/RWmEwm82KOSmvSa2binC5KF9hCAaDgZKSkr8CKKSyy3vdZH6vq6d1UaucvQDYCmDf0NDQ\n" +
            "Xa1VBh62ra1N046Hh+3v7w9bJOP/oUNDQ3cB7KMyy9mLlTIZeNBEJh9dCeAPAD4vLS39gddquMq8\n" +
            "XG3QCsnD9vX1hYU8duzYLG2Wlpb+AOBzKvNKJh+dVU7hKwy5NN0pBFDW3d39UzR1XbPZHNPeVRRF\n" +
            "Yjabo6rrdnd3/wSgjMoqp2iKFQalmtEyWn/ZBGBPTk7ON5OTkyFp29u3b8mWLVveW/F6y5Ytsyr1\n" +
            "k5OTkzk5Od8A2ENlzaeyq9aMFKuAAD6Ttbp///5Tfr/fz97I4XC8l8cSRUVFs2Km3+/379+//xSj\n" +
            "zc+0VgFn1XVp4N0EYBeAL+vr639UincHDx6cN8iDBw8qxtiGhoZmAF9S2TZRWSPWdVUr9dRdb6Ne\n" +
            "7avW1tZbKjcm2dnZcQPMzs4mDQ0NipCtra23AHxFZdpGZdRUqVfS6gLGAxcAKALwJwBfNzY2Km6D\n" +
            "nE4nOXz4MMnIyIgZMCMjgxw+fHjWepSPpqamfwL4mspSRGWTPe0CNW3qo+gGm0lmW1tbf9HpdM6C\n" +
            "goIVeiZjNhqN2Lp1KyorK2G1WmE0GjEyMqIpE9mxYwdsNhsaGhpQXFwMo9EY8rnP5/MdPXr08oED\n" +
            "B24DcAF4Q19dAN4B8ADwMbuikI29oKFZysQ0S6XS+JQGwLJ9+/bfnj17dt/y5ct/owbw5s0b2O12\n" +
            "vHv3Do8fPw7pSlmxYgXMZjNyc3PDbiHHxsaeV1dXX+rs7PwvBXTSIYNOcaDBSKD8o30DtXcZNoXC\n" +
            "WmRwURRTT548ubG8vHxzampqcjybm1wu11RjY+O/Dh061O3z+VwMmKzNtwykpKZNNdMVVNpLldZU\n" +
            "MBAIBG/cuOG4cOHCvdTUVCk7O3thcnKycS6A4+Pjzqampn/v2bPnx8uXLz8IBAIs3BvGXKcAeKkm\n" +
            "2U285oYqtmDGatZIF7us3RS6AzHT90wAjFVVVbk7d+5cuW7durzMzMyFWuBevHjx+s6dO0+vXr36\n" +
            "8MKFC3YK4KEw7+h4y2jRTa+RmKxLtblK0NgxZmC6VIwUSAaWIZPpe0amUTEhKysref369elpaWmJ\n" +
            "q1atSjcYDAKt2JHh4eEJp9Mp9fX1TTgcjimaZvkogJfCTDGwMqCH06Q/UgdZpO4NQWHNJjDalTUs\n" +
            "DxPTr5fIdGUaFEqQQSYPlnNIH9WQxAB5OA3KWpxWWJMk1n5dorJO5SRd7suTqEAmKpSsURZUz8U2\n" +
            "pWTfx2nUw/wta3CagdTcC6i1y4pvfNQx2k2gQIncq0g/Y0EFDpRwoNMMrMS9KgFqbniMpndOUAg/\n" +
            "OgYkQWGwZhsONMjB8sPPAPLhg0QjfCwt5jquxdzArGP5b73K+oTKOg1wGg5wGiSxtq3G2vopcOVS\n" +
            "vqee76sPeQ7CCRrkNMv30/OAMfXmvo9fSfBt4UIYB6f6K4l4NB7H61DbNwOx/+5lzoDzARqP+eft\n" +
            "px7/HwDmccW4HdyWsQAAAABJRU5ErkJggg==";
}
