package com.eneaceolini.aereader;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.RadarChart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by User on 2/28/2017.
 */

public class FeatureFragment extends Fragment {
    private static final String TAG = "Tab2Fragment";
    private RadarChart mChart;
    private Plotter plotter;
   // ListView listview_Classifier;
    ListView listView_Features;
   // ListView listView_IMU;

    Classifier classifier = new Classifier();

    //create an ArrayList object to store selected items
    ArrayList<String> selectedItems = new ArrayList<String>();

    //ArrayList<String> selectedItemsIMU = new ArrayList<String>();

    //private int numIMU = 0;
    private int numFeats = 6;

    private FeatureCalculator fcalc = new FeatureCalculator();

    String[] featureNames = new String[]{
            "MAV",
            "WAV",
            "Turns",
            "Zeros",
            "SMAV",
            "AdjUnique"
    };

/*    String[] IMUs = new String[]{
            "Orientation W",
            "Orientation X",
            "Orientation Y",
            "Orientation Z",
            "Accelerometer X",
            "Accelerometer Y",
            "Accelerometer Z",
            "Gyroscope X",
            "Gyroscope Y",
            "Gyroscope Z",
    };*/

    private static boolean[] featSelected = new boolean[]{true, true, true, true, true, true};

    private static boolean[] imuSelected = new boolean[]{false, false, false, false, false, false, false, false, false, false};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_feature, container, false);
        assert v != null;

        listView_Features = (ListView) v.findViewById(R.id.listView);

        listView_Features.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        //listView_IMU = (ListView) v.findViewById(R.id.listViewIMU);

       // listView_IMU.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        final List<String> FeaturesArrayList = new ArrayList<String>(Arrays.asList(featureNames));

        //final List<String> IMUArrayList = new ArrayList<String>(Arrays.asList(IMUs));

        ArrayAdapter<String> adapter_features = new ArrayAdapter<String>(getActivity(), R.layout.mytextview, FeaturesArrayList);

        //ArrayAdapter<String> adapter_IMU = new ArrayAdapter<String>(getActivity(), R.layout.mytextview, IMUArrayList);

        listView_Features.setAdapter(adapter_features);

       // listView_IMU.setAdapter(adapter_IMU);

        for (int i = 0; i < 6; i++) {
            listView_Features.setItemChecked(i, true);
            selectedItems.add(i, adapter_features.getItem(i));
        }

        /*for (int i = 0; i < 10; i++) {
            selectedItemsIMU.add(i, adapter_IMU.getItem(i));
        }*/

        listView_Features.setOnItemClickListener((parent, view, position, id) -> {

            // selected item
            String Features_selectedItem = ((TextView) view).getText().toString();

            if (selectedItems.contains(Features_selectedItem)) {
                featureManager(Features_selectedItem, false);
                selectedItems.remove(Features_selectedItem); //remove deselected item from the list of selected items
                classifier.numFeatures--;
                numFeats--;
                Log.d("NUM FEAT: ", "" + classifier.numFeatures);
            } else {
                featureManager(Features_selectedItem, true);
                selectedItems.add(Features_selectedItem); //add selected item to the list of selected items
                classifier.numFeatures++;
                numFeats++;
                Log.d("NUM FEAT: ", "" + classifier.numFeatures);
            }

            fcalc.setFeatSelected(featSelected);
            fcalc.setNumFeatSelected(numFeats);
            plotter.setFeatures(featSelected);

        });

/*        listView_IMU.setOnItemClickListener((parent, view, position, id) -> {

            // selected item
            String IMU_selectedItem = ((TextView) view).getText().toString();

            if (selectedItemsIMU.contains(IMU_selectedItem)) {
                IMUManager(IMU_selectedItem, true);
                selectedItemsIMU.remove(IMU_selectedItem); //remove deselected item from the list of selected items
                numIMU++;
            } else {
                IMUManager(IMU_selectedItem, false);
                selectedItemsIMU.add(IMU_selectedItem); //add selected item to the list of selected items
                numIMU--;
            }

            fcalc.setIMUSelected(imuSelected);
            classifier.setnIMUSensors(numIMU);
            fcalc.setNumIMUSelected(numIMU);

        });*/

        mChart = (RadarChart) v.findViewById(R.id.chart);
        plotter = new Plotter(mChart);//must pass chart from this fragment

        return v;
    }

    private void featureManager(String inFeature, boolean selected) {
        int index = 0;
        for (int i = 0; i < 6; i++) {
            if (inFeature == featureNames[i]) {
                index = i;
            }
        }
        featSelected[index] = selected;
    }

/*    private void IMUManager(String inFeature, boolean selected) {
        int index = 0;
        for (int i = 0; i < 10; i++) {
            if (inFeature == IMUs[i]) {
                index = i;
            }
        }

        imuSelected[index] = selected;
    }*/

    public String[] getFeatureNames() {
        return featureNames;
    }

    public static boolean[] getFeatSelected() {
        return featSelected;
    }

    /*public static boolean[] getIMUSelected() {
        return imuSelected;
    }*/
}
