package com.eneaceolini.aereader;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


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

    //******************************** Parameters used for serial connection ******************************/
    int XON = 0xb3;    /* Resume transmission */
    int XOFF = 0xb4;    /* Pause transmission */

    //******************************** variables used to read data ******************************/
    int buffer_size = 0; //initialized in init() function
    int bytesRead = 0;
    int readcount = 0;

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
    public final static short FPGA_CHIPBIAS = 5; // Biases, Chip Config, Channel Config
    public final static short FPGA_SYSINFO = 6;
    public final static short FPGA_DAC = 7;
    public final static short FPGA_SCANNER = 8;
    public final static short FPGA_USB = 9;
    public final static short FPGA_ADC = 10;
    public static final byte VR_FPGA_CONFIG_MULTIPLE = (byte) 0xC2;

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
    private int mode;
    private int DEVICE;

    ReadEvents(Context context, UsbDevice device, UsbManager usbManager, int timeout) {
        context_activity = context;
        // Depending on product ID you need to have different processors
        Log.d("DEVICE ID", "" + device.getProductId());
        switch (device.getProductId()){
            case COCHLEA_ID:
                processor = new CochleaAms1CProcessor();
                DEVICE = COCHLEA_ID;
                mode = FX2;
                break;
            case DVS128_ID:
                processor = new DVS128Processor();
                DEVICE = DVS128_ID;
                mode = FX2;
                break;
            case DAVIS240_ID:
                Log.d("DAVIS240", "" + device.getProductId());
                processor = new DAVIS240Processor();
                DEVICE = DAVIS240_ID;
                mode = FX3;
                break;
        }
        data_image = new int[128 * 128];
        this.device = device;
        this.usbManager = usbManager;
        this.timeout = timeout;
    }

    protected synchronized void disableINEndpoint() {
            final SPIConfigSequence configSequence = new SPIConfigSequence();

            configSequence.addConfig(FPGA_EXTINPUT, (short) 0, 0); // Disable ext detector.
            configSequence.addConfig(FPGA_IMU, (short) 2, 0); // Disable IMU accel.
            configSequence.addConfig(FPGA_IMU, (short) 3, 0); // Disable IMU gyro.
            configSequence.addConfig(FPGA_IMU, (short) 4, 0); // Disable IMU temp.
            configSequence.addConfig(FPGA_APS, (short) 4, 0); // Disable APS.
            configSequence.addConfig(FPGA_DVS, (short) 3, 0); // Disable DVS.

            configSequence.addConfig(FPGA_MUX, (short) 1, 0); // Disable timestamps.
            configSequence.addConfig(FPGA_MUX, (short) 0, 0); // Disable mux.

            configSequence.addConfig(FPGA_USB, (short) 0, 0); // Disable USB.

            configSequence.addConfig(FPGA_MUX, (short) 3, 0); // Disable biasgen.

            configSequence.sendConfigSequence();


    }

    private void enableINEndpoint(){
        final SPIConfigSequence configSequence = new SPIConfigSequence();

        configSequence.addConfig(FPGA_MUX, (short) 3, 1); // Enable biasgen.

        configSequence.addConfig(FPGA_USB, (short) 0, 1); // Enable USB.

        configSequence.addConfig(FPGA_MUX, (short) 1, 1); // Enable timestamps.
        configSequence.addConfig(FPGA_MUX, (short) 0, 1); // Enable mux.

        configSequence.sendConfigSequence();
    }


    public class SPIConfigSequence {
        private class SPIConfigParameter {
            private final byte moduleAddr;
            private final byte paramAddr;
            private final byte[] param;

            public SPIConfigParameter(final short moduleAddr, final short paramAddr, final int param) {
                final byte[] configBytes = new byte[4];

                configBytes[0] = (byte) ((param >>> 24) & 0x00FF);
                configBytes[1] = (byte) ((param >>> 16) & 0x00FF);
                configBytes[2] = (byte) ((param >>> 8) & 0x00FF);
                configBytes[3] = (byte) ((param >>> 0) & 0x00FF);

                this.moduleAddr = (byte) moduleAddr;
                this.paramAddr = (byte) paramAddr;
                this.param = configBytes;
            }

            public byte getModuleAddr() {
                return moduleAddr;
            }

            public byte getParamAddr() {
                return paramAddr;
            }

            public byte[] getParam() {
                return param;
            }
        }

        private final List<SPIConfigParameter> configList;

        public SPIConfigSequence() {
            configList = new ArrayList<>();
        }

        private boolean canAddConfig() {
            // Max number of 6 bytes elements in 4096 bytes buffer is 682.
            return (configList.size() < 682);
        }

        public void addConfig(final short moduleAddr, final short paramAddr, final int param) {
            if (!canAddConfig()) {
                // Send current config, clean up for new one.
                sendConfigSequence();
            }

            configList.add(new SPIConfigParameter(moduleAddr, paramAddr, param));
        }

        public void sendConfigSequence() {
            // Build byte buffer and send it. 6 bytes per config parameter.

//            final ByteBuffer buffer = BufferUtils.allocateByteBuffer(configList.size() * 6);
            final ByteBuffer buffer = ByteBuffer.allocate(configList.size() * 6);

            for (final SPIConfigParameter cfg : configList) {
                buffer.put(cfg.getModuleAddr());
                buffer.put(cfg.getParamAddr());
                buffer.put(cfg.getParam());
            }

//            sendVendorRequest(VR_FPGA_CONFIG_MULTIPLE, (short) configList.size(), (short) 0, buffer);
            int start = connection.controlTransfer(0, VR_FPGA_CONFIG_MULTIPLE, configList.size(), 0, buffer.array(), buffer.array().length, 0);
            Log.d("COMM", ":: " + start);
            clearConfig();
        }

        private void clearConfig() {
            configList.clear();
        }
    }


    /**
     * main loop of the thread
     */
    @Override
    public final void run() {
        init();
        Log.d("MODE", "" + mode);
        switch (mode){
            case FX2:
                int start = connection.controlTransfer(0, 0xb3, 1, 0, null, 0, 0);
                int reset = connection.controlTransfer(0, 0xbb, 1, 0, null, 0, 0);
                break;
            case FX3:
//                start = connection.controlTransfer(0, 0xb3, 1, 0, null, 0, 0);
//                Log.d("COMM", "::" + start);
                enableINEndpoint();
                break;
        }

        int c;
        while (!STOP) {
            synchronized (this) {    //synchronized block of code so the activity handler and this Thread_eDVS do not access data at the same time
                usbData = new byte[dataEndpoint.getMaxPacketSize()];
//                usbData = new byte[384];
//                Log.d("FIFO", ":: " + dataEndpoint.getMaxPacketSize());
//                c = connection.bulkTransfer(statusEndpoint, statusData, statusData.length, timeout);
//                Log.d("STATUS", "" + c);
                c = connection.bulkTransfer(dataEndpoint, usbData, usbData.length, timeout);
//                Log.d("TRANSFER", "@:" + timeout + "::rec:: " + c);

                if (c>0) {
                    switch (DEVICE) {
                        case DVS128_ID:
                            ArrayList<DVS128Processor.DVS128Event> events = processor.process(usbData, c);
                            if (events.size() > 1) {
                                newdata = true;
                                for (int i = 0; i < events.size(); i++) //create image data from events
                                {

                                    DVS128Processor.DVS128Event event = events.get(i);

                                    if (event.polarity == 0)
                                        data_image[128 * event.x + event.y] = 0xFFFF0000; //red
                                    else data_image[128 * event.x + event.y] = 0xFF00FF00; //green
                                }
                            }
                            break;
                        case DAVIS240_ID:
                            ArrayList<DAVIS240Processor.DAVIS240Event> events2 = processor.process(usbData, c);

                            if (events2.size() > 1) {
                                newdata = true;
                                for (int i = 0; i < events2.size(); i++) //create image data from events
                                {

                                    DAVIS240Processor.DAVIS240Event event = events2.get(i);

                                    if (event.polarity == 0)
                                        data_image[240 * event.x + event.y] = 0xFFFF0000; //red
                                    else data_image[240 * event.x + event.y] = 0xFF00FF00; //green
                                }
                            }
                    }

                }

                if (c < 0) {
                    STOP = true;
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }
            }
        }

    }

    private boolean init() {
        Log.d("TRANSFER", "START");
        // only works for dvs128 and ams1c/1b
        // gotta make it dependent for DAVIS240... and in general for FX3
//        UsbInterface usbInterface = device.getInterface(0);
        for (int i=0; i<device.getInterfaceCount(); i++){
            for (int j=0; j<device.getInterface(i).getEndpointCount(); j++){
                Log.d("INTERFACE", "::" + i);
                Log.d("ENDPOINT", "::" + j);
                Log.d("ADD", "::" + device.getInterface(i).getEndpoint(j).getAddress());
            }
        }
        UsbInterface usbInterface = device.getInterface(0); // for DVS128
        dataEndpoint = usbInterface.getEndpoint(1); // for DVS128

//        UsbInterface usbInterface = device.getInterface(0); // for DAVIS240
//        dataEndpoint = usbInterface.getEndpoint(0); // for DAVIS240

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
        UsbEndpoint endpoint = usbInterface.getEndpoint(0);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        boolean claimed = connection.claimInterface(usbInterface, true);

        int res = connection.controlTransfer(0, 0xb4, 0, 0, null, 0, 0);
        Log.d("END TRANSFER", "" + res);
    }
}


