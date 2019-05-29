package com.eneaceolini.aereader;

import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.github.mikephil.charting.charts.LineChart;
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
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.ml.SVM;
import org.opencv.ml.StatModel;
import org.opencv.objdetect.HOGDescriptor;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static android.content.Context.BLUETOOTH_SERVICE;

/**
 * Created by User on 2/28/2017.
 */

public class DVSPlusEMG extends Fragment{
    private UsbDevice device;
    private UsbManager usbManager;
    private ReadEvents readEvents;
    ImageView imageView;
    TextView classView;
    Handler handler;
    Runnable runnable;
    int width_image, height_image;
    private static final String BIAS_FAST = "Fast";
    private static final String BIAS_SLOW = "Slow";
    private static final Logger LOGGER = new Logger();
    private ClassifierTF.Model model = ClassifierTF.Model.QUANTIZED;
    private ClassifierTF.Device dev = ClassifierTF.Device.CPU;
    private int numThreads = -1;

    private float[] mean = new float[1296], std = new float[1296];
    private float[] mean_emg = new float[24], std_emg = new float[24];

    // opencv stuff
    BlockingQueue<ArrayList> blockingQueue = new LinkedBlockingDeque<>();
    BlockingQueue<float[]> blockingQueueEMG = new LinkedBlockingDeque<>();
    ArrayList<Float> currentEMG = new ArrayList<>();
    SVM svm = null;
    SVM svm_emg = null;
    SVM svm_join = null;
    Mat f;

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

    private MyoGattCallback mMyoCallback;
    private MyoCommandList commandList = new MyoCommandList();

    private String deviceName;

    Activity activity;
    private ProgressBar prog;
    private ToggleButton emgButton;

    private ScanCallback scanCallback = new ScanCallback() {
    };


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(getContext()) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {

                    try {
                        InputStream inputStream = getContext().getAssets().open("linear_svm_dvs_v1.xml");
                        File file = createFileFromInputStream(inputStream);

                        svm = SVM.load(file.getPath());
                        Log.d("SVM", "" + svm);

                        inputStream = getContext().getAssets().open("linear_svm_emg_v1.xml");
                        file = createFileFromInputStream(inputStream);

                        svm_emg = SVM.load(file.getPath());
                        f = new Mat(1, 24, CvType.CV_32F);

                        inputStream = getContext().getAssets().open("linear_svm_jon_v1.xml");
                        file = createFileFromInputStream(inputStream);

                        svm_join = SVM.load(file.getPath());

                        // load mean
                        inputStream = getContext().getAssets().open("mean_dvs");
                        file = createFileFromInputStream(inputStream);
                        FileInputStream is = new FileInputStream(file.getPath());
                        LittleEndianDataInputStream dis = new LittleEndianDataInputStream(is);
                        int count = 0;
                        while(dis.available()>0) {
                            mean[count] = dis.readFloat();
                            count += 1;
                        }
                        Log.d("Loading mean", "" + count);

                        // load std
                        inputStream = getContext().getAssets().open("std_dvs");
                        file = createFileFromInputStream(inputStream);
                        is = new FileInputStream(file.getPath());
                        dis = new LittleEndianDataInputStream(is);
                        count = 0;
                        while(dis.available()>0) {
                            std[count] = dis.readFloat();
                            count += 1;
                        }

                        Log.d("Loading std", "" + count);

                        // EMG

                        // load mean
                        inputStream = getContext().getAssets().open("mean_emg");
                        file = createFileFromInputStream(inputStream);
                        is = new FileInputStream(file.getPath());
                        dis = new LittleEndianDataInputStream(is);
                        count = 0;
                        while(dis.available()>0) {
                            mean_emg[count] = dis.readFloat();
                            count += 1;
                        }
                        Log.d("Loading mean", "" + count);

                        // load std
                        inputStream = getContext().getAssets().open("std_emg");
                        file = createFileFromInputStream(inputStream);
                        is = new FileInputStream(file.getPath());
                        dis = new LittleEndianDataInputStream(is);
                        count = 0;
                        while(dis.available()>0) {
                            std_emg[count] = dis.readFloat();
                            count += 1;
                        }

                        Log.d("Loading std", "" + count);

                    } catch (IOException e) {
                        Log.d("SVM", "Could not load SVM");
                        e.printStackTrace();
                    }

                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

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
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, getContext(), mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        connectToDVS();

        //TFLITE

        try {
            LOGGER.d(
                    "Creating classifier (model=%s, device=%s, numThreads=%d)", model, dev, numThreads);
            classifier = ClassifierTF.create(getActivity(), model, dev, numThreads);
//            classifier.runInference();
        } catch (IOException e) {
            LOGGER.e(e, "Failed to create classifier.");
        }

    }

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
                }

                // Trying to connect GATT
                plotter = new Plotter(mHandler, mChart, currentEMG);
//                plotter.setSVM(svm_emg, f, mean_emg, std_emg);
                mMyoCallback = new MyoGattCallback(mHandler, myoConnectionText, connectingText, plotter, getView());
                mBluetoothGatt = device.connectGatt(getActivity(), false, mMyoCallback);
                mMyoCallback.setBluetoothGatt(mBluetoothGatt);
            }
        }
    };

    private ClassifierTF classifier;

    private File createFileFromInputStream(InputStream inputStream) {

        try{
            File f = new File(getContext().getFilesDir().getPath() + "/local_dump_smv.xml");
            OutputStream outputStream = new FileOutputStream(f);
            byte buffer[] = new byte[1024];
            int length = 0;

            while((length=inputStream.read(buffer)) > 0) {
                outputStream.write(buffer,0,length);
            }

            outputStream.close();
            inputStream.close();

            return f;
        }catch (IOException e) {
            //Logging exception
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View v = inflater.inflate(R.layout.dvs_emg, container, false);
        assert v != null;

        imageView = v.findViewById(R.id.imageView);
        classView = v.findViewById(R.id.classView);

        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        width_image = dm.heightPixels / 2;
        height_image = dm.heightPixels / 2;

        mChart = v.findViewById(R.id.chart);

        //////// MYO STUFF

        mHandler = new Handler();
        activity = this.getActivity();

        BluetoothManager mBluetoothManager = (BluetoothManager) getActivity().getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        Intent intent = getActivity().getIntent();
        deviceName = intent.getStringExtra(ListActivity.TAG);

        ///// DELETE ///
        plotter = new Plotter(mHandler, mChart, currentEMG);
        plotter.setFeatures(new boolean[]{true, true, true});
        /////

        if (deviceName != null) {

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
                }
            }





        }

        //////// END


        return v;
    }

    private void connectToDVS() {
        try {
            usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

            device = deviceList.get(deviceList.keySet().iterator().next());
            Log.d("DEVICE", "CONNECTED:: " + device.getDeviceName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(getContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            getActivity().registerReceiver(usbReceiver, filter);
            usbManager.requestPermission(device, permissionIntent);
            Toast.makeText(getContext(), "Connected to DVS128!", Toast.LENGTH_SHORT).show();

            // bias
            if (null != device) {
                UsbInterface usbInterface = device.getInterface(0); // for DVS128

                UsbDeviceConnection connection = usbManager.openDevice(device);
                connection.claimInterface(usbInterface, true);

                byte[] b = formatConfigurationBytes(BIAS_FAST);

                int start = connection.controlTransfer(0, 0xb8, 0, 0, b, b.length, 0);
                Log.d("SEND BIAS", "" + start);
                Toast.makeText(getContext(), "Bias set!", Toast.LENGTH_SHORT).show();

                readEvents = new ReadEvents(getContext(), device, usbManager, blockingQueue);

                readEvents.start();
                //create and start handler used to update GUI
                handler = new Handler();
                runnable = this::update_gui;
                handler.post(runnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Could not open device", Toast.LENGTH_SHORT).show();
        }

    }

    public byte[] formatConfigurationBytes(String config) {
        // we need to cast from PotArray to IPotArray, because we need the shift register stuff

        PotArray potArray = new IPotArray();
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

        if (potArray instanceof IPotArray) {
            IPotArray ipots = (IPotArray) potArray;
            byte[] bytes = new byte[potArray.getNumPots() * 8];
            int byteIndex = 0;


            Iterator i = ipots.getShiftRegisterIterator();
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
        return null;
    }


    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //call method to set up device communication
                            readEvents = new ReadEvents(getContext(), device, usbManager, blockingQueue);
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
    Mat tmp = new Mat(mat.height(), mat.width(), CvType.CV_8UC4);
    try {
        Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_GRAY2RGBA, 4);
        bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(tmp, bmp);
    } catch(CvException e) {
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

    public synchronized Pair<Integer, Integer> findCenter(Mat frame){
        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat thresh = new Mat();

//        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY);
//        Imgproc.GaussianBlur(frame, blurred, new Size(5, 5), 0d);
//        Imgproc.threshold(blurred, thresh, 60d, 255, Imgproc.THRESH_BINARY);

        Moments moments = Imgproc.moments(frame);
        return new Pair<>((int) (moments.m10 / moments.m00), (int) (moments.m01 / moments.m00));

    }

    public synchronized Mat fillMat(ArrayList<DVS128Processor.DVS128Event> events, int height, int width){
        DVS128Processor.DVS128Event e;
        int r;
        int g;
        int b;
        double maxCount = 0;
        float[][] col = new float[height][width];
        Mat mat = new Mat(height, width, CvType.CV_8UC1);
        for(int i = 0; i < height; i++){
            for(int j = 0; j < width; j++){
                mat.put(i, j, (short) 0);
                col[i][j] = 0;
            }
        }

        for (int i = 0; i < events.size(); i += 10) {
            e = events.get(i);
            if (null != e) {
                if (e.polarity > 0) {
                    // magenta
                    r = 255;
                    g = 0;
                    b = 255;
                } else {
                    // blue
                    r = 0;
                    g = 255;
                    b = 255;
                }

                col[127 - e.y][127 - e.x] += 1;
                maxCount = Math.max(maxCount, col[127 - e.y][127 - e.x]);
//                mat.put(127 - e.y, 127 - e.x, (short) col[127 - e.y][127 - e.x]);
            }
        }

        short _v;
        for(int i = 0; i < height; i++){
            for(int j = 0; j < width; j++){
//                col[i][j] /= maxCount;
                mat.put(i, j, (short) (col[i][j] * 255));
            }
        }

        // find center
        Pair<Integer, Integer> center = findCenter(mat);
        int cX = center.first;
        int cY = center.second;
        int k = 60;
        int rc = 237, gc = 255, bc = 33;
        if (cX > k/2 && cX < (128 - k/2) && cY > k/2 && cY < (128 - k/2)) {

            Mat toProc = mat.submat(cY - k / 2, cY + k/2, cX - k/2, cX + k/2);
            // yellow square
            for (int i = 0; i < k; i++) {
                mat.put(cX - k / 2 + i, cY - k / 2, 255);
                mat.put(cX - k / 2, cY - k / 2 + i, 255);
                mat.put(cX - k / 2 + i, cY + k / 2, 255);
                mat.put(cX + k / 2, cY - k / 2 + i, 255);
            }

            double[] feat = exportImgFeatures(toProc);
//            Mat f = new Mat(1, feat.length, CvType.CV_32F);
//
//            for (int i=0; i< feat.length; i++)
//                f.put(0, i, (feat[i] - mean[i]) / std[i]);
//
//            float res = svm.predict(f);
//
//            String _class = "None";
//            if(res == 0.0) _class = "PINKY";
//            if(res == 1.0) _class = "ELLE";
//            if(res == 2.0) _class = "YO";
//            if(res == 3.0) _class = "INDEX";
//            if(res == 4.0) _class = "THUMB";
//
//            classView.setText(_class);

            // retrieve latest emg
            if (null != currentEMG){

                    float[] emg = new float[24];
                    for (int i = 0; i<24; i++){
                        emg[i] = currentEMG.get(i);
                    }

                    Mat f = new Mat(1, feat.length + 24, CvType.CV_32F);

                    for (int i=0; i<feat.length; i++)
                        f.put(0, i, (feat[i] - mean[i]) / std[i]);

                    for (int i=0; i<24; i++)
                        f.put(0, i + feat.length, (emg[i] - mean_emg[i]) / std_emg[i]);

                    float res = svm_join.predict(f);

                    String _class = "None";
                    if(res == 0.0) _class = "PINKY";
                    if(res == 1.0) _class = "ELLE";
                    if(res == 2.0) _class = "YO";
                    if(res == 3.0) _class = "INDEX";
                    if(res == 4.0) _class = "THUMB";

                    classView.setText(_class);

            }
        }

        return mat;
    }

    private static double[] exportImgFeatures(Mat frame) {

//        Mat mat = new Mat();

//        Imgproc.cvtColor(frame, mat, Imgproc.COLOR_RGB2GRAY);

        HOGDescriptor hog = new HOGDescriptor(
                new Size(16, 16), //winSize
                new Size(16, 16), //blocksize
                new Size(8, 8), //blockStride,
                new Size(8, 8), //cellSize,
                9); //nbins

        MatOfFloat descriptors = new MatOfFloat();
        hog.compute(frame, descriptors);

        float[] descArr = descriptors.toArray();
        double retArr[] = new double[descArr.length];
        for (int i = 0; i < descArr.length; i++) {
            retArr[i] = descArr[i];
        }
        return retArr;
    }

    /**
     * get data from thread and updates GUI
     */
    private void update_gui()
    {
        //get image from thread and display it
        Bitmap ima = onCameraFrame();

        if(ima != null)
        {
            Bitmap ima2 = Bitmap.createScaledBitmap(ima, width_image, height_image, false);
            imageView.setImageBitmap(ima2);
        }

        handler.postDelayed(runnable, 30);
    }
}
