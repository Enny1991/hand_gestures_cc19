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
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.Xml;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.objdetect.HOGDescriptor;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class Main4Activity extends AppCompatActivity {

    private UsbManager usbManager;
    private ReadEvents readEvents;

    private Button connectDAS, loadBias;

    BlockingQueue<ArrayList> blockingQueue = new LinkedBlockingDeque<>();

    ImageView imageView;
    Handler handler;
    Runnable runnable;
    int width_image, height_image;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    Mat toShow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        width_image = dm.heightPixels / 2;
        height_image = dm.heightPixels / 2;

        connectDAS = findViewById(R.id.connect_das);
        loadBias = findViewById(R.id.load_bias);
        imageView = findViewById(R.id.imageView);

        connectDAS.setOnClickListener(v1 -> connectToDAS());
//        loadBias.setOnClickListener(v1 -> loadXMLBiases());

    }

    private void loadXMLBiases(Biasgen biasgen) {
        String fileName = "CochleaAMS1cAOffchipPreampLocalization.xml";
        try {
            InputStream inputStream = Main4Activity.this.getAssets().open(fileName);
            BiasesXmlParser parser = new BiasesXmlParser();

            List<BiasesXmlParser.Bias> biases = parser.parse(inputStream);

//            for(BiasesXmlParser.Bias b: biases)
//                Log.d("LIST BIASES", b.key + " :: " + b.value);
            for(BiasesXmlParser.Bias b: biases){
                if (b.key.contains(".IPot")){
                    String[] parts = b.key.split("\\.");
                    Log.d("name", "" + b.key);
                    Log.d("parts", Arrays.toString(parts));
                    if (null != biasgen.ipots.getPotByName(parts[parts.length - 1]))
                        biasgen.ipots.getPotByName(parts[parts.length - 1]).setBitValue(b.value);
                } else if (b.key.contains("VPot")){
                    String[] parts = b.key.split("\\.");
                    if (null != biasgen.vpots.getPotByName(parts[parts.length - 1]))
                        biasgen.vpots.getPotByName(parts[parts.length - 1]).setBitValue(b.value);
                } else if (b.key.contains("powerDown")){
                    biasgen.powerDown.set(b.value == 1);
                } else if (b.key.contains("hostResetTimestamps")){
                    biasgen.hostResetTimestamps.set(b.value == 1);
                } else if (b.key.contains("runAERComm")){
                    biasgen.runAERComm.set(b.value == 1);
                } else if (b.key.contains("enableCPLDAERAck")){
                    biasgen.enableCPLDAERAck.set(b.value == 1);
                } else if (b.key.contains("timestampMasterExternalInputEventsEnabled")){
                    biasgen.timestampMasterExternalInputEventsEnabled.set(b.value == 1);
                } else if (b.key.contains("vCtrlKillBit")){
                    biasgen.vCtrlKillBit.set(b.value == 1);
                } else if (b.key.contains("aerKillBit")){
                    biasgen.aerKillBit.set(b.value == 1);
                } else if (b.key.contains("cochleaBitLatch")){
                    biasgen.cochleaBitLatch.set(b.value == 1);
                } else if (b.key.contains("cochleaReset")){
                    biasgen.cochleaReset.set(b.value == 1);
                } else if (b.key.contains("runAdc")){
//                    biasgen.runAdc.set(b.value == 1);
                    biasgen.runAdc.set(false);
                }
            }

            biasgen.sendFullConfig();
            biasgen.sendFullConfig();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //call method to set up device communication
                            readEvents = new ReadEvents(Main4Activity.this, device, usbManager, blockingQueue, new CochleaAms1CProcessor());
                            Log.d("DEVICE", "CONNECTED");
                        }
                    } else {
                        Log.d("TAG", "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private void connectToDAS() {
        try {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

            UsbDevice device = deviceList.get(deviceList.keySet().iterator().next());
            assert device != null;
            Log.d("DEVICE", "CONNECTED:: " + device.getDeviceName());
            Log.d("DEVICE", "Prod Name:: " + device.getProductName());
            Log.d("DEVICE", "Serial number:: " + device.getSerialNumber());
            Log.d("DEVICE", "DevId:: " + device.getDeviceId());
            Log.d("DEVICE", "Vendor Id:: " + device.getVendorId());
            Log.d("DEVICE", "Prod Id:: " + device.getProductId());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(Main4Activity.this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(usbReceiver, filter);
            usbManager.requestPermission(device, permissionIntent);
            Toast.makeText(Main4Activity.this, "Connected to AMS1c!", Toast.LENGTH_SHORT).show();
            toShow = new Mat(64, 250, CvType.CV_8UC4);

            // bias
            if (null != device) {

                UsbInterface usbInterface = device.getInterface(0); // for DVS128

                UsbDeviceConnection connection = usbManager.openDevice(device);
                connection.claimInterface(usbInterface, true);

                Biasgen biasgen = new Biasgen(connection);
                loadXMLBiases(biasgen);
//                biasgen.sendFullConfig();
//                biasgen.sendFullConfig();

//                int start = connection.controlTransfer(0, 0xb8, 0, 0, b, b.length, 0);
//                Log.d("SEND BIAS", "" + start);
                Toast.makeText(Main4Activity.this, "Bias set!", Toast.LENGTH_SHORT).show();

                readEvents = new ReadEvents(Main4Activity.this, device, usbManager, blockingQueue, new CochleaAms1CProcessor());
                readEvents.start();
                //create and start handler used to update GUI
                handler = new Handler();
                runnable = this::update_gui;
                handler.post(runnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(Main4Activity.this, "Could not open device", Toast.LENGTH_SHORT).show();
        }

    }

    boolean refresh = true;
    int verticalIdx = 0;
    int height = 64;
    int width = 500;


    public synchronized Mat fillMat(ArrayList<CochleaAms1CProcessor.CochleaAMS1cEvent> events) {
        CochleaAms1CProcessor.CochleaAMS1cEvent e;
        int r, g, b;
        float max = 0;
        long lastTs = -1, buff;

        if (refresh) {
            refresh = !refresh;
            verticalIdx = 0;
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    toShow.put(i, j, 0, 0, 0, 255);
                }
            }
        }

        for (int i = 0; i < events.size(); i += 1) {
            e = events.get(i);

            if (null != e) {
                if (lastTs < 0){
                    lastTs = e.ts;
                }
                buff = e.ts - lastTs - 500;
                Log.d("EV", "" + e.ts);
                if (buff > 0){
                    verticalIdx += buff / 500;
                    lastTs = -1;
                }


                if (e.side > 0) {
                    r = 255; g = 0; b = 255; // magenta
                } else {
                    r = 0; g = 255; b = 255; // blue
                }
                toShow.put(63 - e.ch, verticalIdx , r, g, b, 255);
//                Log.d("VERICAL IDX", "" + verticalIdx);
                if (verticalIdx >= 500){
                    refresh = true;
                }
            }
        }

        return toShow;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, Main4Activity.this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(Main4Activity.this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {

            } else {
                super.onManagerConnected(status);
            }
        }
    };

    public Bitmap onCameraFrame() {
//        lastTime = System.currentTimeMillis();
        Bitmap map = null;
        Mat frame = null;
        ArrayList<CochleaAms1CProcessor.CochleaAMS1cEvent> toDraw;
        if (null != readEvents) {
            synchronized (this) {
                try {
                    if (blockingQueue.size() > 0) {
                        toDraw = blockingQueue.take();

                        if (toDraw.size() > 0)
                            frame = fillMat(toDraw);

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

    private void update_gui() {
        //get image from thread and display it
        Bitmap ima = onCameraFrame();
        if (ima != null) {
            Bitmap ima2 = Bitmap.createScaledBitmap(ima, width_image, height_image, false);
            imageView.setImageBitmap(ima2);
        }
        handler.postDelayed(runnable, 10);
    }



    public class BiasesXmlParser {
        // We don't use namespaces
        private final String ns = null;

        public List parse(InputStream in) throws XmlPullParserException, IOException {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);
                parser.nextTag();
                return readFeed(parser);
            } finally {
                in.close();
            }
        }

        private List readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
            List entries = new ArrayList();

            parser.require(XmlPullParser.START_TAG, ns, "preferences");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                // Starts by looking for the entry tag
                if (name.equals("entry")) {
                    entries.add(readEntry(parser));
                } else {
//                    skip(parser);
                }
            }
            return entries;
        }

        public class Bias {
            public final String key;
            public final int value;

            private Bias(String key, int value) {
                this.key = key;
                this.value = value;
            }
        }

        // Parses the contents of an entry. If it encounters a title, summary, or link tag, hands them off
        // to their respective "read" methods for processing. Otherwise, skips the tag.
        private Bias readEntry(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, ns, "entry");
//            String key = null;
//            int value = 0;
            String key = parser.getAttributeValue(null, "key");
            String value = parser.getAttributeValue(null, "value");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
//                String key = parser.getAttributeValue(null, "key");
//                String value = parser.getAttributeValue(null, "key");
//                Log.d("GGG", key + " :: " + value);

            }
            int ret = 0;
            try {
                ret = Integer.parseInt(value);
            } catch (Exception e){
                if (value.equals("true")) ret = 1;
            }
            return new Bias(key, ret);
        }

        private String readKey(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, ns, "key");
            String title = readText(parser);
            parser.require(XmlPullParser.END_TAG, ns, "key");
            return title;
        }

        // For the tags title and summary, extracts their text values.
        private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
            String result = "";
            if (parser.next() == XmlPullParser.TEXT) {
                result = parser.getText();
                parser.nextTag();
            }
            return result;
        }

        private int readValue(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, ns, "value");
            String value = readText(parser);
            parser.require(XmlPullParser.END_TAG, ns, "value");
            int ret = 0;
            try {
                ret = Integer.parseInt(value);
            } catch (Exception e){
                if (value.equals("true")) ret = 1;
            }
            return ret;
        }

    }

}
