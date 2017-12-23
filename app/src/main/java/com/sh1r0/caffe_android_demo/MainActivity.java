package com.sh1r0.caffe_android_demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.sh1r0.caffe_android_lib.CaffeMobile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;


public class MainActivity extends Activity implements CNNListener {
    public static final String LOG_TAG = "Caffe-Android";
    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_IMAGE_SELECT = 200;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static String[] IMAGENET_CLASSES;

    private Button btnCamera;
    private Button btnall;
    private Uri fileUri;
    private Button btnSelect;
    private ImageView ivCaptured;
    private TextView tvLabel;
    private ProgressDialog dialog;
    private Bitmap bmp;
    public static CaffeMobile caffeMobile;
    File sdcard = Environment.getExternalStorageDirectory();
    /*String modelDir = sdcard.getAbsolutePath() + "/caffe_mobile/bvlc_reference_caffenet";
    String modelProto = modelDir + "/deploy.prototxt";
    String modelBinary = modelDir + "/bvlc_reference_caffenet.caffemodel";*/


    String modelDir = sdcard.getAbsolutePath() + "/caffe_mobile/bvlc_alexnet";
    String modelProto = modelDir + "/deploy.prototxt";
    String modelBinary = modelDir + "/bvlc_alexnet.caffemodel";

    static {
        System.loadLibrary("caffe");
        System.loadLibrary("caffe_jni");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivCaptured = (ImageView) findViewById(R.id.ivCaptured);
        tvLabel = (TextView) findViewById(R.id.tvLabel);
        btnall = (Button) findViewById(R.id.btn_multi);
        btnSelect = (Button) findViewById(R.id.btnSelect);
        btnSelect.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                initPrediction();
                Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, REQUEST_IMAGE_SELECT);
            }
        });
        btnCamera = (Button) findViewById(R.id.btnCamera);
        btnCamera.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                initPrediction();
                fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);
                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(i, REQUEST_IMAGE_CAPTURE);
            }
        });


        caffeMobile = new CaffeMobile();
        caffeMobile.setNumThreads(4);
        caffeMobile.loadModel(modelProto, modelBinary);

        float[] meanValues = {104, 117, 123};
        caffeMobile.setMean(meanValues);

        AssetManager am = this.getAssets();
        try {
            InputStream is = am.open("synset_words.txt");
            Scanner sc = new Scanner(is);
            List<String> lines = new ArrayList<String>();
            while (sc.hasNextLine()) {
                final String temp = sc.nextLine();
                lines.add(temp.substring(temp.indexOf(" ") + 1));
            }
            IMAGENET_CLASSES = lines.toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ( (requestCode == REQUEST_IMAGE_CAPTURE ||  requestCode == REQUEST_IMAGE_SELECT ) && resultCode == RESULT_OK) {
            String imgPath;
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                imgPath = fileUri.getPath();
            } else {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = MainActivity.this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgPath = cursor.getString(columnIndex);
                cursor.close();
            }

            bmp = BitmapFactory.decodeFile(imgPath);
            Log.d(LOG_TAG, imgPath);
            Log.d(LOG_TAG, String.valueOf(bmp.getHeight()));
            Log.d(LOG_TAG, String.valueOf(bmp.getWidth()));

            dialog = ProgressDialog.show(MainActivity.this, "Predicting...", "Wait...", true);

            CNNTask cnnTask = new CNNTask(MainActivity.this);
            cnnTask.execute(imgPath);
        } else {
            btnall.setEnabled(true);
            btnSelect.setEnabled(true);
            btnCamera.setEnabled(true);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initPrediction() {
        btnCamera.setEnabled(false);
        btnSelect.setEnabled(false);
        btnall.setEnabled(false);
        tvLabel.setText("");
    }

    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    private static File getOutputMediaFile(int type) {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Caffe-Android-Captures");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(LOG_TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else {
            return null;
        }
        return mediaFile;
    }

    public class CNNTask extends AsyncTask<String, Void, Integer> {
        private CNNListener listener;
        private long startTime;

        public CNNTask(CNNListener listener) {
            this.listener = listener;
        }

        @Override
        protected Integer doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            return caffeMobile.predictImage(strings[0])[0];
        }

        @Override
        protected void onPostExecute(Integer integer) {
            Log.i(LOG_TAG, String.format("elapsed wall time: %d ms", SystemClock.uptimeMillis() - startTime));

            listener.onTaskCompleted(integer);
            super.onPostExecute(integer);
        }
    }

    @Override
    public void onTaskCompleted(int result) {
        ivCaptured.setImageBitmap(bmp);
        tvLabel.setText(IMAGENET_CLASSES[result]);
        btnSelect.setEnabled(true);
        btnall.setEnabled(true);
        btnCamera.setEnabled(true);
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void start_file_loop(View view) {

        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Caffe-Android-Pics");
        if (dir.exists()) {
            deletelogfile();
            File files[] = dir.listFiles();
            for (File fi : files
                    ) {
                CNNTaskMulti cnnTaskmulti = new CNNTaskMulti();
                cnnTaskmulti.execute(fi.getAbsolutePath());
            }
            Toast.makeText(this,"Workers started for all Pics",Toast.LENGTH_LONG);
        }
    }


    private class CNNTaskMulti extends AsyncTask<String, Void, int[]> {
        private long startTime;
        private String filename;

        public CNNTaskMulti() {
        }

        @Override
        protected int[] doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            filename = strings[0];
            return caffeMobile.predictImage(strings[0]);
        }

        @Override
        protected void onPostExecute(int[] integers) {
            Long time = SystemClock.uptimeMillis() - startTime;
            String[] PredictedClass = new String[10];
            int i = 0;
            for (int ind : integers
                    ) {
                PredictedClass[i] = IMAGENET_CLASSES[ind];
                i++;
            }
            String Message = "File Name: " + filename +";Time: " + time + " ms; predicted classes:";
            for ( String clas :PredictedClass
                 ) {
                Message = Message + ";" + clas;
            }
            appendLog(Message);
            Log.i(LOG_TAG,"Worker finished for Pic:"+ filename+" elapsed time: "+time);
            super.onPostExecute(integers);
        }
    }

    public static void deletelogfile()
    {
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Caffe-Android-Results");
        File logFile = new File(dir, "Log.txt");
        if(logFile.exists()){
            logFile.delete();
        }
    }

    public static void appendLog(String message) {
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Caffe-Android-Results");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File logFile = new File(dir, "Log.txt");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(message);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void openLiveCapture(View view){
        Intent myIntent = new Intent(MainActivity.this, CameraView.class);
        MainActivity.this.startActivity(myIntent);
    }

}
