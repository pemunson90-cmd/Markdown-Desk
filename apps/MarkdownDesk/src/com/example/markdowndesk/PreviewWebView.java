package com.example.markdowndesk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.webkit.WebView;

public final class PreviewWebView extends WebView {
    public interface OnTextScaleListener { void onTextScale(float sp); }
    private final ScaleGestureDetector scaleDetector;
    private OnTextScaleListener scaleListener;
    private float textSp = 18f;
    private float pendingSp = 18f;

    public PreviewWebView(Context context) { this(context, null); }
    public PreviewWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) { pendingSp = textSp; return true; }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                pendingSp = Math.max(12f, Math.min(32f, pendingSp * detector.getScaleFactor()));
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                textSp = pendingSp;
                if (scaleListener != null) scaleListener.onTextScale(textSp);
            }
        });
    }

    public void setTextSp(float sp) { textSp = pendingSp = Math.max(12f, Math.min(32f, sp)); }
    public void setOnTextScaleListener(OnTextScaleListener listener) { scaleListener = listener; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (scaleDetector.isInProgress()) return true;
        return super.onTouchEvent(event);
    }
}
