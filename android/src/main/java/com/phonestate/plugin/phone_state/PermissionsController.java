package com.phonestate.plugin.phone_state;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Arrays;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class PermissionsController {
    private final Context context;
    boolean isRequestingPermission = false;
    private final List<String> listPermissionNeed = Arrays.asList(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG
    );

    public PermissionsController(Context context) {
        this.context = context;
    }

    public boolean hasRequiredPermissions() {
        boolean hasPermissions = true;
        for (String permission : listPermissionNeed) {
            hasPermissions = hasPermissions && checkPermission(permission);
        }
        return hasPermissions;
    }

    private boolean checkPermission(String permission) {
        return context.checkSelfPermission(permission) == PERMISSION_GRANTED;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    void requestPermissions(Activity activity, int requestCode) {
        if (!isRequestingPermission) {
            isRequestingPermission = true;
            activity.requestPermissions(listPermissionNeed.toArray(new String[0]), requestCode);
        }
    }

    private String[] getListedPermissions() {
        PackageInfo info;
        try {
            info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            return info.requestedPermissions;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return new String[]{};
    }
}
