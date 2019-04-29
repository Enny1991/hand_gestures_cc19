package com.eneaceolini.aereader;

public interface HardwareInterface {

    /** get text name of interface, e.g. "CypressFX2" or "SiLabsC8051F320" */
    public String getTypeName();

    /**
     * Closes the device and frees the internal device handle. Never throws an exception.
     */
    public void close();

    public void open();

    /** @return true if interface is open, false otherwise */
    public boolean isOpen();


}