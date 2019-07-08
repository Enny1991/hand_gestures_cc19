package com.eneaceolini.aereader.biases;

/**
 * Base class for hardware proxies, which are host-side software objects representing hardware state and maintaining preference information about them.
 * Examples are scanners, ADCs. Chip/board level configuration of a device can use this proxy by encapsulating some state in the proxy, and registering
 * an update listener on it in the Chip's bias generator object.
 * These updates can then cause communication with the hardware through the Chip's bias generator object.
 * Preferences are stored in the Chip's preferences node, which is a field of this.
 * @author tobi
 */
public class HardwareInterfaceProxy{



}