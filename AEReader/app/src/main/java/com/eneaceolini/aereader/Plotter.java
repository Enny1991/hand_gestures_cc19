package com.eneaceolini.aereader;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Log;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.RadarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.RadarData;
import com.github.mikephil.charting.data.RadarDataSet;
import com.github.mikephil.charting.data.RadarEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.opencv.core.Mat;
import org.opencv.ml.SVM;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by Alex on 6/30/2017.
 */

public class Plotter extends Activity {
    //boolean emg;
    private static RadarChart mChart;
    private LineChart lineChart;
    private static Handler mHandler;
    protected Typeface mTfLight;

    private static int currentTab = 0; //current tab from DVSActivity
    private int lineColor = Color.rgb(64, 64, 64);

    public boolean startup = true;

    int[][] dataList1_a = new int[10][50];
    int[][] dataList1_b = new int[10][50];

    private int nowGraphIndex = 3;
    private static int nowGraphIndexIMU = 0;

    private ArrayList<Number> f0, f1, f2;

    private static boolean[] featuresSelected = new boolean[]{true, true, true};

    private int w, x, y, z;
    private double pitch, roll, yaw;

    private SVM svm;
    private Mat f;
    private float[] mean, std;
    BlockingQueue<float[]> blockingQueueEMG = new LinkedBlockingDeque<>();
    ArrayList<Float> currentEMG = new ArrayList<>();

    public Plotter() {
    }

    public Plotter(Handler handler, RadarChart chart, ArrayList<Float> queue) {
        Log.d("PLOTTER", "SPAWN");
        mHandler = handler;
        currentEMG = queue;
        mChart = chart;
        mHandler = new Handler();

        mChart.setNoDataText("");
        mChart.setBackgroundColor(Color.TRANSPARENT);
        mChart.getDescription().setEnabled(false);
        mChart.setWebLineWidth(1f);
        mChart.setWebColor(Color.LTGRAY);
        mChart.setWebLineWidthInner(1f);
        mChart.setWebColorInner(Color.LTGRAY);
        mChart.getLegend().setTextColor(Color.WHITE);
        mChart.setWebAlpha(100);
//      mChart.getLegend().setTextSize(20f);
        mChart.getLegend().setPosition(Legend.LegendPosition.LEFT_OF_CHART_CENTER);

        XAxis xAxis = mChart.getXAxis();
        //xAxis.setTypeface(mTfLight);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setYOffset(0f);
        xAxis.setXOffset(0f);
        xAxis.setValueFormatter(new IAxisValueFormatter() {

            private String[] mActivities = new String[]{"1", "2", "3", "4", "5", "6", "7", "8"};

            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return mActivities[(int) value % mActivities.length];
            }
        });
        YAxis yAxis = mChart.getYAxis();
        //yAxis.setTypeface(mTfLight);
        yAxis.setLabelCount(8, false);
        yAxis.setTextSize(9f);
        yAxis.setAxisMinimum(0);
        yAxis.setAxisMaximum(128);
        yAxis.setDrawLabels(false);

        twoDimArray featemg = new twoDimArray();
        featemg.createMatrix(3, 8);

        this.setCurrentTab(1);

        for(int i=0; i<8; i++){
            for (int j=0;j<3;j++){
                featemg.setMatrixValue(j, i, 128);
            }
        }

        this.pushFeaturePlotter(featemg);

        for(int i=0; i<8; i++){
            for (int j=0;j<3;j++){
                featemg.setMatrixValue(j, i, 0);
            }
        }

        this.pushFeaturePlotter(featemg);
        Log.d("PLOTTER", "INIT");


    }


    private File createFileFromInputStream(InputStream inputStream) {

        try{
            File f = new File(getApplication().getFilesDir().getPath() + "/local_dump_smv.xml");
            OutputStream outputStream = new FileOutputStream(f);
            byte buffer[] = new byte[1024];
            int length = 0;

            while((length=inputStream.read(buffer)) > 0) {
                outputStream.write(buffer,0,length);
            }

            outputStream.close();
            inputStream.close();

            return f;
        }catch (IOException e) {
            //Logging exception
            e.printStackTrace();
        }

        return null;
    }

    public Plotter(Handler handler) {
        mHandler = handler;
    }

    public Plotter(Handler handler, LineChart line){

        mHandler = handler;
        this.lineChart = line;
        lineChart.setNoDataText("");

        // enable description text
        lineChart.getDescription().setEnabled(true);

        // enable touch gestures
        lineChart.setTouchEnabled(true);

        // enable scaling and dragging
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        lineChart.setPinchZoom(true);

        // set an alternative background color
        lineChart.setBackgroundColor(Color.TRANSPARENT);

        // add empty data
        initData();

        // get the legend (only possible after setting data)
//        Legend l = lineChart.getLegend();

        // modify the legend ...
//        l.setForm(Legend.LegendForm.LINE);
//        l.setTextColor(Color.LTGRAY);

        XAxis xl = lineChart.getXAxis();
        xl.setTextColor(Color.LTGRAY);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.LTGRAY);
//        leftAxis.setAxisMaximum(100f);
//        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);
//        lineChart.animateX(2000);
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();

    }

    private final int[] colors = new int[] {
            Color.rgb(169, 95, 95),
            Color.rgb(100, 169, 95),
            Color.rgb(89, 140, 175),
            Color.rgb(64, 64, 64),
            Color.rgb(171, 21, 21),
            Color.rgb(94, 62, 130),
            Color.rgb(171, 89, 43),
            Color.rgb(189, 75, 167)
    };

    private void initData() {
        lineChart.resetTracking();
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();

        for (int z = 0; z < 8; z++) {

            ArrayList<Entry> values = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                double val = (Math.random() * z) + i;
                values.add(new Entry(i, (float) val));
            }

            LineDataSet d = new LineDataSet(values, "DataSet " + (z + 1));
            d.setLineWidth(1f);
            d.setDrawCircles(false);

            int color = colors[z % colors.length];
            d.setColor(color);
            dataSets.add(d);
        }

        // make the first DataSet dashed
        ((LineDataSet) dataSets.get(0)).enableDashedLine(10, 10, 0);
        ((LineDataSet) dataSets.get(0)).setColors(ColorTemplate.VORDIPLOM_COLORS);
        ((LineDataSet) dataSets.get(0)).setCircleColors(ColorTemplate.VORDIPLOM_COLORS);

        LineData data = new LineData(dataSets);
        lineChart.setData(data);
        lineChart.invalidate();

    }


    public void pushPlotter(byte[] data) {
//        setData();
        if (data.length == 16 && (currentTab == 0||currentTab==1)) {
//        if ((data.length == 16 && currentTab == 0)||startup) {

//            Log.d("tag", String.valueOf(startup));

            mHandler.post(new Runnable() {
                @Override
                public void run() {
//                    dataView.setText(callback_msg);
//                    Log.d("In: ", "EMG Graph");

                    for (int inputIndex = 0; inputIndex < 8; inputIndex++) {
                        dataList1_a[inputIndex][0] = data[0 + inputIndex];
                        dataList1_b[inputIndex][0] = data[7 + inputIndex];
                    }
                    // 折れ線グラフ
                    int number = 50;
                    int addNumber = 100;
                    while (0 < number) {
                        number--;
                        addNumber--;

                        //１点目add
                        if (number != 0) {
                            for (int setDatalistIndex = 0; setDatalistIndex < 8; setDatalistIndex++) {
                                dataList1_a[setDatalistIndex][number] = dataList1_a[setDatalistIndex][number - 1];
                            }
                        }
                        addEntry(dataList1_a[nowGraphIndex][number], nowGraphIndex);
                        //linePoint.setColor(Color.parseColor("#9acd32")); // 丸の色をSet

                        //2点目add
                        /////number--;
                        addNumber--;
                        if (number != 0) {
                            for (int setDatalistIndex = 0; setDatalistIndex < 8; setDatalistIndex++) {
                                dataList1_b[setDatalistIndex][number] = dataList1_b[setDatalistIndex][number - 1];
                            }
                        }
                    }
                }
            });
        } else if (data.length == 20 && currentTab == 1) {//emg=false;
//            chartView.setAnimation(an);
//            Log.d("In: ", "IMU Graph");
            w = data[0];
            x = data[1];
            y = data[2];
            z = data[3];

            roll = Math.atan(2 * (x * w + z * y) / (2 * (x ^ 2 + y ^ 2) - 1));

//            Log.d("roll", String.valueOf(roll));

//            Log.d("IMU: ", Arrays.toString(data));

//            Log.d("roll: ", String.valueOf(roll));

//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    mChart.spin(1,0,30, Easing.EasingOption.Linear);
//                }
//            });
        }
    }

    public void addEntry(float e, int idx) {
//        Log.d("ADDING", "ENTRY");
        LineData data = lineChart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(idx);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), e), idx);
            data.notifyDataChanged();

            // let the chart know it's data has changed
            lineChart.notifyDataSetChanged();

            // limit the number of visible entries
            lineChart.setVisibleXRangeMaximum(120);
            // chart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            lineChart.moveViewToX(data.getEntryCount());

            // this automatically refreshes the chart (calls invalidate())
            // chart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }


    long start = System.currentTimeMillis();

    public synchronized void pushFeaturePlotter(twoDimArray featureData) {
        if (mChart != null && currentTab == 1) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    f0 = featureData.getInnerArray(0);
                    f1 = featureData.getInnerArray(1);
                    f2 = featureData.getInnerArray(2);

                    ArrayList<RadarEntry> entries0 = new ArrayList<RadarEntry>();
                    ArrayList<RadarEntry> entries1 = new ArrayList<RadarEntry>();
                    ArrayList<RadarEntry> entries2 = new ArrayList<RadarEntry>();

                    float[] toPush = new float[24];

                    for (int i = 0; i < 8; i++) {
                        //2000 per division 14 000 in total
                        entries0.add(new RadarEntry(setMaxValue(f0.get(i).floatValue() * 200)));
                        entries1.add(new RadarEntry(setMaxValue(f1.get(i).floatValue() * 200)));
                        entries2.add(new RadarEntry(setMaxValue(f2.get(i).floatValue() * 200)));
                        toPush[i] = f0.get(i).floatValue();
                        toPush[i + 8] = f1.get(i).floatValue();
                        toPush[i + 16] = f2.get(i).floatValue();

                    }


                    for(int i = 0; i < 24; i++) {
                        currentEMG.add(toPush[i]);
                        if (currentEMG.size() > 24)
                            currentEMG.remove(0);
                    }

                    start = System.currentTimeMillis();

                    ArrayList<IRadarDataSet> sets = new ArrayList<IRadarDataSet>();

                    RadarDataSet set0 = new RadarDataSet(entries0, "MAV");
//                    set0.setColor(Color.rgb(123, 174, 157));
//                    set0.setFillColor(Color.rgb(78, 118, 118));
                    set0.setColor(Color.argb(255, 0, 255, 255));
                    set0.setFillColor(Color.argb(10, 0, 255, 255));
                    set0.setDrawFilled(true);
                    set0.setFillAlpha(100);
                    set0.setLineWidth(2f);

                    RadarDataSet set1 = new RadarDataSet(entries1, "RMS");
//                    set1.setColor(Color.rgb(241, 148, 138));
//                    set1.setFillColor(Color.rgb(205, 97, 85));

                    set1.setColor(Color.argb(255, 255, 0, 255));
                    set1.setFillColor(Color.argb(10, 255, 0, 255));
                    set1.setDrawFilled(true);
                    set1.setFillAlpha(100);
                    set1.setLineWidth(2f);

//                    RadarDataSet set2 = new RadarDataSet(entries2, "SD");
//                    set2.setColor(Color.rgb(175, 122, 197));
//                    set2.setFillColor(Color.rgb(165, 105, 189));
//                    set2.setDrawFilled(true);
//                    set2.setFillAlpha(180);
//                    set2.setLineWidth(2f);

                    if (featuresSelected[0])
                        sets.add(set0);
                    if (featuresSelected[1])
                        sets.add(set1);
//                    if (featuresSelected[2])
//                        sets.add(set2);

                    if (!sets.isEmpty()) {
                        RadarData data = new RadarData(sets);
                        data.setValueTextSize(18f);
                        data.setDrawValues(false);
                        mChart.setData(data);
                        mChart.notifyDataSetChanged();
                        mChart.invalidate();
                    }
                }
            });
        }else if (mChart==null){
            Log.d("wassup ", "mchart might be null************************************");
        }
    }

    public void setEMG(int color, int emg) {
        lineColor = color;
        nowGraphIndex = emg;
    }

    public static void setIMU(int imu) {
        nowGraphIndexIMU = imu;
    }

    public void setCurrentTab(int tab) {
        currentTab = tab;
    }

    public void setFeatures(boolean[] features) {
        featuresSelected = features;
    }

    public float setMaxValue(float inValue){
        float value = inValue;
        if (inValue > 14000) {
            value =  14000;
        }
        return value;
    }
}
