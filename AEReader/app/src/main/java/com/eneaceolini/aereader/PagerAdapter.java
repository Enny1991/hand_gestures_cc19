package com.eneaceolini.aereader;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class PagerAdapter extends FragmentStatePagerAdapter {
    int mNumOfTabs = 5;
    private String[] tabTitles = new String[]{"EMG", "DVS", "FEATURES", "IMU", "CLASSIFIER"};

    public PagerAdapter(FragmentManager fm, int NumOfTabs) {
        super(fm);
        this.mNumOfTabs = NumOfTabs;
    }

    @Override
    public Fragment getItem(int position) {

        switch (position) {
            case 0:
                EmgFragment tab1 = new EmgFragment();
                return tab1;
            case 1:
//                DVSPlusEMG tab11 = new DVSPlusEMG();
                DVSFragmentv2 tab11 = new DVSFragmentv2();
                return tab11;
            default:
                return null;
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return tabTitles[position];
    }

    @Override
    public int getCount() {
        return mNumOfTabs;
    }
}
