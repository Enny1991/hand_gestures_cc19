package com.eneaceolini.aereader;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class ListActivity extends AppCompatActivity {

    public static final int MENU_SCAN = 0;
//    public static final int LIST_DEVICE_MAX = 5;

    public static String TAG = "BluetoothList";

    /**
     * Device Scanning Time (ms)
     */
    private static final long SCAN_PERIOD = 5000;

    /**
     * Intent code for requesting Bluetooth enable
     */
    private static final int REQUEST_ENABLE_BT = 1;

    private ArrayList<String> deviceNames = new ArrayList<>();
    public static String myoName = null;

    private ArrayAdapter<String> adapter;

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;

    private TextView scanningText;
    private ProgressBar prog;
    private Button scanButton;

    /*Updated by Ricardo Colin 06/15/18*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showPhoneStatePermission();
        setContentView(R.layout.activity_list);

        prog = (ProgressBar) findViewById(R.id.progressBar2);
//        scanButton = (Button) findViewById(R.id.scanButton);
//        scanningText = (TextView) findViewById(R.id.scanning_text);

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Bluetooth Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        ListView devicesList = (ListView) findViewById(R.id.listDevices);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_expandable_list_item_1, deviceNames);

        devicesList.setAdapter(adapter);


        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                filters = new ArrayList<ScanFilter>();
            }
        }

        devicesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListView listView = (ListView) parent;
                String item = (String) listView.getItemAtPosition(position);
                Toast.makeText(getApplicationContext(), item + " is connecting...", Toast.LENGTH_SHORT).show();
//                mLEScanner.stopScan(mScanCallback);//added this for the tablet that sucks
                myoName = item;


                Intent intent;
                intent = new Intent(getApplicationContext(), MainActivity.class);

                intent.putExtra(TAG, myoName);

                startActivity(intent);
            }
        });
        //scans for devices when initiated
        scanDevice();
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanDevice();
            }
        });



    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_list, menu);
        menu.add(0, MENU_SCAN, 0, "Scan");

        return true;
    }

   @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == MENU_SCAN) {
            scanDevice();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }*/



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            scanDevice();
        }
    }

    private void scanDevice() {
        scanButton.setVisibility(View.INVISIBLE);
        scanningText.setVisibility(View.VISIBLE);
        prog.setVisibility(View.VISIBLE);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            deviceNames.clear();
            // Scanning Time out by Handler.
            // The device scanning needs high energy.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLEScanner.stopScan(mScanCallback);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(getApplicationContext(), "Scan Stopped", Toast.LENGTH_SHORT).show();
                    prog.setVisibility(View.INVISIBLE);
                    scanningText.setVisibility(View.INVISIBLE);
                    scanButton.setVisibility(View.VISIBLE);

                }
            }, SCAN_PERIOD);
            mLEScanner.startScan(filters, settings, mScanCallback);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("callbackType", String.valueOf(callbackType));
            Log.d("result", result.toString());
            BluetoothDevice device = result.getDevice();
            ParcelUuid[] uuids = device.getUuids();
            String uuid = "";
            if (uuids != null) {
                for (ParcelUuid puuid : uuids) {
                    uuid += puuid.toString() + " ";
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
            Log.d(TAG, "onScanResult: Device = " + result.getDevice().getName());
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

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d("BTScan", "ENTERED onLeScan");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i("onLeScan", device.toString());
                }
            });
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }
        }
    };

    private void showPhoneStatePermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {

            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH}, 1);
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 1);

            }
        } else {
            //Toast.makeText(this, "Location Permission (already) Granted!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permission denied to access location services (coarse)", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

}