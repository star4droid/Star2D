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
				OutputStream outputStream = context.getContentResolver().openOutputStream(destinationUri, "wt");
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
		try {
			copyUriToUriChecked(context, sourceUri, destinationUri);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Copies and makes the destination exactly the source: an existing longer file (an older APK chosen to overwrite)
	 * is emptied first and cut to the new length afterwards, otherwise its old tail stays and the APK is broken.
	 */
	public static void copyUriToUriChecked(Context context, Uri sourceUri, Uri destinationUri) throws IOException {
		ContentResolver cr = context.getContentResolver();
		try { writeFile("", convertUriToFilePath(context, destinationUri)); } catch (Throwable ignored) {}
		checkAndCreateUriFile(destinationUri, context);
		long written = 0;
		InputStream in = cr.openInputStream(sourceUri);
		if (in == null) throw new IOException("cannot read " + sourceUri);
		OutputStream out = null;
		try {
			try {
				out = cr.openOutputStream(destinationUri, "wt"); // "w" does not truncate on every provider
			} catch (Exception notSupported) {
				out = null;
			}
			if (out == null) {
				android.os.ParcelFileDescriptor pfd = cr.openFileDescriptor(destinationUri, "rw");
				if (pfd == null) throw new IOException("cannot write " + destinationUri);
				java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor());
				fos.getChannel().truncate(0);
				fos.getChannel().position(0);
				out = fos;
			}
			byte[] buf = new byte[64 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				written += n;
			}
			out.flush();
		} finally {
			try { in.close(); } catch (IOException ignored) {}
			if (out != null) try { out.close(); } catch (IOException ignored) {}
		}
		// still longer than what was written (provider ignored the truncate): cut the old tail
		try {
			android.os.ParcelFileDescriptor pfd = cr.openFileDescriptor(destinationUri, "rw");
			if (pfd != null) {
				long size = pfd.getStatSize();
				if (size > written) {
					java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor());
					fos.getChannel().truncate(written);
					fos.close();
				}
				pfd.close();
			}
		} catch (Throwable ignored) {
		}
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