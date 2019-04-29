package com.eneaceolini.aereader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class IPotArray extends PotArray implements Iterable<IPot>{

    /** Creates a new instance of IPotArray */
    public IPotArray() {
        super();
    }

    /** Returns an iterator in shift register order, starting from far end of shift register,
     * since bits are loaded so that first bit loaded ends up at this position.
     *
     * @return the ShiftRegisterIterator
     */
    @Override
    public Iterator<IPot> iterator() {
        return new ShiftRegisterIterator();
    }

    /** Provides pots in order of loading to the shift register, used in hardware interfaces to load bits.
     */
    protected class ShiftRegisterIterator implements Iterator<IPot>{
        ArrayList<IPot> list=new ArrayList<IPot>();
        int index=0;
        ShiftRegisterIterator(){
            for(Pot p: pots){
                list.add((IPot)p);
            }
            Collections.sort(list, new ShiftRegisterComparator());
        }

        public boolean hasNext() {
            return index<list.size();
        }

        public IPot next() {
            return list.get(index++);
        }

        public void remove() {
            list.remove(index);
        }

    }

    /** orders pots in order of shift register so that bias at input end of SR is returned last, used in hardware interfaces to load bits.
     * The shift register index should be numbered so that lower numbers correspond to "closer to the input end" where the bits are loaded.
     */
    protected class ShiftRegisterComparator implements Comparator<IPot> {
        /** Compares two pots.
         *
         * @param p1 first pot
         * @param p2 second pot
         * @return 1 if pot2 has larger shift register index than pot1, 0 if they have the same index, or -1 if pot1 has a larger index.
         */
        public int compare(IPot p1, IPot p2){
            if(p1.getShiftRegisterNumber()<p2.getShiftRegisterNumber()) return 1;
            if(p1.getShiftRegisterNumber()==p2.getShiftRegisterNumber()) return 0;
            return -1;
        }
        public boolean equals(IPot p1, IPot p2){
            if(p1.getShiftRegisterNumber()==p2.getShiftRegisterNumber()) return true; else return false;
        }
    }

    /** Returns an Iterator that iterates over the pots in the order of their shift register location, ordered from low to high,
     * starting with the far end of the shift register, furthest from the bit input.
     * @return the iterator
     */
    public ShiftRegisterIterator getShiftRegisterIterator() {
        return new ShiftRegisterIterator();
    }
}
