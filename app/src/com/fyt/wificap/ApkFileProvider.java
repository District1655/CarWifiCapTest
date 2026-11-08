package com.fyt.wificap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 极简 FileProvider：把自己的 APK 以 content:// 形式交给系统安装器，
 * 避免 Android 7+ 的 FileUriExposedException，也不依赖 androidx。
 */
public class ApkFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.fyt.wificap.files";

    public static Uri uriFor(Context ctx, File f) {
        return Uri.parse("content://" + AUTHORITY + "/" + f.getName());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("..")) {
            throw new FileNotFoundException("bad uri " + uri);
        }
        File dir = ApkInstaller.apkDir(getContext());
        File f = new File(dir, name);
        if (!f.exists()) {
            throw new FileNotFoundException(f.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
