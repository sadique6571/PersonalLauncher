package com.sadique.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** A single private, verified APK; access is granted only to the shell or Android installer. */
public final class UpdateApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        File file=GitHubUpdates.readyFile(getContext());
        if (!"/apk".equals(uri.getPath()) || !file.isFile()) throw new FileNotFoundException("No verified update");
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        try {
            File file=file(uri);
            String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(columns); Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i]="PersonalLauncher.apk";
                else if(OpenableColumns.SIZE.equals(columns[i])) values[i]=file.length();
            }
            cursor.addRow(values); return cursor;
        } catch(FileNotFoundException unavailable) { return null; }
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
