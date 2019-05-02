package com.eneaceolini.aereader;

import java.util.ArrayList;

class DVS128Processor extends AERProcessor {

	private ArrayList<DVS128Event> mEvents;
	private int wrapAdd = 0;

	private final short X_MASK = 0xfe, X_SHIFT = 1, Y_MASK = 0x7f00, Y_SHIFT = 8;
	private final short TICK_US = 1;

	class DVS128Event extends AEREvent {
		int x;
		int y;
		int polarity;
		int type;
		long ts;

		DVS128Event() {
			this.x = 0;
			this.y = 0;
			this.polarity = 0;
			this.ts = 0;
			this.type = 0;
		}
	}


	DVS128Processor() {
		mEvents = new ArrayList<>();
	}


	public ArrayList<DVS128Event> process(byte[] buf, int bytesSent) {
		if(!mEvents.isEmpty()) 	mEvents.clear();

		int lastTimestampTmp = 0;
		int address, shortts, timestamps;
		for (int i = 0; i < bytesSent; i += 4) {


			if ((buf[i + 3] & 0x80) == 0x80) { // timestamp bit 15 is one -> wrap

				wrapAdd += 0x4000L; // uses only 14 bit timestamps

			}
			else if ((buf[i + 3] & 0x40) == 0x40) { // timestamp bit 14 is one -> wrapAdd reset
				// this firmware version uses reset events to reset timestamps
				resetTimestamps();
				lastTimestampTmp = 0; // Also reset this one to avoid spurious warnings.

			}
			else {
				// address is LSB MSB
				address = (buf[i] & 0xFF) | ((buf[i + 1] & 0xFF) << 8);

				// same for timestamp, LSB MSB
				shortts = ((buf[i + 2] & 0xff) | ((buf[i + 3] & 0xff) << 8)); // this is 15 bit value
				// of timestamp in
				// TICK_US tick

				timestamps = TICK_US * (shortts + wrapAdd); // *TICK_US; //add in the wrap offset
				// and convert to 1us tick

				lastTimestampTmp = timestamps;

				DVS128Event e = new DVS128Event();
				e.type = (byte) ((1 - address) & 1);
				e.polarity = e.type == 0 ? 0 : 1;
				e.x = (short) (128 - ((short) ((address & X_MASK) >>> X_SHIFT)));
				e.y = (short) ((address & Y_MASK) >>> Y_SHIFT);
				e.ts = timestamps;

				if ((e.x < 128) & (e.y < 128)){
					mEvents.add(e);
				}

			}
		} // end for

		return mEvents;
	}

	void resetTimestamps(){
		// TODO
	}
}
