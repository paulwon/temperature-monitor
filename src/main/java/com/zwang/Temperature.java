package com.zwang;

public class Temperature {
    private long timestamp;
    private int value;
    private String timestampString;

  

    public Temperature(long timestamp,  String timeString, int value) {
        this.timestamp = timestamp;
        this.timestampString = timeString;
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
    public String getTimestampString() {
        return this.timestampString;
    }

    public void setTimestampString(String timestampString) {
        this.timestampString = timestampString;
    }

}
