package com.zwang;

public class Temperature {
    private long timestamp;
    private int value;


    public Temperature(long timestamp, int value) {
        this.timestamp = timestamp;
        this.value = value;
    }
   


    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        this.value = value;
    }
    
}
