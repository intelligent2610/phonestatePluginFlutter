package com.phonestate.plugin.phone_state;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.phonestate.plugin.phone_state.ActionType;
import com.phonestate.plugin.phone_state.PhoneAction;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

import static android.content.Context.ROLE_SERVICE;
import static com.phonestate.plugin.phone_state.utils.Constants.ALL_REQUEST_CODE;
import static com.phonestate.plugin.phone_state.utils.Constants.BACKGROUND_HANDLE;
import static com.phonestate.plugin.phone_state.utils.Constants.FAILED_FETCH;
import static com.phonestate.plugin.phone_state.utils.Constants.ILLEGAL_ARGUMENT;
import static com.phonestate.plugin.phone_state.utils.Constants.PERMISSION_DENIED;
import static com.phonestate.plugin.phone_state.utils.Constants.PERMISSION_DENIED_MESSAGE;
import static com.phonestate.plugin.phone_state.utils.Constants.REQUEST_ROLE_CALL_SCREEN;
import static com.phonestate.plugin.phone_state.utils.Constants.SETUP_HANDLE;
import static com.phonestate.plugin.phone_state.utils.Constants.WRONG_METHOD_TYPE;

public class PhoneStateMethodCallHandler extends
        BroadcastReceiver implements PluginRegistry.RequestPermissionsResultListener,
        MethodChannel.MethodCallHandler, PluginRegistry.ActivityResultListener {
    private final Context context;
    private final PermissionsController permissionsController;

    private MethodChannel.Result result;
    private MethodChannel foregroundChannel;
    private Activity activity;

    private PhoneAction action;

    private String selection;
    private List<String> selectionArgs;

    private Long setupHandle = -1L;
    private Long backgroundHandle = -1L;

    private String phoneNumber;


    public PhoneStateMethodCallHandler(Context context, PermissionsController permissionsController) {
        this.context = context;
        this.permissionsController = permissionsController;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {

        }
    }

    @Override
    public void onMethodCall(@NonNull @NotNull MethodCall call, @NonNull @NotNull MethodChannel.Result result) {
        this.result = result;
        action = PhoneAction.fromMethod(call.method);
        if (call.method.isEmpty()) {
            return;
        }
        if (action.toActionType() == ActionType.BACKGROUND) {
            if (call.hasArgument(SETUP_HANDLE)
                    && call.hasArgument(BACKGROUND_HANDLE)) {
                HashMap<String, Object> objectHashMap = (HashMap<String, Object>) call.arguments;
                final Long setupHandle = (Long) objectHashMap.get(SETUP_HANDLE);
                final Long backgroundHandle = (Long) objectHashMap.get(BACKGROUND_HANDLE);
                if (setupHandle == null || backgroundHandle == null) {
                    result.error(ILLEGAL_ARGUMENT, "Setup handle or background handle missing", null);
                    return;
                }

                this.setupHandle = setupHandle;
                this.backgroundHandle = backgroundHandle;
            }
            handleMethod(action);
        } else if (action.toActionType() == ActionType.REQUEST_ROLL) {
            requestRole();
        } else if (action.toActionType() == ActionType.PERMISSION) {
            handleMethod(action);
        }

    }

    /**
     * Request default App Caller-Id and Spam
     */
    private void requestRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) context.getSystemService(ROLE_SERVICE);
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                activity.startActivityIfNeeded(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_ROLE_CALL_SCREEN, null);
            } else {
                execute(action);
            }
        } else {
            execute(action);
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private boolean checkOrRequestPermission(int requestCode) {
        if (activity == null) {
            return permissionsController.hasRequiredPermissions();
        }

        if (!permissionsController.hasRequiredPermissions()) {
            permissionsController.requestPermissions(activity, requestCode);
            return false;
        }
        return true;
    }


    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    /**
     * Calls the [execute] method after checking if the necessary permissions are granted.
     * <p>
     * If not granted then it will request the permission from the user.
     */
    private void handleMethod(PhoneAction phoneAction) {
        if (checkOrRequestPermission(ALL_REQUEST_CODE)) {
            execute(phoneAction);
        }
    }

    private void execute(PhoneAction action) {
        try {
            switch (action.toActionType()) {
                case BACKGROUND:
                    handleBackgroundActions(action);
                    break;
                case REQUEST_ROLL:
                case PERMISSION:
                    result.success(true);
                    break;
//                case CALL:
//                    handleCallActions(action);
//                    break;
            }

        } catch (IllegalArgumentException e) {
            result.error(ILLEGAL_ARGUMENT, WRONG_METHOD_TYPE, null);
        } catch (RuntimeException e) {
            result.error(FAILED_FETCH, e.getMessage(), null);
        }
    }

    private void handleBackgroundActions(PhoneAction action) {
        if (action == PhoneAction.START_BACKGROUND_SERVICE) {
            PhoneStateHandler.IncomingPhoneHandler.getInstance().setBackgroundSetupHandle(context, setupHandle);
            PhoneStateHandler.IncomingPhoneHandler.getInstance().setBackgroundMessageHandle(context, backgroundHandle);
        } else if (action == PhoneAction.BACKGROUND_SERVICE_INITIALIZED) {
            PhoneStateHandler.IncomingPhoneHandler.getInstance().onChannelInitialized();
        } else {
            throw new IllegalArgumentException();
        }

    }

    public void setForegroundChannel(MethodChannel channel) {
        foregroundChannel = channel;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        permissionsController.isRequestingPermission = false;

        List<String> deniedPermissions = new ArrayList<>();
        if (requestCode != ALL_REQUEST_CODE && action == null && requestCode != REQUEST_ROLE_CALL_SCREEN) {
            return false;
        }
        boolean allPermissionGranted = true;
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permissions[i]);
            }
            allPermissionGranted &= grantResults[i] == PackageManager.PERMISSION_GRANTED;
        }


        if (allPermissionGranted) {
            execute(action);
            return true;
        } else {
            onPermissionDenied(deniedPermissions);
            return false;
        }
    }

    private void onPermissionDenied(List<String> deniedPermissions) {
        result.error(PERMISSION_DENIED, PERMISSION_DENIED_MESSAGE, deniedPermissions);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_ROLE_CALL_SCREEN == requestCode) {
            RoleManager roleManager = (RoleManager) context.getSystemService(ROLE_SERVICE);
            if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                execute(action);
            }
        }
        return false;
    }
}
