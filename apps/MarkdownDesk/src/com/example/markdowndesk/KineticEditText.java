package com.example.markdowndesk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.EditText;
import android.widget.OverScroller;

/* JADX INFO: loaded from: classes.dex */
public class KineticEditText extends EditText {
    private int activeLineColor;
    private int baseLeftPadding;
    private int chromeColor;
    private boolean chromeConfigured;
    private final Paint chromePaint;
    private boolean currentLine;
    private final GestureDetector gestures;
    private final OverScroller kineticScroller;
    private boolean lineNumbers;
    private final ScaleGestureDetector scaleGestures;
    private OnTextScaleListener scaleListener;
    private boolean scaling;
    private boolean typewriter;
    private LogicalLineIndex logicalLineStarts;

    interface HistoryActionListener {boolean onHistoryAction(int actionId);}
    private HistoryActionListener historyActionListener;

    void setHistoryActionListener(HistoryActionListener listener) {historyActionListener=listener;}

    boolean performNativeHistoryAction(int actionId) {
        if(actionId!=android.R.id.undo&&actionId!=android.R.id.redo)return false;
        return super.onTextContextMenuItem(actionId);
    }

    @Override
    public boolean onTextContextMenuItem(int actionId) {
        if((actionId==android.R.id.undo||actionId==android.R.id.redo)&&historyActionListener!=null)
            return historyActionListener.onHistoryAction(actionId);
        return super.onTextContextMenuItem(actionId);
    }

    public interface OnTextScaleListener {
        void onTextScale(float f);
    }

    public KineticEditText(Context context) {
        this(context, null);
    }

    public KineticEditText(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, android.R.attr.editTextStyle);
    }

    public KineticEditText(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        if (this.logicalLineStarts == null) this.logicalLineStarts = new LogicalLineIndex();
        this.chromePaint = new Paint(1);
        this.currentLine = true;
        this.chromeColor = 1718906212;
        this.activeLineColor = 264789042;
        this.kineticScroller = new OverScroller(context);
        this.gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() { // from class: com.example.markdowndesk.KineticEditText.1
            @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnGestureListener
            public boolean onDown(MotionEvent motionEvent) {
                return true;
            }

            @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnGestureListener
            public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent2, float f, float f2) {
                if (KineticEditText.this.getLayout() == null || Math.abs(f2) < 350.0f) {
                    return false;
                }
                KineticEditText.this.kineticScroller.fling(0, KineticEditText.this.getScrollY(), 0, Math.round((-f2) * 0.72f), 0, 0, 0, Math.max(0, KineticEditText.this.getLayout().getHeight() - Math.max(1, (KineticEditText.this.getHeight() - KineticEditText.this.getTotalPaddingTop()) - KineticEditText.this.getTotalPaddingBottom())));
                KineticEditText.this.postInvalidateOnAnimation();
                return false;
            }
        });
        this.scaleGestures = new ScaleGestureDetector(context, new AnonymousClass2());
        setOverScrollMode(1);
        setScrollbarFadingEnabled(true);
        this.baseLeftPadding = getPaddingLeft();
        this.chromePaint.setTypeface(Typeface.MONOSPACE);
    }

    /* JADX INFO: renamed from: com.example.markdowndesk.KineticEditText$2, reason: invalid class name */
    class AnonymousClass2 extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        AnonymousClass2() {
        }

        @Override // android.view.ScaleGestureDetector.SimpleOnScaleGestureListener, android.view.ScaleGestureDetector.OnScaleGestureListener
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            KineticEditText.this.scaling = true;
            return true;
        }

        @Override // android.view.ScaleGestureDetector.SimpleOnScaleGestureListener, android.view.ScaleGestureDetector.OnScaleGestureListener
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            float textSize = KineticEditText.this.getTextSize() / Math.max(0.1f, KineticEditText.this.getResources().getDisplayMetrics().scaledDensity);
            float fMax = Math.max(12.0f, Math.min(32.0f, scaleGestureDetector.getScaleFactor() * textSize));
            if (Math.abs(fMax - textSize) < 0.08f) {
                return true;
            }
            KineticEditText.this.setTextSize(2, fMax);
            if (KineticEditText.this.scaleListener != null) {
                KineticEditText.this.scaleListener.onTextScale(fMax);
            }
            return true;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onScaleEnd$0() {
            KineticEditText.this.scaling = false;
        }

        @Override // android.view.ScaleGestureDetector.SimpleOnScaleGestureListener, android.view.ScaleGestureDetector.OnScaleGestureListener
        public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
            KineticEditText.this.postDelayed(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    KineticEditText.this.scaling = false;
                }
            }, 40L);
        }
    }

    public void setOnTextScaleListener(OnTextScaleListener onTextScaleListener) {
        this.scaleListener = onTextScaleListener;
    }

    public void setEditorChrome(boolean z, boolean z2, int i, int i2) {
        if (!this.chromeConfigured) {
            this.baseLeftPadding = getPaddingLeft();
            this.chromeConfigured = true;
        }
        this.lineNumbers = z;
        this.currentLine = z2;
        this.chromeColor = i;
        this.activeLineColor = i2;
        setPadding(this.baseLeftPadding + (this.lineNumbers ? Math.round(getResources().getDisplayMetrics().density * 48.0f) : 0), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        invalidate();
    }

    public void setTypewriterMode(boolean z) {
        this.typewriter = z;
        if (z) {
            centerSelection();
        }
    }


    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int count) {
        super.onTextChanged(text, start, before, count);
        // TextView can invoke this callback from its constructor, before our fields initialize.
        if (logicalLineStarts == null) logicalLineStarts = new LogicalLineIndex();
        logicalLineStarts.changed(text, start, before, count);
    }

    private int logicalLineForOffset(int offset) {
        return logicalLineStarts == null ? 0 : logicalLineStarts.lineForOffset(offset);
    }

    @Override // android.widget.TextView
    protected void onSelectionChanged(int i, int i2) {
        super.onSelectionChanged(i, i2);
        if (this.currentLine) {
            invalidate();
        }
        if (!this.typewriter || !isFocused()) {
            return;
        }
        post(new Runnable() { // from class: com.example.markdowndesk.KineticEditText$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                KineticEditText.this.centerSelection();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void centerSelection() {
        if (!this.typewriter || getLayout() == null || getHeight() <= 0) {
            return;
        }
        scrollTo(getScrollX(), Math.max(0, (getLayout().getLineTop(getLayout().getLineForOffset(Math.max(0, Math.min(length(), getSelectionEnd())))) - (getHeight() / 2)) + getLineHeight()));
    }

    @Override // android.widget.TextView, android.view.View
    protected void onDraw(Canvas canvas) {
        if (getLayout() != null && this.currentLine && isFocused()) {
            int lineForOffset = getLayout().getLineForOffset(Math.max(0, Math.min(length(), getSelectionEnd())));
            int lineTop = (getLayout().getLineTop(lineForOffset) + getTotalPaddingTop()) - getScrollY();
            int lineBottom = (getLayout().getLineBottom(lineForOffset) + getTotalPaddingTop()) - getScrollY();
            this.chromePaint.setColor(this.activeLineColor);
            canvas.drawRect(0.0f, lineTop, getWidth(), lineBottom, this.chromePaint);
        }
        super.onDraw(canvas);
        if (!this.lineNumbers || getLayout() == null) {
            return;
        }
        this.chromePaint.setColor(this.chromeColor);
        this.chromePaint.setTextSize(getTextSize() * 0.62f);
        this.chromePaint.setTextAlign(Paint.Align.RIGHT);
        int iMax = Math.max(0, getLayout().getLineForVertical(getScrollY()));
        int iMin = Math.min(getLayout().getLineCount() - 1, getLayout().getLineForVertical(getScrollY() + getHeight()));
        float fRound = this.baseLeftPadding + Math.round(getResources().getDisplayMetrics().density * 35.0f);
        while (iMax <= iMin) {
            int visualLine = iMax++;
            int offset = getLayout().getLineStart(visualLine);
            int logical = logicalLineForOffset(offset);
            if (logical >= logicalLineStarts.size() || logicalLineStarts.start(logical) != offset) continue;
            float lineBaseline = (getLayout().getLineBaseline(visualLine) + getTotalPaddingTop()) - getScrollY();
            canvas.drawText(Integer.toString(logical + 1), fRound, lineBaseline, this.chromePaint);
        }
    }

    @Override // android.view.View
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override // android.widget.TextView, android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getActionMasked() == 0 && !this.kineticScroller.isFinished()) {
            this.kineticScroller.forceFinished(true);
        }
        this.scaleGestures.onTouchEvent(motionEvent);
        if (!this.scaleGestures.isInProgress()) {
            this.gestures.onTouchEvent(motionEvent);
        }
        if (!this.scaling && motionEvent.getActionMasked() == 1) {
            performClick();
        }
        return super.onTouchEvent(motionEvent);
    }

    @Override // android.widget.TextView, android.view.View
    public void computeScroll() {
        super.computeScroll();
        if (this.kineticScroller.computeScrollOffset()) {
            scrollTo(getScrollX(), this.kineticScroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }
}
