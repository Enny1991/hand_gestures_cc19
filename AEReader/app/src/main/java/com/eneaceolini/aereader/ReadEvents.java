package com.eneaceolini.aereader;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;


public class ReadEvents extends Thread {
    static final String TAG = "Thread_eDVS";

    /**
     * for stopping the thread
     */
    boolean STOP = false;

    /**
     * Reference to context of the main activity
     */
    Context context_activity;

    AERProcessor processor;

    int bytesRead = 0;

    private static final int COCHLEA_ID = 33797;
    private static final int DVS128_ID = 33792;
    private static final int DAVIS240_ID = 33819;
    private static final int FX2 = 0;
    private static final int FX3 = 1;
    public final static short FPGA_MUX = 0;
    public final static short FPGA_DVS = 1; // GenericAERConfig, DVSAERConfig, AERKillConfig
    public final static short FPGA_APS = 2;
    public final static short FPGA_IMU = 3;
    public final static short FPGA_EXTINPUT = 4;

    public final static short FPGA_USB = 9;
    public static final byte VR_FPGA_CONFIG_MULTIPLE = (byte) 0xC2;


    byte[] usbData;

    private UsbDevice device;
    private UsbManager usbManager;
    private UsbEndpoint dataEndpoint;
    private UsbDeviceConnection connection;
    public int height, width;
    private BlockingQueue<ArrayList> blockingQueue;
    private long globalClock = 0L;
    private int frameLength = 200; // ms

    ReadEvents(Context context,
               UsbDevice device,
               UsbManager usbManager,
               BlockingQueue<ArrayList> blockingQueue) {
        context_activity = context;
        this.blockingQueue = blockingQueue;
        processor = new DVS128Processor();
        this.device = device;
        this.usbManager = usbManager;
    }

    /**
     * main loop of the thread
     */
    @Override
    public final void run() {
        init();
        int first = connection.controlTransfer(0, 0xb3, 1, 0, null, 0, 0);
//        int second = connection.controlTransfer(0, 0xbb, 1, 0, null, 0, 0);
        Log.d("Initiated", "Transfer");
        Log.d("FIRST", "" + first);
//        Log.d("SECOND", "" + second);
        int c;
        ArrayList<DVS128Processor.DVS128Event> events;
        ArrayList<DVS128Processor.DVS128Event> toPush = new ArrayList<>();
        globalClock = System.currentTimeMillis();
        while (!STOP) {
            synchronized (this) {
                usbData = new byte[dataEndpoint.getMaxPacketSize()];
//                usbData = new byte[128];
                c = connection.bulkTransfer(dataEndpoint, usbData, usbData.length, 0);

                if (c > 0) {
                    events = processor.process(usbData, c);
                    if (events.size() > 0) {
                        toPush.addAll(events);
                        if (toPush.size() > 2){
                            if ((toPush.get(toPush.size()-1).ts - toPush.get(0).ts) > frameLength * 1000 || (System.currentTimeMillis() - globalClock) > frameLength) {
                                try {
                                    blockingQueue.put(toPush);
                                    globalClock = System.currentTimeMillis();
                                    toPush= new ArrayList<>();

                                } catch (InterruptedException ex) {
                                    Log.d("INTERRUPTED", "Had to DROP");
                                }
                            }
                        }

                    }
                }
            }
        }

    }

    private boolean init() {
        Log.d("TRANSFER", "START");
        for (int i=0; i<device.getInterfaceCount(); i++){
            for (int j=0; j<device.getInterface(i).getEndpointCount(); j++){
                Log.d("INTERFACE", "::" + i);
                Log.d("ENDPOINT", "::" + j);
                Log.d("ADD", "::" + device.getInterface(i).getEndpoint(j).getAddress());
            }
        }
        UsbInterface usbInterface = device.getInterface(0); // for DVS128
        dataEndpoint = usbInterface.getEndpoint(1); // for DVS128
        connection = usbManager.openDevice(device);
        return connection.claimInterface(usbInterface, true);
    }


    /**
     * stops the thread
     */
    synchronized void stop_thread() {
        STOP = true;
        UsbInterface usbInterface = device.getInterface(0);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        connection.claimInterface(usbInterface, true);
        int res = connection.controlTransfer(0, 0xb4, 0, 0, null, 0, 0);
        Log.d("END TRANSFER", "" + res);
    }
}


