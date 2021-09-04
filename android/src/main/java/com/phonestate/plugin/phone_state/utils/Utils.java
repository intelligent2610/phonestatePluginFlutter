package com.phonestate.plugin.phone_state.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class Utils {

    public static final int MIN_STEP = 15;
    public static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    public static final SimpleDateFormat FORMAT_MONTH_YEAR = new SimpleDateFormat("MMM yyyy", Locale.US);
    public static final SimpleDateFormat FORMAT_DATE_TIME_APPOINTMENT = new SimpleDateFormat("EEE, MMM d yyyy @ hh:mm aa", Locale.US);

    private static final SimpleDateFormat FORMAT_DATE_TIME = new SimpleDateFormat("EEE, MMM dd, yyyy @ K:mm a", Locale.US);
    public static final SimpleDateFormat FORMAT_TIME = new SimpleDateFormat("hh:mm aa", Locale.US);
    public static final SimpleDateFormat FORMAT_TIME_24 = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US);

    public static final SimpleDateFormat FORMAT_LOG = new SimpleDateFormat("MM dd, yyyy HH:mm", Locale.US);

    private Utils() {
    }

    public static String formatName(String name) {
        try {
            String nameFormat = name.trim();
            if (nameFormat.contains(",")) {
                String[] list = nameFormat.split(",");
                String firstName = list[1].toLowerCase().trim();
                firstName = firstName.substring(0, 1).toUpperCase() + firstName.substring(1);
                String lastName = list[0].toLowerCase().trim();
                lastName = lastName.substring(0, 1).toUpperCase() + lastName.substring(1);
                return firstName + " " + lastName;
            } else if (nameFormat.contains(" ")) {
                String[] list = nameFormat.split(" ");
                String firstName = list[0].toLowerCase().trim();
                firstName = firstName.substring(0, 1).toUpperCase() + firstName.substring(1);
                String lastName = list[1].toLowerCase().trim();
                lastName = lastName.substring(0, 1).toUpperCase() + lastName.substring(1);
                return firstName + " " + lastName;
            } else {
                nameFormat = nameFormat.toLowerCase();
                return nameFormat.substring(0, 1).toUpperCase() + nameFormat.substring(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }

    /**
     * Return the last 10 digit of phone number +14085104196 ==> 4085104196
     */
    public static String formatSimplePhoneNo(String phoneNumber) {
        phoneNumber = phoneNumber.replaceAll("[^0-9]+", Constants.EMPTY_STRING);
        if (phoneNumber.length() > 10) {
            phoneNumber = phoneNumber.substring(phoneNumber.length() - 10);
        }

        return phoneNumber;
    }

    public static String formatPrice(long price, String symbol) {
        float p = price / 100f;
        return symbol + String.format(Locale.US, "%.2f", p);
    }

    public static String formatPhoneForSubmit(String phoneNumber) {
        return "+1" + phoneNumber
                .replaceAll("\\(", "").replaceAll("\\)", "")
                .replaceAll("-", "").replaceAll(" ", "");
    }

    public static String formatUSPhoneNumber(String inputPhoneNumber) {
        if (inputPhoneNumber.length() == 10) {
            return inputPhoneNumber.replaceFirst("(\\d{3})(\\d{3})(\\d+)", "($1) $2-$3");
        } else if (inputPhoneNumber.length() == 11) { // "14085104196"
            inputPhoneNumber = inputPhoneNumber.substring(1);
        } else if (inputPhoneNumber.length() == 12) { // "+14085104196"
            inputPhoneNumber = inputPhoneNumber.substring(2);
        } else {
            return Constants.EMPTY_STRING;
        }

        return inputPhoneNumber.replaceFirst("(\\d{3})(\\d{3})(\\d+)", "($1) $2-$3");
    }
}
