package com.schlaikjer.music.utility;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

public class ThreadManager {

    private static volatile Handler uiThreadHandler;
    private static volatile Handler bgThreadHandler;

    public static void runOnUIThread(Runnable runnable) {
        if (isOnUIThread()) {
            runnable.run();
            return;
        }
        getUIHandler().post(runnable);
    }

    public static void runOnBgThread(Runnable runnable) {
        getBgThreadHandler().post(runnable);
    }

    public static boolean isOnUIThread() {
        return Looper.getMainLooper().equals(Looper.myLooper());
    }

    private static Handler getUIHandler() {
        if (uiThreadHandler == null) {
            synchronized (ThreadManager.class) {
                if (uiThreadHandler == null) {
                    uiThreadHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        return uiThreadHandler;
    }

    private static Handler getBgThreadHandler() {
        if (bgThreadHandler == null) {
            synchronized (ThreadManager.class) {
                if (bgThreadHandler == null) {
                    HandlerThread bgHandlerThread = new HandlerThread("ThreadManager");
                    bgHandlerThread.start();
                    bgThreadHandler = new Handler(bgHandlerThread.getLooper());
                }
            }
        }
        return bgThreadHandler;
    }

}
