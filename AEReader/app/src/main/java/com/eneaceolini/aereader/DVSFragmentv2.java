package com.eneaceolini.aereader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.objdetect.HOGDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by User on 2/28/2017.
 */

public class DVSFragmentv2 extends Fragment{
    Button btnStart;
    Button btnStop;
    Button btnBias;
    Button btnConnect;
    TextView textInfo;
    private UsbDevice device;
    private UsbManager usbManager;
    private ReadEvents readEvents;
    private Spinner biasSpinner;
    ImageView imageView;
    Handler handler;
    Runnable runnable;
    int width_image, height_image;
    private static final String BIAS_FAST = "Fast";
    private static final String BIAS_SLOW = "Slow";


    // opencv stuff
    BlockingQueue<ArrayList> blockingQueue = new LinkedBlockingDeque<>();


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(getContext()) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View v = inflater.inflate(R.layout.dvs_activity_v2, container, false);
        assert v != null;


        btnStart = v.findViewById(R.id.start);
        btnStop = v.findViewById(R.id.stop);
        btnBias = v.findViewById(R.id.loadBias);
        btnConnect = v.findViewById(R.id.connect);
        textInfo = v.findViewById(R.id.info);
        biasSpinner = v.findViewById(R.id.spinner);
        imageView = v.findViewById(R.id.imageView);

        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        width_image = dm.heightPixels / 2;
        height_image = dm.heightPixels / 2;


        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                    btnBias.setEnabled(true);
                    btnStart.setEnabled(true);
                    Toast.makeText(getContext(), "Connected to DVS128!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Could not open device", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnStop.setEnabled(true);
                btnStart.setEnabled(false);
                readEvents.start();
                //create and start handler used to update GUI
                handler = new Handler();
                runnable = new Runnable() {
                    public void run() {
                        update_gui();
                    }
                };
                handler.post(runnable);
            }
        });

        btnStop.setOnClickListener(v1 -> {
            readEvents.stop_thread();
            btnStart.setEnabled(true);
            readEvents = new ReadEvents(getContext(), device, usbManager, blockingQueue);
        });

        btnBias.setOnClickListener(v12 -> {
            UsbInterface usbInterface = device.getInterface(0); // for DVS128

            UsbDeviceConnection connection = usbManager.openDevice(device);
            connection.claimInterface(usbInterface, true);

            String selectedBias = biasSpinner.getSelectedItem().toString();

            byte[] b = formatConfigurationBytes(selectedBias);

            int start = connection.controlTransfer(0, 0xb8, 0, 0, b, b.length, 0);
            Log.d("SEND BIAS", "" + start);
            Toast.makeText(getContext(), "Bias set!", Toast.LENGTH_SHORT).show();

        });
        return v;
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
//        Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_GRAY2RGBA, 4);
        bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
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
                            // extract HOG
//                            double[] feat = exportImgFeatures(frame);
//                            Log.d("HOG", "" + feat.length);
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

        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0d);
        Imgproc.threshold(blurred, thresh, 60d, 255, Imgproc.THRESH_BINARY);

        Moments moments = Imgproc.moments(thresh);
        return new Pair<>((int) (moments.m10 / moments.m00), (int) (moments.m01 / moments.m00));

    }

    public synchronized Mat fillMat(ArrayList<DVS128Processor.DVS128Event> events, int height, int width){
        DVS128Processor.DVS128Event e;
        int r;
        int g;
        int b;
        Mat mat = new Mat(height, width, CvType.CV_8UC4);
        for(int i = 0; i < height; i++){
            for(int j = 0; j < width; j++){
                mat.put(i, j, 0, 0, 0, 255);
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
                    // blu
                    r = 0;
                    g = 255;
                    b = 255;
                }
//                for (int j = 0; j < 4; j++) {
//                    for (int k = 0; k < 4; k++) {
//                        mat.put(e.x * 4 + j, e.y * 4 + k, r, g, 10, 255);
                        mat.put(127 - e.y, 127 - e.x, r, g, b, 255);
//                    }
//                }
            }
        }

        // find center
        Pair<Integer, Integer> center = findCenter(mat);
        int cX = center.first;
        int cY = center.second;
        int k = 60;
        int rc = 237, gc=255, bc=33;
        if (cX > 61 && cY > 61) {
            for (int i = 0; i < k; i++) {
                mat.put(cX - k / 2 + i, cY - k / 2, rc, gc, bc, 255);
                mat.put(cX - k / 2, cY - k / 2 + i, rc, gc, bc, 255);
                mat.put(cX - k / 2 + i, cY + k / 2, rc, gc, bc, 255);
                mat.put(cX + k / 2, cY - k / 2 + i, rc, gc, bc, 255);
            }
        }

//        Mat toProc = mat.su

        return mat;
    }

    private static double[] exportImgFeatures(Mat frame) {

        Mat mat = new Mat();

        Imgproc.cvtColor(frame, mat, Imgproc.COLOR_RGB2GRAY);

        HOGDescriptor hog = new HOGDescriptor(
                new Size(32, 32), //winSize
                new Size(16, 16), //blocksize
                new Size(8, 8), //blockStride,
                new Size(8, 8), //cellSize,
                9); //nbins

        MatOfFloat descriptors = new MatOfFloat();
        hog.compute(mat, descriptors);

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
