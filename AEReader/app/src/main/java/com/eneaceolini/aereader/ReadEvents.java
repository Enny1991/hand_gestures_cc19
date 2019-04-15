package com.eneaceolini.aereader;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;


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

    DVS128Processor processor;

    //******************************** Parameters used for serial connection ******************************/
    int XON = 0xb3;    /* Resume transmission */
    int XOFF = 0xb4;    /* Pause transmission */

    //******************************** variables used to read data ******************************/
    int buffer_size = 0; //initialized in init() function
    int bytesRead = 0;
    int readcount = 0;


    /**
     * Data that will be mapped into a bitmap for display
     */
    int[] data_image;

    /**
     * Data that will be read from the eDVS over USB
     */
    byte[] usbData;
    byte[] statusData;

    boolean newdata = false;

    private UsbDevice device;
    private UsbManager usbManager;
    private UsbEndpoint dataEndpoint;
    private UsbEndpoint statusEndpoint;
    private UsbDeviceConnection connection;
    private int timeout;

    ReadEvents(Context context, UsbDevice device, UsbManager usbManager, int timeout) {
        context_activity = context;
        processor = new DVS128Processor();
        data_image = new int[128 * 128];
        this.device = device;
        this.usbManager = usbManager;
        this.timeout = timeout;
    }

    /**
     * main loop of the thread
     */
    @Override
    public final void run() {
        init();        //initialize serial connection, and send E+ to eDVS
        int res = connection.controlTransfer(0, 0xb3, 1, 0, null, 0, 0);
        int res2 = connection.controlTransfer(0, 0xbb, 1, 0, null, 0, 0);
//        int res3 = connection.controlTransfer(0, 0xbe, 1, 0, null, 0, 0);
        Log.d("START", "" + res);
        Log.d("RESET", "" + res2);
//        Log.d("SYNC", "" + res3);

        statusData = new byte[statusEndpoint.getMaxPacketSize()];
        int c;
        while (!STOP) {
            synchronized (this) {    //synchronized block of code so the activity handler and this Thread_eDVS do not access data at the same time
                usbData = new byte[dataEndpoint.getMaxPacketSize()];
//                c = connection.bulkTransfer(statusEndpoint, statusData, statusData.length, timeout);
//                Log.d("STATUS", "" + c);
                c = connection.bulkTransfer(dataEndpoint, usbData, usbData.length, timeout);

                ArrayList<DVS128Processor.DVS128Event> events = processor.process(usbData, c);
//                Log.d("TRANSFER", "@:" + timeout + "::rec:: " + c + " evs::" + events.size());
                if (events.size() > 1) {
                    newdata = true;
                    for(int i=0; i<events.size(); i++) //create image data from events
                    {
                        DVS128Processor.DVS128Event event = events.get(i);

//                        if(event.polarity == 0) data_image[128*event.x + event.y] = 0xFFFFFFFF; //white
//                        else 			 data_image[128*event.x + event.y] = 0x80808080; //grey

							if(event.polarity == 0) data_image[128*event.x + event.y] = 0xFFFF0000; //red
							else 			 data_image[128*event.x + event.y] = 0xFF00FF00; //green
                    }
                }
                if (c < 0) {
                    STOP = true;
//                    try {
//                        Thread.sleep(200);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }
            }
        }

    }

    private boolean init() {
        Log.d("TRANSFER", "START");
        UsbInterface usbInterface = device.getInterface(0);
        dataEndpoint = usbInterface.getEndpoint(1);
        statusEndpoint = usbInterface.getEndpoint(0);
        connection = usbManager.openDevice(device);
        return connection.claimInterface(usbInterface, true);
    }

    /**
     * function called by activity handler to get image from events and data_image.
     * Synchronized so the handler and the Thread_eDVS do not access data_image at the same time
     *
     * @return
     */
    public synchronized Bitmap get_image() {
        if (newdata == true) {
            newdata = false;
            Bitmap ima = Bitmap.createBitmap(data_image, 128, 128, Bitmap.Config.ARGB_8888);
            Arrays.fill(data_image, 0xFF000000);    //reset data_image
            return ima;
        } else return null;
    }

    /**
     * @return
     */
    public synchronized int get_bytesRead() {
        return bytesRead;
    }

    /**
     * stops the thread
     */
    public synchronized void stop_thread() {
        STOP = true;
        UsbInterface usbInterface = device.getInterface(0);
        UsbEndpoint endpoint = usbInterface.getEndpoint(1);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        boolean claimed = connection.claimInterface(usbInterface, true);

        int res = connection.controlTransfer(0, 0xb4, 0, 0, null, 0, 0);
        Log.d("END TRANSFER", "" + res);
    }
}


