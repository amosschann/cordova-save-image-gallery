package com.tanyijia.saveImageGallery;

import java.io.File;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Arrays;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

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
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

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
    public static final String REMOVE_IMAGE_ACTION = "removeImageFromLibrary";

    private JSONArray _args;
    private CallbackContext _callback;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if(action.equals(REMOVE_IMAGE_ACTION)) {
            this.removeImage(args, callbackContext);
        }
        else {
            this._args = args;
            this._callback = callbackContext;
            saveBase64Image(this._args, this._callback);
        }

        return true;
    }

    /**
     * It deletes an image from the given path.
     */
    private void removeImage(JSONArray args, CallbackContext callbackContext) throws JSONException {
        String filename = args.optString(0);

        // isEmpty() requires API level 9
        if (filename.equals(EMPTY_STR)) {
            callbackContext.error("Missing filename string");
        }

        File file = new File(filename);
        if (file.exists()) {
            try {
                file.delete();
            } catch (Exception ex) {
                callbackContext.error(ex.getMessage());
            }
        }

        callbackContext.success(filename);
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
                Toast toast = Toast.makeText(context, "Failed to save image. Please try again.", Toast.LENGTH_SHORT);
                toast.show();
            } else if (mediaScannerEnabled) {
                // Update image gallery
                scanPhoto(imageUri);
                callbackContext.success(imageUri.getPath());
                Toast toast = Toast.makeText(context, "Image saved to gallery successfully.", Toast.LENGTH_SHORT);
                toast.show();
            }

            // String path = imageFile.toString();

            // if (!path.startsWith("file://")) {
            //     path = "file://" + path;
            // }

            // callbackContext.success(path);
        }
    }

    /**
     * Private method to save a {@link Bitmap} into the photo library/temp folder with a format, a prefix and with the given quality.
     */
    private Uri savePhoto(Bitmap bmp, String prefix, String format, int quality, CallbackContext callbackContext) {
        // File retVal = null;
        Uri imageUri = null;
        // Context context = this.cordova.getActivity().getApplicationContext();

        try {
            Calendar c = Calendar.getInstance();
            String date = EMPTY_STR + c.get(Calendar.YEAR) + c.get(Calendar.MONTH) + c.get(Calendar.DAY_OF_MONTH)
                    + c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND);

            // File folder;
            ContentResolver contentResolver = this.cordova.getContext().getContentResolver();

            // if (Build.VERSION.SDK_INT >= 29) {
            //     // Use MediaStore instead
            //     folder = context.getDownloadsDir();
            // } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            //     folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            // } else {
            //     folder = Environment.getExternalStorageDirectory();
            // }

            // if (!folder.exists()) {
            //     folder.mkdirs();
            // }

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

            // now we create the image in the folder
            // File imageFile = new File(folder, fileName);
            // FileOutputStream out = new FileOutputStream(imageFile);

            // now we save the file using MediaStore
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
}
