package com.vg.live;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.mutable.MutableInt;

class Histo<T> {
    final Map<T, MutableInt> map = new HashMap<>();
    int max = -1;
    T mostFrequentVal = null;

    public T getMostFrequentValue() {
        return mostFrequentVal;
    }

    public void add(int amount, T value) {
        if (!map.containsKey(value)) {
            map.put(value, new MutableInt(0));
        }
        MutableInt mutableInt = map.get(value);
        mutableInt.add(amount);
        int intValue = mutableInt.intValue();
        if (intValue > max) {
            max = intValue;
            mostFrequentVal = value;
        }
    }

    public void inc(T value) {
        add(1, value);
    }

    public int getBins() {
        return map.size();
    }

    public Set<T> keys() {
        return map.keySet();
    }

}