package com.phonestate.plugin.phone_state.contact;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.phonestate.plugin.phone_state.R;
import com.phonestate.plugin.phone_state.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ContactHelper {
    private static final String LOG_TAG = ContactHelper.class.getSimpleName();
    private static byte[] imageStream;

    public static void insertPhoneNumberToContact(Context context, String phoneNumber, String groupName,
                                                  String customerName, String lastName) {
        long contactId = getContactIdByPhoneNumber(context, Utils.formatSimplePhoneNo(phoneNumber));

        ContactHelper contactHelper = new ContactHelper();
        long groupId = ContactHelper.checkAndInsertGroup(context.getContentResolver(), groupName);
        if (groupId != -1) {
            if (contactId == -1) {
                Log.d(LOG_TAG, "--> the phone number doesn't exist <--");
                contactHelper.addContact(context, groupId,
                        contactHelper.generateContactDTO(context,
                                customerName, lastName,
                                Utils.formatSimplePhoneNo(phoneNumber)));
            } else {
                // update contact businessGroup
                contactHelper.insertGroupIdToExistedContact(context, groupId, contactId);
                contactHelper.updateContactName(context, String.valueOf(contactId), customerName, lastName);
            }
        }
    }

    public static long getContactIdByPhoneNumber(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        long contactId = -1;

        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID}, null, null, null)) {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                contactId = Long.parseLong(contactLookup.getString(0));
            }
        }

        return contactId;
    }

    public static long checkAndInsertGroup(ContentResolver contentResolver, String groupTitle) {
        if (TextUtils.isEmpty(groupTitle)) {
            return -1;
        }

        long groupId = getExistGroup(contentResolver, groupTitle);
        // Group do not exist.
        if (groupId == -1) {
            // Create a new group
            ContentValues contentValues = new ContentValues();
            contentValues.put(ContactsContract.Groups.TITLE, groupTitle);
//            contentValues.put(ContactsContract.Groups.NOTES, groupNotes);
            Uri groupUri = contentResolver.insert(ContactsContract.Groups.CONTENT_URI, contentValues);
            // Get the newly created raw contact id.
            groupId = ContentUris.parseId(Objects.requireNonNull(groupUri));
        }

        return groupId;
    }

    // Create a new contact and add it to android contact address book.
    public void addContact(Context context, long groupId, ContactDTO contactDto) {
        // First get content resolver object.
        ContentResolver contentResolver = context.getContentResolver();

        // Create a new contact.
        long rawContactId = insertContact(contentResolver, contactDto);

        // Set group id.
        contactDto.setGroupId(groupId);
        // Contact id and raw contact id has same value.
        contactDto.setContactId(rawContactId);
        contactDto.setRawContactId(rawContactId);

        // Insert contact group membership data (group id).
        insertGroupId(contentResolver, contactDto.getGroupId(), contactDto.getRawContactId());

        // Insert contact address list data.
        insertListData(contentResolver,
                contactDto.getRawContactId(),
                ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,
                contactDto.getAddressList());

        // Insert contact display, given and family name.
        insertName(contentResolver, contactDto);

        insertListData(contentResolver,
                contactDto.getRawContactId(),
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                contactDto.getPhoneList());

        // Insert identity
        insertIdentity(contentResolver, contactDto);

        // Insert photo
        insertPhoto(contentResolver, contactDto);
    }

    public ContactDTO generateContactDTO(Context context, String firstName, String lastName, String phoneNumber) {
        Log.d("generateContactDTO", firstName + " _ " + lastName + " _ " + phoneNumber);
        ContactDTO contactDto = new ContactDTO();
        //***************************************************************
        // Below is structured name related info.
        contactDto.setDisplayName(firstName + " " + lastName);
        contactDto.setGivenName(firstName);
        contactDto.setFamilyName(lastName);
        //***************************************************************
        // Create phone list.
        List<DataDTO> phoneList = new ArrayList<>();

        // Create mobile phone.
        DataDTO mobilePhone = new DataDTO();
        mobilePhone.setDataType(ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        mobilePhone.setDataValue(phoneNumber);
        phoneList.add(mobilePhone);

        contactDto.setPhoneList(phoneList);
        //**************************************************************
        // Set photo info.
        contactDto.setPhoto(generateImageStream(context));

        return contactDto;
    }

    private static byte[] generateImageStream(Context context) {
        if (Objects.isNull(imageStream)) {
            Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_logo_round_box);
            assert d != null;
            Bitmap bitmap = drawableToBitmap(d);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            if (bitmap != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            imageStream = stream.toByteArray();
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return imageStream;
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                drawable.getAlpha() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private static Bitmap getPhoto(Context context, long contactId) {
        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);

        return Objects.nonNull(input) ? BitmapFactory.decodeStream(input) : null;
    }

    private static long getExistGroup(ContentResolver contentResolver, String groupTitle) {
        long ret = -1;


        String[] queryColumnArr = {ContactsContract.Groups._ID};

        String whereClauseBuf = ContactsContract.Groups.TITLE + "='" + groupTitle + "'";
        Cursor cursor = contentResolver.query(ContactsContract.Groups.CONTENT_URI, queryColumnArr, whereClauseBuf, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(ContactsContract.Groups._ID);
                ret = cursor.getLong(columnIndex);
            }
            cursor.close();
        }
        return ret;
    }

    private void insertListData(ContentResolver contentResolver, long rawContactId, String mimeType, String dataValueColumnName, List<DataDTO> dataList) {
        if (dataList != null) {
            ContentValues contentValues = new ContentValues();
            int size = dataList.size();
            for (int i = 0; i < size; i++) {
                DataDTO dataDto = dataList.get(i);
                contentValues.clear();
                // Set raw contact id. Data table only has raw_contact_id.
                contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
                // Set data mimetype.
                contentValues.put(ContactsContract.Data.MIMETYPE, mimeType);
                // Set data type.
                contentValues.put(ContactsContract.CommonDataKinds.Phone.TYPE, dataDto.getDataType());
                // Set data value.
                contentValues.put(dataValueColumnName, dataDto.getDataValue());

                contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues);
            }
        }
    }

    private long insertContact(ContentResolver contentResolver, ContactDTO contactDto) {
        // Insert an empty contact in both contacts and raw_contacts table.
        // Return the system generated new contact and raw_contact id.
        // The id in contacts and raw_contacts table has same value.
        ContentValues contentValues = new ContentValues();
        contentValues.put(ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY, contactDto.getDisplayName());
        contentValues.put(ContactsContract.RawContacts.DISPLAY_NAME_ALTERNATIVE, contactDto.getDisplayName());

        Uri rawContactUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, contentValues);

        return ContentUris.parseId(Objects.requireNonNull(rawContactUri));
    }

    public void updateContactName(Context context, String contactId, String first, String last) {
        try {
            if (first == null || last == null)
                return;

            String where = ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?";
            String[] nameParams = new String[]{contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};


            ArrayList<android.content.ContentProviderOperation> ops = new ArrayList<>();

            android.content.ContentProviderOperation.Builder t;
            android.content.ContentProviderOperation b;

            t = android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI);
            t = t.withSelection(where, nameParams);

            t = t.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, first.trim());

            t = t.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, last.trim());

            b = t.build();
            ops.add(b);
            context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insertGroupId(ContentResolver contentResolver, long groupRowId, long rawContactId) {
        ContentValues contentValues = new ContentValues();
        // Set raw contact id. Data table only has raw_contact_id.
        contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        // Set mimetype first.
        contentValues.put(ContactsContract.CommonDataKinds.StructuredName.MIMETYPE,
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE);
        // Set contact belongs group id.
        contentValues.put(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupRowId);
        // Insert to data table.
        contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues);
    }

    public void insertGroupIdToExistedContact(Context context, long groupRowId, long contactId) {
        Uri rawContactUri = getRawContactURI(context.getContentResolver(), contactId);
        if (rawContactUri == null) {
            Log.e(LOG_TAG, "---> rawContactUri is null <---");
            return;
        }
        insertGroupId(context.getContentResolver(), groupRowId, ContentUris.parseId(rawContactUri));
    }

    private void insertName(ContentResolver contentResolver, ContactDTO contactDto) {
        if (contactDto != null) {
            ContentValues contentValues = new ContentValues();

            // Set raw contact id. Data table only has raw_contact_id.
            contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, contactDto.getRawContactId());
            // Set data mimetype.
            contentValues.put(ContactsContract.CommonDataKinds.StructuredName.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
            // Set display name.
            contentValues.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contactDto.getDisplayName());
            // Set given name.
            contentValues.put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contactDto.getGivenName());
            // Set family name.
            contentValues.put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contactDto.getFamilyName());
            // Insert to data table.
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues);
        }
    }

    private void insertIdentity(ContentResolver contentResolver, ContactDTO contactDto) {
        ContentValues contentValues = new ContentValues();
        // Set raw contact id. Data table only has raw_contact_id.
        contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, contactDto.getRawContactId());
        // Set mimetype first.
        contentValues.put(ContactsContract.CommonDataKinds.Identity.MIMETYPE,
                ContactsContract.CommonDataKinds.Identity.CONTENT_ITEM_TYPE);
        // Set identity
        contentValues.put(ContactsContract.CommonDataKinds.Identity.IDENTITY, contactDto.getIdentity());
        // Set namespace
        contentValues.put(ContactsContract.CommonDataKinds.Identity.NAMESPACE, contactDto.getNamespace());

        // Insert to data table.
        contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues);
    }

    private void insertPhoto(ContentResolver contentResolver, ContactDTO contactDto) {
        ContentValues contentValues = new ContentValues();
        // Set raw contact id. Data table only has raw_contact_id.
        contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, contactDto.getRawContactId());
        // Set mimetype first.
        contentValues.put(ContactsContract.CommonDataKinds.Photo.MIMETYPE,
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
        // Set photo
        contentValues.put(ContactsContract.CommonDataKinds.Photo.PHOTO, contactDto.getPhoto());

        // Insert to data table.
        contentResolver.insert(ContactsContract.Data.CONTENT_URI, contentValues);
    }

    public static Uri getRawContactURI(ContentResolver contentResolver, long contactId) {
        Uri rawContactUri = null;
        Cursor rawContactCursor = contentResolver.query(ContactsContract.RawContacts.CONTENT_URI,
                new String[]{ContactsContract.RawContacts._ID},
                ContactsContract.RawContacts.CONTACT_ID + " = " + contactId, null, null);
        if (rawContactCursor != null && !rawContactCursor.isAfterLast()) {
            rawContactCursor.moveToFirst();
            rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendPath("" + rawContactCursor.getLong(0)).build();
        }
        if (rawContactCursor != null) {
            rawContactCursor.close();
        }

        return rawContactUri;
    }
}
