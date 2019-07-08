package com.eneaceolini.aereader.biases;

import java.util.ArrayList;

public class PotArray {

    /** use for property updates on pots */
//    transient public PropertyChangeSupport support=new PropertyChangeSupport(this);
    protected ArrayList<Pot> pots=new ArrayList<Pot>();

    /** Creates a new instance of IPotArray */
    public PotArray() {
    }

    /** store the present values as the preferred values */
    private void storePreferedValues(){
        for(Pot p:pots){
            p.storePreferences();
        }
    }

    /** Loads  the stored preferred values. */
    private void loadPreferedValues(){
        for(Pot p:pots){
            p.loadPreferences();
        }
    }

    public String toString(){
        return "PotArray with "+pots.size()+" Pots";
    }

    public void exportPreferences(java.io.OutputStream os) {
        //        prefs.exportPreferences(os); // handled by pots themselves
    }

    public void importPreferences(java.io.InputStream is) {
        //        prefs.importPrefrences(is);
    }

    /** Loads preferences (preferred values). */
    public void loadPreferences() {
        //        System.out.println("PotArray.loadPreferences()");
        loadPreferedValues();
    }

    /** Flushes the preferred values to the Preferences */
    public void storePreferences() {
        storePreferedValues();
    }

//    public PropertyChangeSupport getSupport() {
//        return this.support;
//    }

    /** add a pot to the end of the list.
     The biasgen is also added as an observer for changes in the pot.
     * @param pot an Pot
     @return true
     */
    public boolean addPot(Pot pot){
        pots.add(pot);
//        pot.addObserver(this);
        return true;
    }

    /** Get an Pot by name.
     * @param name name of pot as assigned in Pot
     *@return the Pot, or null if there isn't one named that
     */
    public Pot getPotByName(String name){
        Pot p=null;
        for(Pot pot:pots){
            String potName=pot.getName();
            if(potName.equals(name)) p=pot;
        }
        return p;
    }


    /** Get an Pot by number in PotArray. For on-chip biasgen design kit biases, first entry is last one in shift register.
     * @param number name of pot as assigned in Pot
     *@return the Pot, or null if there isn't one at that array position.
     */
    public Pot getPotByNumber(int number){
        if(number<0 || number>pots.size()) return null;
        return (Pot)(pots.get(number));
    }

    /** gets the number of ipots in this array
     * @return the number of pots
     */
    public int getNumPots(){
        return pots.size();
    }

    public ArrayList<Pot> getPots() {
        return this.pots;
    }

    public void setPots(final ArrayList<Pot> pots) {
        this.pots = pots;
    }

}