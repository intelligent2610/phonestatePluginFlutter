package com.phonestate.plugin.phone_state;

import android.content.Context;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import static com.phonestate.plugin.phone_state.utils.Constants.CHANNEL_PHONE_FOREGROUND;

/**
 * PhoneStatePlugin
 */
public class PhoneStatePlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

    private PhoneStateMethodCallHandler phoneStateMethodCallHandler;

    private BinaryMessenger binaryMessenger;

    private PermissionsController permissionsController;

    private MethodChannel phoneChannel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        if (binaryMessenger == null) {
            binaryMessenger = flutterPluginBinding.getBinaryMessenger();
        }

        setupPlugin(flutterPluginBinding.getApplicationContext(), binaryMessenger);
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else {
            result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        tearDownPlugin();
    }

    @Override
    public void onAttachedToActivity(@NonNull @NotNull ActivityPluginBinding binding) {
        PhoneStateHandler.foregroundSmsChannel = phoneChannel;
        phoneStateMethodCallHandler.setActivity(binding.getActivity());
        binding.addRequestPermissionsResultListener(phoneStateMethodCallHandler);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull @NotNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        tearDownPlugin();
    }

    private void setupPlugin(Context context, BinaryMessenger messenger) {
        permissionsController = new PermissionsController(context);
        phoneStateMethodCallHandler = new PhoneStateMethodCallHandler(context, permissionsController);

        phoneChannel = new MethodChannel(messenger, CHANNEL_PHONE_FOREGROUND);
        phoneChannel.setMethodCallHandler(phoneStateMethodCallHandler);
        phoneStateMethodCallHandler.setForegroundChannel(phoneChannel);
    }

    private void tearDownPlugin() {
        PhoneStateHandler.foregroundSmsChannel = null;
        phoneChannel.setMethodCallHandler(null);
    }

}
