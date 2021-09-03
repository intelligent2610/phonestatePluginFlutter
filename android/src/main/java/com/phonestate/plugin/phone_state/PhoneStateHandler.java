package com.phonestate.plugin.phone_state;


import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;


import com.phonestate.plugin.phone_state.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterJNI;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.FlutterCallbackInformation;

import static com.phonestate.plugin.phone_state.utils.Constants.HANDLE;
import static com.phonestate.plugin.phone_state.utils.Constants.HANDLE_BACKGROUND_PHONE_STATE;
import static com.phonestate.plugin.phone_state.utils.Constants.ON_PHONE_OFF_HOOK;
import static com.phonestate.plugin.phone_state.utils.Constants.PHONE_NUMBER;
import static com.phonestate.plugin.phone_state.utils.Constants.SHARED_PREFERENCES_NAME;
import static com.phonestate.plugin.phone_state.utils.Constants.SHARED_PREFS_BACKGROUND_PHONE_HANDLE;
import static com.phonestate.plugin.phone_state.utils.Constants.SHARED_PREFS_BACKGROUND_SETUP_HANDLE;

public class PhoneStateHandler extends BroadcastReceiver {

    public static MethodChannel foregroundSmsChannel = null;
    public static String phoneNumberCalling = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) {
                phoneNumberCalling = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            }
        }
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equalsIgnoreCase(state)) {
            if (TextUtils.isEmpty(phoneNumberCalling)) {
                return;
            }
            String phoneNumber = "+1" + phoneNumberCalling.replaceAll("[^0-9]+", Constants.EMPTY_STRING);
            processOffHookPhoneNumber(context, phoneNumber);
        }
    }

    private void processOffHookPhoneNumber(Context context, String phoneNumberCalling) {
        if (IncomingPhoneHandler.isApplicationForeground(context) && foregroundSmsChannel != null) {
            foregroundSmsChannel.invokeMethod(ON_PHONE_OFF_HOOK, phoneNumberCalling);
        } else {
            processInBackground(context, phoneNumberCalling);
        }
    }

    private void processInBackground(Context context, String phoneNumberCalling) {
        IncomingPhoneHandler incomingPhoneHandler = IncomingPhoneHandler.getInstance();
        if (!incomingPhoneHandler.isIsolateRunning.get()) {
            incomingPhoneHandler.initialize(context);
            SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
            Long backgroundCallbackHandle = preferences.getLong(SHARED_PREFS_BACKGROUND_SETUP_HANDLE, 0);
            incomingPhoneHandler.startBackgroundIsolate(context, backgroundCallbackHandle);
            incomingPhoneHandler.backgroundMessageQueue.add(phoneNumberCalling);
        } else {
            incomingPhoneHandler.executeDartCallbackInBackgroundIsolate(context, phoneNumberCalling);
        }

    }


    public static class IncomingPhoneHandler implements MethodChannel.MethodCallHandler {

        private static IncomingPhoneHandler instance;

        public static IncomingPhoneHandler getInstance() {
            if (instance == null) {
                instance = new IncomingPhoneHandler();
            }
            return instance;
        }

        public AtomicBoolean isIsolateRunning = new AtomicBoolean(false);

        private List<String> backgroundMessageQueue = new ArrayList<>();
        Context backgroundContext;
        private MethodChannel backgroundChannel;
        private FlutterEngine backgroundFlutterEngine;
        private FlutterLoader flutterLoader;

        private Long backgroundMessageHandle;

        private void initialize(Context context) {
            FlutterInjector flutterInjector = FlutterInjector.instance();
            backgroundContext = context;
            flutterLoader = flutterInjector.flutterLoader();
            flutterLoader.startInitialization(backgroundContext);
            flutterLoader.ensureInitializationComplete(context.getApplicationContext(), null);
        }

        public void startBackgroundIsolate(Context context, Long callbackHandle) {
            String appBundlePath = flutterLoader.findAppBundlePath();
            FlutterCallbackInformation flutterCallback = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);

            DartExecutor.DartCallback dartEntryPoint = new DartExecutor.DartCallback(context.getAssets(), appBundlePath, flutterCallback);

            backgroundFlutterEngine = new FlutterEngine(context, flutterLoader, new FlutterJNI());
            backgroundFlutterEngine.getDartExecutor().executeDartCallback(dartEntryPoint);

            backgroundChannel = new MethodChannel(backgroundFlutterEngine.getDartExecutor(), Constants.CHANNEL_PHONE_BACKGROUND);
            backgroundChannel.setMethodCallHandler(this);
        }

        public void onChannelInitialized() {
            isIsolateRunning.set(true);
            synchronized (backgroundMessageQueue) {

                backgroundMessageQueue.forEach(v ->
                        executeDartCallbackInBackgroundIsolate(backgroundContext, v));

                backgroundMessageQueue.clear();
            }
        }

        /**
         * Invoke the method on background channel to handle the message
         */
        private void executeDartCallbackInBackgroundIsolate(Context context, String phoneNumber) {
            if (backgroundChannel == null) {
                throw new RuntimeException(
                        "setBackgroundChannel was not called before phone call came in, exiting.");
            }
            final HashMap args = new HashMap();
            if (backgroundMessageHandle == null) {
                backgroundMessageHandle = getBackgroundMessageHandle(context);
            }
            args.put(HANDLE, backgroundMessageHandle);
            args.put(PHONE_NUMBER, phoneNumber);
            backgroundChannel.invokeMethod(HANDLE_BACKGROUND_PHONE_STATE, args);
        }

        public void setBackgroundSetupHandle(Context context, Long setupBackgroundHandle) {
            // Store background setup handle in shared preferences so it can be retrieved
            // by other application instances.
            SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
            preferences.edit().putLong(SHARED_PREFS_BACKGROUND_SETUP_HANDLE, setupBackgroundHandle).apply();
        }

        public void setBackgroundMessageHandle(Context context, Long handle) {
            backgroundMessageHandle = handle;

            // Store background message handle in shared preferences so it can be retrieved
            // by other application instances.
            SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
            preferences.edit().putLong(SHARED_PREFS_BACKGROUND_PHONE_HANDLE, handle).apply();

        }

        private Long getBackgroundMessageHandle(Context context) {
            return context
                    .getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .getLong(SHARED_PREFS_BACKGROUND_PHONE_HANDLE, 0);
        }

        @Override
        public void onMethodCall(@NonNull @org.jetbrains.annotations.NotNull MethodCall call,
                                 @NonNull @org.jetbrains.annotations.NotNull MethodChannel.Result result) {
            if (PhoneAction.fromMethod(call.method) == PhoneAction.BACKGROUND_SERVICE_INITIALIZED) {
                onChannelInitialized();
            }
        }

        public static boolean isApplicationForeground(Context context) {
            final KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager.isKeyguardLocked()) {
                return false;
            }
            final int myPid = Process.myPid();
            final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> list = activityManager.getRunningAppProcesses();
            if (list != null) {
                for (ActivityManager.RunningAppProcessInfo alist : list) {
                    if (alist.pid == myPid) {
                        return alist.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                    }
                }
            }
            return false;
        }
    }
}


