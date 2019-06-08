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


public class MyoGattCallbackv2 extends BluetoothGattCallback {
    private static double superTimeInitial;
    /**
     * Service ID
     */
    private static final String MYO_CONTROL_ID = "d5060001-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_EMG_DATA_ID = "d5060005-a904-deb9-4748-2c7f4a124842";
    /**
     * Characteristics ID
     */
    private static final String MYO_INFO_ID = "d5060101-a904-deb9-4748-2c7f4a124842";
    private static final String FIRMWARE_ID = "d5060201-a904-deb9-4748-2c7f4a124842";
    private static final String COMMAND_ID = "d5060401-a904-deb9-4748-2c7f4a124842";

    private static final String EMG_0_ID = "d5060105-a904-deb9-4748-2c7f4a124842";
//    private static final String EMG_1_ID = "d5060205-a904-deb9-4748-2c7f4a124842";
    /**
     * android Characteristic ID (from Android Samples/BluetoothLeGatt/SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
     */
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic_command;
    private BluetoothGattCharacteristic mCharacteristic_emg0;

    private MyoCommandList commandList = new MyoCommandList();

    private String TAG = "MyoGatt";

    private Handler mHandler;

    private static Boolean myoConnected;

    private FeatureCalculatorv2 fcalc;//maybe needs to be later in process


    private long last_send_never_sleep_time_ms = System.currentTimeMillis();
    private final static long NEVER_SLEEP_SEND_TIME = 10000;  // Milli Second

    MyoGattCallbackv2(Handler handler, Plotter plot) {
        mHandler = handler;
        fcalc = new FeatureCalculatorv2(plot);
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
            if (service_emg == null) {//should probably break this into another separate checker for IMU service
                Log.d(TAG, "No Myo Service !!");
            } else {
                Log.d(TAG, "Find Myo Data Service !!");
                // Getting CommandCharacteristic
                mCharacteristic_emg0 = service_emg.getCharacteristic(UUID.fromString(EMG_0_ID));
                if (mCharacteristic_emg0 != null) {

                    // Setting the notification
                    boolean registered_0 = gatt.setCharacteristicNotification(mCharacteristic_emg0, true);
                    if (!registered_0) {
                        Log.d(TAG, "EMG-Data Notification FALSE !!");
                    } else {
                        Log.d(TAG, "EMG-Data Notification TRUE !!");
                        // Turn ON the Characteristic Notification
                        BluetoothGattDescriptor descriptor_0 = mCharacteristic_emg0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        if (descriptor_0 != null ) {
                            descriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptorWriteQueue.add(descriptor_0);
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
                if (characteristic != null) {
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
                if (mCharacteristic_command != null) {
                    Log.d(TAG, "Find command Characteristic !!");
                }
            }
        }
    }


    private void consumeAllGattDescriptors() {
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
            mHandler.post(() -> myoConnected = true);
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

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (EMG_0_ID.equals(characteristic.getUuid().toString())) {

            long systemTime_ms = System.currentTimeMillis();
            superTimeInitial = systemTime_ms;
            byte[] emg_data = characteristic.getValue();

//            byte[] emg_data1 = Arrays.copyOfRange(emg_data,0,8);
            byte[] emg_data2 = Arrays.copyOfRange(emg_data,8,16);

            fcalc.pushFeatureBuffer(emg_data2);

//
            if (systemTime_ms > last_send_never_sleep_time_ms + NEVER_SLEEP_SEND_TIME) {
                setMyoControlCommand(commandList.sendUnSleep());
                last_send_never_sleep_time_ms = systemTime_ms;
            }
        }
    }

    void setBluetoothGatt(BluetoothGatt gatt) {
        mBluetoothGatt = gatt;
    }

    private boolean setMyoControlCommand(byte[] command) {
        if (mCharacteristic_command != null) {
            mCharacteristic_command.setValue(command);
            int i_prop = mCharacteristic_command.getProperties();
            if (i_prop == BluetoothGattCharacteristic.PROPERTY_WRITE) {
                return mBluetoothGatt.writeCharacteristic(mCharacteristic_command);
            }
        }
        return false;
    }

    private void stopCallback() {
        // Before the closing GATT, set Myo [Normal Sleep Mode].
        setMyoControlCommand(commandList.sendNormalSleep());
        descriptorWriteQueue = new LinkedList<>();
        readCharacteristicQueue = new LinkedList<>();
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
