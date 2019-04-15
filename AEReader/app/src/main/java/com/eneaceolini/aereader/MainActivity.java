package com.eneaceolini.aereader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends Activity {

	Button btnCheck;
	Button btnStart;
	Button btnStop;
	TextView textInfo;
	EditText timeout;
	private UsbDevice device;
	private UsbManager usbManager;
	private ReadEvents readEvents;

	ImageView imageView;
	Handler handler;
	Runnable runnable;
	int width_image, height_image;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		imageView = findViewById(R.id.an_imageView);
		btnStart = findViewById(R.id.start);
		btnStop = findViewById(R.id.stop);
		textInfo = findViewById(R.id.info);
		timeout = findViewById(R.id.editText);

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		width_image = dm.heightPixels/2;
		height_image = dm.heightPixels/2;

		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		if (getIntent().hasExtra(UsbManager.EXTRA_DEVICE)) {
			device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
			readEvents = new ReadEvents(MainActivity.this, device, usbManager, Integer.parseInt(timeout.getText().toString()));
			Log.d("DEVICE", "CONNECTED");
		}

		btnStart.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				readEvents.start();
				handler = new Handler();
				runnable = new Runnable() {
					public void run() {
						update_gui();
					}
				};
				handler.post(runnable);
			}
		});

		btnStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				readEvents.stop_thread();
				readEvents = new ReadEvents(MainActivity.this, device, usbManager, Integer.parseInt(timeout.getText().toString()));
				handler.removeCallbacks(runnable);
			}
		});
    }

	private void update_gui()
	{
		//get image from thread and display it
		Bitmap ima = readEvents.get_image();
		if(ima != null)
		{
			Bitmap ima2 = Bitmap.createScaledBitmap(ima, width_image, height_image, false);
			imageView.setImageBitmap(ima2);
		}
		handler.postDelayed(runnable, 1);
	}

}
