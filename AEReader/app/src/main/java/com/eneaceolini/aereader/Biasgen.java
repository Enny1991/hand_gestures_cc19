package com.eneaceolini.aereader;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.eneaceolini.aereader.biases.ADCHardwareInterfaceProxy;
import com.eneaceolini.aereader.biases.BufferIPot;
import com.eneaceolini.aereader.biases.DAC;
import com.eneaceolini.aereader.biases.IPot;
import com.eneaceolini.aereader.biases.IPotArray;
import com.eneaceolini.aereader.biases.Pot;
import com.eneaceolini.aereader.biases.PotArray;
import com.eneaceolini.aereader.biases.VPot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Scanner;

public class Biasgen {

    // VENDOR REQUEST
    public final static byte VENDOR_REQUEST_START_TRANSFER = (byte) 0xb3; // this is request to start sending events from FIFO endpoint
    public final static byte VENDOR_REQUEST_STOP_TRANSFER = (byte) 0xb4; // this is request to stop sending events from FIFO endpoint
    public final static byte VENDOR_REQUEST_EARLY_TRANFER = (byte) 0xb7; // this is request to transfer whatever you have now
    public static final byte VENDOR_REQUEST_SEND_BIAS_BYTES = (byte) 0xb8; // vendor command to send bias bytes out on SPI interface
    public final byte VENDOR_REQUEST_POWERDOWN = (byte) 0xb9; // vendor command to send bias bytes out on SPI interface
    public final byte VENDOR_REQUEST_FLASH_BIASES = (byte) 0xba;  // vendor command to flash the bias values to EEPROM
    public final byte VENDOR_REQUEST_RESET_TIMESTAMPS = (byte) 0xbb; // vendor command to reset timestamps
    public final byte VENDOR_REQUEST_SET_ARRAY_RESET = (byte) 0xbc; // vendor command to set array reset of retina
    public final byte VENDOR_REQUEST_DO_ARRAY_RESET = (byte) 0xbd; // vendor command to do an array reset (toggle arrayReset for a fixed time)
    public final byte VENDOR_REQUEST_SET_SYNC_ENABLED = (byte) 0xbe;  // vendor command to set whether sync input generates sync events
    //final byte VENDOR_REQUEST_WRITE_EEPROM=(byte)0xbe; // vendor command to write EEPROM
    public final byte VENDOR_REQUEST_SET_LED = (byte) 0xbf; // vendor command to set the board's LED
    public static final byte VR_DOWNLOAD_FIRMWARE = (byte) 0xC5;  // vendor request to program CPLD or FPGA
    public static final byte VR_SET_DEVICE_NAME = (byte) 0xC2;  // set serial number string

    public static final byte VENDOR_REQUEST_WRITE_CPLD_SR = (byte) 0xCF;  // write CPLD shift register (configuration data); also stops ADC if running
    public static final byte VENDOR_REQUEST_RUN_ADC = (byte) 0xCE;  // start and stop aquisition of ADC data
    //final byte VENDOR_REQUEST_READ_EEPROM=(byte)0xca; // vendor command to write EEPROM
    // #define VR_EEPROM		0xa2 // loads (uploads) EEPROM
    public final byte VR_EEPROM = (byte) 0xa2;
    // #define	VR_RAM			0xa3 // loads (uploads) external ram
    public final byte VR_RAM = (byte) 0xa3;    // this is special hw vendor request for reading and writing RAM, used for firmware download
    public static final byte VENDOR_REQUEST_FIRMWARE = (byte) 0xA0; // download/upload firmware -- builtin FX2 feature
    protected final static short CONFIG_INDEX = 0;
    protected final static short CONFIG_NB_OF_INTERFACES = 1;
    protected final static short CONFIG_INTERFACE = 0;
    protected final static short CONFIG_ALT_SETTING = 0;
    protected final static int CONFIG_TRAN_SIZE = 512;
    //


    private final short ADC_CONFIG = (short) 0x100;   //normal power mode, single ended, sequencer unused : (short) 0x908;
    private OnChipPreamp onchipPreamp;
    private OffChipPreamp offchipPreampLeft;
    private OffChipPreamp offchipPreampRight;
    private OffChipPreampARRatio offchipPreampARRatio;
    // lists of ports and CPLD config
    ArrayList<PortBit> portBits = new ArrayList();
    ArrayList<CPLDConfigValue> cpldConfigValues = new ArrayList();
    ArrayList<AbstractConfigValue> config = new ArrayList<AbstractConfigValue>();
    /** The DAC on the board. Specified with 5V reference even though Vdd=3.3 because the internal 2.5V reference is used and so that the VPot controls display correct voltage. */
    protected final DAC dac = new DAC(32, 12, 0, 5f, 3.3f); // the DAC object here is actually 2 16-bit DACs daisy-chained on the Cochlea board; both corresponding values need to be sent to change one value
    IPotArray ipots = new IPotArray();
    PotArray vpots = new PotArray();
    //        private IPot diffOn, diffOff, refr, pr, sf, diff;
    // config bits/values
    // portA
    public PortBit hostResetTimestamps = new PortBit("a7", "hostResetTimestamps", "High to reset timestamps", false);
    public PortBit runAERComm = new PortBit("a3", "runAERComm", "High to run CPLD state machine (send events)- also controls CPLDLED2", true);
    public PortBit enableCPLDAERAck = new PortBit("a0", "enableCPLDAERAck", "Set to enable the CPLD sending AER Ack signal. Clear when using external readout device on the CAVIAR connector", true);
    public PortBit timestampMasterExternalInputEventsEnabled = new PortBit("a1", "timestampMasterExternalInputEventsEnabled", "High makes this device a timestamp master and enables external input events on the sync IN pin low-going edges. Low makes this device a timestamp slave device.", true);
    // portC
    public PortBit runAdc = new PortBit("c0", "runAdc", "High to run ADC", true);
    // portD
    public PortBit vCtrlKillBit = new PortBit("d6", "vCtrlKill", "Controls whether neurons can be killed. Set high to enable killing neurons.", true);
    protected PortBit aerKillBit = new PortBit("d7", "aerKillBit", "The bit loaded into bank of 8 selected neuron kill bit latches. ", false);
    // portE
    // tobi changed config bits on rev1 board since e3/4 control maxim mic preamp attack/release and gain now
    public PortBit cochleaBitLatch = new PortBit("e1", "cochleaBitLatch", "The latch signal for the cochlea address and data SRs; 0 to make latches transparent.", true);


    public class PowerDownBit extends PortBit {

        public PowerDownBit(String portBit, String name, String tip, boolean def) {
            super(portBit, name, tip, def);
        }
    }

    public PowerDownBit powerDown; // on port e2
    public PortBit cochleaReset = new PortBit("e3", "cochleaReset", "High resets all neuron and Q latches; global latch reset (1=reset); aka vReset", false);
    // CPLD config on CPLD shift register
    public CPLDBit yBit = new CPLDBit(0, "yBit", "Used to select whether bandpass (0) or lowpass (1) neurons are killed for local kill", false),
            selAER = new CPLDBit(3, "selAER", "Chooses whether lpf (0) or rectified (1) lpf output drives low-pass filter neurons", true),
            selIn = new CPLDBit(4, "selIn", "Parallel (1) or Cascaded (0) cochlea architecture", false);
    public CPLDInt onchipPreampGain = new CPLDInt(1, 2, "onchipPreampGain", "chooses onchip microphone preamp feedback resistor selection", 3);
    // adc configuration is stored in adcProxy; updates to here should update CPLD config below
    public CPLDInt adcConfig = new CPLDInt(11, 22, "adcConfig", "determines configuration of ADC - value depends on channel and sequencing enabled " + ADC_CONFIG, ADC_CONFIG),
            adcTrackTime = new CPLDInt(23, 38, "adcTrackTime", "ADC track time in clock cycles which are 15 cycles/us", 0),
            adcIdleTime = new CPLDInt(39, 54, "adcIdleTime", "ADC idle time after last acquisition in clock cycles which are 15 cycles/us", 0);
    // scanner config stored in scannerProxy; updates should update state of below fields
    private CPLDInt scanX = new CPLDInt(55, 61, "scanChannel", "cochlea tap to monitor when not scanning continuously", 0);
    private CPLDBit scanSel = new CPLDBit(62, "scanSel", "selects which on-chip cochlea scanner shift register to monitor for sync (0=BM, 1=Gang Cells) - also turns on CPLDLED1 near FXLED1", false), // TODO firmware controlled?
            scanContinuouslyEnabled = new CPLDBit(63, "scanContinuouslyEnabled", "enables continuous scanning of on-chip scanner", true);
    // preamp config stored in preamp objects
    // preamp left/right bits are swapped here to correspond to board
    public TriStateableCPLDBit preampAR = new TriStateableCPLDBit(5, 6, "preampAttackRelease", "offchip preamp attack/release ratio (0=attack/release ratio=1:500, 1=A/R=1:2000, HiZ=A/R=1:4000)", Tristate.Low),
            preampGainRight = new TriStateableCPLDBit(9, 10, "preampGain.Right", "offchip preamp gain bit (1=gain=40dB, 0=gain=50dB, HiZ=60dB if preamp threshold \"PreampAGCThreshold (TH)\"is set above 2V)", Tristate.HiZ),
            preampGainLeft = new TriStateableCPLDBit(7, 8, "preampGain.Left", "offchip preamp gain bit (1=gain=40dB, 0=gain=50dB, HiZ=60dB if preamp threshold \"PreampAGCThreshold (TH)\"is set above 2V)", Tristate.HiZ);
    // store all values here, then iterate over this array to build up CPLD shift register stuff and dialogs
//        volatile AbstractConfigValue[] config = {hostResetTimestamps, runAERComm,
//            runAdc, vCtrlKillBit, aerKillBit,
//            powerDown, nCochleaReset, yBit, selAER, selIn, onchipPreampGain,
//            adcConfig, adcTrackTime, adcIdleTime, scanX, scanSel,
//            scanContinuouslyEnabled, preampAR, preampGainLeft, preampGainRight
//        };
    public CPLDConfig cpldConfig;
    /*
    #define DataSel 	1	// selects data shift register path (bitIn, clock, latch)
    #define AddrSel 	2	// selects channel selection shift register path
    #define BiasGenSel 	4	// selects biasgen shift register path
    #define ResCtr1 	8	// a preamp feedback resistor selection bitmask
    #define ResCtr2 	16	// another microphone preamp feedback resistor selection bitmask
    #define Vreset		32	// (1) to reset latch states
    #define SelIn		64	// Parallel (0) or Cascaded (1) Arch
    #define Ybit		128	// Chooses whether lpf (0) or bpf (1) neurons to be killed, use in conjunction with AddrSel and AERKillBit
     */
    public Equalizer equalizer = new Equalizer();
    public BufferIPot bufferIPot = new BufferIPot();
    protected VPot preampAGCThresholdPot; // used in Microphone preamp control panel
    // wraps around ADC, updates come back here to send CPLD config to hardware. Proxy used in GUI.
    public  ADC adcProxy;
    private Scanner scanner;
    public IPotArray potArray;
    public UsbDeviceConnection connection;


    public Biasgen(UsbDeviceConnection connection) {

        this.connection = connection;
        powerDown = new PowerDownBit("e2", "powerDown", "High to power down bias generator", false);


        cpldConfig = new CPLDConfig();  // stores everything in the CPLD configuration shift register
        for (CPLDConfigValue c : cpldConfigValues) {
            cpldConfig.add(c);
        }

        onchipPreamp = new OnChipPreamp(onchipPreampGain);
        offchipPreampLeft = new OffChipPreamp(preampGainLeft, Ear.Left);
        offchipPreampRight = new OffChipPreamp(preampGainRight, Ear.Right);
        offchipPreampARRatio = new OffChipPreampARRatio(preampAR);



        // inspect config to build up CPLDConfig

//    public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
        potArray = new IPotArray(); //construct IPotArray whit shift register stuff

        ipots.addPot(new IPot( "VAGC", 0, IPot.Type.NORMAL, IPot.Sex.N, 0, 1, "Sets reference for AGC diffpair in SOS"));  // second to list bits loaded, just before buffer bias bits. displayed first in GUI
        ipots.addPot(new IPot( "Curstartbpf", 1, IPot.Type.NORMAL, IPot.Sex.P, 0, 2, "Sets master current to local DACs for BPF Iq"));
        ipots.addPot(new IPot( "DacBufferNb", 2, IPot.Type.NORMAL, IPot.Sex.N, 0, 3, "Sets bias current of amp in local DACs"));
        ipots.addPot(new IPot( "Vbp", 3, IPot.Type.NORMAL, IPot.Sex.P, 0, 4, "Sets bias for readout amp of BPF"));
        ipots.addPot(new IPot( "Ibias20OpAmp", 4, IPot.Type.NORMAL, IPot.Sex.P, 0, 5, "Bias current for preamp"));
//            ipots.addPot(new IPot( "N.C.", 5, IPot.Type.NORMAL, IPot.Sex.N, 0, 6, "not used"));
        ipots.addPot(new IPot( "Vioff", 5, IPot.Type.NORMAL, IPot.Sex.P, 0, 11, "Sets DC shift input to LPF"));
        ipots.addPot(new IPot( "Vsetio", 6, IPot.Type.CASCODE, IPot.Sex.P, 0, 7, "Sets 2I0 and I0 for LPF time constant"));
        ipots.addPot(new IPot( "Vdc1", 7, IPot.Type.NORMAL, IPot.Sex.P, 0, 8, "Sets DC shift for close end of cascade"));
        ipots.addPot(new IPot( "NeuronRp", 8, IPot.Type.NORMAL, IPot.Sex.P, 0, 9, "Sets bias current of neuron"));
        ipots.addPot(new IPot( "Vclbtgate", 9, IPot.Type.NORMAL, IPot.Sex.P, 0, 10, "Bias gate of CLBT"));
        ipots.addPot(new IPot( "N.C.", 10, IPot.Type.NORMAL, IPot.Sex.N, 0, 6, "not used"));
//            ipots.addPot(new IPot( "Vioff", 10, IPot.Type.NORMAL, IPot.Sex.P, 0, 11, "Sets DC shift input to LPF"));
        ipots.addPot(new IPot( "Vbias2", 11, IPot.Type.NORMAL, IPot.Sex.P, 0, 12, "Sets lower cutoff freq for cascade"));
        ipots.addPot(new IPot( "Ibias10OpAmp", 12, IPot.Type.NORMAL, IPot.Sex.P, 0, 13, "Bias current for preamp"));
        ipots.addPot(new IPot( "Vthbpf2", 13, IPot.Type.CASCODE, IPot.Sex.P, 0, 14, "Sets high end of threshold current for bpf neurons"));
        ipots.addPot(new IPot( "Follbias", 14, IPot.Type.NORMAL, IPot.Sex.N, 0, 15, "Bias for PADS"));
        ipots.addPot(new IPot( "pdbiasTX", 15, IPot.Type.NORMAL, IPot.Sex.N, 0, 16, "pulldown for AER TX"));
        ipots.addPot(new IPot( "Vrefract", 16, IPot.Type.NORMAL, IPot.Sex.N, 0, 17, "Sets refractory period for AER neurons"));
        ipots.addPot(new IPot( "VbampP", 17, IPot.Type.NORMAL, IPot.Sex.P, 0, 18, "Sets bias current for input amp to neurons"));
        ipots.addPot(new IPot( "Vcascode", 18, IPot.Type.CASCODE, IPot.Sex.N, 0, 19, "Sets cascode voltage"));
        ipots.addPot(new IPot( "Vbpf2", 19, IPot.Type.NORMAL, IPot.Sex.P, 0, 20, "Sets lower cutoff freq for BPF"));
        ipots.addPot(new IPot( "Ibias10OTA", 20, IPot.Type.NORMAL, IPot.Sex.N, 0, 21, "Bias current for OTA in preamp"));
        ipots.addPot(new IPot( "Vthbpf1", 21, IPot.Type.CASCODE, IPot.Sex.P, 0, 22, "Sets low end of threshold current to bpf neurons"));
        ipots.addPot(new IPot( "Curstart ", 22, IPot.Type.NORMAL, IPot.Sex.P, 0, 23, "Sets master current to local DACs for SOS Vq"));
        ipots.addPot(new IPot( "Vbias1", 23, IPot.Type.NORMAL, IPot.Sex.P, 0, 24, "Sets higher cutoff freq for SOS"));
        ipots.addPot(new IPot( "NeuronVleak", 24, IPot.Type.NORMAL, IPot.Sex.P, 0, 25, "Sets leak current for neuron"));
        ipots.addPot(new IPot( "Vioffbpfn", 25, IPot.Type.NORMAL, IPot.Sex.N, 0, 26, "Sets DC level for input to bpf"));
        ipots.addPot(new IPot( "Vcasbpf", 26, IPot.Type.CASCODE, IPot.Sex.P, 0, 27, "Sets cascode voltage in cm BPF"));
        ipots.addPot(new IPot( "Vdc2", 27, IPot.Type.NORMAL, IPot.Sex.P, 0, 28, "Sets DC shift for SOS at far end of cascade"));
        ipots.addPot(new IPot( "Vterm", 28, IPot.Type.CASCODE, IPot.Sex.N, 0, 29, "Sets bias current of terminator xtor in diffusor"));
        ipots.addPot(new IPot( "Vclbtcasc", 29, IPot.Type.CASCODE, IPot.Sex.P, 0, 30, "Sets cascode voltage in CLBT"));
        ipots.addPot(new IPot( "reqpuTX", 30, IPot.Type.NORMAL, IPot.Sex.P, 0, 31, "Sets pullup bias for AER req ckts"));
        ipots.addPot(new IPot( "Vbpf1", 31, IPot.Type.NORMAL, IPot.Sex.P, 0, 32, "Sets higher cutoff freq for BPF"));   // first bits loaded, at end of shift register


//    public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
        // top dac in schem/layout, first 16 channels of 32 total
        vpots.addPot(new VPot( "Vterm", dac, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets bias current of terminator xtor in diffusor"));
        vpots.addPot(new VPot( "Vrefhres", dac, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets source of terminator xtor in diffusor"));
        vpots.addPot(new VPot( "VthAGC", dac, 2, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets input to diffpair that generates VQ"));
        vpots.addPot(new VPot( "Vrefreadout", dac, 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets reference for readout amp"));
//            vpots.addPot(new VPot( "Vbpf2x", dac,         4, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
        vpots.addPot(new VPot( "BiasDACBufferNBias", dac, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets bias current of buffer in pixel DACs"));
//            vpots.addPot(new VPot( "Vbias2x", dac,        5, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
        vpots.addPot(new VPot( "Vrefract", dac, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets refractory period of neuron"));
//            vpots.addPot(new VPot( "Vbpf1x", dac,         6, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
        vpots.addPot(preampAGCThresholdPot = new VPot( "PreampAGCThreshold (TH)", dac, 6, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Threshold for microphone preamp AGC gain reduction turn-on"));
        vpots.addPot(new VPot( "Vrefpreamp", dac, 7, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets virtual group of microphone drain preamp"));
//            vpots.addPot(new VPot( "Vbias1x", dac,        8, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
        vpots.addPot(new VPot( "NeuronRp", dac, 8, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets bias current of neuron comparator- overrides onchip bias"));
        vpots.addPot(new VPot( "Vthbpf1x", dac, 9, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets threshold for BPF neuron"));
        vpots.addPot(new VPot( "Vioffbpfn", dac, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets DC level for BPF input"));
        vpots.addPot(new VPot( "NeuronVleak", dac, 11, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets leak current for neuron - not connected on board"));
        vpots.addPot(new VPot( "DCOutputLevel", dac, 12, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Microphone DC output level to cochlea chip"));
        vpots.addPot(new VPot( "Vthbpf2x", dac, 13, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets threshold for BPF neuron"));
        vpots.addPot(new VPot( "DACSpOut2", dac, 14, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
        vpots.addPot(new VPot( "DACSpOut1", dac, 15, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));

        // bot DAC in schem/layout, 2nd 16 channels
        vpots.addPot(new VPot( "Vth4", dac, 16, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets high VT for LPF neuron"));
        vpots.addPot(new VPot( "Vcas2x", dac, 17, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets cascode voltage for subtraction of neighboring filter outputs"));
        vpots.addPot(new VPot( "Vrefo", dac, 18, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets src for output of CM LPF"));
        vpots.addPot(new VPot( "Vrefn2", dac, 19, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets DC gain gain cascode bias in BPF"));
        vpots.addPot(new VPot( "Vq", dac, 20, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets tau of feedback amp in SOS"));

        vpots.addPot(new VPot( "Vpf", dac, 21, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets bias current for scanner follower"));

        vpots.addPot(new VPot( "Vgain", dac, 22, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets bias for differencing amp in BPF/LPF"));
        vpots.addPot(new VPot( "Vrefn", dac, 23, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets cascode bias in BPF"));
        vpots.addPot(new VPot( "VAI0", dac, 24, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets tau of CLBT for ref current"));
        vpots.addPot(new VPot( "Vdd1", dac, 25, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets up power to on-chip DAC"));
        vpots.addPot(new VPot( "Vth1", dac, 26, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets low VT for LPF neuron"));
        vpots.addPot(new VPot( "Vref", dac, 27, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets src for input of CM LPF"));
        vpots.addPot(new VPot( "Vtau", dac, 28, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets tau of forward amp in SOS"));
        vpots.addPot(new VPot( "VcondVt", dac, 29, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets VT of conductance neuron"));
        vpots.addPot(new VPot( "Vpm", dac, 30, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "sets bias of horizontal element of diffusor"));
        vpots.addPot(new VPot( "Vhm", dac, 31, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "sets bias of horizontal element of diffusor"));
//            Pot.setModificationTrackingEnabled(false); // don't flag all biases modified on construction

        // ADC
        adcProxy = new ADC(); // notifies us with updates
        adcProxy.setMaxADCchannelValue(3);
        adcProxy.setMaxIdleTimeValue(0xffff / 15);
        adcProxy.setMaxTrackTimeValue(0xffff / 15);
        adcProxy.setMinTrackTimeValue(1);
        adcProxy.setMinIdleTimeValue(0);

//        sendFullConfig();
    }

    void sendConfig(int cmd, int index, byte[] bytes) {

        boolean debug = true;
        StringBuilder sb = null;
        if (debug) {
            sb = new StringBuilder(String.format("sending command vendor request cmd=0x%x, index=0x%x, with %d bytes ",cmd,index,bytes==null?0:bytes.length));
        }
        if ((bytes == null) || (bytes.length == 0)) {
        } else if(debug){
            sb.append(": hex bytes values are ");
            int max = 100;
            if (bytes.length < max) {
                max = bytes.length;
            }
            for (int i = 0; i < max; i++) {
                sb.append(String.format("%02X, ", bytes[i]));
            }
        }
        if (bytes == null) {
            bytes = emptyByteArray;
        }
        if (debug) {
            Log.d("SEND CONDIG DEBUG", sb.toString());
        }

        int start = connection.controlTransfer(0, VENDOR_REQUEST_SEND_BIAS_BYTES, (0xffff & cmd), (short) (0xffff & index), bytes, bytes.length, 0);

//        sendVendorRequest(VENDOR_REQUEST_SEND_BIAS_BYTES, (short) (0xffff & cmd), (short) (0xffff & index), bytes); // & to prevent sign extension for negative shorts

    }

    private void sendMyVPots(){
        byte[] bytes;

        bytes = new byte[]{(byte) 0x00, (byte) 0xC0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x01, (byte) 0xD3, (byte) 0xBC, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x02, (byte) 0xCF, (byte) 0x88, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x03, (byte) 0xDB, (byte) 0xCC, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x04, (byte) 0xCC, (byte) 0x98, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x05, (byte) 0xC5, (byte) 0xC0, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x06, (byte) 0xC5, (byte) 0x74, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x07, (byte) 0xCC, (byte) 0x18, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x08, (byte) 0xE2, (byte) 0x98, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x09, (byte) 0xC2, (byte) 0xE0, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x0A, (byte) 0xC5, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x0B, (byte) 0xE3, (byte) 0x2C, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x0C, (byte) 0xCF, (byte) 0x8C, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x0D, (byte) 0xC1, (byte) 0xD0, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x0E, (byte) 0xCA, (byte) 0x58, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x0F, (byte) 0xE0, (byte) 0xAC, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xC4, (byte) 0x08};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0xC5, (byte) 0x04};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0xE8, (byte) 0x38};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0xC4, (byte) 0x6C};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0xE4, (byte) 0xD4};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0xC9, (byte) 0xC5};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0xE3, (byte) 0x64};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0xC9, (byte) 0xC5};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x08, (byte) 0xE7, (byte) 0xF0};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0xE5, (byte) 0xB0};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A, (byte) 0xCB, (byte) 0xE4};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0B, (byte) 0xE7, (byte) 0x44};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0xE5, (byte) 0x68};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0D, (byte) 0xEF, (byte) 0x38};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0E, (byte) 0xC5, (byte) 0x2C};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0xC5, (byte) 0xA4};
        sendConfig(CMD_VDAC, 0, bytes);
        bytes = new byte[]{(byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x00, (byte) 0x00};
        sendConfig(CMD_VDAC, 0, bytes);

    }

    void sendConfig(int cmd, int index) {
        sendConfig(cmd, index, emptyByteArray);
    }

    private final short CMD_IPOT = 1, CMD_RESET_EQUALIZER = 2,
            CMD_SCANNER = 3, CMD_EQUALIZER = 4,
            CMD_SETBIT = 5, CMD_VDAC = 6, CMD_INITDAC = 7,
            CMD_CPLD_CONFIG = 8;
    public final String[] CMD_NAMES = {"IPOT", "RESET_EQUALIZER", "SCANNER", "EQUALIZER", "SET_BIT", "VDAC", "INITDAC", "CPLD_CONFIG"};
    private final byte[] emptyByteArray = new byte[0];

    public void sendFullConfig(){
        // send all of the biases

        Log.d("AMS1c BIASES", "iPots");
        //IPot
        sendSingle(ipots.getPots().get(0));


        // VPot
        Log.d("AMS1c BIASES", "vPots");
//        for(Pot c: vpots.getPots()){
////            sendSingle(c);
////        }
        sendMyVPots();


        Log.d("AMS1c BIASES","reset");
        sendSingle(powerDown);
        sendSingle(hostResetTimestamps);
        runAERComm.set(true);
        sendSingle(runAERComm);
        enableCPLDAERAck.set(true);
        sendSingle(enableCPLDAERAck);
        timestampMasterExternalInputEventsEnabled.set(true);
        sendSingle(timestampMasterExternalInputEventsEnabled);
        sendSingle(vCtrlKillBit);
        sendSingle(aerKillBit);
        sendSingle(cochleaBitLatch);
        sendSingle(cochleaReset);
        sendSingle(runAdc);

//        // preamp
//        Log.d("AMS1c BIASES", "onChipPreamp");
//        sendSingle(onchipPreamp);
//
//        Log.d("AMS1c BIASES", "offChipPreamp");
//        sendSingle(offchipPreampLeft);
//
//        Log.d("AMS1c BIASES", "offchip...");
//        sendSingle(offchipPreampRight);
//
//        Log.d("AMS1c BIASES", "offChip...");
//        sendSingle(offchipPreampARRatio);

        Log.d("AMS1c BIASES", "cpld");
        // CPLD
//        for(CPLDConfigValue c: cpldConfigValues){
//            sendSingle(c);
//        }

        sendConfig(CMD_CPLD_CONFIG, 0, new byte[] {0x00, 0x00, 0x00, 0x01, (byte) 0xD8, (byte) 0x89, 0x37, (byte) 0x8A});

    }

    public void sendSingle(Object observable){
        if ((observable instanceof IPot) || (observable instanceof BufferIPot)) { // must send all IPot values and set the select to the ipot shift register, this is done by the cypress
//            byte[] bytes = new byte[1 + (ipots.getNumPots() * ipots.getPots().get(0).getNumBytes())];
//            int ind = 0;
//            Iterator itr = ipots.getShiftRegisterIterator();
//            while (itr.hasNext()) {
//                IPot p = (IPot) itr.next(); // iterates in order of shiftregister index, from Vbpf to VAGC
//                byte[] b = p.getBinaryRepresentation();
//                System.arraycopy(b, 0, bytes, ind, b.length);
//                ind += b.length;
//            }
//            bytes[ind] = (byte) bufferIPot.getValue(); // isSet 8 bitmask buffer bias value, this is *last* byte sent because it is at start of biasgen shift register
//
            byte[] bytes = new byte[] {0x00, 0x00, 0x3D, 0x2B, 0x69, 0x4D, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x65,
                     0x0E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1C, 0x0C, (byte) 0xD1, 0x2F, 0x02, (byte) 0x91, 0x1D, 0x00, 0x04, 0x3A, 0x00, 0x00, 0x05, 0x00,
                     0x00, 0x05, 0x03, (byte) 0x94, (byte) 0x81, 0x00, 0x00, (byte) 0xAE, (byte) 0xFF, (byte)  0xFF, (byte) 0xFF, 0x10, 0x00, 0x00, 0x01, 0x65, 0x0E, 0x00, 0x00, 0x76,
                     0x00, 0x00, 0x03, 0x03, 0x63, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x2C, 0x69, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
                     0x00, 0x00, 0x00, 0x00, 0x05, 0x01, 0x3F, (byte) 0x93, 0x06, 0x3B, (byte) 0xBB, 0x3C, (byte) 0x8C, 0x35, 0x00, 0x00, 0x00, 0x32};

            sendConfig(CMD_IPOT, 0, bytes); // the usual packing of ipots
        } else if (observable instanceof VPot) {
            // There are 2 16-bit AD5391 DACs daisy chained; we need to send data for both
            // to change one of them. We can send all zero bytes to the one we're notifyChange changing and it will notifyChange affect any channel
            // on that DAC. We also take responsibility to formatting all the bytes here so that they can just be piped out
            // surrounded by nSync low during the 48 bit write on the controller.
            VPot p = (VPot) observable;
            sendDAC(p);
        } else if (observable instanceof TriStateablePortBit) { // tristateable should come first before configbit since it is subclass
            TriStateablePortBit b = (TriStateablePortBit) observable;
            byte[] bytes = {(byte) ((b.isSet() ? (byte) 1 : (byte) 0) | (b.isHiZ() ? (byte) 2 : (byte) 0))};
            sendConfig(CMD_SETBIT, b.portbit, bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]= OR of value (1,0), hiZ=2/0, bit is set if tristate, unset if driving port
        } else if (observable instanceof PortBit) {
            PortBit b = (PortBit) observable;
            byte[] bytes = {b.isSet() ? (byte) 1 : (byte) 0};
            Log.d("Sending Port BIT", observable.toString());
            sendConfig(CMD_SETBIT, b.portbit, bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]=value (1,0)
        } else if (observable instanceof CPLDConfigValue) {
//                    System.out.println(String.format("sending CPLDConfigValue %s",observable));
//            sendCPLDConfig();
            // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]=value (1,0)

        } else if (observable instanceof Equalizer.EqualizerChannel) {
            // sends 0 byte message (no data phase for speed)
            Equalizer.EqualizerChannel c = (Equalizer.EqualizerChannel) observable;
//                    log.info("Sending "+c);
            int value = (c.channel << 8) + CMD_EQUALIZER; // value has cmd in LSB, channel in MSB
            int index = c.qsos + (c.qbpf << 5) + (c.lpfkilled ? 1 << 10 : 0) + (c.bpfkilled ? 1 << 11 : 0); // index has b11=bpfkilled, b10=lpfkilled, b9:5=qbpf, b4:0=qsos
            sendConfig(value, index);
//                        System.out.println(String.format("channel=%50s value=%16s index=%16s",c.toString(),Integer.toBinaryString(0xffff&value),Integer.toBinaryString(0xffff&index)));
            // killed byte has 2 lsbs with bitmask 1=lpfkilled, bitmask 0=bpf killed, active high (1=kill, 0=alive)
        } else if (observable instanceof Equalizer) {
            // TODO everything is in the equalizer channel, nothing yet in equalizer (e.g global settings)
        } else if (observable instanceof OnChipPreamp) { // TODO check if nothing needs to be done on update
        } else if (observable instanceof OffChipPreamp) {
        } else if (observable instanceof ADCHardwareInterfaceProxy) {
            adcIdleTime.set(adcProxy.getIdleTime() * 15); // multiplication with 15 to get from us to clockcycles
            adcTrackTime.set(adcProxy.getTrackTime() * 15); // multiplication with 15 to get from us to clockcycles
            int lastChan = adcProxy.getADCChannel();
            boolean seq = adcProxy.isSequencingEnabled();
            // from AD7933/AD7934 datasheet
            int config = (1 << 8) + (lastChan << 5) + (seq ? 6 : 0);
            adcConfig.set(config);

//            sendCPLDConfig();
        }
    }

    private void sendCPLDConfig() {
//            boolean old = adcProxy.isADCEnabled(); // old state of whether ADC is running - now done in firmware

//            runAdc.set(false); // disable ADC before loading new configuration // TODO do this on device!!
        byte[] bytes = cpldConfig.getBytes();
        sendConfig(CMD_CPLD_CONFIG, 0, bytes);
//            if (old) {
//                runAdc.set(true); // reenable ADC
//            }
    }

    void sendDAC(VPot pot)  {
        int chan = pot.getChannel();
        int value = pot.getBitValue();
        byte[] b = new byte[6]; // 2*24=48 bits
// original firmware code
//            unsigned char dat1 = 0x00; //00 00 0000;
//            unsigned char dat2 = 0xC0; //Reg1=1 Reg0=1 : Write output data
//            unsigned char dat3 = 0x00;
//
//            dat1 |= (address & 0x0F);
//            dat2 |= ((msb & 0x0F) << 2) | ((lsb & 0xC0)>>6) ;
//            dat3 |= (lsb << 2) | 0x03; // DEBUG; the last 2 bits are actually don't care
        byte msb = (byte) (0xff & ((0xf00 & value) >> 8));
        byte lsb = (byte) (0xff & value);
        byte dat1 = 0;
        byte dat2 = (byte) 0xC0;
        byte dat3 = 0;
        dat1 |= (0xff & ((chan % 16) & 0xf));
        dat2 |= ((msb & 0xf) << 2) | ((0xff & ((lsb & 0xc0) >> 6)));
        dat3 |= (0xff & ((lsb << 2)));
        if (chan < 16) { // these are first VPots in list; they need to be loaded first to isSet to the second DAC in the daisy chain
            b[0] = dat1;
            b[1] = dat2;
            b[2] = dat3;
            b[3] = 0;
            b[4] = 0;
            b[5] = 0;
        } else { // second DAC VPots, loaded second to end up at start of daisy chain shift register
            b[0] = 0;
            b[1] = 0;
            b[2] = 0;
            b[3] = dat1;
            b[4] = dat2;
            b[5] = dat3;
        }
//            System.out.print(String.format("value=%-6d channel=%-6d ",value,chan));
//            for(byte bi:b) System.out.print(String.format("%2h ", bi&0xff));
//            System.out.println();
        sendConfig(CMD_VDAC, 0, b); // value=CMD_VDAC, index=0, bytes as above
    }



    /** Handles CPLD configuration shift register. This class maintains the information in the CPLD shift register. */
    class CPLDConfig {

        int numBits, minBit = Integer.MAX_VALUE, maxBit = Integer.MIN_VALUE;
        ArrayList<CPLDConfigValue> cpldConfigValues = new ArrayList();
        boolean[] bits;
        byte[] bytes = null;

        /** Computes the bits to be sent to the CPLD from all the CPLD config values: bits, tristateable bits, and ints.
         * Writes the bits boolean[] so that they are set according to the bit position, e.g. for a bit if startBit=n, then bits[n] is set.
         *
         */
        private void compute() {
            if (minBit > 0) {
                return; // notifyChange yet, we haven't filled in bit 0 yet
            }
            bits = new boolean[maxBit + 1];
            for (CPLDConfigValue v : cpldConfigValues) {
                if (v instanceof CPLDBit) {
                    bits[v.startBit] = ((ConfigBit) v).isSet();
                    if (v instanceof TriStateableCPLDBit) {
                        bits[v.startBit + 1] = ((TriStateableCPLDBit) v).isHiZ(); // assumes hiZ bit is next one up
                    }
                } else if (v instanceof ConfigInt) {
                    int i = ((ConfigInt) v).get();
                    for (int k = v.startBit; k <= v.endBit; k++) {
                        bits[k] = (i & 1) == 1;
                        i = i >>> 1;
                    }
                }
            }
        }

        void add(CPLDConfigValue val) {
            if (val.endBit < val.startBit) {
                throw new RuntimeException("bad CPLDConfigValue with endBit<startBit: " + val);
            }

            if (val.endBit > maxBit) {
                maxBit = val.endBit;
            }
            if (val.startBit < minBit) {
                minBit = val.startBit;
            }

            cpldConfigValues.add(val);
            compute();

        }

        /** Returns byte[] to send to uC to load into CPLD shift register.
         * This array is returned in big endian order so that
         the bytes sent will be sent in big endian order to the device, according to how they are handled in firmware
         and loaded into the CPLD shift register. In other words, the msb of the first byte returned (getBytes()[0] is the last bit
         * in the bits[] array of booleans, bit 63 in the case of 64 bits of CPLD SR contents.
         *
         */
        private byte[] getBytes() {
            compute();
            int nBytes = bits.length / 8;
            if ((bits.length % 8) != 0) {
                nBytes++;
            }
            if ((bytes == null) || (bytes.length != nBytes)) {
                bytes = new byte[nBytes];
            }
            Arrays.fill(bytes, (byte) 0);
            int byteCounter = 0;
            int bitcount = 0;
            for (int i = bits.length - 1; i >= 0; i--) { // start with msb and go down
                bytes[byteCounter] = (byte) (0xff & (bytes[byteCounter] << 1)); // left shift the bits in this byte that are already there
//                    if (bits[i]) {
//                        System.out.println("true bit at bit " + i);
//                    }
                bytes[byteCounter] = (byte) (0xff & (bytes[byteCounter] | (bits[i] ? 1 : 0))); // set or clear the current bit
                bitcount++;
                if (((bitcount) % 8) == 0) {
                    byteCounter++; // go to next byte when we finish each 8 bits
                }
            }
            return bytes;
        }

        @Override
        public String toString() {
            return "CPLDConfig{" + "numBits=" + numBits + ", minBit=" + minBit + ", maxBit=" + maxBit + ", cpldConfigValues=" + cpldConfigValues + ", bits=" + bits + ", bytes=" + bytes + '}';
        }
    }

    /** A single bit of digital configuration, either controlled by dedicated Cypress port bit
     * or as part of the CPLD configuration shift register. */
    abstract class AbstractConfigValue {

        protected String name, tip;
        protected String key = "AbstractConfigValue";

        public AbstractConfigValue(String name, String tip) {
            this.name = name;
            this.tip = tip;
            this.key = getClass().getSimpleName() + "." + name;
        }

        @Override
        public String toString() {
            return String.format("AbstractConfigValue name=%s key=%s", name, key);
        }

        public String getName() {
            return name;
        }



        public String getDescription() {
            return tip;
        }
    }

    public class AbstractConfigBit extends AbstractConfigValue implements ConfigBit {

        protected volatile boolean value;
        protected boolean def; // default preference value

        public AbstractConfigBit(String name, String tip, boolean def) {
            super(name, tip);
            this.name = name;
            this.tip = tip;
            this.def = def;
            key = "CochleaAMS1c.Biasgen.ConfigBit." + name;

        }

        @Override
        public void set(boolean value) {

            this.value = value;
//                log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
        }

        @Override
        public boolean isSet() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("AbstractConfigBit name=%s key=%s value=%s", name, key, value);
        }
    }

    public void setPowerDown(boolean def){
        this.powerDown = new Biasgen.PowerDownBit("e2", "powerDown", "High to power down bias generator", def);
    }

    /** A direct bit output from CypressFX2 port. */
    public class PortBit extends AbstractConfigBit implements ConfigBit {

        String portBitString;
        int port;
        short portbit; // has port as char in MSB, bitmask in LSB
        int bitmask;

        public PortBit(String portBit, String name, String tip, boolean def) {
            super(name, tip, def);
            if ((portBit == null) || (portBit.length() != 2)) {
                throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters");
            }
            String s = portBit.toLowerCase();
            if (!(s.startsWith("a") || s.startsWith("c") || s.startsWith("d") || s.startsWith("e"))) {
                throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters and start with A, C, D, or E");
            }
            portBitString = portBit;
            char ch = s.charAt(0);
            switch (ch) {
                case 'a':
                    port = 0;
                    break;
                case 'c':
                    port = 1;
                    break;
                case 'd':
                    port = 2;
                    break;
                case 'e':
                    port = 3;
                    break;
                default:
                    throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters and start with A, C, D, or E");
            }
            bitmask = 1 << Integer.valueOf(s.substring(1, 2));
            portbit = (short) (0xffff & ((port << 8) + (0xff & bitmask)));
        }

        @Override
        public String toString() {
            return String.format("PortBit name=%s port=%s value=%s", name, portBitString, value);
        }
    }

    /** Adds a hiZ state to the bit to set port bit to input */
    class TriStateablePortBit extends PortBit implements ConfigTristate {

        private volatile boolean hiZEnabled = false;
        String hiZKey;
        Tristate def;

        TriStateablePortBit(String portBit, String name, String tip, Tristate def) {
            super(portBit, name, tip, def.isHigh());
            this.def = def;
            hiZKey = "CochleaAMS1c.Biasgen.BitConfig." + name + ".hiZEnabled";
        }

        /**
         * @return the hiZEnabled
         */
        @Override
        public boolean isHiZ() {
            return hiZEnabled;
        }

        /**
         * @param hiZEnabled the hiZEnabled to set
         */
        @Override
        public void setHiZ(boolean hiZEnabled) {

            this.hiZEnabled = hiZEnabled;
        }

        @Override
        public String toString() {
            return String.format("TriStateablePortBit name=%s portbit=%s value=%s hiZEnabled=%s", name, portBitString, Boolean.toString(isSet()), hiZEnabled);
        }
    }

    class CPLDConfigValue extends AbstractConfigValue {

        protected int startBit, endBit;
        protected int nBits = 8;

        public CPLDConfigValue(int startBit, int endBit, String name, String tip) {
            super(name, tip);
            this.startBit = startBit;
            this.endBit = endBit;
            nBits = (endBit - startBit) + 1;
        }


        @Override
        public String toString() {
            return "CPLDConfigValue{" + "name=" + name + " startBit=" + startBit + "endBit=" + endBit + "nBits=" + nBits + '}';
        }
    }

    /** A bit output from CPLD port. */
    public class CPLDBit extends CPLDConfigValue implements ConfigBit {

        int pos; // bit position from lsb position in CPLD config
        boolean value;
        boolean def;

        /** Constructs new CPLDBit.
         *
         * @param pos position in shift register
         * @param name name label
         * @param tip tool-tip
         * @param def default preferred value
         */
        public CPLDBit(int pos, String name, String tip, boolean def) {
            super(pos, pos, name, tip);
            this.pos = pos;
            this.def = def;
//                hasPreferencesList.add(this);
        }

        @Override
        public void set(boolean value) {

            this.value = value;
//                log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
        }

        @Override
        public boolean isSet() {
            return value;
        }

        @Override
        public String toString() {
            return "CPLDBit{" + " name=" + name + " pos=" + pos + " value=" + value + '}';
        }
    }

    /** Adds a hiZ state to the bit to set port bit to input */
    class TriStateableCPLDBit extends CPLDBit implements ConfigTristate {

        private int hiZBit;
        private volatile boolean hiZEnabled = false;
        String hiZKey;
        Tristate def;

        TriStateableCPLDBit(int valBit, int hiZBit, String name, String tip, Tristate def) {
            super(valBit, name, tip, def == Tristate.High);
            this.def = def;
            this.hiZBit = hiZBit;
            hiZKey = "CochleaAMS1c.Biasgen.TriStateableCPLDBit." + name + ".hiZEnabled";
//                hasPreferencesList.add(this);
        }

        /**
         * @return the hiZEnabled
         */
        @Override
        public boolean isHiZ() {
            return hiZEnabled;
        }

        /**
         * @param hiZEnabled the hiZEnabled to set
         */
        @Override
        public void setHiZ(boolean hiZEnabled) {
            this.hiZEnabled = hiZEnabled;
        }

        @Override
        public String toString() {
            return String.format("TriStateableCPLDBit name=%s shiftregpos=%d value=%s hiZ=%s", name, pos, Boolean.toString(isSet()), hiZEnabled);
        }

    }

    /** A integer configuration on CPLD shift register. */
    class CPLDInt extends CPLDConfigValue implements ConfigInt {

        private volatile int value;
        private int def;

        CPLDInt(int startBit, int endBit, String name, String tip, int def) {
            super(startBit, endBit, name, tip);
            this.startBit = startBit;
            this.endBit = endBit;
            this.def = def;
            key = "CochleaAMS1c.Biasgen.CPLDInt." + name;
        }

        @Override
        public void set(int value) throws IllegalArgumentException {
            if ((value < 0) || (value >= (1 << nBits))) {
                throw new IllegalArgumentException("tried to store value=" + value + " which larger than permitted value of " + (1 << nBits) + " or is negative in " + this);
            }
            this.value = value;
//                log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
        }

        @Override
        public int get() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("CPLDInt name=%s value=%d", name, value);
        }

    }

    public class ADC extends ADCHardwareInterfaceProxy {


        @Override
        public boolean isADCEnabled() {
            return runAdc.isSet();
        }

        @Override
        public void setADCEnabled(boolean yes) {
            super.setADCEnabled(yes);
            runAdc.set(yes);
        }
    }

    /** Encapsulates each channels equalizer. Information is sent to device as
     * <p>
     * <img source="doc-files/equalizerBits.png"/>
     * where the index field of the vendor request has the quality and kill bit information.
     */
    public class Equalizer { // describes the local gain and Q registers and the kill bits

        public static final int NUM_CHANNELS = 128, MAX_VALUE = 31;
        //            private int globalGain = 15;
//            private int globalQuality = 15;
        public EqualizerChannel[] channels = new EqualizerChannel[NUM_CHANNELS];
        public final static int RUBBER_BAND_RANGE = 5;
        /**
         * Header for preferences key for EqualizerChannels
         */
        public final String CHANNEL_PREFS_HEADER="CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel.";

        Equalizer() {
            for (int i = 0; i < NUM_CHANNELS; i++) {
                channels[i] = new EqualizerChannel(this,i);
            }
        }

        /** Resets equalizer channels to default state, and finally does sequence with special bits to ensure hardware latches are all cleared.
         *
         */
        void reset() {
            for (EqualizerChannel c : channels) {
                c.reset();
            }
            // TODO special dance with logic bits to reset here

        }
        void setAllLPFKilled(boolean yes) {
            for (EqualizerChannel c : channels) {
                c.setLpfKilled(yes);
            }
            // TODO special dance with logic bits to reset here

        }
        void setAllBPFKilled(boolean yes) {
            for (EqualizerChannel c : channels) {
                c.setBpfKilled(yes);
            }
            // TODO special dance with logic bits to reset here

        }
        void setAllQSOS(int gain){
            for (EqualizerChannel c : channels) {
                c.setQSOS(gain);
            }
        }
        void setAllQBPF(int gain){
            for (EqualizerChannel c : channels) {
                c.setQBPF(gain);
            }
        }




        /**
         * One equalizer channel
         */
        public class EqualizerChannel{

            final int max = 31;
            int channel;
            private String prefsKey;
            private volatile int qsos;
            private volatile int qbpf;
            private volatile boolean bpfkilled, lpfkilled;
            private Equalizer equalizer=null;

            EqualizerChannel(Equalizer e, int n) {
                equalizer=e;
                channel = n;
                prefsKey =  CHANNEL_PREFS_HEADER + channel + ".";
            }

            @Override
            public String toString() {
                return String.format("EqualizerChannel: channel=%-3d qbpf=%-2d qsos=%-2d bpfkilled=%-6s lpfkilled=%-6s", channel, qbpf, qsos, Boolean.toString(bpfkilled), Boolean.toString(lpfkilled));
            }

            public int getQSOS() {
                return qsos;
            }

            public void setQSOS(int qsos) {
                if (this.qsos != qsos) {
                }
                this.qsos = qsos;
            }

            public int getQBPF() {
                return qbpf;
            }

            public void setQBPF(int qbpf) {
                this.qbpf = qbpf;
            }


            public boolean isLpfKilled() {
                return lpfkilled;
            }

            public void setLpfKilled(boolean killed) {

                this.lpfkilled = killed;
            }

            public boolean isBpfkilled() {
                return bpfkilled;
            }

            public void setBpfKilled(boolean bpfkilled) {

                this.bpfkilled = bpfkilled;
            }

            private void reset() {
                setBpfKilled(false);
                setLpfKilled(false);
                setQBPF(15);
                setQSOS(15);
            }
        }
    } // equalizer

    /** Represents the on-chip preamps */
    class OnChipPreamp {

        protected String key = "OnChipPreamp";

        OnChipPreampGain gain;
        CPLDInt gainBits;

        public OnChipPreamp(CPLDInt gainBits) {
            this.gainBits = gainBits;

        }

        void setGain(OnChipPreampGain gain) {

            this.gain = gain;
            gainBits.set(gain.code); // sends the new bit values via listener update on gainBits
        }

        OnChipPreampGain getGain() {
            return gain;
        }



        @Override
        public String toString() {
            return "OnChipPreamp{" + "key=" + key + ", gain=" + gain + '}';
        }
    }//preamp

    /** Represents the combined off-chip AGC attack/release ratio setting; this setting common for both preamps. */
    class OffChipPreampARRatio {

        final String arkey = "OffChipPreamp.arRatio";
        TriStateableCPLDBit arBit;
        private OffChipPreamp_AGC_AR_Ratio arRatio;

        public OffChipPreampARRatio(TriStateableCPLDBit arBit) {
            this.arBit = arBit;
        }

        /**
         * @return the arRatio
         */
        public OffChipPreamp_AGC_AR_Ratio getArRatio() {
            return arRatio;
        }


        public void setArRatio(OffChipPreamp_AGC_AR_Ratio arRatio) {

            this.arRatio = arRatio;
            switch (arRatio) {
                case Fast:
                    arBit.set(false);
                    arBit.setHiZ(false);
                    break;
                case Medium:
                    arBit.set(true);
                    arBit.setHiZ(false);
                    break;
                case Slow:
                    arBit.setHiZ(true);
            }
        }

    }

    /** Represents a single off-chip pre-amplifier. */
    class OffChipPreamp{

        Ear ear = Ear.Both;
        final String gainkey = "OffChipPreamp.gain";
        private OffChipPreampGain gain;
        TriStateableCPLDBit gainBit;

        public OffChipPreamp(TriStateableCPLDBit gainBit, Ear ear) {
            this.gainBit = gainBit;
            this.ear = ear;
        }

        /** Sets off-chip pre-amp gain via
         <pre>
         preampGainLeft = new TriStateableCPLDBit(5, 6, "preamp gain, left", "offchip preamp gain bit (1=gain=40dB, 0=gain=50dB, HiZ=60dB if preamp threshold \"PreampAGCThreshold (TH)\"is set above 2V)"),
         * </pre>
         * @param gain
         */
        void setGain(OffChipPreampGain gain) {
            this.gain = gain;
            switch (gain) {
                case High:
                    gainBit.setHiZ(true);
                    break;
                case Medium:
                    gainBit.setHiZ(false);
                    gainBit.set(false);
                    break;
                case Low:
                    gainBit.setHiZ(false);
                    gainBit.set(true);
            }
        }

        OffChipPreampGain getGain() {
            return gain;
        }

        @Override
        public String toString() {
            return "OffChipPreamp{" + " gainkey=" + key() + ", gain=" + gain + '}';
        }

        private String key() {
            return gainkey + "." + ear.toString();
        }
    }// offchip preamp

    /** Enum for on-chip preamp gain values */
    public enum OnChipPreampGain {

        Low(0, "Low_80 (80 kohm)"),
        Medium(1, "Medium_160 (160 kohm)"),
        Higher(2, "Higher_320 (320 kohm)"),
        Highest(3, "Highest_640 (640 kohm)");
        private final int code;
        private final String label;

        OnChipPreampGain(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int code() {
            return code;
        }

    }

    /** Used for preamp preferences */
    public enum Ear {
    Left, Right, Both
};

    interface ConfigBase {


        String getName();

        String getDescription();
    }

    interface ConfigBit extends ConfigBase {

        boolean isSet();

        void set(boolean yes);
    }

    interface ConfigInt extends ConfigBase {

        int get();

        void set(int v) throws IllegalArgumentException;
    }

    interface ConfigTristate extends ConfigBit {

        boolean isHiZ();

        void setHiZ(boolean yes);
    }

    public enum OffChipPreampGain {

        Low(0, "Low (40dB)"),
        Medium(1, "Medium (50dB)"),
        High(2, "High (60dB)");
        private final int code;
        private final String label;

        OffChipPreampGain(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int code() {
            return code;
        }

        public String label() {
            return label;
        }
    };

    public enum OffChipPreamp_AGC_AR_Ratio {

        Fast(0, "Fast (1:500)"),
        Medium(1, "Medium (1:2000)"),
        Slow(2, "Slow (1:4000)");
        private final int code;
        private final String label;

        OffChipPreamp_AGC_AR_Ratio(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int code() {
            return code;
        }

        public String label() {
            return label;
        }
    };

    /** Used for tristate outputs */
    public enum Tristate {

        High, Low, HiZ;

        public boolean isHigh() {
            return this == Tristate.High;
        }

        public boolean isLow() {
            return this == Tristate.Low;
        }

        public boolean isHiZ() {
            return this == Tristate.HiZ;
        }
    }

}


