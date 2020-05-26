package com.beechen.appengine.nwwsjson;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.json.simple.JSONObject;

public class Entry {
    private static final SimpleDateFormat ISO8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    // The product's text
    public String message;

    public String subject;

    // The wmo product ID
    public String productID;

    // The stationr eporting
    public String center;

    // Date of the entry
    public Date date;

    // Unique server id of the message
    public String uniqueID;

    // The six character AWIPS ID, sometimes called AFOS PIL.
    public String awipsID;

    public Entry() {
        // For firestore object storage
    }

    public Entry(String productID, String center, Date date, String uniqueID, String awipsID, String subject, String message) {
        this.productID = productID;
        this.center = center;
        this.date = date;
        this.uniqueID = uniqueID;
        this.awipsID = awipsID;
        this.message = message;
        this.subject = subject;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
            .append(this.productID)
            .append(this.center)
            .append(this.date)
            .append(this.uniqueID)
            .append(this.awipsID)
            .append(this.subject)
            .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!Entry.class.isAssignableFrom(obj.getClass())) {
            return false;
        }

        final Entry other = (Entry) obj;
        return this.productID == other.productID
            && this.center == other.center 
            && this.date == other.date 
            && this.uniqueID == other.uniqueID 
            && this.awipsID == other.awipsID;
    }

    @SuppressWarnings("unchecked")
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("wmo_product_id", this.productID);
        obj.put("issuing_center", this.center);
        obj.put("id", this.uniqueID);
        obj.put("date", ISO8601_FORMAT.format(this.date));
        obj.put("awips_id", this.awipsID);
        obj.put("subject", this.subject);
        obj.put("message", this.message);
        return obj;
    }

    public String key() {
        return getKey(this.center, this.productID);
    }

    public static String getKey(String station, String productID){
        return station + productID;
    }
}