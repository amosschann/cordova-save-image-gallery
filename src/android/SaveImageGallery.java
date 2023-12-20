package com.tanyijia.SaveImageGallery;

import java.io.OutputStream;
import java.util.Calendar;
import java.util.Arrays;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;

import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

/**
 * SaveImageGallery.java
 *
 * Extended Android implementation of the Base64ToGallery for iOS.
 * Inspirated by StefanoMagrassi's code
 * https://github.com/Nexxa/cordova-base64-to-gallery
 * Updated for Android 10+ and 13 by Tan Yi Jia
 *
 * @author Tan Yi Jia <tanyijia@gmail.com>
 */
public class SaveImageGallery extends CordovaPlugin {

    // Consts
    public static final String EMPTY_STR = "";

    public static final String JPG_FORMAT = "JPG";
    public static final String PNG_FORMAT = "PNG";

    // actions constants
    public static final String SAVE_BASE64_ACTION = "saveImageDataToLibrary";

    // Android version check
    public static final int SDK_BUILD = android.os.Build.VERSION.SDK_INT;
    public static final int P_SDK_BUILD = android.os.Build.VERSION_CODES.P;

    // permissions
    public static final int WRITE_PERM_REQUEST_CODE = 0;
    private final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private JSONArray _args;
    private CallbackContext _callback;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this._args = args;
        this._callback = callbackContext;

        if (SDK_BUILD <= P_SDK_BUILD) {
            // For Android 9 and below
            if (PermissionHelper.hasPermission(this, WRITE_EXTERNAL_STORAGE)) {
                Log.d("SaveImageGallery", "Permissions already granted, or Android version is lower than 6");
                saveBase64Image(this._args, this._callback);
            } else {
                Log.d("SaveImageGallery", "Requesting permissions for WRITE_EXTERNAL_STORAGE");
                PermissionHelper.requestPermission(this, WRITE_PERM_REQUEST_CODE, WRITE_EXTERNAL_STORAGE);
            }
        } else {
            // For Android 10+
            saveBase64Image(this._args, this._callback);
        }

        return true;
    }

    /**
     * It saves a Base64 String into an image.
     */
    private void saveBase64Image(JSONArray args, CallbackContext callbackContext) throws JSONException {
        String base64 = args.optString(0);
        String filePrefix = args.optString(1);
        boolean mediaScannerEnabled = args.optBoolean(2);
        String format = args.optString(3);
        int quality = args.optInt(4);
        Context context = this.cordova.getActivity().getApplicationContext();

        List<String> allowedFormats = Arrays.asList(new String[] { JPG_FORMAT, PNG_FORMAT });

        // isEmpty() requires API level 9
        if (base64.equals(EMPTY_STR)) {
            callbackContext.error("Missing base64 string");
        }

        // isEmpty() requires API level 9
        if (format.equals(EMPTY_STR) || !allowedFormats.contains(format.toUpperCase())) {
            format = JPG_FORMAT;
        }

        if (quality <= 0) {
            quality = 100;
        }

        // Create the bitmap from the base64 string
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        if (bmp == null) {
            callbackContext.error("The image could not be decoded");

        } else {

            // Save the image
            Uri imageUri = savePhoto(bmp, filePrefix, format, quality, callbackContext);

            if (imageUri == null) {
                callbackContext.error("Error while saving image");
            } else if (mediaScannerEnabled) {
                // Update image gallery
                scanPhoto(imageUri);
                callbackContext.success(imageUri.getPath());
            } else {
                // File saved but not in gallery as mediascanner is disabled
                callbackContext.error("Image saved but mediascanner is disabled. Use a file manager to find the image at "+imageUri.getPath());
            }
        }
    }

    /**
     * Private method to save a {@link Bitmap} into the photo library/temp folder with a format, a prefix and with the given quality.
     */
    private Uri savePhoto(Bitmap bmp, String prefix, String format, int quality, CallbackContext callbackContext) {
        Uri imageUri = null;

        try {
            Calendar c = Calendar.getInstance();
            String date = EMPTY_STR + c.get(Calendar.YEAR) + c.get(Calendar.MONTH) + c.get(Calendar.DAY_OF_MONTH)
                    + c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND);

            // ContentResolver for MediaStore file download to gallery
            ContentResolver contentResolver = this.cordova.getContext().getContentResolver();

            // building the filename
            String fileName = prefix + date;
            Bitmap.CompressFormat compressFormat = null;
            // switch for String is not valid for java < 1.6, so we avoid it
            if (format.equalsIgnoreCase(JPG_FORMAT)) {
                fileName += ".jpg";
                compressFormat = Bitmap.CompressFormat.JPEG;
            } else if (format.equalsIgnoreCase(PNG_FORMAT)) {
                fileName += ".png";
                compressFormat = Bitmap.CompressFormat.PNG;
            } else {
                // default case
                fileName += ".jpg";
                compressFormat = Bitmap.CompressFormat.JPEG;
            }

            // use MediaStore to create the file
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, format.equalsIgnoreCase(PNG_FORMAT) ? "image/png" : "image/jpeg");

            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

            // now we save the file to gallery using MediaStore
            OutputStream out = contentResolver.openOutputStream(imageUri);

            // compress it
            bmp.compress(compressFormat, quality, out);
            out.flush();
            out.close();

            // retVal = imageFile;

        } catch (Exception e) {
            Log.e("SaveImageToGallery", "An exception occured while saving image: " + e.toString());
        }

        return imageUri;
    }

    /**
     * Invoke the system's media scanner to add your photo to the Media Provider's database,
     * making it available in the Android Gallery application and to other apps.
     */
    private void scanPhoto(Uri imageUri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(imageUri);
        cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    /**
     * Callback from PermissionHelper.requestPermission method for Android 9 and below
     */
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
		for (int r : grantResults) {
			if (r == PackageManager.PERMISSION_DENIED) {
				Log.d("SaveImageGallery", "Permission not granted by the user");
				_callback.error("Permissions denied");
				return;
			}
		}
		switch (requestCode) {
		case WRITE_PERM_REQUEST_CODE:
			Log.d("SaveImageGallery", "User granted the permission for WRITE_EXTERNAL_STORAGE");
            saveBase64Image(this._args, this._callback);
			break;
		}
	}
}
