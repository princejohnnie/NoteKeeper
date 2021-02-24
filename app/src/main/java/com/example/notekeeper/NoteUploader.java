package com.example.notekeeper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.notekeeper.NoteKeeperProviderContract.Notes;

public class NoteUploader {
    public static final String TAG = NoteBackup.class.getSimpleName();

    private final Context mContext;
    private boolean mCancelled;

    public NoteUploader(Context context) {
        mContext = context;
    }

    public boolean isCancelled() {
        return mCancelled;
    }
    public void cancel() {
        mCancelled = true;
        Log.i(TAG, ">>>***UPLOAD CANCELLED***<<<");
    }

    public void doUpload(Uri dataUri) {
        String[] columns = {Notes.COLUMN_COURSE_ID,
                Notes.COLUMN_NOTE_TITLE,
                Notes.COLUMN_NOTE_TEXT
        };

        Cursor cursor = mContext.getContentResolver().query(dataUri, columns, null, null, null);
        assert cursor != null;
        int courseIdPos = cursor.getColumnIndex(Notes.COLUMN_COURSE_ID);
        int noteTitlePos = cursor.getColumnIndex(Notes.COLUMN_NOTE_TITLE);
        int noteTextPos = cursor.getColumnIndex(Notes.COLUMN_NOTE_TEXT);

        Log.i(TAG, ">>>***UPLOAD START - " + dataUri + "***<<<");
        mCancelled = false;

        while (!mCancelled && cursor.moveToNext()) {
            String courseId = cursor.getString(courseIdPos);
            String noteTitle = cursor.getString(noteTitlePos);
            String noteText = cursor.getString(noteTextPos);

            if (!noteTitle.equals(" ")) {
                Log.i(TAG, ">>>Uploading Note<<< " + courseId + "|" + noteTitle + "|" + noteText);
                simulateLongRunningWork();
            }
        }
        Log.i(TAG, ">>>UPLOAD COMPLETE!<<<" );
        cursor.close();
    }

    private static void simulateLongRunningWork() {
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't cause thread to sleep");
        }
    }
}
