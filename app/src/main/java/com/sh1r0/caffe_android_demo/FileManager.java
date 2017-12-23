package com.sh1r0.caffe_android_demo;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class FileManager {
    private String TAG = "Caffe-Camera";

    public static  void writeFrame(String fileName, byte[] data) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Caffe-Live-Capture/");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File myFile = new File(dir, fileName);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(myFile));
            bos.write(data);
            bos.flush();
            bos.close();
//            Log.e(TAG, "" + data.length + " bytes have been written to " + filesDir + fileName + ".jpg");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] YUV_420_888toNV21(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    public static byte[] NV21toJPEG(byte[] nv21, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        return out.toByteArray();
    }



}
