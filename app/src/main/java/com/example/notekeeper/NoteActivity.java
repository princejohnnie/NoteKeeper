package com.example.notekeeper;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.notekeeper.NoteKeeperDatabaseContract.CourseInfoEntry;
import com.example.notekeeper.NoteKeeperDatabaseContract.NoteInfoEntry;

import static com.example.notekeeper.NoteKeeperProviderContract.Courses;
import static com.example.notekeeper.NoteKeeperProviderContract.Notes;

public class NoteActivity extends AppCompatActivity {
    private static final int NOTE_UPLOADER_JOB_ID = 1;
    private final String TAG = getClass().getSimpleName();
    public static final String NOTE_ID = "com.example.notekeeper.NOTE_POSITION";
    public static final String ORIGINAL_NOTE_COURSE_ID = "com.example.notekeeper.ORIGINAL_NOTE_COURSE_ID";
    public static final String ORIGINAL_NOTE_TITLE = "com.example.notekeeper.ORIGINAL_NOTE_TITLE";
    public static final String ORIGINAL_NOTE_TEXT = "com.example.notekeeper.ORIGINAL_NOTE_TEXT";
    public static final int ID_NOT_SET = -1;

    private NotificationManagerCompat mNotificationManager;

    private NoteInfo mNote;
    private boolean mIsNewNote;

    private Spinner mSpinnerCourses;
    private EditText mTextNoteTitle;
    private EditText mTextNoteText;
    private int mNoteId;
    private boolean mIsCancelling;
    private String mOriginalNoteCourseId;
    private String mOriginalNoteTitle;
    private String mOriginalNoteText;
    private NoteKeeperOpenHelper mDbOpenHelper;

    private Cursor mNoteCursor;
    private int mCourseIdPos;
    private int mNoteTitlePos;
    private int mNoteTextPos;
    private int mNotePosition;
    private SimpleCursorAdapter mAdapterCourses;
    private boolean mIsEmptyNote;
    private Uri mNoteUri;
    private ModuleStatusView mViewModuleStatus;

    @Override
    protected void onDestroy() {
        mDbOpenHelper.close();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mNotificationManager = NotificationManagerCompat.from(this);

        mDbOpenHelper = new NoteKeeperOpenHelper(this);


        //PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);

        mSpinnerCourses = findViewById(R.id.spinner_courses);

        mAdapterCourses = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item, null,
                new String[] {CourseInfoEntry.COLUMN_COURSE_TITLE}, new int[] {android.R.id.text1}, 0);

        /*List<CourseInfo> courses = DataManager.getInstance().getCourses();
        ArrayAdapter<CourseInfo> adapterCourses = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, courses);*/
        mAdapterCourses.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerCourses.setAdapter(mAdapterCourses);

        loadCourseData();

        readDisplayStateValues();
        if (savedInstanceState == null) {
//            saveOriginalNoteValues();
        }else {
            restoreOriginalNoteValues(savedInstanceState);
        }

        mTextNoteTitle = findViewById(R.id.text_note_title);
        mTextNoteText = findViewById(R.id.text_note_text);

        if (!mIsNewNote) {
            loadNoteData();
        }

        mViewModuleStatus = findViewById(R.id.module_status);
        loadModuleStatusValues();

        Log.d(TAG, "onCreate");
    }

    private void loadModuleStatusValues() {
        // In real life we'd lookup the selected courses's module statuses from the content provider
        int totalNumberOfModules = 11;
        int completedNumberOfModules = 7;

        boolean[] moduleStatus = new boolean[totalNumberOfModules];
        for (int moduleIndex = 0; moduleIndex < completedNumberOfModules; moduleIndex++) {
            moduleStatus[moduleIndex] = true;

            mViewModuleStatus.setModuleStatus(moduleStatus);
        }
    }

    private void loadCourseData() {
        Uri uri = NoteKeeperProviderContract.Courses.CONTENT_URI;
        String [] courseColumns = {
                Courses.COLUMN_COURSE_TITLE,
                Courses.COLUMN_COURSE_ID,
                Courses._ID
        };
        Cursor cursor = getContentResolver().query(uri, courseColumns,null,
                null, Courses.COLUMN_COURSE_TITLE);
        mAdapterCourses.changeCursor(cursor);


    }

    private void loadNoteData() {
        /*SQLiteDatabase db = mDbOpenHelper.getReadableDatabase();
        String selection = NoteInfoEntry._ID + " = ?";
        String[] selectionArgs = {Integer.toString(mNoteId)};*/

        String[] noteColumns = {
                Notes.COLUMN_COURSE_ID,
                Notes.COLUMN_NOTE_TITLE,
                Notes.COLUMN_NOTE_TEXT,
        };
        mNoteUri = ContentUris.withAppendedId(Notes.CONTENT_URI, mNoteId);
        mNoteCursor = getContentResolver().query(mNoteUri, noteColumns, null, null, null);

        //mNoteCursor = db.query(NoteInfoEntry.TABLE_NAME, noteColumns, selection, selectionArgs, null, null, null);

        assert mNoteCursor != null;
        mCourseIdPos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_COURSE_ID);
        mNoteTitlePos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TITLE);
        mNoteTextPos = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TEXT);

        mNoteCursor.moveToNext();
        displayNote();
    }

    private void restoreOriginalNoteValues(Bundle savedInstanceState) {
        mOriginalNoteCourseId = savedInstanceState.getString(ORIGINAL_NOTE_COURSE_ID);
        mOriginalNoteTitle = savedInstanceState.getString(ORIGINAL_NOTE_TITLE);
        mOriginalNoteText = savedInstanceState.getString(ORIGINAL_NOTE_TEXT);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ORIGINAL_NOTE_COURSE_ID, mOriginalNoteCourseId);
        outState.putString(ORIGINAL_NOTE_TITLE, mOriginalNoteTitle);
        outState.putString(ORIGINAL_NOTE_TEXT, mOriginalNoteText);
    }

    private void saveOriginalNoteValues() {
        if (mIsNewNote)
            return;
        mOriginalNoteCourseId = mNote.getCourse().getCourseId();
        mOriginalNoteTitle = mNote.getTitle();
        mOriginalNoteText = mNote.getText();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsEmptyNote = isNoteEmpty();
         if (mIsCancelling) {
//             deleteNoteFromDatabase();
             Log.i(TAG, "cancelling note at position: " + mNoteId);
             if (mIsNewNote) {
                deleteNoteFromDatabase();
             }else {
//                storePreviousNoteValues();
            }
        } else if (mIsEmptyNote) {
             deleteNoteFromDatabase();
        } else {
             saveNote();
         }
         Log.d(TAG, "onPause");
    }

    private boolean isNoteEmpty() {
        String noteTitle = mTextNoteTitle.getText().toString();
        String noteText = mTextNoteText.getText().toString();
        return noteText.isEmpty() && noteTitle.isEmpty();
    }

    private void deleteNoteFromDatabase() {
        final String selection = NoteInfoEntry._ID + " = ?";
        final String[] selectionArgs = {Integer.toString(mNoteId)};

        /*mNoteUri = ContentUris.withAppendedId(Notes.CONTENT_URI, mNoteId);
        getContentResolver().delete(mNoteUri, selection, selectionArgs);*/

        SQLiteDatabase db = mDbOpenHelper.getWritableDatabase();
        db.delete(NoteInfoEntry.TABLE_NAME, selection, selectionArgs);
    }

    private void storePreviousNoteValues() {
        CourseInfo course = DataManager.getInstance().getCourse(mOriginalNoteCourseId);
        mNote.setCourse(course);
        mNote.setTitle(mOriginalNoteTitle);
        mNote.setText(mOriginalNoteText);
    }

    private void saveNote() {
        String courseId = selectedCourseId();
        String noteTitle = mTextNoteTitle.getText().toString();
        String noteText = mTextNoteText.getText().toString();

        saveNoteToDatabase(courseId, noteTitle, noteText);
    }

    private String selectedCourseId() {
        int selectedPosition = mSpinnerCourses.getSelectedItemPosition();
        Cursor cursor = mAdapterCourses.getCursor();
        cursor.moveToPosition(selectedPosition);
        int courseIdPos = cursor.getColumnIndex(CourseInfoEntry.COLUMN_COURSE_ID);
        String courseId = cursor.getString(courseIdPos);
        return courseId;
    }

    private void saveNoteToDatabase(String courseId, String noteTitle, String noteText) {
        String selection = NoteInfoEntry._ID + " = ?";
        String[] selectionArgs = {Integer.toString(mNoteId)};
        mNoteUri = ContentUris.withAppendedId(Notes.CONTENT_URI, mNoteId);

        ContentValues values = new ContentValues();
        values.put(NoteInfoEntry.COLUMN_COURSE_ID, courseId);
        values.put(NoteInfoEntry.COLUMN_NOTE_TITLE, noteTitle);
        values.put(NoteInfoEntry.COLUMN_NOTE_TEXT, noteText);

        mNoteId = getContentResolver().update(mNoteUri, values, null, null);
        /*SQLiteDatabase db = mDbOpenHelper.getWritableDatabase();
        db.update(NoteInfoEntry.TABLE_NAME, values, selection, selectionArgs);*/
    }

    private void displayNote() {
        String courseId = mNoteCursor.getString(mCourseIdPos);
        String noteTitle = mNoteCursor.getString(mNoteTitlePos);
        String noteText = mNoteCursor.getString(mNoteTextPos);

        /*List<CourseInfo> courses = DataManager.getInstance().getCourses();
        CourseInfo course = DataManager.getInstance().getCourse(courseId);*/

        int courseIndex = getIndexOfCourseId(courseId);

        mSpinnerCourses.setSelection(courseIndex);
        mTextNoteTitle.setText(noteTitle);
        mTextNoteText.setText(noteText);

        CourseEventBroadcastHelper.sendEventBroadcast(this, courseId, "Editing Note");
    }

    private int getIndexOfCourseId(String courseId) {
        Cursor cursor = mAdapterCourses.getCursor();
        int courseIdPos = cursor.getColumnIndex(CourseInfoEntry.COLUMN_COURSE_ID);
        int courseRowIndex = 0;

        boolean more = cursor.moveToFirst();
        while (more) {
            String cursorCourseId = cursor.getString(courseIdPos);
            if (courseId.equals(cursorCourseId))
                break;

            courseRowIndex++;
            more = cursor.moveToNext();
        }
        return courseRowIndex;
    }

    private void readDisplayStateValues() {
        Intent intent = getIntent();
        mNoteId = intent.getIntExtra(NOTE_ID, ID_NOT_SET);
        mIsNewNote = mNoteId == ID_NOT_SET;
        if (mIsNewNote) {
            createNewNote();
        }
        Log.i(TAG, "mNoteId: " + mNoteId);
       // mNote = DataManager.getInstance().getNotes().get(mNoteId);
    }

    private void createNewNote() {
        AsyncTask<ContentValues, Integer, Uri> task = new  AsyncTask<ContentValues, Integer, Uri>() {
            private ProgressBar mProgressBar;
            @Override
            protected Uri doInBackground(ContentValues... contentValues) {
                Log.d(TAG, "Started doInBackground - thread: " + Thread.currentThread().getId());
                ContentValues insertValues = contentValues[0];
                Uri rowUri = getContentResolver().insert(Notes.CONTENT_URI, insertValues);
                simulateLongRunningWork();
                publishProgress(2);
                simulateLongRunningWork();
                publishProgress(3);
                return rowUri;
            }


            @Override
            protected void onProgressUpdate(Integer... values) {
                int progressValue = values[0];
                mProgressBar.setProgress(progressValue);
            }

            @Override
            protected void onPreExecute() {
                mProgressBar = findViewById(R.id.progressBar);
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressBar.setProgress(1);
            }

            @Override
            protected void onPostExecute(Uri uri) {
                mNoteUri = uri;
                Log.d(TAG, "Called onPostExecute - thread: " + Thread.currentThread().getId());
                mProgressBar.setVisibility(View.GONE);
            }
        };

        ContentValues values = new ContentValues();
        values.put(Notes.COLUMN_COURSE_ID, " ");
        values.put(Notes.COLUMN_NOTE_TITLE, " ");
        values.put(Notes.COLUMN_NOTE_TEXT, " ");

        Log.d(TAG, "Call to execute on Thread: " + Thread.currentThread().getId());
        task.execute(values);
       // mNoteUri = getContentResolver().insert(Notes.CONTENT_URI, values);

        /*SQLiteDatabase db = mDbOpenHelper.getWritableDatabase();
        mNoteId = (int) db.insert(NoteInfoEntry.TABLE_NAME, null, values);*/

    }

    private void simulateLongRunningWork() {
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            Log.e(TAG, "Cannot delay thread");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_send_mail) {
            sendEmail();
            return true;
        }else if (id == R.id.action_cancel) {
            mIsCancelling = true;
            finish();
        } else if (id == R.id.action_next) {
            moveNext();
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_set_notification) {
            showReminderNotification();
        } else if (id == R.id.action_backup_note) {
            backUpNotes();
        } else if (id == R.id.action_upload_note) {
            scheduleNoteUpload();
        }

        return super.onOptionsItemSelected(item);
    }

    private void scheduleNoteUpload() {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(NoteUploaderJobService.EXTRA_DATA_URI, Notes.CONTENT_URI.toString());

        ComponentName componentName = new ComponentName(this, NoteUploaderJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(NOTE_UPLOADER_JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
                .build();

        JobScheduler jobScheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(jobInfo);
    }

    private void backUpNotes() {
        Intent intent = new Intent(NoteActivity.this, NoteBackupService.class);
        intent.putExtra(NoteBackupService.EXTRA_COURSE_ID, NoteBackup.ALL_COURSES);
        startService(intent);
    }

    private void showReminderNotification() {
        Intent noteIntent = new Intent(this, NoteActivity.class);
        PendingIntent notePendingIntent = PendingIntent.getActivity(this, 0, noteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent noteListIntent = new Intent(this, NoteListActivity.class);
        PendingIntent noteListPendingIntent = PendingIntent.getActivity(this, 1, noteListIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String noteText = mTextNoteText.getText().toString();
        Notification notification = new NotificationCompat.Builder(this, App.CHANNEL_1_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle("Review Note")
                .setContentText(noteText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(Color.BLUE)
                .setStyle( new NotificationCompat.BigTextStyle()
                .bigText(noteText)
                .setBigContentTitle(mTextNoteTitle.getText())
                .setSummaryText("Review Note"))
                .setContentIntent(notePendingIntent)
                .setAutoCancel(true)
                .addAction(0, "View all Notes", noteListPendingIntent)
                .build();

        mNotificationManager.notify(1, notification);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_next);
        int lastNoteIndex = DataManager.getInstance().getNotes().size() - 1;
        item.setEnabled(mNoteId < lastNoteIndex);

        return super.onPrepareOptionsMenu(menu);
    }

    private void moveNext() {
        saveNote();

        ++mNoteId;
        mNote = DataManager.getInstance().getNotes().get(mNoteId);

        saveOriginalNoteValues();
       // displayNote();

        invalidateOptionsMenu();
    }

    private void sendEmail() {
        CourseInfo course = (CourseInfo) mSpinnerCourses.getSelectedItem();
        String subject = mTextNoteTitle.getText().toString();
        String text = "Check out what I learned in the PluralSight course: \"" + course.getTitle() + "\"\n\n" + mTextNoteText.getText().toString();

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc2822");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(intent);
    }

    /*private void displayNote() {
        DataManager dm = DataManager.getInstance();
        String noteTitle = mNoteCursor.getString(mNoteTitlePos);
        String noteText = mNoteCursor.getString(mNoteTextPos);

        *//*String courseId = mNoteCursor.getString(mCourseIdPos);
        String noteTitle = mNoteCursor.getString(mNoteTitlePos);
        String noteText = mNoteCursor.getString(mNoteTextPos);*//*

        List<CourseInfo> courses = DataManager.getInstance().getCourses();
        CourseInfo course = DataManager.getInstance().getCourse(courseId);

        int courseIndex = courses.indexOf(course);
        mSpinnerCourses.setSelection(courseIndex);
        mTextNoteTitle.setText(noteTitle);
        mTextNoteText.setText(noteText);
    }*/
}