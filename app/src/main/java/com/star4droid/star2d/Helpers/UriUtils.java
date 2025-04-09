package com.star4droid.star2d.Helpers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.io.OutputStream;
import static com.star4droid.star2d.Helpers.FileUtil.*;

public class UriUtils {
	
	private static final String TAG = UriUtils.class.getSimpleName();
	
	public static boolean moveUriToUri(Context context, Uri sourceUri, Uri destinationUri) {
		try {
			writeFile("",convertUriToFilePath(context,destinationUri));
			checkAndCreateUriFile(destinationUri,context);
			InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
			if (inputStream != null) {
				OutputStream outputStream = context.getContentResolver().openOutputStream(destinationUri);
				if (outputStream != null) {
					copyStream(inputStream, outputStream);
					inputStream.close();
					outputStream.close();
					deleteUri(context, sourceUri);
					return true;
				}
				inputStream.close();
			}
			} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean copyUriToUri(Context context, Uri sourceUri, Uri destinationUri) {
		writeFile("",convertUriToFilePath(context,destinationUri));
		checkAndCreateUriFile(destinationUri,context);
		try {
			InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
			if (inputStream != null) {
				OutputStream outputStream = context.getContentResolver().openOutputStream(destinationUri);
				if (outputStream != null) {
					copyStream(inputStream, outputStream);
					inputStream.close();
					outputStream.close();
				return true;}
				inputStream.close();
			}
			} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static boolean deleteUri(Context context, Uri uri) {
		ContentResolver contentResolver = context.getContentResolver();
		int deleteCount = contentResolver.delete(uri, null, null);
		return deleteCount > 0;
	}
	
	public static InputStream getInputStream(Context context, Uri uri) throws IOException {
		return context.getContentResolver().openInputStream(uri);
	}
	
	public static OutputStream getOutputStream(Context context, Uri uri) throws IOException {
		return context.getContentResolver().openOutputStream(uri);
	}
	
	private static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
		byte[] buffer = new byte[4096];
		int bytesRead;
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, bytesRead);
		}
	}
	
	
	public static void checkAndCreateUriFile(Uri uri,Context context) {
		ContentResolver contentResolver = context.getContentResolver();
		
		try {
			// Check if the Uri file exists
			ParcelFileDescriptor fileDescriptor = contentResolver.openFileDescriptor(uri, "r");
			if (fileDescriptor != null) {
				fileDescriptor.close();
				return; // File already exists, no need to create it
			}
			} catch (FileNotFoundException e) {
			// File doesn't exist, create it
			try {
				OutputStream outputStream = contentResolver.openOutputStream(uri);
				if (outputStream != null) {
					outputStream.close();
				}
				} catch (IOException ex) {
				ex.printStackTrace();
				// Handle the exception
			}
			} catch (IOException e) {
			e.printStackTrace();
			// Handle the exception
		}
	}
}