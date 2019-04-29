package com.eneaceolini.aereader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.INPUT_METHOD_SERVICE;


/**
 * Created by User on 2/28/2017.
 */

public class ClassificationFragment extends Fragment {

    private FeatureCalculator fcalc;
    private List<String> ListElementsArrayList;
    private List<String> ClassifierArrayList;
    private static SaveData saver;
    private ArrayList<DataVector> trainData = new ArrayList<>();
    private int count = 4;
    private Handler mHandler = new Handler();
    private int gestureCounter = 0;
    private TextView liveView, status;
    private TextView or_text;
    private ArrayList<Runnable> taskList = new ArrayList<Runnable>();

    Runnable r1;
    Runnable r2;

    EditText GetValue;
    ImageButton addButton;
    ImageButton deleteButton;
    ImageButton clearButton;
    ImageButton uploadButton;
    ImageButton trainButton;
    ImageButton loadButton;
    ImageButton resetButton;
    ListView listview_Classifier;
    ListView listview;
    ProgressBar progressBar;
    LayoutInflater inflater;
    ViewGroup container;

    //create an ArrayList object to store selected items
    public static ArrayList<String> selectedItems = new ArrayList<>();

    Classifier classifier = new Classifier();


    private Context context;

    String[] ListElements = new String[]{
            "Rest",
            "Fist",
            "Point",
            "Open Hand",
            "Wave In",
            "Wave Out",
            "Supination",
            "Pronation"
    };

    String[] classifier_options = new String[]{
            "LDA",
            "SVM",
            "Logistic Regression",
            "Decision Tree",
            "Neural Net",
            "KNN",
            "Adaboost"
    };

    private boolean listsDone = false;

    boolean getListsDone() {
        return listsDone;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final View v = inflater.inflate(R.layout.fragment_classification, container, false);

        assert v != null;
        context = this.getContext();

        this.inflater = inflater;
        this.container = container;

        fcalc = new FeatureCalculator(v, getActivity());
        classifier = new Classifier(getActivity());
        saver = new SaveData(this.getContext());
        saver.checkReadExternalStoragePermission();


        or_text = (TextView) v.findViewById(R.id.or_text);
        liveView = (TextView) v.findViewById(R.id.gesture_detected);
        GetValue = (EditText) v.findViewById(R.id.add_gesture_text);
        trainButton = (ImageButton) v.findViewById(R.id.bt_train);
        loadButton = (ImageButton) v.findViewById(R.id.bt_load);
        addButton = (ImageButton) v.findViewById(R.id.im_add);
        deleteButton = (ImageButton) v.findViewById(R.id.im_delete);
        uploadButton = (ImageButton) v.findViewById(R.id.im_upload);
        resetButton = (ImageButton) v.findViewById(R.id.im_reset);
        listview = (ListView) v.findViewById(R.id.listView);
        listview_Classifier = (ListView) v.findViewById(R.id.listView1);
        listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listview_Classifier.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        ListElementsArrayList = new ArrayList<String>(Arrays.asList(ListElements));
        ClassifierArrayList = new ArrayList<String>(Arrays.asList(classifier_options));
//        DocumentsContract = new DocumentsContract();

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.mytextview, ListElementsArrayList);

        ArrayAdapter<String> adapter_classifier = new ArrayAdapter<String>(getActivity(), R.layout.myradioview, ClassifierArrayList);

        listview.setAdapter(adapter);
        listview_Classifier.setAdapter(adapter_classifier);

        //selectes lda
        listview_Classifier.setItemChecked(0, true);

        for (int i = 0; i < ListElements.length; i++) {
            listview.setItemChecked(i, true);
            selectedItems.add(i, adapter.getItem(i));
        }

        //set OnItemClickListener
        listview.setOnItemClickListener((parent, view, position, id) -> {

            // selected item
            String selectedItem = ((TextView) view).getText().toString();

            if (selectedItems.contains(selectedItem)) {
                selectedItems.remove(selectedItem); //remove deselected item from the list of selected items
            } else {
                selectedItems.add(selectedItem); //add selected item to the list of selected items
            }
        });

        listview_Classifier.setOnItemClickListener((parent, view, position, id) -> {
            classifier.setChoice(position);
            String Classifier_selectedItem = ((TextView) view).getText().toString();
            Toast.makeText(getActivity(), "selected: " + Classifier_selectedItem, Toast.LENGTH_SHORT).show();
        });

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (selectedItems.size() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                    builder.setTitle("Delete?");
                    builder.setMessage("Are you sure you want to delete " + selectedItems + "?");

                    builder.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                        }
                    });
                    builder.setNegativeButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            String selItems = "";
                            while (selectedItems.size() > 0) {
                                for (int i = 0; i < selectedItems.size(); ++i) {
                                    String item = selectedItems.get(i);

                                    for (int x = 0; x <= item.length(); ++x) {
                                        selectedItems.remove(item); //remove deselected item from the list of selected items
                                        listview.setItemChecked(x, false);
                                        adapter.remove(item);
                                    }
                                    selItems += "," + item;
                                }
                            }
                            Toast.makeText(getActivity(), "Deleting: " + selItems, Toast.LENGTH_SHORT).show();
                            adapter.notifyDataSetChanged();
                        }
                    });
                    builder.show();

                } else if (ListElementsArrayList.size() > 0) {
                    Toast.makeText(getActivity(), "Please select the desired gestures to be deleted!", Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(getActivity(), "There is nothing to delete!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        addButton.setOnClickListener(v12 -> {

            try {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
            } catch (Exception e) {

            }

            String newGesture = GetValue.getText().toString();

            if (newGesture.matches("")) {
                Toast.makeText(getActivity(), "Please Enter A Gesture", Toast.LENGTH_SHORT).show();


            } else {
                ListElementsArrayList.add(GetValue.getText().toString());
                GetValue.setText("");
                adapter.notifyDataSetChanged();
            }
        });

//        uploadButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v){
//                fcalc.setClassify(false);
//                countdown(false);
//            }
//        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fileUpload();
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gestureCounter = 0;
                liveView.setText("");
                fcalc.reset();
                classifier.reset();
                trainButton.setVisibility(View.VISIBLE);
                liveView.setText("");
            }
        });

        trainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (MyoGattCallback.myoConnected == null) {
                    AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
                    alertDialog.setTitle("Myo not detected");
                    alertDialog.setMessage("Myo armband should be connected before training gestures.");
                    alertDialog.setIcon(R.drawable.stop_icon);

                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(getContext(), "On the top right corner, select 'Connect'", Toast.LENGTH_LONG).show();
                        }
                    });

                    alertDialog.show();

                } else {

                    //onClickTrain(v);
                    countdown(true);
//                mHandler.post(r1);
                }
            }
        });

        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fileLoad();
            }
        });

        return v;
    }

    private void countdown(boolean train) {

        fcalc.sendClasses(selectedItems);
        count = 4;
        gestureCounter = 0;

        r1 = new Runnable() {
            @Override
            public void run() {
                if (selectedItems.size() > 1) {
                    trainButton.setVisibility(View.GONE);

                    if ((--count != -1) && (gestureCounter != selectedItems.size())) {
                        mHandler.postDelayed(this, 1000);
                        liveView.setText("Do " + selectedItems.get(gestureCounter) + " in " + String.valueOf(count));
//                        progressBar.setVisibility(View.VISIBLE);

                        if (count == 0) {
//                            progressBar.setVisibility(View.INVISIBLE);
                            liveView.setText("Hold " + selectedItems.get(gestureCounter));
                            //status.getText(featureCalculator.sampleClassifier);
                        }
                    } else if (gestureCounter != selectedItems.size()) {
                        count = 4;//3 seconds + 1
                        mHandler.post(this);
                        fcalc.setTrain(true);
                        while (fcalc.getTrain()) {//wait till trainig is done

                            /* For some reason we must print something here or else it gets stuck */
                            System.out.print("");
                        }
                        gestureCounter++;
                    } else {
                        liveView.setText("");
                        if (train) {
                            fcalc.Train();
                        } else {
                            fileUpload();
                        }
                        fcalc.setClassify(true);
                    }
                } else if (selectedItems.size() == 1) {
                    Toast.makeText(getActivity(), "at least 2 gestures must be selected!", Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(getActivity(), "No gestures selected!", Toast.LENGTH_SHORT).show();

                }
            }
        };
        r1.run();
    }

    private void fileLoad() {
        if (MyoGattCallback.myoConnected == null) {
            AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
            alertDialog.setTitle("Myo not detected");
            alertDialog.setMessage("Myo armband should be connected before importing data.");
            alertDialog.setIcon(R.drawable.stop_icon);

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(getContext(), "On the top right corner, select 'Connect'", Toast.LENGTH_LONG).show();
                }
            });

            alertDialog.show();

        } else {
            AlertDialog.Builder loadDialog = new AlertDialog.Builder(getContext());
            loadDialog.setTitle("Load From:");
            loadDialog.setMessage("Where would you like to load the Trained Gestures from?");
            loadDialog.setIcon(R.drawable.add_icon_extra);
            loadDialog.setCancelable(true);

            loadDialog.setPositiveButton(
                    "SD CARD",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            openFolder();
                        }
                    });

//            loadDialog.setNegativeButton(
//                    "Cloud",
//                    new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int id) {
//                        }
//                    });

            loadDialog.setNeutralButton(
                    "Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });

            AlertDialog loadOptions = loadDialog.create();
            loadOptions.show();

            // Get the alert dialog buttons reference
            Button positiveButton = loadOptions.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = loadOptions.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button neutralButton = loadOptions.getButton(AlertDialog.BUTTON_NEUTRAL);

            // Change the alert dialog buttons text and background color
            positiveButton.setTextColor(Color.parseColor("#FFFFFF"));
            positiveButton.setBackgroundColor(Color.parseColor("#000000"));

            negativeButton.setTextColor(Color.parseColor("#FFFFFF"));
            negativeButton.setBackgroundColor(Color.parseColor("#030000"));

            neutralButton.setTextColor(Color.parseColor("#FFFFFF"));
            neutralButton.setBackgroundColor(Color.parseColor("#FF0000"));
        }
    }

    private void fileUpload() {
        Button cancel;
        Button sdCard;
        Button cloud;
        Button both;
        AlertDialog.Builder upload_pop = new AlertDialog.Builder(getActivity());

        View view = inflater.inflate(R.layout.upload_dialog, container, false);

        cancel = (Button) view.findViewById(R.id.bt_cancel);
        sdCard = (Button) view.findViewById(R.id.bt_sdcard);
        cloud = (Button) view.findViewById(R.id.bt_cloud);
        both = (Button) view.findViewById(R.id.bt_both);

        File file = saver.addData(fcalc.getFeatureData());

        final AlertDialog dialog = upload_pop.create();

        context = this.getContext();

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                file.delete();
                Toast.makeText(getActivity(), "Canceled", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        sdCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //saver.addData(fcalc.getSamplesClassifier(), selectedItems);
                Toast.makeText(getActivity(), "Saving on SDCARD!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        cloud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Response.Listener<String> responseListener = new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            boolean success = jsonResponse.getBoolean("success");

                            if (success) {
                                Log.d("Success ", "uploading emg data");
                            } else {
                                Log.d("Failed ", "uploading emg data");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                };

                ArrayList<DataVector> featureRows = fcalc.getFeatureData();

                Toast.makeText(getActivity(), "Saving on Cloud!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        both.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "Saving on SDCARD and Cloud!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.setView(view);
        dialog.show();
    }

    public void openFolder() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        getActivity().startActivityForResult(intent, 2);
    }

    public void givePath(Uri data, Context context) {
        saver.checkReadExternalStoragePermission();
        saver.checkWriteExternalStoragePermission();
        ArrayList<String> TempGestures = new ArrayList<>();
        for( int j = 0; j < ListElements.length; j++) {
            TempGestures.add(j, ListElements[j]);
        }
//        Log.d("tempGestures:", TempGestures.toString());
        try {
                BufferedReader reader = new BufferedReader(new FileReader(getPath(this.getContext(), data)));
                String text;
                String[] column;
                String[] emgData;
                double[] lineData = new double[48];
                ArrayList<Integer> classes = new ArrayList<>();

                int i = 0;
                while ((text = reader.readLine()) != null) {
                    column = text.split("\t");
                    classes.add(Integer.parseInt(column[0]));
                    emgData = column[1].split(",");
                    for (int j = 0; j < emgData.length; j++) {
                        lineData[j] = Double.parseDouble(emgData[j].replaceAll("[^\\d.]", ""));
                    }
                    Number[] feat_dataObj = ArrayUtils.toObject(lineData);
                    ArrayList<Number> LineData = new ArrayList<Number>(Arrays.asList(feat_dataObj));
                    DataVector dvec = new DataVector(Integer.parseInt(column[0]), lineData.length, LineData);
                    trainData.add(dvec);
                    i++;
                }
                Log.d("clases len, samples len", String.valueOf(classes.size()) + ", " + String.valueOf(trainData.size()));
                fcalc.setClasses(classes);
                fcalc.setSamplesClassifier(trainData);
                fcalc.sendClasses(TempGestures);
                fcalc.Train();
                fcalc.setClassify(true);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.d("REQUEST_CODE", String.valueOf(requestCode));
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Write Storage Permission (already) Granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case 2: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Read Storage Permission (already) Granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    public static String getPath(final Context context, final Uri uri) {

//        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
//
//        // DocumentProvider
//        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
////        if (isKitKat) {
//            // ExternalStorageProvider
//            if (isExternalStorageDocument(uri)) {
//                final String docId = DocumentsContract.getDocumentId(uri);
//                final String[] split = docId.split(":");
//                final String type = split[0];
//
//                Log.d("HELLLOOOO!!", "");
//
//                if ("primary".equalsIgnoreCase(type)) {
//                    return Environment.getExternalStorageDirectory() + "/" + split[1];
//                }
//            }
//            // DownloadsProvider
//            else if (isDownloadsDocument(uri)) {
//
//                final String id = DocumentsContract.getDocumentId(uri);
//                final Uri contentUri = ContentUris.withAppendedId(
//                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
//
//                return getDataColumn(context, contentUri, null, null);
//            }
//            // MediaProvider
//            else if (isMediaDocument(uri)) {
//                final String docId = DocumentsContract.getDocumentId(uri);
//                final String[] split = docId.split(":");
//                final String type = split[0];
//
//                Uri contentUri = null;
//                if ("image".equals(type)) {
//                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
//                } else if ("video".equals(type)) {
//                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
//                } else if ("audio".equals(type)) {
//                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
//                }
//
//                final String selection = "_id=?";
//                final String[] selectionArgs = new String[]{
//                        split[1]
//                };
//
//                return getDataColumn(context, contentUri, selection, selectionArgs);
//            }
//        }
//        // MediaStore (and general)
//        else if ("content".equalsIgnoreCase(uri.getScheme())) {
//            return getDataColumn(context, uri, null, null);
//        }
//        // File
//        else if ("file".equalsIgnoreCase(uri.getScheme())) {
//            return uri.getPath();
//        }

//        return context.getFilesDir().getPath();

        return uri.getPath();
//        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}

