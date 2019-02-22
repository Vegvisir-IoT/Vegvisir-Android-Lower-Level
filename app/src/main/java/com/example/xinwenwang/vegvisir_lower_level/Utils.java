package com.example.xinwenwang.vegvisir_lower_level;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.vegvisir.lower.datatype.proto.Location;
import com.vegvisir.lower.datatype.proto.LocationRequestResponse;
import com.vegvisir.lower.datatype.proto.Payload;
import com.vegvisir.lower.datatype.proto.Timestamp;

import java.security.Signature;
import java.util.Date;

public class Utils {

    /**
     * Get current time
     * @return  time in milliseconds since Jan 1, 1970, 00:00:00.000 GMT.
     */
    public static Timestamp getTime() {
        /* TODO: Implement */
        return Timestamp.newBuilder().build();
    }

    /**
     * Get current location
     * @return a response object that contains eReturn a response object that contains either a GPS location object or A LocationServiceNotAvailableException objectither a GPS location object or A LocationServiceNotAvailableException object
     */
    public static LocationRequestResponse getLocation() {
        return LocationRequestResponse.newBuilder().build();
    }

    /**
     * sign given message with private key
     * @param payload the message to be signed. This can be Charlotte block, vegvisir block or whatever wrapped inside Payload.
     * @return signed message
     */
    public static String sign(Payload payload) {
        return null;
    }

    public static Long getTimeInMilliseconds() {
        return new Date().getTime();
    }
}
