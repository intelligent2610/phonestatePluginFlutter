package com.phonestate.plugin.phone_state;

import android.telecom.Call;
import android.telecom.CallScreeningService;

import androidx.annotation.NonNull;


public class ScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        PhoneStateHandler.phoneNumberCalling = callDetails.getHandle().getSchemeSpecificPart();
    }
}
