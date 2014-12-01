package com.thosedays.ui;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.thosedays.provider.EventContract;
import com.thosedays.sync.SyncHelper;
import com.thosedays.util.AccountUtils;
import com.thosedays.util.DateTimeUtils;
import com.thosedays.util.MediaUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import joey.thosedays.R;

import static com.thosedays.util.LogUtils.LOGD;
import static com.thosedays.util.LogUtils.makeLogTag;

/**
 * Created by joey on 14/11/18.
 */
public class AddEventActivity extends BaseActivity {

    private static final String TAG = makeLogTag(AddEventActivity.class);

    private static final int REQUEST_PICK_PHOTO = 10001;

    private EditText mEditViewDescription;
    private ImageView mImageViewMain;
    private TextView mTextViewEventTime;
    private Uri mPhotoUri;
    private Date mEventTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_event);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        mEditViewDescription = (EditText) findViewById(R.id.edit_description);
        mTextViewEventTime = (TextView) findViewById(R.id.text_event_time);
        setEventTime(DateTimeUtils.getNow());
        View btnPost = findViewById(R.id.button_post);
        btnPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
                SyncHelper.requestManualSync(AccountUtils.getActiveAccount(AddEventActivity.this), true);
                Toast.makeText(AddEventActivity.this, "Event has posted", Toast.LENGTH_LONG).show();
                if (getParent() == null) {
                    setResult(Activity.RESULT_OK, null);
                } else {
                    getParent().setResult(Activity.RESULT_OK, null);
                }
                finish();
            }
        });

        mImageViewMain = (ImageView)findViewById(R.id.img_main);
        mImageViewMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, REQUEST_PICK_PHOTO);
            }
        });

        Uri imageUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null)
            loadPhoto(imageUri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_PHOTO && resultCode == RESULT_OK) {
            final Uri imageUri = data.getData();
            loadPhoto(imageUri);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void loadPhoto(Uri imageUri) {
        LOGD(TAG, "select photo uri=" + imageUri);
        mPhotoUri = imageUri;
        new LoadPhotoTask().execute(imageUri);
        new LoadExifTask().execute(imageUri);
    }

    private void setEventTime(Date datetime) {
        mEventTime = datetime;
        mTextViewEventTime.setText(DateTimeUtils.sDateFormat.format(datetime));
    }

    /**
     * Saves the session feedback using the appropriate content provider.
     */
    private void save() {

        ContentValues values = new ContentValues();
        values.put(EventContract.Events.EVENT_ID, "");
        values.put(EventContract.Events.EVENT_DESCRIPTION, mEditViewDescription.getText().toString());
        values.put(EventContract.Events.PHOTO_URL, mPhotoUri == null ? "" : mPhotoUri.toString());
        values.put(EventContract.Events.PHOTO_WIDTH, 0);
        values.put(EventContract.Events.PHOTO_HEIGHT, 0);
        values.put(EventContract.Events.SYNCED, 0);

        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));

        //Time in GMT
        values.put(EventContract.Events.EVENT_TIME, dateFormatGmt.format(mEventTime));


        Uri uri = getContentResolver().insert(EventContract.Events.CONTENT_URI, values);
        LOGD(TAG, null == uri ? "No event was saved" : uri.toString());
    }

    private class LoadPhotoTask extends AsyncTask<Uri, Integer, Bitmap> {

        protected Bitmap doInBackground(Uri... uris) {
            Bitmap bitmap = null;
            try {
                final InputStream imageStream = getContentResolver().openInputStream(uris[0]);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                bitmap = BitmapFactory.decodeStream(imageStream, null, options);

                LOGD(TAG, "bitmap w=" + bitmap.getWidth() + " h=" + bitmap.getHeight());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Bitmap bitmap) {
            mImageViewMain.setImageBitmap(bitmap);
        }
    }

    private class LoadExifTask extends AsyncTask<Uri, Integer, ExifInterface> {

        protected ExifInterface doInBackground(Uri... uris) {
            ExifInterface newExif = null;
            File file = MediaUtils.getFile(getApplicationContext(), uris[0]);
            try {
                newExif = new ExifInterface(file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return newExif;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(ExifInterface exif) {
            LOGD(TAG, "[exif] TAG_DATETIME" + exif.getAttribute(ExifInterface.TAG_DATETIME));
            LOGD(TAG, "[exif] TAG_GPS_ALTITUDE" + exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE));
            LOGD(TAG, "[exif] TAG_GPS_LATITUDE" + exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE));
            LOGD(TAG, "[exif] TAG_GPS_LONGITUDE" + exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE));
            LOGD(TAG, "[exif] TAG_GPS_DATESTAMP" + exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP));
            LOGD(TAG, "[exif] TAG_GPS_TIMESTAMP" + exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));
            String gpsDate = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
            String gpsTime = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
            if (!TextUtils.isEmpty(gpsDate) && !TextUtils.isEmpty(gpsTime)) {
                String[] dates = gpsDate.split(":");
                String[] times = gpsTime.split(":");
                if (dates.length >= 3 && times.length >=3) {
                    Date eventTime = DateTimeUtils.getDate(
                            Integer.parseInt(dates[0]),
                            Integer.parseInt(dates[1]),
                            Integer.parseInt(dates[2]),
                            Integer.parseInt(times[0]),
                            Integer.parseInt(times[1]),
                            Integer.parseInt(times[2])
                                );
                    setEventTime(DateTimeUtils.toCurrentTimeZone(eventTime));
                }
            }
        }
    }
}
