package com.eneaceolini.aereader.biases;

/**
 * Interface to external ADC on PCB.
 *
 * @author tobi
 */
public interface ADCHardwareInterface {
    public static final String EVENT_ADC_CHANGED="adcChanged";
    public static final String EVENT_ADC_CHANNEL_MASK = "adcChannelMask";
    public static final String EVENT_ADC_ENABLED = "adcEnabled";
    public static final String EVENT_IDLE_TIME = "idleTime";
    public static final String EVENT_TRACK_TIME = "trackTime";
    public static final String EVENT_SEQUENCING = "sequencingEnabled"; // method not yet in this interface


    /**
     * @return the ADCchannel
     */
    public int getADCChannel();

    /**
     * @return the IdleTime
     */
    public int getIdleTime();


    /**
     * @return the TrackTime
     */
    public int getTrackTime();

    public boolean isADCEnabled();

    public void setADCEnabled(boolean yes) ;

    public void setADCChannel(int chan);

    public void setIdleTime(int trackTimeUs);


    public void setTrackTime(int trackTimeUs);

    public void startADC() ;

    public void stopADC() ;

    public void sendADCConfiguration() ;

//    public boolean isSequencingEnabled(); // not in general used
//
//    public void setSequencingEnabled(boolean yes);
}
