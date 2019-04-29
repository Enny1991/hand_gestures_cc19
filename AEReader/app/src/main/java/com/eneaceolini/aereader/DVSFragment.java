package com.eneaceolini.aereader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by User on 2/28/2017.
 */

public class DVSFragment extends Fragment{
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



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View v = inflater.inflate(R.layout.dvs_activity, container, false);
        assert v != null;
        imageView = v.findViewById(R.id.an_imageView);
        btnStart = v.findViewById(R.id.start);
        btnStop = v.findViewById(R.id.stop);
        btnBias = v.findViewById(R.id.loadBias);
        btnConnect = v.findViewById(R.id.connect);
        textInfo = v.findViewById(R.id.info);
        biasSpinner = v.findViewById(R.id.spinner);

        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        width_image = dm.heightPixels/2;
        height_image = dm.heightPixels/2;

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
                }catch (Exception e){
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
                handler = new Handler();
                runnable = new Runnable() {
                    public void run() {
                        update_gui();
                    }
                };
                handler.post(runnable);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readEvents.stop_thread();
                btnStart.setEnabled(true);
                readEvents = new ReadEvents(getContext(), device, usbManager, 0);
                handler.removeCallbacks(runnable);
            }
        });

        btnBias.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    UsbInterface usbInterface = device.getInterface(0); // for DVS128
                    UsbEndpoint dataEndpoint = usbInterface.getEndpoint(1); // for DVS128

                    UsbDeviceConnection connection = usbManager.openDevice(device);
                    connection.claimInterface(usbInterface, true);

                    String selectedBias = biasSpinner.getSelectedItem().toString();

                    byte[] b = formatConfigurationBytes(selectedBias);

                    int start = connection.controlTransfer(0, 0xb8, 0, 0, b, b.length, 0);
                    Log.d("SEND BIAS", "" + start);
            }
        });

        return v;
    }

    public byte[] formatConfigurationBytes(String config) {
        // we need to cast from PotArray to IPotArray, because we need the shift register stuff

        PotArray potArray = new IPotArray();
        Log.d("BIAS", "Sending bias config:" + config);
        switch (config){
            case BIAS_FAST:
                potArray.addPot(new IPot("cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 1992, 2, "Photoreceptor cascode"));
                potArray.addPot(new IPot( "injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 1108364, 7, "Differentiator switch level, higher to turn on more"));
                potArray.addPot(new IPot( "reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N, 16777215, 12, "AER request pulldown"));
                potArray.addPot(new IPot( "puX", 8, IPot.Type.NORMAL, IPot.Sex.P, 8159221, 11, "2nd dimension AER static pullup"));
                potArray.addPot(new IPot( "diffOff", 7, IPot.Type.NORMAL, IPot.Sex.N, 132, 6, "OFF threshold, lower to raise threshold"));
                potArray.addPot(new IPot( "req", 6, IPot.Type.NORMAL, IPot.Sex.N, 309590, 8, "OFF request inverter bias"));
                potArray.addPot(new IPot( "refr", 5, IPot.Type.NORMAL, IPot.Sex.P, 969, 9, "Refractory period"));
                potArray.addPot(new IPot( "puY", 4, IPot.Type.NORMAL, IPot.Sex.P, 16777215, 10, "1st dimension AER static pullup"));
                potArray.addPot(new IPot( "diffOn", 3, IPot.Type.NORMAL, IPot.Sex.N, 209996, 5, "ON threshold - higher to raise threshold"));
                potArray.addPot(new IPot( "diff", 2, IPot.Type.NORMAL, IPot.Sex.N, 13125, 4, "Differentiator"));
                potArray.addPot(new IPot( "foll", 1, IPot.Type.NORMAL, IPot.Sex.P, 271, 3, "Src follower buffer between photoreceptor and differentiator"));
                potArray.addPot(new IPot( "Pr", 0, IPot.Type.NORMAL, IPot.Sex.P, 217, 1, "Photoreceptor"));
                break;
            case BIAS_SLOW:
                potArray.addPot(new IPot("cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 54, 2, "Photoreceptor cascode"));
                potArray.addPot(new IPot( "injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 1108364, 7, "Differentiator switch level, higher to turn on more"));
                potArray.addPot(new IPot( "reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N, 16777215, 12, "AER request pulldown"));
                potArray.addPot(new IPot( "puX", 8, IPot.Type.NORMAL, IPot.Sex.P, 8159221, 11, "2nd dimension AER static pullup"));
                potArray.addPot(new IPot( "diffOff", 7, IPot.Type.NORMAL, IPot.Sex.N, 132, 6, "OFF threshold, lower to raise threshold"));
                potArray.addPot(new IPot( "req", 6, IPot.Type.NORMAL, IPot.Sex.N, 159147, 8, "OFF request inverter bias"));
                potArray.addPot(new IPot( "refr", 5, IPot.Type.NORMAL, IPot.Sex.P, 6, 9, "Refractory period"));
                potArray.addPot(new IPot( "puY", 4, IPot.Type.NORMAL, IPot.Sex.P, 16777215, 10, "1st dimension AER static pullup"));
                potArray.addPot(new IPot( "diffOn", 3, IPot.Type.NORMAL, IPot.Sex.N, 482443, 5, "ON threshold - higher to raise threshold"));
                potArray.addPot(new IPot( "diff", 2, IPot.Type.NORMAL, IPot.Sex.N, 30153, 4, "Differentiator"));
                potArray.addPot(new IPot( "foll", 1, IPot.Type.NORMAL, IPot.Sex.P, 51, 3, "Src follower buffer between photoreceptor and differentiator"));
                potArray.addPot(new IPot( "Pr", 0, IPot.Type.NORMAL, IPot.Sex.P, 3, 1, "Photoreceptor"));
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
                        if(device != null){
                            //call method to set up device communication
                            readEvents = new ReadEvents(getContext(), device, usbManager, 0);
                            Log.d("DEVICE", "CONNECTED");
                        }
                    }
                    else {
                        Log.d("TAG", "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private void update_gui()
    {
        //get image from thread and display it
        Bitmap ima = readEvents.get_image();
        if(ima != null)
        {
            Bitmap ima2 = Bitmap.createScaledBitmap(ima, width_image, height_image, false);
            imageView.setImageBitmap(ima2);
        }
        handler.postDelayed(runnable, 5);
    }


}
