package com.eneaceolini.aereader;

import java.util.ArrayList;

abstract class AERProcessor {

	abstract class AEREvent {
	}

	private ArrayList<AEREvent> mEvents;

	AERProcessor() {
		mEvents = new ArrayList<>();
	}

	abstract  <T extends AEREvent> ArrayList<T> process(byte[] buf, int bytesSent);

	abstract void resetTimestamps();
}
