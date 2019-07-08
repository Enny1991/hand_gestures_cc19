package com.eneaceolini.aereader;

import android.util.Log;

import java.util.ArrayList;

/**
*
* @author Nicolas Oros and Julien Martel, 2014
* 
* https://github.com/UCI-ABR
* http://www.socsci.uci.edu/~jkrichma/ABR/
* https://groups.google.com/forum/#!forum/android-based-robotics
* https://neuromorphs.net/nm/wiki/AndroideDVS
*/
public class CochleaAms1CProcessor extends AERProcessor {
	static final String TAG = "Serial processor";

	private ArrayList<CochleaAMS1cEvent>     mEvents;
	private int wrapAdd = 0;

	private String						mAsciiData;
	private int[]                      	mEDVSinCollection;
	private long                        mEDVSTimestamp;
	private int							mInputProcessingIndex;
	private int eventCounter;

	public final short TICK_US = 1;
	/** The USB product ID of this device */
	static public final short PID = (short) 0x8406;
	/** data type is either timestamp or data (AE address or ADC reading) */
	public static final int DATA_CODE_MASK = 0xc000,
			DATA_CODE_DATA = 0x0000,
			DATA_CODE_TIMESTAMP = 0x4000,
			DATA_CODE_WRAP = 0x8000,
			DATA_CODE_TIMESTAMP_RESET = 0xd000;
	/** Data word is tagged as AER address or ADC sample by bit 0x2000 (DATA_TYPE_MASK). Set (ADDRESS_TYPE_ADC)=ADC sample; unset (DATA_TYPE_AER_ADDRESS)=AER address. */
	public static final int DATA_TYPE_MASK = 0x2000, //
			DATA_TYPE_AER_ADDRESS = 0x0000, // aer addresses don't have the bit set
			DATA_TYPE_ADC = 0x2000; // adc samples have this bit set
	/** For ADC data, the data is defined by the ADC channel and whether it is the first ADC value from the scanner. */
	public static final int ADC_DATA_MASK=0x3fff, // all ADC + sync bits+ ID bit that it is ADC sample
			ADC_SAMPLE_MASK = 0x3ff, // the actual sample bits
			ADC_SYNC_BIT = 0x1000, // marks sync input active
			ADC_CHANNEL_MASK = 0x0c00; // marks ADC channel of data
	/** AER_DATA_MASK (0x3ff) part of data word. */
	public static final int AER_DATA_MASK = 0x3ff; // used elsewhere to mask for AER data
	public final byte VR_IS_TS_MASTER = (byte) 0xCB;  // this VR make the board timestamp master if argument is 1, otherwise it is slave device (default in firmware is master device)


	public enum EDVS4337EventMode {
		TS_MODE_E0,
		TS_MODE_E1,
		TS_MODE_E2,
		TS_MODE_E3,
		TS_MODE_E4
	}



	public class CochleaAMS1cEvent extends AEREvent {
		CochleaAMS1cEvent(int ch, int neuron, int side, long ts) {
			this.ch = ch;
			this.neuron = neuron;
			this.side = side;
			this.ts = ts;
		}

		CochleaAMS1cEvent() {
			this.ch = 0;
			this.neuron = 0;
			this.side = 0;
			this.ts = 0;
		}

		int ch;
		int neuron;
		int side;
		long ts;
	}



	/**
	 *
	 */
	CochleaAms1CProcessor() {
		mEvents 				= new ArrayList<>();
		mEDVSinCollection 		= new int[20000]; //was 512
		mAsciiData 				= "";
		mEDVSTimestamp 			= 0;		
		mInputProcessingIndex 	= 0;
	}

	@Override
	void resetTimestamps() {

	}


	public ArrayList<CochleaAMS1cEvent> process(byte[] buf, int bytesSent) {
		if(!mEvents.isEmpty()) 	mEvents.clear();

		int currentts = 0;
		for (int i = 0; i < bytesSent; i += 2) {
			int dataword = (0xff & buf[i]) | (0xff00 & (buf[i + 1] << 8));  // data sent little endian
//			int dataword = (buf[i+1] << 8) + buf[i]; // 16 bit value of data;  // data sent little endian
			String s1 = String.format("%8s", Integer.toBinaryString(dataword)).replace(' ', '0');

			final int code = (buf[i + 1] & 0xC0) >> 6; // gets two bits at XX00 0000 0000 0000. (val&0xC000)>>>14;
			//  log.info("code " + code);
			switch (code) {
				case 0: // data, either AER address or ADC sample
					// If the data is an address, we write out an address value if we either get an ADC reading or an x address.
					// To simplify data structure handling in AEPacketRaw and AEPacketRawPool,
					// ADC events are timestamped just like address-events.
					// NOTE2: unmasked bits are read as 1's from the hardware. Therefore it is crucial to properly mask bits.
//					if ((eventCounter >= aeBufferSize) || (buffer.overrunOccuredFlag)) {
//						buffer.overrunOccuredFlag = true; // throw away events if we have overrun the output arrays
//					 else
						if (isADCSample(dataword)) {
//							addresses[eventCounter] = dataword & ADC_DATA_MASK; // leave all bits unchanged for ADC sample
//							timestamps[eventCounter] = currentts;  // ADC event gets timestamp too
//							eventCounter++;
//							Log.d("ADC", "EVENT");
//                                        System.out.println("ADC word: " + dataword + " adcChannel=" + adcChannel(dataword) + " adcSample=" + adcSample(dataword) + " isScannerSyncBit=" + isScannerSyncBit(dataword));
						} else { //  received an address, write out event to addresses/timestamps output arrays, masking out other bits
							CochleaAMS1cEvent e = new CochleaAMS1cEvent();
							int add = (dataword & AER_DATA_MASK);
							e.neuron = (add & 0x300) >> 8;
							e.ch = (add & 0xfc) >> 2;
							e.side = (add & 0x2) >> 1;
							e.ts = currentts;
							eventCounter++;
							mEvents.add(e);
//							Log.d("EV", "EVENT");
						}

					break;
				case 1: // timestamp - always comes before data
					currentts = ((0x3f & buf[i + 1]) << 8) | (buf[i] & 0xff);
//					currentts = (dataword & 0x3fff);
					currentts = (TICK_US * (currentts + wrapAdd));
//                                System.out.println("timestamp=" + currentts);

					break;
				case 2: // timestamp wrap
					wrapAdd += 0x4000L;
//					NumberOfWrapEvents++;
//					wrapAdd += currentts;
//					Log.d("EVENT", "WRAP");
					//   log.info("wrap");
					break;
				case 3: // ts reset event
//                                log.info("got timestamp reset event from hardware, resetting timestamps to zero");
					Log.d("RESET", "TIMESTAMP");
//					this.resetTimestamps();
					//   log.info("timestamp reset");
					break;
			}
		} // end for

		return mEvents;
	}

	/** Determines if data is AER address
	 *
	 * @param dataword 16 bit 'address' data
	 * @return true if AER address
	 */
	final public static boolean isAERAddress(int dataword) {
		return (dataword & DATA_TYPE_MASK) == DATA_TYPE_AER_ADDRESS;
	}

	/** Determines is data is ADC sample.
	 *
	 * @param dataword 16 bit 'address' data
	 * @return true if ADC sample
	 */
	final public static boolean isADCSample(int dataword) {
		return (dataword & DATA_TYPE_MASK) == DATA_TYPE_ADC;
	}

	/** Parses the ADC channel number from the ADC sample word
	 *
	 * @param adcData
	 * @return channel number, 0-3
	 */
	final public static int adcChannel(int adcData) {
		return ((adcData & ADC_CHANNEL_MASK) >>> Integer.bitCount(ADC_SAMPLE_MASK)); // bits 11:10
	}

	/** Parses the ADC sample value from the ADC sample word
	 *
	 * @param adcData
	 * @return sample value, 10 bits
	 */
	final public static int adcSample(int adcData) {
		return (adcData & ADC_SAMPLE_MASK); // rightmost 10 bits 9:0
	}

	/** Is the ADC sample associated with the sync input high at the time of sampling.
	 *
	 * @param adcData
	 * @return true if sync input (from cochlea) is active (it is active low during sync period when no channel is selected)
	 */
	final public static boolean isScannerSyncBit(int adcData) {
		return ((adcData & ADC_SYNC_BIT) == 0);
	}

}
