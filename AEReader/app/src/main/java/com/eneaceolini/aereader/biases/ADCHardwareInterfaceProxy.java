package com.eneaceolini.aereader.biases;

import java.util.logging.Logger;


/**
 * A proxy to wrap around the actual hardware interface to expose the ADC controls
 * for purposes of GUI building using ParameterControlPanel.
 * It  stores
 * in preferences the state of the ADC, and calls update listener(s) when the state is changed.
 * A listener (e.g. a Chip's configuration control (biasgen)) registers itself as a listener
 * on this. In the update() method it reads the desired ADC state and sends appropriate messages
 * to the hardware.
 */
public class ADCHardwareInterfaceProxy extends HardwareInterfaceProxy implements ADCHardwareInterface{

    static final Logger log = Logger.getLogger("HardwareInterfaceProxy");
    private boolean adcEnabled;
    private int trackTime,  idleTime;
    private boolean sequencingEnabled;
//    private boolean UseCalibration;
    // following define limits for slider controls that are automagically constucted by ParameterControlPanel

    private int minTrackTime = 0;
    private int maxTrackTime = 100;
    private int minIdleTime = 0;
    private int maxIdleTime = 100;
    private int minADCchannel = 0;
    private int maxADCchannel = 3;
    private int adcChannel = 0;




    @Override
    public void setADCEnabled(boolean yes) {
        this.adcEnabled = yes;

    }

    @Override
    public boolean isADCEnabled() {
        return adcEnabled;
    }

    /** Sets the time in us that track and hold should should track before closing switch to sample signal.
     *
     * @param trackTime in us
     */
    @Override
    public void setTrackTime(int trackTime) {
        this.trackTime = trackTime;

    }

    /** Sets the time in us that ADC should idle after conversion is finished, before starting next track and hold.
     *
     * @param idleTime in us
     */
    @Override
    public void setIdleTime(int idleTime) {
        this.idleTime = idleTime;

    }

    /** Sets either the maximum ADC channel (if sequencing) or the selected channel
     *
     * @param channel the max (if sequencing) or selected channel. 0 based.
     */
    public void setADCChannel(int channel) {
        if(adcChannel<minADCchannel) adcChannel=minADCchannel; else if(adcChannel>maxADCchannel) adcChannel=maxADCchannel;
        this.adcChannel = channel;

    }

    @Override
    public int getTrackTime() {
        return trackTime;
    }

    @Override
    public int getIdleTime() {
        return idleTime;
    }

    @Override
    public int getADCChannel() {
        return adcChannel;
    }

    public int getMinTrackTime() {
        return minTrackTime;
    }

    public int getMaxTrackTime() {
        return maxTrackTime;
    }

    public int getMinIdleTime() {
        return minIdleTime;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public int getMinADCchannel() {
        return minADCchannel;
    }

    public int getMaxADCChannel() {
        return maxADCchannel;
    }

    @Override
    public void startADC() {
        setADCEnabled(true);
    }

    @Override
    public void stopADC()  {
        setADCEnabled(false);
    }

    @Override
    public void sendADCConfiguration()  {
    }

    @Override
    public String toString() {
        return "ADCHardwareInterfaceProxy{" + "adcEnabled=" + adcEnabled + ", trackTime=" + trackTime + ", idleTime=" + idleTime + ", lastChannel=" + adcChannel + '}';
    }

    /**
     * @return the sequencingEnabled
     */
    public boolean isSequencingEnabled() {
        return sequencingEnabled;
    }

    /**
     * @param sequencingEnabled the sequencingEnabled to set
     */
    public void setSequencingEnabled(boolean sequencingEnabled) {
        this.sequencingEnabled = sequencingEnabled;
    }

    /**
     * @param minTrackTime the minTrackTime to set
     */
    public void setMinTrackTimeValue(int minTrackTime) { // named this to avoid javabeans property
        this.minTrackTime = minTrackTime;
    }

    /**
     * @param maxTrackTime the maxTrackTime to set
     */
    public void setMaxTrackTimeValue(int maxTrackTime) { // named this to avoid javabeans property
        this.maxTrackTime = maxTrackTime;
    }

    /**
     * @param minIdleTime the minIdleTime to set
     */
    public void setMinIdleTimeValue(int minIdleTime) { // named this to avoid javabeans property
        this.minIdleTime = minIdleTime;
    }

    /**
     * @param maxIdleTime the maxIdleTime to set
     */
    public void setMaxIdleTimeValue(int maxIdleTime) { // named this to avoid javabeans property
        this.maxIdleTime = maxIdleTime;
    }


    /**
     * @param maxADCchannel the maxADCchannel to set
     */
    public void setMaxADCchannelValue(int maxADCchannel) { // named this to avoid javabeans property
        this.maxADCchannel = maxADCchannel;
    }

}