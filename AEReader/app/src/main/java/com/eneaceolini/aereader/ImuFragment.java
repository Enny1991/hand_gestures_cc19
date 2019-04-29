package com.eneaceolini.aereader;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.SENSOR_SERVICE;

/**
 * Created by Ricardo on 6/20/18.
 */

public class ImuFragment extends Fragment implements SensorEventListener {

    byte[] data = {0};
    private static ImageView img_horizon;
    private static TextView txt_azimuth;
    int mAzimuth;
    private SensorManager mSensorManager;
    private Sensor mRotationV, mAccelerometer, mMagnetometer;
    private Context context;
    float[] rMat = new float[9];
    float[] orientation = new float[9];
    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer  = new float[3];
    private boolean haveSensor = false, haveSensor2 = false;//to check if it has sensors
    private boolean mlastAccelerometerSet = false;
    private boolean mLastMagnetometerSet  = false;
    float roll;
    float pitch;
    float yaw;
    int rot = 0;
    private Handler mHandler = new Handler(Looper.getMainLooper());//works with the UI
    String stringTemp;//using this for the phone data

    //private LineGraph graph;

    private static final String TAG = "Tab4Fragment";

    private EmgFragment emg;

    ListView listView_IMU;

    Classifier classifier = new Classifier();

    //create an ArrayList object to store selected items

    ArrayList<String> selectedItemsIMU = new ArrayList<String>();

    private int numIMU = 0;

    private FeatureCalculator fcalc = new FeatureCalculator();

    private LineChart cubicLineChart;
    private Plotter plotter;

    String[] IMUs = new String[]{
            "Orientation W",
            "Orientation X",
            "Orientation Y",
            "Orientation Z",
            "Accelerometer X",
            "Accelerometer Y",
            "Accelerometer Z",
            "Gyroscope X",
            "Gyroscope Y",
            "Gyroscope Z",
    };


    private static boolean[] imuSelected = new boolean[]{false, false, false, false, false, false, false, false, false, false};

    //@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_imu, container, false);
        assert v != null;

        mSensorManager = (SensorManager) getActivity().getSystemService(SENSOR_SERVICE);
        img_horizon = (ImageView) v.findViewById(R.id.horizon_sphere);
        txt_azimuth = (TextView) v.findViewById(R.id.txt_result);
        listView_IMU = (ListView) v.findViewById(R.id.listViewIMU);


        cubicLineChart = v.findViewById(R.id.chart);

        mHandler = new Handler();

        plotter = new Plotter(mHandler, cubicLineChart);//must pass chart from this fragment


        listView_IMU.setChoiceMode(ListView.CHOICE_MODE_SINGLE);




        final List<String> IMUArrayList = new ArrayList<String>(Arrays.asList(IMUs));

        ArrayAdapter<String> adapter_IMU = new ArrayAdapter<String>(getActivity(), R.layout.myradioview, IMUArrayList);

        mHandler = new Handler();

        //graph = (LineGraph) v.findViewById(R.id.holo_graph_view_imu);

        listView_IMU.setAdapter(adapter_IMU);
        //plotter = new Plotter(mHandler, graph);
        listView_IMU.setItemChecked(0, true);


        emg = new EmgFragment();

        //set OnItemClickListener
        listView_IMU.setOnItemClickListener((parent, view, position, id) -> {

            classifier.setChoice(position);

            // selected item
            String Classifier_selectedItem = ((TextView) view).getText().toString();

            Toast.makeText(getActivity(), "selected: " + Classifier_selectedItem, Toast.LENGTH_SHORT).show();

        });

        return v;
    }
//____________________Added_by_Danny_Ceron__________________________________________________________

    public void sendIMUValues(DataVector data){

        //plotter.pushImuPlotter(data);

        float w,x,y,z;

        //added function to work with the IMU raw data from armband
        w = (float)data.getValue(0).byteValue();
        x = (float)data.getValue(1).byteValue();
        y = (float)data.getValue(2).byteValue();
        z = (float)data.getValue(3).byteValue();
        //double check this is good IMU data

        //putting the accelerometer data into and sting and saving it to a file
        String acceData = String.valueOf(x)+","+ String.valueOf(y)+","+ String.valueOf(z);

        //saver2.addToFile(phoneFile,acceData);

        roll = (float) Math.atan2(x,z);//outcome in radians so might need to convert to degrees.
        pitch = (float) Math.asin(Math.max(-1.0f, Math.min(1.0f,2.0f*(w*y-z*x))));
        yaw = 0;

        //converting to degrees. converting it to degrees could be one on the same roll calculation line.
        roll =  roll*(180f/(float) Math.PI);
        roll = Math.round(roll);

        //String stuffArray =



        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //updates the UI
                img_horizon.setRotation(-1*roll);
                txt_azimuth.setText(roll+"°"+"\nW:" +w+"\nX:"+x+"\nY:"+y+"\nZ:"+z);
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {

        if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR);
        {
            SensorManager.getRotationMatrixFromVector(rMat,event.values);
            mAzimuth = (int) ((Math.toDegrees(SensorManager.getOrientation(rMat,orientation)[0])+360)%360);
        }
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            System.arraycopy(event.values,0,mLastAccelerometer,0,event.values.length);
            mlastAccelerometerSet = true;
        }
        else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            System.arraycopy(event.values,0,mLastMagnetometer,0,event.values.length);
            mLastMagnetometerSet = true;
        }
        if(mLastMagnetometerSet && mlastAccelerometerSet)
        {
            SensorManager.getRotationMatrix(rMat,null,mLastAccelerometer,mLastMagnetometer);
            SensorManager.getOrientation(rMat, orientation);
            mAzimuth = (int) ((Math.toDegrees(SensorManager.getOrientation(rMat,orientation)[0])+360)%360);
        }

        mAzimuth = Math.round(mAzimuth);
        //System.out.println("this is the phone data"+ mAzimuth);
//        img_horizon.setRotation(rot);
//
//        Log.d("ROT", String.valueOf(rot));

        String where = "NO";
        if(mAzimuth >= 358||mAzimuth <= 10)
            where = "N";
        if(mAzimuth >= 350||mAzimuth <= 280)
            where = "NW";
        if(mAzimuth >= 280||mAzimuth <= 260)
            where = "W";
        if(mAzimuth >= 360||mAzimuth <= 190)
            where = "SW";
        if(mAzimuth >= 190||mAzimuth <= 170)
            where = "S";
        if(mAzimuth >= 170||mAzimuth <= 100)
            where = "SE";
        if(mAzimuth >= 100||mAzimuth <= 80)
            where = "E";
        if(mAzimuth >= 80||mAzimuth <= 10)
            where = "NE";

        //txt_azimuth.setText(stringTemp);

        //sendIMUValues(byte[] data);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {}

    public void start()
    {
        if(mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)==null)
        {
            if(mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)==null || mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)== null)
            {
                noSensonAler();
            }
            else{
                mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                mMagnetometer  = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

                //haveSensor = mSensorManager.registerListener(this.mAccelerometer,SensorManager.SENSOR_DELAY_NORMAL);
                haveSensor= mSensorManager.registerListener(this,mAccelerometer, SensorManager.SENSOR_DELAY_UI);
                haveSensor2= mSensorManager.registerListener(this,mMagnetometer, SensorManager.SENSOR_DELAY_UI);
            }
        }
        else
        {
            mRotationV = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            haveSensor = mSensorManager.registerListener(this,mRotationV, SensorManager.SENSOR_DELAY_UI);}
    }

    public void noSensonAler()
    {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());


        alertDialog.setMessage("your device does")
                .setCancelable(false)
                .setNegativeButton("close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        //finish();
                        stop();
                    }
                });
    }

    public void stop()
    {
        if(haveSensor&&haveSensor2)
        {
            mSensorManager.unregisterListener(this,mAccelerometer);
            mSensorManager.unregisterListener(this,mMagnetometer);
        }
        else
        {
            if(haveSensor)
            {//unregister the rotation
                mSensorManager.unregisterListener(this,mRotationV);
            }
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        stop();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        start();
    }

}