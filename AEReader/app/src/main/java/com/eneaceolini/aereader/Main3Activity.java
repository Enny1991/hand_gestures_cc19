package com.eneaceolini.aereader;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eneaceolini.aereader.biases.IPot;
import com.eneaceolini.aereader.biases.IPotArray;
import com.github.mikephil.charting.charts.RadarChart;
import com.google.common.io.LittleEndianDataInputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.ml.SVM;
import org.opencv.objdetect.HOGDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class Main3Activity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private LinearLayout bottomSheetLayout;
    private LinearLayout gestureLayout;
    private BottomSheetBehavior sheetBehavior;
    protected TextView recognitionTextView,
            recognition1TextView,
            recognition2TextView,
            recognitionValueTextView,
            recognition1ValueTextView,
            recognition2ValueTextView;
//    protected TextView frameValueTextView,
//            cropValueTextView,
//            cameraResolutionTextView,
//            rotationTextView,

    protected TextView inferenceTimeTextView;
//    protected ImageView bottomSheetArrowImageView;
    private Spinner modelSpinner;
    private Spinner featuresSpinner;

    private ClassifierTF.Model modelDVS = ClassifierTF.Model.FLOAT;
    private ClassifierEMG.Model modelEMG = ClassifierEMG.Model.FLOAT;
    private ClassifierFUS.Model modelFUS = ClassifierFUS.Model.FLOAT;
    private int numThreads = -1;

    private static final int NUM_FEAT_DVS = 1296;
    private static final int NUM_FEAT_EMG = 16;

    private UsbManager usbManager;
    private ReadEvents readEvents;
    ImageView imageView;

    Handler handler;
    Runnable runnable;
    int width_image, height_image;
    private static final String BIAS_FAST = "Fast";
    private static final String BIAS_SLOW = "Slow";
    private static final Logger LOGGER = new Logger();

    private ClassifierTF.Device devDVS = ClassifierTF.Device.CPU;
    private ClassifierEMG.Device devEMG = ClassifierEMG.Device.CPU;
    private ClassifierFUS.Device devFUS = ClassifierFUS.Device.CPU;

    private ClassifierTF classifierDVS;
    private ClassifierEMG classifierEMG;
    private ClassifierFUS classifierFUS;

    private float[] meanDVS = new float[NUM_FEAT_DVS], stdDVS = new float[NUM_FEAT_DVS];
    private float[] meanEMG = new float[NUM_FEAT_EMG], stdEMG = new float[NUM_FEAT_EMG];

    // openCV stuff
    BlockingQueue<ArrayList> blockingQueue = new LinkedBlockingDeque<>();
    ArrayList<Float> currentEMG = new ArrayList<>();
    SVM svmDVS, svmEMG, svmJOIN;

    //emg
    private RadarChart mChart;
    private Plotter plotter;
    private static final int REQUEST_ENABLE_BT = 1;

    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    //    private TextView emgDataText;
    private TextView myoConnectionText;
    private TextView connectingText;
    private BluetoothLeScanner mLEScanner;

    private MyoGattCallbackv2 mMyoCallback;

    private String deviceName;

    HOGDescriptor hog;

    private ImageView scanDevices;
    private ImageView connectDVS;
    ArrayAdapter<String> adapter;
    ListView devicesList;
    AlertDialog devDialog;

    ArrayList<Integer> majorVoting = new ArrayList<>();
    int countVoting = 0;
    private static final int ARRAY_MAJORITY = 10;
    private static final int DELAY_MAJORITY = 5;


    /** The model type used for classification. */
    public enum Model {
        SVM,
        CNN,
    }

    /** The runtime device type used for executing classification. */
    public enum Features {
        DVS,
        EMG,
        JOINT
    }

    private Model useModel = Model.valueOf("SVM");
    private Features useFeatures = Features.valueOf("DVS");

    /**
     * Device Scanning Time (ms)
     */
    private static final long SCAN_PERIOD = 5000;

    /**
     * Intent code for requesting Bluetooth enable
     */

    private ArrayList<String> deviceNames = new ArrayList<>();
    public static String myoName = null;

    private ScanSettings settings;
    private List<ScanFilter> filters;
//    private TextView scanningText;
    private ProgressBar prog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        for (int i=0; i<ARRAY_MAJORITY; i++)
            majorVoting.add(0);

        modelSpinner = findViewById(R.id.model_spinner);
        featuresSpinner = findViewById(R.id.features_spinner);
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
        gestureLayout = findViewById(R.id.gesture_layout);
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
//        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);

        ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        //                int width = bottomSheetLayout.getMeasuredWidth();
                        int height = gestureLayout.getMeasuredHeight();

                        sheetBehavior.setPeekHeight(height);
                    }
                });
        sheetBehavior.setHideable(false);

        sheetBehavior.setBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        switch (newState) {
                            case BottomSheetBehavior.STATE_HIDDEN:
                                break;
                            case BottomSheetBehavior.STATE_EXPANDED:
                            {
//                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                            }
                            break;
                            case BottomSheetBehavior.STATE_COLLAPSED:
                            {
//                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                            }
                            break;
                            case BottomSheetBehavior.STATE_DRAGGING:
                                break;
                            case BottomSheetBehavior.STATE_SETTLING:
//                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                                break;
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
                });

        recognitionTextView = findViewById(R.id.detected_item);
        recognitionValueTextView = findViewById(R.id.detected_item_value);
        recognition1TextView = findViewById(R.id.detected_item1);
        recognition1ValueTextView = findViewById(R.id.detected_item1_value);
        recognition2TextView = findViewById(R.id.detected_item2);
        recognition2ValueTextView = findViewById(R.id.detected_item2_value);

//        frameValueTextView = findViewById(R.id.frame_info);
//        cropValueTextView = findViewById(R.id.crop_info);
//        cameraResolutionTextView = findViewById(R.id.view_info);
//        rotationTextView = findViewById(R.id.rotation_info);
        inferenceTimeTextView = findViewById(R.id.inference_info);

        modelSpinner.setOnItemSelectedListener(this);
        featuresSpinner.setOnItemSelectedListener(this);

        imageView = findViewById(R.id.imageView);

        scanDevices = findViewById(R.id.connect_myo);
        scanDevices.setOnClickListener(v1 -> deviceList());

        connectDVS = findViewById(R.id.connect_dvs);
        connectDVS.setOnClickListener(v1 -> connectToDVS());

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        width_image = dm.heightPixels / 2;
        height_image = dm.heightPixels / 2;

        //////// MYO STUFF
        mChart = findViewById(R.id.chart);
        mHandler = new Handler();
        BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        //////// END
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (parent == modelSpinner) {
            useModel = Model.valueOf(parent.getItemAtPosition(pos).toString().toUpperCase());
        } else if (parent == featuresSpinner) {
            useFeatures = Features.valueOf(parent.getItemAtPosition(pos).toString());
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(Main3Activity.this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                hog = new HOGDescriptor(
                        new Size(16, 16), //winSize
                        new Size(16, 16), //blockSize
                        new Size(8, 8), //blockStride,
                        new Size(8, 8), //cellSize,
                        9); //nBins

                svmDVS = loadSVM("lin_svm_dvs.xml");
                svmEMG = loadSVM("lin_svm_emg.xml");
                svmJOIN = loadSVM("lin_svm_emgdvs.xml");

                loadArray("lin_svm_dvs.mean", meanDVS);
                loadArray("lin_svm_dvs.std", stdDVS);
                loadArray("lin_svm_emg.mean", meanEMG);
                loadArray("lin_svm_emg.std", stdEMG);
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    private SVM loadSVM(String fileName) {

        InputStream inputStream;
        try {
            inputStream = Objects.requireNonNull(Main3Activity.this).getAssets().open(fileName);
            File file = createFileFromInputStream(inputStream);
            assert file != null;
            return SVM.load(file.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadArray(String fileName, float[] array) {
        float min=1e15f, max=0;
        try {
            InputStream inputStream = Main3Activity.this.getAssets().open(fileName);
            File file = createFileFromInputStream(inputStream);
            assert file != null;
            FileInputStream is = new FileInputStream(file.getPath());
            //noinspection UnstableApiUsage
            LittleEndianDataInputStream dis = new LittleEndianDataInputStream(is);
            int count = 0;
            while (dis.available() > 0) {
                array[count] = dis.readFloat();
                if(array[count] > max)
                    max = array[count];

                if(array[count] < min)
                    min = array[count];
                count += 1;
            }

            Log.d("MIMMAX VALS", "min :: " + min + "  :: MAX ::" + max);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (null != handler)
            handler.removeCallbacks(runnable);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, Main3Activity.this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        // TFLITE

        try {
            classifierDVS = ClassifierTF.create(Main3Activity.this, modelDVS, devDVS, numThreads);
            classifierEMG = ClassifierEMG.create(Main3Activity.this, modelEMG, devEMG, numThreads);
            classifierFUS = ClassifierFUS.create(Main3Activity.this, modelFUS, devFUS, numThreads);

        } catch (IOException e) {
            LOGGER.e(e, "Failed to create classifier.");
        }
    }

    public void deviceList() {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(Main3Activity.this);
        LayoutInflater inflater = this.getLayoutInflater();
        @SuppressLint("InflateParams") final View dialogView = inflater.inflate(R.layout.activity_list, null);
        dialogBuilder.setView(dialogView);

        prog = (ProgressBar) dialogView.findViewById(R.id.progressBar2);

        mHandler = new Handler();
        if (!Main3Activity.this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(Main3Activity.this, "Bluetooth Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) Main3Activity.this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        devicesList = dialogView.findViewById(R.id.listDevices);
        adapter = new ArrayAdapter<>(Main3Activity.this, android.R.layout.simple_expandable_list_item_1, deviceNames);

        devicesList.setAdapter(adapter);


        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                filters = new ArrayList<>();
            }
        }

        devicesList.setOnItemClickListener((parent, view, position, id) -> {
            ListView listView = (ListView) parent;
            String item = (String) listView.getItemAtPosition(position);
            Toast.makeText(Main3Activity.this, item + " is connecting...", Toast.LENGTH_SHORT).show();
            myoName = item;
            deviceName = item;
            // DO OTHER STUFF
            Log.d("SELECTED", myoName);
            devDialog.dismiss();
            connectToMyo();
        });

        scanDevice();

        dialogBuilder.setTitle("Devices");

        devDialog = dialogBuilder.create();
        devDialog.show();
    }

    private void scanDevice() {
//        scanningText.setVisibility(View.VISIBLE);
        prog.setVisibility(View.VISIBLE);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            deviceNames.clear();
            // Scanning Time out by Handler.
            // The device scanning needs high energy.
            mHandler.postDelayed(() -> {
                mLEScanner.stopScan(mScanCallback2);
                adapter.notifyDataSetChanged();
                Toast.makeText(Main3Activity.this, "Scan Stopped", Toast.LENGTH_SHORT).show();
                prog.setVisibility(View.INVISIBLE);
//                scanningText.setVisibility(View.INVISIBLE);

            }, SCAN_PERIOD);
            mLEScanner.startScan(filters, settings, mScanCallback2);
        }
    }

    private ScanCallback mScanCallback2 = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("callbackType", String.valueOf(callbackType));
            Log.d("result", result.toString());
            BluetoothDevice device = result.getDevice();
            ParcelUuid[] uuids = device.getUuids();
            StringBuilder uuid = new StringBuilder();
            if (uuids != null) {
                for (ParcelUuid puuid : uuids) {
                    uuid.append(puuid.toString()).append(" ");
                }
            }

            String msg = "name=" + device.getName() + ", bondStatus="
                    + device.getBondState() + ", address="
                    + device.getAddress() + ", type" + device.getType()
                    + ", uuids=" + uuid;
            Log.d("BLEActivity", msg);

            if (device.getName() != null && !deviceNames.contains(device.getName())) {
                deviceNames.add(device.getName());
            }
            Log.d("SCANNING", "onScanResult: Device = " + result.getDevice().getName());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d("BTScan", "ENTERED onBatchScanResult");
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (deviceName.equals(device.getName())) {
                //mLEScanner.stopScan(scanCallback);
                BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
                if (scanner != null) {
                    scanner.stopScan(mScanCallback);
                } else {
                    // Device Bluetooth is disabled; scanning should already be stopped, nothing to do here.
                    Toast.makeText(Main3Activity.this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
                }
                // Trying to connect GATT
                plotter = new Plotter(mHandler, mChart, currentEMG);
                mMyoCallback = new MyoGattCallbackv2(mHandler, plotter);
                mBluetoothGatt = device.connectGatt(Main3Activity.this, false, mMyoCallback);
                mMyoCallback.setBluetoothGatt(mBluetoothGatt);
            }
        }
    };


    private File createFileFromInputStream(InputStream inputStream) {

        try {
            File f = new File(Main3Activity.this.getFilesDir().getPath() + "/local_dump_smv.xml");
            OutputStream outputStream = new FileOutputStream(f);
            byte[] buffer = new byte[1024];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            return f;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void connectToMyo(){

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {

            //mLEScanner.startScan(mScanCallback);
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (scanner != null) {
                scanner.startScan(mScanCallback);
            } else {
                // Device Bluetooth is disabled; check and prompt user to enable.
                Toast.makeText(Main3Activity.this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void connectToDVS() {
        try {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

            UsbDevice device = deviceList.get(deviceList.keySet().iterator().next());
            assert device != null;
            Log.d("DEVICE", "CONNECTED:: " + device.getDeviceName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(Main3Activity.this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(usbReceiver, filter);
            usbManager.requestPermission(device, permissionIntent);
            Toast.makeText(Main3Activity.this, "Connected to DVS128!", Toast.LENGTH_SHORT).show();

            // bias
            if (null != device) {
                UsbInterface usbInterface = device.getInterface(0); // for DVS128

                UsbDeviceConnection connection = usbManager.openDevice(device);
                connection.claimInterface(usbInterface, true);

                byte[] b = formatConfigurationBytes(BIAS_FAST);

                int start = connection.controlTransfer(0, 0xb8, 0, 0, b, b.length, 0);
                Log.d("SEND BIAS", "" + start);
                Toast.makeText(Main3Activity.this, "Bias set!", Toast.LENGTH_SHORT).show();

                readEvents = new ReadEvents(Main3Activity.this, device, usbManager, blockingQueue, new DVS128Processor());

                readEvents.start();
                //create and start handler used to update GUI
                handler = new Handler();
                runnable = this::update_gui;
                handler.post(runnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(Main3Activity.this, "Could not open device", Toast.LENGTH_SHORT).show();
        }

    }

    public byte[] formatConfigurationBytes(String config) {
        // we need to cast from PotArray to IPotArray, because we need the shift register stuff

        IPotArray potArray = new IPotArray();
        Log.d("BIAS", "Sending bias config:" + config);
        switch (config) {
            case BIAS_FAST:
                potArray.addPot(new IPot("cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 1992, 2, "Photoreceptor cascode"));
                potArray.addPot(new IPot("injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 1108364, 7, "Differentiator switch level, higher to turn on more"));
                potArray.addPot(new IPot("reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N, 16777215, 12, "AER request pulldown"));
                potArray.addPot(new IPot("puX", 8, IPot.Type.NORMAL, IPot.Sex.P, 8159221, 11, "2nd dimension AER static pullup"));
                potArray.addPot(new IPot("diffOff", 7, IPot.Type.NORMAL, IPot.Sex.N, 132, 6, "OFF threshold, lower to raise threshold"));
                potArray.addPot(new IPot("req", 6, IPot.Type.NORMAL, IPot.Sex.N, 309590, 8, "OFF request inverter bias"));
                potArray.addPot(new IPot("refr", 5, IPot.Type.NORMAL, IPot.Sex.P, 969, 9, "Refractory period"));
                potArray.addPot(new IPot("puY", 4, IPot.Type.NORMAL, IPot.Sex.P, 16777215, 10, "1st dimension AER static pullup"));
                potArray.addPot(new IPot("diffOn", 3, IPot.Type.NORMAL, IPot.Sex.N, 209996, 5, "ON threshold - higher to raise threshold"));
                potArray.addPot(new IPot("diff", 2, IPot.Type.NORMAL, IPot.Sex.N, 13125, 4, "Differentiator"));
                potArray.addPot(new IPot("foll", 1, IPot.Type.NORMAL, IPot.Sex.P, 271, 3, "Src follower buffer between photoreceptor and differentiator"));
                potArray.addPot(new IPot("Pr", 0, IPot.Type.NORMAL, IPot.Sex.P, 217, 1, "Photoreceptor"));
                break;
            case BIAS_SLOW:
                potArray.addPot(new IPot("cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 54, 2, "Photoreceptor cascode"));
                potArray.addPot(new IPot("injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 1108364, 7, "Differentiator switch level, higher to turn on more"));
                potArray.addPot(new IPot("reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N, 16777215, 12, "AER request pulldown"));
                potArray.addPot(new IPot("puX", 8, IPot.Type.NORMAL, IPot.Sex.P, 8159221, 11, "2nd dimension AER static pullup"));
                potArray.addPot(new IPot("diffOff", 7, IPot.Type.NORMAL, IPot.Sex.N, 132, 6, "OFF threshold, lower to raise threshold"));
                potArray.addPot(new IPot("req", 6, IPot.Type.NORMAL, IPot.Sex.N, 159147, 8, "OFF request inverter bias"));
                potArray.addPot(new IPot("refr", 5, IPot.Type.NORMAL, IPot.Sex.P, 6, 9, "Refractory period"));
                potArray.addPot(new IPot("puY", 4, IPot.Type.NORMAL, IPot.Sex.P, 16777215, 10, "1st dimension AER static pullup"));
                potArray.addPot(new IPot("diffOn", 3, IPot.Type.NORMAL, IPot.Sex.N, 482443, 5, "ON threshold - higher to raise threshold"));
                potArray.addPot(new IPot("diff", 2, IPot.Type.NORMAL, IPot.Sex.N, 30153, 4, "Differentiator"));
                potArray.addPot(new IPot("foll", 1, IPot.Type.NORMAL, IPot.Sex.P, 51, 3, "Src follower buffer between photoreceptor and differentiator"));
                potArray.addPot(new IPot("Pr", 0, IPot.Type.NORMAL, IPot.Sex.P, 3, 1, "Photoreceptor"));
                break;
        }


        // we make an array of bytes to hold the values sent, then we fill the array, copy it to a
        // new array of the proper size, and pass it to the routine that actually sends a vendor request
        // with a data buffer that is the bytes

        byte[] bytes = new byte[potArray.getNumPots() * 8];
        int byteIndex = 0;


        Iterator i = potArray.getShiftRegisterIterator();
        while (i.hasNext()) {
            // for each bias starting with the first one (the one closest to the ** FAR END ** of the shift register
            // we get the binary representation in byte[] form and from MSB ro LSB stuff these values into the byte array
            IPot iPot = (IPot) i.next();
            byte[] thisBiasBytes = iPot.getBinaryRepresentation();
            System.arraycopy(thisBiasBytes, 0, bytes, byteIndex, thisBiasBytes.length);
            byteIndex += thisBiasBytes.length;
        }
        byte[] toSend = new byte[byteIndex];
        System.arraycopy(bytes, 0, toSend, 0, byteIndex);
        return toSend;
    }

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //call method to set up device communication
                            readEvents = new ReadEvents(Main3Activity.this, device, usbManager, blockingQueue, new CochleaAms1CProcessor());
                            Log.d("DEVICE", "CONNECTED");
                        }
                    } else {
                        Log.d("TAG", "permission denied for device " + device);
                    }
                }
            }
        }
    };

    public Bitmap mat2Bit(Mat mat) {
        Bitmap bmp = null;
        try {
            bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bmp);

        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
        }
        return bmp;
    }

    long lastTime = System.currentTimeMillis();

    public Bitmap onCameraFrame() {
        lastTime = System.currentTimeMillis();
        Bitmap map = null;
        Mat frame = null;
        ArrayList<DVS128Processor.DVS128Event> toDraw;
        if (null != readEvents) {
            synchronized (this) {
                try {
                    if (blockingQueue.size() > 0) {
                        toDraw = blockingQueue.take();
                        if (toDraw.size() > 0)
                            frame = fillMat(toDraw, 128, 128);

                        if (null != frame) {
                            map = mat2Bit(frame);
                        }

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return map;
    }

    public synchronized Pair<Integer, Integer> findCenter(Mat frame) {
        Moments moments = Imgproc.moments(frame);
        return new Pair<>((int) (moments.m10 / moments.m00), (int) (moments.m01 / moments.m00));
    }

    public synchronized Mat fillMat(ArrayList<DVS128Processor.DVS128Event> events, int height, int width) {
        DVS128Processor.DVS128Event e;
        int r, g, b;
        float[][] col = new float[height][width];
        float max = 0;
        Mat mat = new Mat(height, width, CvType.CV_8UC1);
        Mat toShow = new Mat(height, width, CvType.CV_8UC4);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
//                mat.put(i, j, (short) 0);
//                col[i][j] = 0;
                toShow.put(i, j, 0, 0, 0, 255);
            }
        }

        for (int i = 0; i < events.size(); i += 10) {
            e = events.get(i);
            if (null != e) {
                if (e.polarity > 0) {
                    r = 255; g = 0; b = 255; // magenta
                } else {
                    r = 0; g = 255; b = 255; // blue
                }
                if ((127 - e.y > 20) && (127 - e.y < 110) && (127 - e.x > 20) && (127 - e.x < 110)) {
                    col[127 - e.y][127 - e.x] += 1;
                    if (col[127 - e.y][127 - e.x] > max)
                        max = col[127 - e.y][127 - e.x];
//
                }
//                mat.put(127 - e.y, 127 - e.x, (short) (col[127 - e.y][127 - e.x] * 255));

                toShow.put(127 - e.y, 127 - e.x, r, g, b, 255);
            }
        }

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                col[i][j] /= max;
                mat.put(i, j, (short) (col[i][j] * 255));
            }
        }



        // find center
        Pair<Integer, Integer> center = findCenter(mat);
        int cX = center.first;
        int cY = center.second;
        int halfCenter = 30;
        int rc = 237, gc = 255, bc = 33;
        if (cX > halfCenter && cX < (128 - halfCenter) && cY > halfCenter && cY < (128 - halfCenter)) {

            Mat toProcess = mat.submat(cY - halfCenter, cY + halfCenter, cX - halfCenter, cX + halfCenter);

            // yellow square
            for (int i = 0; i < halfCenter * 2; i++) {
                toShow.put(cY - halfCenter + i, cX - halfCenter, rc, gc, bc, 255);
                toShow.put(cY - halfCenter, cX - halfCenter + i, rc, gc, bc, 255);
                toShow.put(cY - halfCenter + i, cX + halfCenter, rc, gc, bc, 255);
                toShow.put(cY + halfCenter, cX - halfCenter + i, rc, gc, bc, 255);
            }


            long start = SystemClock.uptimeMillis();
            long stop = SystemClock.uptimeMillis() - start;

//            if (useModel == Model.CNN && useFeatures == Features.DVS) {
//                start = SystemClock.uptimeMillis();
//                final List<ClassifierTF.Recognition> results = classifierDVS.recognizeImage(toProcess);
//                MyPair[] res = majorityVoting(Float.parseFloat(results.get(0).getId()));
//                if (null != res){
//                    recognitionTextView.setText(getLabel((float) res[0].index));
//                    recognitionValueTextView.setText(String.format("%.2f", (100 * res[0].value)) + "%");
//                    recognition1TextView.setText(getLabel((float) res[1].index));
//                    recognition1ValueTextView.setText(String.format("%.2f", (100 * res[1].value)) + "%");
//                    recognition2TextView.setText(getLabel((float) res[2].index));
//                    recognition2ValueTextView.setText(String.format("%.2f", (100 * res[2].value)) + "%");
//                }
//                stop = SystemClock.uptimeMillis() - start;
//              }


            double[] hogDVS = exportImgFeatures(toProcess);

            Mat featDVS = new Mat(1, NUM_FEAT_DVS, CvType.CV_32F);

            for (int i = 0; i < NUM_FEAT_DVS; i++) {
                featDVS.put(0, i, (hogDVS[i] - meanDVS[i]) / stdDVS[i]);
            }

            if (useModel == Model.SVM && useFeatures == Features.DVS) {
                start = SystemClock.uptimeMillis();
                float pred = svmDVS.predict(featDVS);
                MyPair[] res = majorityVoting(pred);
                if (null != res){
                    recognitionTextView.setText(getLabel((float) res[0].index));
                    recognitionValueTextView.setText(String.format("%.2f", (100 * res[0].value)) + "%");
                    recognition1TextView.setText(getLabel((float) res[1].index));
                    recognition1ValueTextView.setText(String.format("%.2f", (100 * res[1].value)) + "%");
                    recognition2TextView.setText(getLabel((float) res[2].index));
                    recognition2ValueTextView.setText(String.format("%.2f", (100 * res[2].value)) + "%");
                }
                stop = SystemClock.uptimeMillis() - start;
            }

            // retrieve latest emg
            if (null != currentEMG && currentEMG.size() > 0) {

                float[] emg = new float[NUM_FEAT_EMG];
                for (int i = 0; i < NUM_FEAT_EMG; i++) {
                    emg[i] = currentEMG.get(i);
                }

                Mat featEMG = new Mat(1, NUM_FEAT_EMG, CvType.CV_32F);
                Mat featJOIN = new Mat(1, NUM_FEAT_DVS + NUM_FEAT_EMG, CvType.CV_32F);

                for (int i = 0; i < NUM_FEAT_DVS; i++) {
                    featJOIN.put(0, i + NUM_FEAT_EMG, (hogDVS[i] - meanDVS[i]) / stdDVS[i]);
                }

                for (int i = 0; i < NUM_FEAT_EMG; i++) {
                    featEMG.put(0, i, (emg[i] - meanEMG[i]) / stdEMG[i]);
                    featJOIN.put(0, i, (emg[i] - meanEMG[i]) / stdEMG[i]);
                }

                if (useModel == Model.SVM) {
                    start = SystemClock.uptimeMillis();

                    if (useFeatures == Features.EMG){
                        float pred = svmEMG.predict(featEMG);
                        MyPair[] res = majorityVoting(pred);
                        if (null != res){
                            recognitionTextView.setText(getLabel((float) res[0].index));
                            recognitionValueTextView.setText(String.format("%.2f", (100 * res[0].value)) + "%");
                            recognition1TextView.setText(getLabel((float) res[1].index));
                            recognition1ValueTextView.setText(String.format("%.2f", (100 * res[1].value)) + "%");
                            recognition2TextView.setText(getLabel((float) res[2].index));
                            recognition2ValueTextView.setText(String.format("%.2f", (100 * res[2].value)) + "%");
                        }
                    }
                    if (useFeatures == Features.JOINT){
                        float pred = svmJOIN.predict(featJOIN);
                        MyPair[] res = majorityVoting(pred);
                        if (null != res){
                            recognitionTextView.setText(getLabel((float) res[0].index));
                            recognitionValueTextView.setText(String.format("%.2f", (100 * res[0].value)) + "%");
                            recognition1TextView.setText(getLabel((float) res[1].index));
                            recognition1ValueTextView.setText(String.format("%.2f", (100 * res[1].value)) + "%");
                            recognition2TextView.setText(getLabel((float) res[2].index));
                            recognition2ValueTextView.setText(String.format("%.2f", (100 * res[2].value)) + "%");
                        }
                    }
                    stop = SystemClock.uptimeMillis() - start;
                }


                if (useModel == Model.CNN){

                    if (useFeatures == Features.EMG){
                        start = SystemClock.uptimeMillis();
                        final List<ClassifierEMG.Recognition> results = classifierEMG.recognizeEMG(featEMG);
                        MyPair[] res = majorityVoting(Float.parseFloat(results.get(0).getId()));
                        if (null != res){
                            recognitionTextView.setText(getLabel((float) res[0].index));
                            recognitionValueTextView.setText(String.format("%.2f", (100 * res[0].value)) + "%");
                            recognition1TextView.setText(getLabel((float) res[1].index));
                            recognition1ValueTextView.setText(String.format("%.2f", (100 * res[1].value)) + "%");
                            recognition2TextView.setText(getLabel((float) res[2].index));
                            recognition2ValueTextView.setText(String.format("%.2f", (100 * res[2].value)) + "%");
                        }
                        stop = SystemClock.uptimeMillis() - start;
                    }

                    if (useFeatures == Features.DVS){
                        start = SystemClock.uptimeMillis();
                        final List<ClassifierTF.Recognition> results = classifierDVS.recognizeImage(toProcess);
                        MyPair[] res = majorityVoting(Float.parseFloat(results.get(0).getId()));
                        if (null != res){
                            recognitionTextView.setText(getLabel((float) res[0].index));
                            recognitionValueTextView.setText(String.format("%.2f", (100 * res[0].value)) + "%");
                            recognition1TextView.setText(getLabel((float) res[1].index));
                            recognition1ValueTextView.setText(String.format("%.2f", (100 * res[1].value)) + "%");
                            recognition2TextView.setText(getLabel((float) res[2].index));
                            recognition2ValueTextView.setText(String.format("%.2f", (100 * res[2].value)) + "%");
                        }
                        stop = SystemClock.uptimeMillis() - start;
                    }

                    if (useFeatures == Features.JOINT){
                        start = SystemClock.uptimeMillis();
                        final List<ClassifierFUS.Recognition> results = classifierFUS.recognizeJoint(toProcess, featEMG);

                        MyPair[] res = majorityVoting(Float.parseFloat(results.get(0).getId()));
                        if (null != res){
                            recognitionTextView.setText(getLabel((float) res[0].index));
                            recognitionValueTextView.setText(String.format("%.2f", (100 * res[0].value)) + "%");
                            recognition1TextView.setText(getLabel((float) res[1].index));
                            recognition1ValueTextView.setText(String.format("%.2f", (100 * res[1].value)) + "%");
                            recognition2TextView.setText(getLabel((float) res[2].index));
                            recognition2ValueTextView.setText(String.format("%.2f", (100 * res[2].value)) + "%");
                        }
                        stop = SystemClock.uptimeMillis() - start;
                    }
                }
            }
            inferenceTimeTextView.setText(stop + "ms");
        }

        return toShow;
    }

    private MyPair[] majorityVoting(float prediction){

        countVoting += 1;
        majorVoting.add((int) prediction);
        majorVoting.remove(0);
        if (countVoting == DELAY_MAJORITY) {
            MyPair[] resMaj = findMajor();
            countVoting = 0;
            return resMaj;
        }
        return null;
    }

    private MyPair[] findMajor() {
        float[] counter = new float[5];

        MyPair[] pairArray = new MyPair[5];

        // count
        for (int i=0; i<majorVoting.size(); i++){
            counter[majorVoting.get(i)] += 1;
        }

        for (int i=0; i<5; i++){
            pairArray[i] = new MyPair(i, counter[i] / ARRAY_MAJORITY);
        }

        Arrays.sort(pairArray);



        return pairArray;
    }

    public String getLabel(float res) {
        String _class = "None";
        if (res == 0.0) _class = "PINKY";
        if (res == 1.0) _class = "ELLE";
        if (res == 2.0) _class = "YO";
        if (res == 3.0) _class = "INDEX";
        if (res == 4.0) _class = "THUMB";
        return _class;
    }

    private double[] exportImgFeatures(Mat frame) {

        MatOfFloat descriptors = new MatOfFloat();
        hog.compute(frame, descriptors);

        float[] descArr = descriptors.toArray();
        double[] retArr = new double[descArr.length];
        for (int i = 0; i < descArr.length; i++) {
            retArr[i] = descArr[i];
        }
        return retArr;
    }

    /**
     * get data from thread and updates GUI
     */
    private void update_gui() {
        //get image from thread and display it
        Bitmap ima = onCameraFrame();
        if (ima != null) {
            Bitmap ima2 = Bitmap.createScaledBitmap(ima, width_image, height_image, false);
            imageView.setImageBitmap(ima2);
        }
        handler.postDelayed(runnable, 20);
    }

    public class MyPair implements Comparable<MyPair> {
        public final int index;
        public final float value;

        public MyPair(int index, float value) {
            this.index = index;
            this.value = value;
        }

        @Override
        public int compareTo(MyPair other) {
            //multiplied to -1 as the author need descending sort order
            return -1 * Float.valueOf(this.value).compareTo(other.value);
        }
    }

}
