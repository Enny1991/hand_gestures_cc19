package com.eneaceolini.aereader.biases;

public class IPot extends Pot {



    /** the position of this ipot in the chain of shift register cells; zero based and starting at the end where the bits are loaded.
     The order is very important because the bits for the FIRST bias in the shift register are loaded LAST.
     */
    protected int shiftRegisterNumber=0;


    protected IPot() {
    }

    /** Creates a new instance of IPot
     *@param name displayed and used to return by name.
     *@param shiftRegisterNumber the position in the shift register,
     * 0 based, starting on end from which bits are loaded.
     * This order determines how the bits are sent to the shift register,
     * lower shiftRegisterNumber are loaded later, so that they end up at the start of the shift register.
     * The last bit on the shift register is loaded first and is the msb of the last bias
     * on the shift register.
     * The last bit loaded into the shift register is the lsb of the first bias on the shift register.
     *@param type (NORMAL, CASCODE) - for user information.
     *@param sex Sex (N, P). User tip.
     * @param bitValue initial bitValue.
     *@param displayPosition position in GUI from top (logical order).
     *@param tooltipString a String to display to user of GUI telling them what the pots does.
     */
    public IPot(String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.bitValue=bitValue;
        this.displayPosition=displayPosition;
        this.tooltipString=tooltipString;
        this.shiftRegisterNumber=shiftRegisterNumber;
        loadPreferences(); // do this after name is set

    }



    public void setChipNumber(final int chipNumber) {
        this.chipNumber = chipNumber;
    }

    /**
     *  The shift register number is ordered so that the lowest shiftRegisterNumber is the bias at the start of the shift register and must be loaded *last*.
     * The highest number should go to the end of the shift register. This bias needs to be loaded first.
     *
     * @return  The shift register number which is ordered so that the lowest shiftRegisterNumber is the bias at the start of the shift register.
     */
    public int getShiftRegisterNumber() {
        return this.shiftRegisterNumber;
    }

    /**
     * The shift register number is ordered so that the lowest shiftRegisterNumber is the bias at the start of the shift register and must be loaded *last*.
     * The highest number should go to the end of the shift register. This bias needs to be loaded first.
     * @param shiftRegisterNumber which lower towards the input side and starts with 0 by convention.
     */
    public void setShiftRegisterNumber(final int shiftRegisterNumber) {
        this.shiftRegisterNumber = shiftRegisterNumber;
    }



    /** @return min possible current (presently zero, although in reality limited by off-current and substrate leakage). */
    public float getMinCurrent(){
        return 0f;
    }

    /** return resolution of pot in current. This is just Im/2^nbits.
     *@return smallest possible current change -- in principle
     */
    public float getCurrentResolution(){
        return 1f/((1<<getNumBits())-1);
    }

    /** increment pot value by {@link #CHANGE_FRACTION} ratio */
    public void incrementCurrent(){
        int v=Math.round(1+((1+CHANGE_FRACTION)*bitValue));
        setBitValue(v);
    }

    /** decrement pot value by  ratio */
    public void decrementCurrent(){
        int v=Math.round(-1+((1-CHANGE_FRACTION)*bitValue));
        setBitValue(v);
    }

    /** Change current value by ratio, or at least by one bit value.
     @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    public void changeByRatio(float ratio){
        int oldv=getBitValue();
        int v=Math.round(getBitValue()*ratio);
        if(v==oldv){
            v = v + (ratio >= 1 ? 1 : -1);
        }
        setBitValue(v);
    }





    private byte[] bytes=null;

    @Override
    public float getPhysicalValue() {
        return 0;
    }

    @Override
    public void setPhysicalValue(float value) {

    }

    @Override
    public String getPhysicalValueUnits() {
        return null;
    }

    /** Computes and returns a the reused array of bytes representing
     * the bias to be sent over hardware interface to the device.
     @return array of bytes to be sent, by convention values are ordered in
      * big endian format so that byte 0 is the most significant
      * byte and is sent first to the hardware.
     */
    @Override
    public byte[] getBinaryRepresentation() {
        int n=getNumBytes();
        if(bytes==null) bytes=new byte[n];
        int val=getBitValue();
        int k=0;
        for(int i=bytes.length-1;i>=0;i--){
            bytes[k++]=(byte)(0xff&(val>>>(i*8)));
        }
        return bytes;
    }


}