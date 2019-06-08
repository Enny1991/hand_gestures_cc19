package com.eneaceolini.aereader;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


public class MainActivity2 extends AppCompatActivity {

    private static final int NUM_FEAT_DVS = 1296;
    private static final int NUM_FEAT_EMG = 24;

    private UsbManager usbManager;
    private ReadEvents readEvents;
    ImageView imageView;
    TextView classDVS, classEMG, classJOIN;

    Handler handler;
    Runnable runnable;
    int width_image, height_image;
    private static final String BIAS_FAST = "Fast";
    private static final String BIAS_SLOW = "Slow";
    private static final Logger LOGGER = new Logger();
    private ClassifierTF.Model model = ClassifierTF.Model.QUANTIZED;
    private ClassifierTF.Device dev = ClassifierTF.Device.CPU;
    private int numThreads = -1;

    private ClassifierTF classifier;

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

    private Button scanDevices;
    private Button connectDVS;
    ArrayAdapter<String> adapter;
    ListView devicesList;
    AlertDialog devDialog;

    private CheckBox checkDVS, checkEMG, checkJOINT;

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
    private TextView scanningText;
    private ProgressBar prog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.dvs_emg);

        imageView = findViewById(R.id.imageView);

        classDVS = findViewById(R.id.classDVS);
        classEMG = findViewById(R.id.classEMG);
        classJOIN = findViewById(R.id.classJOIN);

        checkDVS = findViewById(R.id.checkDVS);
        checkEMG = findViewById(R.id.checkEMG);
        checkJOINT = findViewById(R.id.checkJOINT);

        scanDevices = findViewById(R.id.buttonScan);
        scanDevices.setOnClickListener(v1 -> deviceList());

        connectDVS = findViewById(R.id.connectDVS);
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

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(MainActivity2.this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                hog = new HOGDescriptor(
                        new Size(16, 16), //winSize
                        new Size(16, 16), //blockSize
                        new Size(8, 8), //blockStride,
                        new Size(8, 8), //cellSize,
                        9); //nBins

                svmDVS = loadSVM("linear_svm_dvs_v1.xml");
                svmEMG = loadSVM("linear_svm_emg_v1.xml");
                svmJOIN = loadSVM("linear_svm_jon_v1.xml");

                loadArray("mean_dvs", meanDVS);
                loadArray("std_dvs", stdDVS);
                loadArray("mean_emg", meanEMG);
                loadArray("std_emg", stdEMG);
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    private SVM loadSVM(String fileName) {

        InputStream inputStream;
        try {
            inputStream = Objects.requireNonNull(MainActivity2.this).getAssets().open(fileName);
            File file = createFileFromInputStream(inputStream);
            assert file != null;
            return SVM.load(file.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadArray(String fileName, float[] array) {
        try {
            InputStream inputStream = MainActivity2.this.getAssets().open(fileName);
            File file = createFileFromInputStream(inputStream);
            assert file != null;
            FileInputStream is = new FileInputStream(file.getPath());
            //noinspection UnstableApiUsage
            LittleEndianDataInputStream dis = new LittleEndianDataInputStream(is);
            int count = 0;
            while (dis.available() > 0) {
                array[count] = dis.readFloat();
                count += 1;
            }

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
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, MainActivity2.this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        // TFLITE

        try {
            LOGGER.d("Creating classifier (model=%s, device=%s, numThreads=%d)", model, dev, numThreads);
            classifier = ClassifierTF.create(MainActivity2.this, model, dev, numThreads);

        } catch (IOException e) {
            LOGGER.e(e, "Failed to create classifier.");
        }
    }

    public void deviceList() {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity2.this);
        LayoutInflater inflater = this.getLayoutInflater();
        @SuppressLint("InflateParams") final View dialogView = inflater.inflate(R.layout.activity_list, null);
        dialogBuilder.setView(dialogView);

        prog = (ProgressBar) dialogView.findViewById(R.id.progressBar2);
//        scanButton = (Button) dialogView.findViewById(R.id.scanButton);
//        scanningText = (TextView) dialogView.findViewById(R.id.scanning_text);

        mHandler = new Handler();
        if (!MainActivity2.this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(MainActivity2.this, "Bluetooth Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) MainActivity2.this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        devicesList = dialogView.findViewById(R.id.listDevices);
        adapter = new ArrayAdapter<>(MainActivity2.this, android.R.layout.simple_expandable_list_item_1, deviceNames);

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
            Toast.makeText(MainActivity2.this, item + " is connecting...", Toast.LENGTH_SHORT).show();
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
        scanningText.setVisibility(View.VISIBLE);
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
                Toast.makeText(MainActivity2.this, "Scan Stopped", Toast.LENGTH_SHORT).show();
                prog.setVisibility(View.INVISIBLE);
                scanningText.setVisibility(View.INVISIBLE);

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
                    Toast.makeText(MainActivity2.this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
                }
                // Trying to connect GATT
                plotter = new Plotter(mHandler, mChart, currentEMG);
                mMyoCallback = new MyoGattCallbackv2(mHandler, plotter);
                mBluetoothGatt = device.connectGatt(MainActivity2.this, false, mMyoCallback);
                mMyoCallback.setBluetoothGatt(mBluetoothGatt);
            }
        }
    };


    private File createFileFromInputStream(InputStream inputStream) {

        try {
            File f = new File(MainActivity2.this.getFilesDir().getPath() + "/local_dump_smv.xml");
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
                Toast.makeText(MainActivity2.this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
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
            PendingIntent permissionIntent = PendingIntent.getBroadcast(MainActivity2.this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(usbReceiver, filter);
            usbManager.requestPermission(device, permissionIntent);
            Toast.makeText(MainActivity2.this, "Connected to DVS128!", Toast.LENGTH_SHORT).show();

            // bias
            if (null != device) {
                UsbInterface usbInterface = device.getInterface(0); // for DVS128

                UsbDeviceConnection connection = usbManager.openDevice(device);
                connection.claimInterface(usbInterface, true);

                byte[] b = formatConfigurationBytes(BIAS_FAST);

                int start = connection.controlTransfer(0, 0xb8, 0, 0, b, b.length, 0);
                Log.d("SEND BIAS", "" + start);
                Toast.makeText(MainActivity2.this, "Bias set!", Toast.LENGTH_SHORT).show();

                readEvents = new ReadEvents(MainActivity2.this, device, usbManager, blockingQueue);

                readEvents.start();
                //create and start handler used to update GUI
                handler = new Handler();
                runnable = this::update_gui;
                handler.post(runnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity2.this, "Could not open device", Toast.LENGTH_SHORT).show();
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
                            readEvents = new ReadEvents(MainActivity2.this, device, usbManager, blockingQueue);
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
        Mat mat = new Mat(height, width, CvType.CV_8UC1);
        Mat toShow = new Mat(height, width, CvType.CV_8UC4);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                mat.put(i, j, (short) 0);
                col[i][j] = 0;
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
                col[127 - e.y][127 - e.x] += 1;
                toShow.put(127 - e.y, 127 - e.x, r, g, b, 255);
                mat.put(127 - e.y, 127 - e.x, (short) (col[127 - e.y][127 - e.x] * 255));
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
                toShow.put(cX - halfCenter + i, cY - halfCenter, rc, gc, bc, 255);
                toShow.put(cX - halfCenter, cY - halfCenter + i, rc, gc, bc, 255);
                toShow.put(cX - halfCenter + i, cY + halfCenter, rc, gc, bc, 255);
                toShow.put(cX + halfCenter, cY - halfCenter + i, rc, gc, bc, 255);
            }

            final List<ClassifierTF.Recognition> results = classifier.recognizeImage(toProcess);

            Log.d("CNN", results.get(0).getTitle() + " :: " + results.get(0).getConfidence());
            classDVS.setText(results.get(0).getTitle());
            double[] hogDVS = exportImgFeatures(toProcess);

            // retrieve latest emg
            if (null != currentEMG && currentEMG.size() > 0) {

                float[] emg = new float[24];
                for (int i = 0; i < 24; i++) {
                    emg[i] = currentEMG.get(i);
                }

                Mat featDVS = new Mat(1, NUM_FEAT_DVS, CvType.CV_32F);
                Mat featEMG = new Mat(1, NUM_FEAT_EMG, CvType.CV_32F);
                Mat featJOIN = new Mat(1, NUM_FEAT_DVS + NUM_FEAT_EMG, CvType.CV_32F);

                for (int i = 0; i < NUM_FEAT_DVS; i++) {
                    featDVS.put(0, i, (hogDVS[i] - meanDVS[i]) / stdDVS[i]);
                    featJOIN.put(0, i + NUM_FEAT_EMG, (hogDVS[i] - meanDVS[i]) / stdDVS[i]);
                }

                for (int i = 0; i < NUM_FEAT_EMG; i++) {
                    featEMG.put(0, i, (emg[i] - meanEMG[i]) / stdEMG[i]);
                    featJOIN.put(0, i, (emg[i] - meanEMG[i]) / stdEMG[i]);
                }


                if (checkDVS.isChecked()) classDVS.setText(getLabel(svmDVS.predict(featDVS)));
                else classDVS.setText("---");
                if (checkEMG.isChecked()) classEMG.setText(getLabel(svmEMG.predict(featEMG)));
                else classEMG.setText("---");
                if (checkJOINT.isChecked()) classJOIN.setText(getLabel(svmJOIN.predict(featJOIN)));
                else classJOIN.setText("---");
            }
        }
        return toShow;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
       getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.connect:
                Intent intent = new Intent(this, ListActivity.class);
                startActivity(intent);
                return true;
            case R.id.disconnect:
                Intent mStartActivity = new Intent(MainActivity2.this, MainActivity2.class);
                int mPendingIntentId = 12;
                PendingIntent mPendingIntent = PendingIntent.getActivity(MainActivity2.this, mPendingIntentId, mStartActivity,
                        PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) MainActivity2.this.getSystemService(ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                System.exit(0);
                //closeBLEGatt();
                Toast.makeText(getApplicationContext(), "Close GATT", Toast.LENGTH_SHORT).show();
                return true;
        }
        return false;
    }

}


