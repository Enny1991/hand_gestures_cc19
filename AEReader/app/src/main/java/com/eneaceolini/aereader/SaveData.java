package com.eneaceolini.aereader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Charles on 7/12/17.
 */

public class SaveData extends Activity {

    private static final int MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE = 1;
    private static final int MY_PERMISSIONS_READ_EXTERNAL_STORAGE = 2;

    private Context context;

    String FileName;

    public SaveData(Context context){
        this.context = context;
        checkWriteExternalStoragePermission();//move to initial upload file button
    }

    public File addData(ArrayList<DataVector> trainData){

        File file = null;
        String state;
        state = Environment.getExternalStorageState();

        String date = new SimpleDateFormat("yyyy-MM-dd-hh-mm").format(new Date());

        if(Environment.MEDIA_MOUNTED.equals(state)){
            File Root = Environment.getExternalStorageDirectory();
            File Dir = new File(Root.getAbsolutePath() + "/MyoAppFile");
            if(!Dir.exists()){
                Dir.mkdir();
            }

            FileName  = date + ".txt";

            file = new File(Dir, FileName);

            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                OutputStreamWriter osw = new OutputStreamWriter(fileOutputStream);

                for(int i=0;i<trainData.size();i++) {
                    DataVector data = trainData.get(i);
                    double trunc = i/100;
                    //            saver.addData(selectedItems.get((int)trunc), data.getVectorData().toString() + "\t" + String.valueOf(data.getTimestamp()));
                    osw.append((int)trunc + "\t" + data.getVectorData().toString() + "\t" + String.valueOf(data.getTimestamp()));
                    osw.append("\n");
//                    Log.d("To be saved: ", selectedItems.get((int)trunc) + data.getVectorData().toString() + "\t" + String.valueOf(data.getTimestamp()));
                }
                osw.flush();
                osw.close();

//                cloudUpload.beginUpload(file);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            Log.d("EXTERNAL STRG","No SD card found");
        }
        return file;
    }

    public File makeFile(String filename){
        File file = null;
        String state;
        state = Environment.getExternalStorageState();

        String date = new SimpleDateFormat("yyyy-MM-dd-hh-mm").format(new Date());

        if(Environment.MEDIA_MOUNTED.equals(state)){
            File Root = Environment.getExternalStorageDirectory();
            File Dir = new File(Root.getAbsolutePath() + "/MyoAppFile");
            if(!Dir.exists()){
                Dir.mkdir();
            }

            FileName  =  filename + " " + date + ".txt";

            file = new File(Dir, FileName);
        }
        else {
            Log.d("EXTERNAL STRG","No SD card found");
        }
        return file;
    }

//    public void readData(uri Uri)

    public void addToFile(File file, String line){
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                OutputStreamWriter osw = new OutputStreamWriter(fileOutputStream);

                osw.append(line + "\n");

                osw.flush();
                osw.close();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public void checkWriteExternalStoragePermission() {
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
        }
    }

    public void checkReadExternalStoragePermission() {
        Log.d("FUKKK","Checking reading external storage");
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_READ_EXTERNAL_STORAGE);
        }
    }

    public void requestPermissions() {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_READ_EXTERNAL_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.d("REQUEST_CODE", String.valueOf(requestCode));
        switch (requestCode) {
            case MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Write Storage Permission (already) Granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case MY_PERMISSIONS_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Write Storage Permission (already) Granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

}