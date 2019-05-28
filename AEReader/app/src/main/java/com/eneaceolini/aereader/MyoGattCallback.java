package com.eneaceolini.aereader;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;


public class MyoGattCallback extends BluetoothGattCallback {
    private static double superTimeInitial;
    /**
     * Service ID
     */
    private static final String MYO_CONTROL_ID = "d5060001-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_EMG_DATA_ID = "d5060005-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_IMU_DATA_ID = "d5060002-a904-deb9-4748-2c7f4a124842";
    /**
     * Characteristics ID
     */
    private static final String MYO_INFO_ID = "d5060101-a904-deb9-4748-2c7f4a124842";
    private static final String FIRMWARE_ID = "d5060201-a904-deb9-4748-2c7f4a124842";
    private static final String COMMAND_ID = "d5060401-a904-deb9-4748-2c7f4a124842";

    private static final String EMG_0_ID = "d5060105-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_1_ID = "d5060205-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_2_ID = "d5060305-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_3_ID = "d5060405-a904-deb9-4748-2c7f4a124842";
    private static final String IMU_0_ID = "d5060402-a904-deb9-4748-2c7f4a124842";
    /**
     * android Characteristic ID (from Android Samples/BluetoothLeGatt/SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
     */
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic_command;
    private BluetoothGattCharacteristic mCharacteristic_emg0;
    private BluetoothGattCharacteristic mCharacteristic_emg1;
    private BluetoothGattCharacteristic mCharacteristic_emg2;
    private BluetoothGattCharacteristic mCharacteristic_emg3;
    private BluetoothGattCharacteristic mCharacteristic_imu0;

    private MyoCommandList commandList = new MyoCommandList();

    private String TAG = "MyoGatt";

    private TextView textView;
    private TextView connectingTextView;
    private String callback_msg;
    private Handler mHandler;

    private Plotter plotter;
    private static Plotter imuPlotter;
    private static Handler imuHandler;
    private ProgressBar progress;
    static Boolean myoConnected;
    private ImuFragment imuFragment;

    private FeatureCalculator fcalc;//maybe needs to be later in process

    MyoGattCallback(Handler handler, TextView view, ProgressBar prog, TextView connectingText, Plotter plot, View v) {
        mHandler = handler;
        connectingTextView = connectingText;
        textView = view;
        plotter = plot;
        progress = prog;
        fcalc = new FeatureCalculator(plotter);

//        thread = new ServerCommunicationThread();
//        thread.start();
//
//        clientThread = new ClientCommunicationThread();
//        clientThread.start();

//        fcalc.connect();
    }

    public MyoGattCallback(Handler handler, Plotter plot) {
        imuHandler = handler;
        imuPlotter = plot;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        Log.d(TAG, "onConnectionStateChange: " + status + " -> " + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {

            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // GATT Disconnected
            stopCallback();
            Log.d(TAG, "Bluetooth Disconnected");
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {

        super.onServicesDiscovered(gatt, status);
        Log.d(TAG, "onServicesDiscovered received: " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Find GATT Service
            BluetoothGattService service_emg = gatt.getService(UUID.fromString(MYO_EMG_DATA_ID));
            BluetoothGattService service_imu = gatt.getService(UUID.fromString(MYO_IMU_DATA_ID));
            if (service_emg == null || service_imu == null) {//should probably break this into another separate checker for IMU service
                Log.d(TAG, "No Myo Service !!");
            } else {
                Log.d(TAG, "Find Myo Data Service !!");
                // Getting CommandCharacteristic
                mCharacteristic_emg0 = service_emg.getCharacteristic(UUID.fromString(EMG_0_ID));
                mCharacteristic_emg1 = service_emg.getCharacteristic(UUID.fromString(EMG_1_ID));
                mCharacteristic_emg2 = service_emg.getCharacteristic(UUID.fromString(EMG_2_ID));
                mCharacteristic_emg3 = service_emg.getCharacteristic(UUID.fromString(EMG_3_ID));
                mCharacteristic_imu0 = service_imu.getCharacteristic(UUID.fromString(IMU_0_ID));
                if (mCharacteristic_emg0 == null || mCharacteristic_imu0 == null) {
                    callback_msg = "Not Found Data Characteristics";
                } else {
                    // Setting the notification
                    boolean registered_0 = gatt.setCharacteristicNotification(mCharacteristic_emg0, true);
                    boolean registered_1 = gatt.setCharacteristicNotification(mCharacteristic_emg1, true);
                    boolean registered_2 = gatt.setCharacteristicNotification(mCharacteristic_emg2, true);
                    boolean registered_3 = gatt.setCharacteristicNotification(mCharacteristic_emg3, true);
                    boolean iregistered_0 = gatt.setCharacteristicNotification(mCharacteristic_imu0, true);
                    if (!registered_0 || !iregistered_0) {
                        Log.d(TAG, "EMG-Data Notification FALSE !!");
                    } else {
                        Log.d(TAG, "EMG-Data Notification TRUE !!");
                        // Turn ON the Characteristic Notification
                        BluetoothGattDescriptor descriptor_0 = mCharacteristic_emg0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_1 = mCharacteristic_emg1.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_2 = mCharacteristic_emg2.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_3 = mCharacteristic_emg3.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor idescriptor_0 = mCharacteristic_imu0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        if (descriptor_0 != null || idescriptor_0 != null) {
                            idescriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_1.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_3.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptorWriteQueue.add(idescriptor_0);
                            descriptorWriteQueue.add(descriptor_0);
                            descriptorWriteQueue.add(descriptor_1);
                            descriptorWriteQueue.add(descriptor_2);
                            descriptorWriteQueue.add(descriptor_3);
                            consumeAllGattDescriptors();
                            Log.d(TAG, "Set descriptor");
                        } else {
                            Log.d(TAG, "No descriptor");
                        }
                    }
                }
            }

            BluetoothGattService service = gatt.getService(UUID.fromString(MYO_CONTROL_ID));
            if (service == null) {
                Log.d(TAG, "No Myo Control Service !!");
            } else {
                Log.d(TAG, "Find Myo Control Service !!");
                // Get the MyoInfoCharacteristic
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString(MYO_INFO_ID));
                if (characteristic == null) {
                } else {
                    Log.d(TAG, "Find read Characteristic !!");
                    //put the characteristic into the read queue
                    readCharacteristicQueue.add(characteristic);
                    //if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the callback above
                    //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
                    if ((readCharacteristicQueue.size() == 1) && (descriptorWriteQueue.size() == 0)) {
                        mBluetoothGatt.readCharacteristic(characteristic);
                    }
                }

                // Get CommandCharacteristic
                mCharacteristic_command = service.getCharacteristic(UUID.fromString(COMMAND_ID));
                if (mCharacteristic_command == null) {
                } else {
                    Log.d(TAG, "Find command Characteristic !!");
                }
            }
        }
    }

    public void writeGattDescriptor(BluetoothGattDescriptor d) {
        //put the descriptor into the write queue
        descriptorWriteQueue.add(d);
        //if there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
        if (descriptorWriteQueue.size() == 1) {
            mBluetoothGatt.writeDescriptor(d);
        }
    }

    public void consumeAllGattDescriptors() {
        mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());//the rest will happen in callback
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Callback: Wrote GATT Descriptor successfully.");
        } else {
            Log.d(TAG, "Callback: Error writing GATT Descriptor: " + status);
        }
        descriptorWriteQueue.remove();  //pop the item that we just finishing writing
        //if there is more to write, do it!
        if (descriptorWriteQueue.size() > 0)
            mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());
        else if (readCharacteristicQueue.size() > 0)
            mBluetoothGatt.readCharacteristic(readCharacteristicQueue.element());
    }

    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        if (status == BluetoothGatt.GATT_SUCCESS) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //textView.setText("" + ListActivity.myoName + " Connected");
                    myoConnected = true;
                    progress.setVisibility(View.INVISIBLE);
                    connectingTextView.setVisibility(View.INVISIBLE);
                }
            });
        } else {
            myoConnected = false;
            Log.d(TAG, "onCharacteristicRead error: " + status);
        }
        if (setMyoControlCommand(commandList.sendImuAndEmg())) {
            Log.d(TAG, "Successfully started EMG stream");
        } else {
            Log.d(TAG, "Unable to start EMG stream");
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onCharacteristicWrite success");
        } else {
            Log.d(TAG, "onCharacteristicWrite error: " + status);
        }
    }

    long last_send_never_sleep_time_ms = System.currentTimeMillis();
    final static long NEVER_SLEEP_SEND_TIME = 10000;  // Milli Second

    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (EMG_0_ID.equals(characteristic.getUuid().toString()) || EMG_1_ID.equals(characteristic.getUuid().toString()) || EMG_2_ID.equals(characteristic.getUuid().toString()) || EMG_3_ID.equals(characteristic.getUuid().toString())) {

            long systemTime_ms = System.currentTimeMillis();
            superTimeInitial = systemTime_ms;
            byte[] emg_data = characteristic.getValue();

            byte[] emg_data1 = Arrays.copyOfRange(emg_data,0,8);
            byte[] emg_data2 = Arrays.copyOfRange(emg_data,8,16);

//            fcalc.pushFeatureBuffer(emg_data1);
            fcalc.pushFeatureBuffer(emg_data2);

//            byte cloudControl = 0;
//            if (fcalc.getClassify()) {
//                cloudControl = 1;
//            } else if (fcalc.getTrain()) {
//                cloudControl = 2;
//            }
//            byte[] emg_data_controlled = ArrayUtils.add(emg_data, 0, cloudControl);

//            /*The following can test the tcp latency by sending a timestamp to the server DOES NOT WORK NEED TO IMPLEMENT LAMPORT TIMESTAMPS*/
//            long currentTime = System.currentTimeMillis();
//            byte[] bytetime = longToBytes(currentTime);
//            byte[] guy = new byte[]{0,0,0,0,0,0,0,0,0};
//            byte[] bytetime17 = ArrayUtils.addAll(bytetime, guy);
//            System.out.println(currentTime);
////            System.out.println(Arrays.toString(bytetime17));

//            thread.send(emg_data_controlled);

//            plotter.pushPlotter(emg_data);

            if (systemTime_ms > last_send_never_sleep_time_ms + NEVER_SLEEP_SEND_TIME) {
                setMyoControlCommand(commandList.sendUnSleep());
                last_send_never_sleep_time_ms = systemTime_ms;
            }
        } else if (IMU_0_ID.equals(characteristic.getUuid().toString())) {
            long systemTime_ms = System.currentTimeMillis();
            byte[] imu_data = characteristic.getValue();
            Number[] emg_dataObj = ArrayUtils.toObject(imu_data);
            ArrayList<Number> imu_data_list1 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 0, 10)));
            ArrayList<Number> imu_data_list2 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 10, 20)));
            DataVector dvec1 = new DataVector(true, 1, 10, imu_data_list1, systemTime_ms);
            DataVector dvec2 = new DataVector(true, 2, 10, imu_data_list2, systemTime_ms);
            fcalc.pushIMUFeatureBuffer(dvec1);
            fcalc.pushIMUFeatureBuffer(dvec2);

//            imuFragment = new ImuFragment();
//            imuFragment.sendIMUValues(dvec2);
        }
    }

    public void setBluetoothGatt(BluetoothGatt gatt) {
        mBluetoothGatt = gatt;
    }

    public boolean setMyoControlCommand(byte[] command) {
        if (mCharacteristic_command != null) {
            mCharacteristic_command.setValue(command);
            int i_prop = mCharacteristic_command.getProperties();
            if (i_prop == BluetoothGattCharacteristic.PROPERTY_WRITE) {
                if (mBluetoothGatt.writeCharacteristic(mCharacteristic_command)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void stopCallback() {
        // Before the closing GATT, set Myo [Normal Sleep Mode].
        setMyoControlCommand(commandList.sendNormalSleep());
        descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
        readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();
        if (mCharacteristic_command != null) {
            mCharacteristic_command = null;
        }
        if (mCharacteristic_emg0 != null) {
            mCharacteristic_emg0 = null;
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt = null;
        }
    }

}
