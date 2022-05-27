package com.mxplay.adloader.nativeCompanion.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TableItemWrapperLayout extends FrameLayout {

    public interface OnWindowAttachListener{
        default void onAttachedToWindow(){}
        default void onDetachedFromWindow(){}
    }

    public TableItemWrapperLayout(Context context) {
        super(context);
    }

    public TableItemWrapperLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TableItemWrapperLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TableItemWrapperLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }



    private final List<OnWindowAttachListener> listeners = new LinkedList<>();
    private final List<OnWindowAttachListener> listenersTmp = new LinkedList<>();

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        for (OnWindowAttachListener listener : listeners()) {
            listener.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        for (OnWindowAttachListener listener : listeners()) {
            listener.onDetachedFromWindow();
        }
    }



    public void addAttachedListener(OnWindowAttachListener listener) {
        listeners.add(listener);
    }


    public void removeAttachedListener(OnWindowAttachListener listener) {
        listeners.remove(listener);
    }

    private List<OnWindowAttachListener> listeners() {
        if (listeners.isEmpty()) {
            return Collections.emptyList();
        }

        listenersTmp.clear();
        listenersTmp.addAll(listeners);
        return listenersTmp;
    }



}
