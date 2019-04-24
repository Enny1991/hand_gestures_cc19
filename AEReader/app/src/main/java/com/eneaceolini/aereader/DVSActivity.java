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

public class DVSActivity extends Activity {

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
		setContentView(R.layout.dvs_activity);
		imageView = findViewById(R.id.an_imageView);
		btnStart = findViewById(R.id.start);
		btnStop = findViewById(R.id.stop);
		btnCheck = findViewById(R.id.devInfo);
		textInfo = findViewById(R.id.info);
		timeout = findViewById(R.id.editText);

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		width_image = dm.heightPixels/2;
		height_image = dm.heightPixels/2;

		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		if (getIntent().hasExtra(UsbManager.EXTRA_DEVICE)) {
			device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
			Log.d("INFO::PRODUCT ID", "" + device.getProductId());
			Log.d("INFO::VENDOR ID", "" + device.getVendorId());
			readEvents = new ReadEvents(DVSActivity.this, device, usbManager, Integer.parseInt(timeout.getText().toString()));
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
				readEvents = new ReadEvents(DVSActivity.this, device, usbManager, Integer.parseInt(timeout.getText().toString()));
				handler.removeCallbacks(runnable);
			}
		});

		btnCheck.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				checkInfo();
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

	private void checkInfo() {
		UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

		String i = "";
		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();
			i += "\n" +
					"DeviceID: " + device.getDeviceId() + "\n" +
					"DeviceName: " + device.getDeviceName() + "\n" +
					"DeviceClass: " + device.getDeviceClass() + " - "
					+ translateDeviceClass(device.getDeviceClass()) + "\n" +
					"DeviceSubClass: " + device.getDeviceSubclass() + "\n" +
					"VendorID: " + device.getVendorId() + "\n" +
					"ProductID: " + device.getProductId() + "\n";
		}

		textInfo.setText(i);
	}

	private String translateDeviceClass(int deviceClass){
		switch(deviceClass){
			case UsbConstants.USB_CLASS_APP_SPEC:
				return "Application specific USB class";
			case UsbConstants.USB_CLASS_AUDIO:
				return "USB class for audio devices";
			case UsbConstants.USB_CLASS_CDC_DATA:
				return "USB class for CDC devices (communications device class)";
			case UsbConstants.USB_CLASS_COMM:
				return "USB class for communication devices";
			case UsbConstants.USB_CLASS_CONTENT_SEC:
				return "USB class for content security devices";
			case UsbConstants.USB_CLASS_CSCID:
				return "USB class for content smart card devices";
			case UsbConstants.USB_CLASS_HID:
				return "USB class for human interface devices (for example, mice and keyboards)";
			case UsbConstants.USB_CLASS_HUB:
				return "USB class for USB hubs";
			case UsbConstants.USB_CLASS_MASS_STORAGE:
				return "USB class for mass storage devices";
			case UsbConstants.USB_CLASS_MISC:
				return "USB class for wireless miscellaneous devices";
			case UsbConstants.USB_CLASS_PER_INTERFACE:
				return "USB class indicating that the class is determined on a per-interface basis";
			case UsbConstants.USB_CLASS_PHYSICA:
				return "USB class for physical devices";
			case UsbConstants.USB_CLASS_PRINTER:
				return "USB class for printers";
			case UsbConstants.USB_CLASS_STILL_IMAGE:
				return "USB class for still image devices (digital cameras)";
			case UsbConstants.USB_CLASS_VENDOR_SPEC:
				return "Vendor specific USB class";
			case UsbConstants.USB_CLASS_VIDEO:
				return "USB class for video devices";
			case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
				return "USB class for wireless controller devices";
			default: return "Unknown USB class!";

		}
	}

}
