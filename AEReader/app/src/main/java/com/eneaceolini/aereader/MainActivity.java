package com.eneaceolini.aereader;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {

    public static final int MENU_LIST = 0;
    public static final int MENU_BYE = 1;

    private static final long SCAN_PERIOD = 5000;

    private static final int REQUEST_ENABLE_BT = 1;

    private static final String TAG = "BLE_Myo";

    public int gestureCounter = 0;
    private static Plotter plotter = new Plotter();//static may cause issues

    private ScanCallback scanCallback = new ScanCallback() {
    };

    TextView countdown;
    private static final String FORMAT = "%2d";

    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private TextView emgDataText;
    private TextView gestureText;
    private BluetoothLeScanner mLEScanner;

    private MyoGattCallback mMyoCallback;
    private MyoCommandList commandList = new MyoCommandList();

    /***********************Below ADDED BY CHARLES FOR SWIPEABLE TABS***************************/

    ViewPager mViewPager;
    TabsAdapter mTabsAdapter;

    TabLayout tabLayout;
    public ClassificationFragment classificationFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mViewPager = (ViewPager) this.findViewById(R.id.view_pager);
        mViewPager.setOffscreenPageLimit(3);
//
        EmgFragment emgFragment = new EmgFragment();
        FeatureFragment featureFragment = new FeatureFragment();
        classificationFragment = new ClassificationFragment();
        ImuFragment imuFragment = new ImuFragment();

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        TabLayout.Tab EMGTab = tabLayout.newTab();
        TabLayout.Tab FeatureTab = tabLayout.newTab();
        TabLayout.Tab IMUTab = tabLayout.newTab();
        TabLayout.Tab ClassificationTab = tabLayout.newTab();

        tabLayout.addTab(EMGTab, 0, true);
        tabLayout.addTab(FeatureTab, 1, true);
        tabLayout.addTab(IMUTab, 2, true);
        tabLayout.addTab(ClassificationTab, 3, true);


        tabLayout.setupWithViewPager(mViewPager);

        PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager(), tabLayout.getTabCount());
        mViewPager.setAdapter(adapter);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));


    }

    public static class TabsAdapter extends FragmentStatePagerAdapter implements ActionBar.TabListener, ViewPager.OnPageChangeListener {

        private final MainActivity mContext;
        private final ActionBar mActionBar;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();
        private final ArrayList<Fragment> mFrag = new ArrayList<Fragment>();

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            Object tag = tab.getTag();
            for (int i = 0; i < mTabs.size(); i++) {
                if (mTabs.get(i) == tag) {
                    Log.d("Tab", String.valueOf(i));
                    plotter.setCurrentTab(i);
                    mViewPager.setCurrentItem(i);
                }
            }
        }

        @Override
        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            //set booleans to kill unseen processes
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            //set booleans to kill unseen processes
        }

        static final class TabInfo {
            private final Class<?> clss;
            private final Bundle args;

            TabInfo(Class<?> _class, Bundle _args) {
                clss = _class;
                args = _args;
            }
        }

        public TabsAdapter(MainActivity activity, ViewPager pager) {
            super(activity.getSupportFragmentManager());
            mContext = activity;
            mActionBar = activity.getSupportActionBar();
            mViewPager = pager;
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
        }

        public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args, Fragment frag) {
            TabInfo info = new TabInfo(clss, args);
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mFrag.add(frag);
            mActionBar.addTab(tab);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Fragment getItem(int position) {
            TabInfo info = mTabs.get(position);
            return mFrag.get(position);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            mActionBar.setSelectedNavigationItem(position);
            /*
            MenuInflater inflater = mContext.getSupportMenuInflater();
            mContext.menu.clear();
            inflater.inflate(R.menu.MainActivity, mContext.menu);
            */
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    }

    /***********************TOP ADDED BY CHARLES FOR SWIPEABLE TABS***************************/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
       getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.connect:
            case MENU_LIST:
                Intent intent = new Intent(this, ListActivity.class);
                startActivity(intent);
                return true;

            case R.id.disconnect:
                Intent mStartActivity = new Intent(MainActivity.this, MainActivity.class);
                int mPendingIntentId = 12;
                PendingIntent mPendingIntent = PendingIntent.getActivity(MainActivity.this, mPendingIntentId, mStartActivity,
                        PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) MainActivity.this.getSystemService(getApplicationContext().ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                System.exit(0);
                //closeBLEGatt();
                Toast.makeText(getApplicationContext(), "Close GATT", Toast.LENGTH_SHORT).show();
                return true;
        }
        return false;
    }

/*

  @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menu.add(0, MENU_LIST, 0, "Connect to Myo");
        menu.add(1, MENU_BYE, 1, "Disconnect");
        return true;
    }


  @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        switch (id) {
            case MENU_LIST:
//                Log.d("Menu","Select Menu A");
                Intent intent = new Intent(this, ListActivity.class);
                startActivity(intent);
                return true;

            case MENU_BYE:
//                Log.d("Menu","Select Menu B");
                closeBLEGatt();
                Toast.makeText(getApplicationContext(), "Close GATT", Toast.LENGTH_SHORT).show();
                return true;
        }
        return false;
    }*/

    /*
    int nSamples = 100;

    public void onClickTrain(View v) {
        //for(int i = 0; i < gestureCounter; i++) {
        //Log.d(TAG, "NEW GESTURE %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
            /*
            try {
                Thread.sleep(3000);

            } catch(InterruptedException e) {
                e.printStackTrace();
            }

        for (int j = 0; j < nSamples; j++) {
            mMyoCallback.getFeatCalc().setTrain(true);
            //if(j == 100 ){Log.d(TAG, "NEW GESTURE %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");}
        }
        //}
        mMyoCallback.getFeatCalc().printClassiferTrainer();
    }
    */
    public void onClickedcbGesture(View view) {

        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();

        // Check which checkbox was clicked
//        switch(view.getId()) {
//            case R.id.cbFist:
//                if (checked){
//                    gestureCounter++;
//                }
//
//                else{
//                    if(gestureCounter > 0){gestureCounter--;}
//                    //Remove value from gestureCounter++;
//                }
//
//                break;
//            case R.id.cbOpenHand:
//                if (checked){
//                    gestureCounter++;
//                }
//
//                else{
//                    if(gestureCounter > 0){gestureCounter--;}
//                }
//                break;
//
//            case R.id.cbPoint:
//                if(checked){
//                    gestureCounter++;
//                }
//                else{
//                    if(gestureCounter > 0){gestureCounter--;}
//                }
//                break;
//        }
    }

    public void onClickedAddGesture(View view) {
        //Using to debig the amount of gestures at the moment
        Log.d(TAG, "Gesture Count: " + gestureCounter);
        //Add Name for the gesture
        //Add gesture to the gesture counter
        //Call classifier and add the gesture to it

    }

//    public void onClickVibration(View v) {
//        if (mBluetoothGatt == null || !mMyoCallback.setMyoControlCommand(commandList.sendVibration3())) {
//            Log.d(TAG, "False Vibrate");
//        }
//    }
//
//    public void onClickUnlock(View v) {
//        if (mBluetoothGatt == null || !mMyoCallback.setMyoControlCommand(commandList.sendUnLock())) {
//            Log.d(TAG, "False UnLock");
//        }
//    }
//
//    public void onToggleClicked(View v) {
//        Log.d(TAG, "Toggle Clicked");
//        if (click = true) {
//            if (mBluetoothGatt == null || !mMyoCallback.setMyoControlCommand(commandList.sendEmgOnly())) {
//                Log.d(TAG, "False EMG");
//                click = false;
//            }
//        } else {
//            if (mBluetoothGatt == null
//                    || !mMyoCallback.setMyoControlCommand(commandList.sendUnsetData())
//                    || !mMyoCallback.setMyoControlCommand(commandList.sendNormalSleep())) {
//                Log.d(TAG, "False Data Stop");
//                click = true;
//            }
//
//        }
//        click=!click;
//    }

    public void closeBLEGatt() {
        if (mBluetoothGatt == null) {
            return;
        }
        mMyoCallback.stopCallback();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLEScanner.stopScan(scanCallback);
                }
            }, SCAN_PERIOD);
            mLEScanner.startScan(scanCallback);
        }
        else if(requestCode==2 && resultCode==RESULT_OK){
            classificationFragment.givePath(data.getData(), this);
        }
    }

}


