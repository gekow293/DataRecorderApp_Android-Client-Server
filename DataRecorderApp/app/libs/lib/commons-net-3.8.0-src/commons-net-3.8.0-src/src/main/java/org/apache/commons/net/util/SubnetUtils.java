/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.net.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class that performs some subnet calculations given a network address and a subnet mask.
 * @see "http://www.faqs.org/rfcs/rfc1519.html"
 * @since 2.0
 */
public class SubnetUtils {

    private static final String IP_ADDRESS = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})";
    private static final String SLASH_FORMAT = IP_ADDRESS + "/(\\d{1,2})"; // 0 -> 32
    private static final Pattern addressPattern = Pattern.compile(IP_ADDRESS);
    private static final Pattern cidrPattern = Pattern.compile(SLASH_FORMAT);
    private static final int NBITS = 32;

    private static final String PARSE_FAIL = "Could not parse [%s]";

    private final int netmask;
    private final int address;
    private final int network;
    private final int broadcast;

    /** Whether the broadcast/network address are included in host count */
    private boolean inclusiveHostCount;


    /**
     * Constructor that takes a CIDR-notation string, e.g. "192.168.0.1/16"
     * @param cidrNotation A CIDR-notation string, e.g. "192.168.0.1/16"
     * @throws IllegalArgumentException if the parameter is invalid,
     * i.e. does not match n.n.n.n/m where n=1-3 decimal digits, m = 1-2 decimal digits in range 0-32
     */
    public SubnetUtils(final String cidrNotation) {
      final Matcher matcher = cidrPattern.matcher(cidrNotation);

      if (matcher.matches()) {
          this.address = matchAddress(matcher);

          /* Create a binary netmask from the number of bits specification /x */

          final int trailingZeroes = NBITS - rangeCheck(Integer.parseInt(matcher.group(5)), 0, NBITS);
          /*
           * An IPv4 netmask consists of 32 bits, a contiguous sequence
           * of the specified number of ones followed by all zeros.
           * So, it can be obtained by shifting an unsigned integer (32 bits) to the left by
           * the number of trailing zeros which is (32 - the # bits specification).
           * Note that there is no unsigned left shift operator, so we have to use
           * a long to ensure that the left-most bit is shifted out correctly.
           */
          this.netmask = (int) (0x0FFFFFFFFL << trailingZeroes );

          /* Calculate base network address */
          this.network = address & netmask;

          /* Calculate broadcast address */
          this.broadcast = network | ~netmask;
      } else {
          throw new IllegalArgumentException(String.format(PARSE_FAIL, cidrNotation));
      }
    }

    /**
     * Constructor that takes a dotted decimal address and a dotted decimal mask.
     * @param address An IP address, e.g. "192.168.0.1"
     * @param mask A dotted decimal netmask e.g. "255.255.0.0"
     * @throws IllegalArgumentException if the address or mask is invalid,
     * i.e. does not match n.n.n.n where n=1-3 decimal digits and the mask is not all zeros
     */
    public SubnetUtils(final String address, final String mask) {
        this.address = toInteger(address);
        this.netmask = toInteger(mask);

        if ((this.netmask & -this.netmask) - 1 != ~this.netmask) {
            throw new IllegalArgumentException(String.format(PARSE_FAIL, mask));
        }

        /* Calculate base network address */
        this.network = this.address & this.netmask;

        /* Calculate broadcast address */
        this.broadcast = this.network | ~this.netmask;
    }


    /**
     * Returns <code>true</code> if the return value of {@link SubnetInfo#getAddressCount()}
     * includes the network and broadcast addresses.
     * @since 2.2
     * @return true if the host count includes the network and broadcast addresses
     */
    public boolean isInclusiveHostCount() {
        return inclusiveHostCount;
    }

    /**
     * Set to <code>true</code> if you want the return value of {@link SubnetInfo#getAddressCount()}
     * to include the network and broadcast addresses.
     * This also applies to {@link SubnetInfo#isInRange(int)}
     * @param inclusiveHostCount true if network and broadcast addresses are to be included
     * @since 2.2
     */
    public void setInclusiveHostCount(final boolean inclusiveHostCount) {
        this.inclusiveHostCount = inclusiveHostCount;
    }



    /**
     * Convenience container for subnet summary information.
     *
     */
    public final class SubnetInfo {
        /* Mask to convert unsigned int to a long (i.e. keep 32 bits) */
        private static final long UNSIGNED_INT_MASK = 0x0FFFFFFFFL;

        private SubnetInfo() {}

        // long versions of the values (as unsigned int) which are more suitable for range checking
        private long networkLong()  { return network &  UNSIGNED_INT_MASK; }
        private long broadcastLong(){ return broadcast &  UNSIGNED_INT_MASK; }

        private int low() {
            return isInclusiveHostCount() ? network :
                broadcastLong() - networkLong() > 1 ? network + 1 : 0;
        }

        private int high() {
            return isInclusiveHostCount() ? broadcast :
                broadcastLong() - networkLong() > 1 ? broadcast -1  : 0;
        }

        /**
         * Returns true if the parameter <code>address</code> is in the
         * range of usable endpoint addresses for this subnet. This excludes the
         * network and broadcast addresses. Use {@link SubnetUtils#setInclusiveHostCount(boolean)}
         * to change this.
         * @param address A dot-delimited IPv4 address, e.g. "192.168.0.1"
         * @return True if in range, false otherwise
         */
        public boolean isInRange(final String address) {
            return isInRange(toInteger(address));
        }

        /**
         * Returns true if the parameter <code>address</code> is in the
         * range of usable endpoint addresses for this subnet. This excludes the
         * network and broadcast addresses by default. Use {@link SubnetUtils#setInclusiveHostCount(boolean)}
         * to change this.
         * @param address the address to check
         * @return true if it is in range
         * @since 3.4 (made public)
         */
        public boolean isInRange(final int address) {
            if (address == 0) { // cannot ever be in range; rejecting now avoids problems with CIDR/31,32
                return false;
            }
            final long addLong = address & UNSIGNED_INT_MASK;
            final long lowLong = low() & UNSIGNED_INT_MASK;
            final long highLong = high() & UNSIGNED_INT_MASK;
            return addLong >= lowLong && addLong <= highLong;
        }

        public String getBroadcastAddress() {
            return format(toArray(broadcast));
        }

        public String getNetworkAddress() {
            return format(toArray(network));
        }

        public String getNetmask() {
            return format(toArray(netmask));
        }

        public String getAddress() {
            return format(toArray(address));
        }

        public String getNextAddress() {
            return format(toArray(address + 1));
        }

        public String getPreviousAddress() {
            return format(toArray(address - 1));
        }

        /**
         * Return the low address as a dotted IP address.
         * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
         *
         * @return the IP address in dotted format, may be "0.0.0.0" if there is no valid address
         */
        public String getLowAddress() {
            return format(toArray(low()));
        }

        /**
         * Return the high address as a dotted IP address.
         * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
         *
         * @return the IP address in dotted format, may be "0.0.0.0" if there is no valid address
         */
        public String getHighAddress() {
            return format(toArray(high()));
        }

        /**
         * Get the count of available addresses.
         * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
         * @return the count of addresses, may be zero.
         * @throws RuntimeException if the correct count is greater than {@code Integer.MAX_VALUE}
         * @deprecated (3.4) use {@link #getAddressCountLong()} instead
         */
        @Deprecated
        public int getAddressCount() {
            final long countLong = getAddressCountLong();
            if (countLong > Integer.MAX_VALUE) {
                throw new RuntimeException("Count is larger than an integer: " + countLong);
            }
            // N.B. cannot be negative
            return (int)countLong;
        }

        /**
         * Get the count of available addresses.
         * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
         * @return the count of addresses, may be zero.
         * @since 3.4
         */
        public long getAddressCountLong() {
            final long b = broadcastLong();
            final long n = networkLong();
            final long count = b - n + (isInclusiveHostCount() ? 1 : -1);
            return count < 0 ? 0 : count;
        }

        public int asInteger(final String address) {
            return toInteger(address);
        }

        public String getCidrSignature() {
            return format(toArray(address)) + "/" + pop(netmask);
        }

        public String[] getAllAddresses() {
            final int ct = getAddressCount();
            final String[] addresses = new String[ct];
            if (ct == 0) {
                return addresses;
            }
            for (int add = low(), j=0; add <= high(); ++add, ++j) {
                addresses[j] = format(toArray(add));
            }
            return addresses;
        }

        /*
        * Convert a packed integer address into a 4-element array
        */
        private int[] toArray(final int val) {
            final int ret[] = new int[4];
            for (int j = 3; j >= 0; --j) {
                ret[j] |= val >>> 8*(3-j) & 0xff;
            }
            return ret;
        }

        /*
        * Convert a 4-element array into dotted decimal format
        */
        private String format(final int[] octets) {
            final StringBuilder str = new StringBuilder();
            for (int i =0; i < octets.length; ++i){
                str.append(octets[i]);
                if (i != octets.length - 1) {
                    str.append(".");
                }
            }
            return str.toString();
        }

        /**
         * {@inheritDoc}
         * @since 2.2
         */
        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append("CIDR Signature:\t[").append(getCidrSignature()).append("]")
                .append(" Netmask: [").append(getNetmask()).append("]\n")
                .append("Network:\t[").append(getNetworkAddress()).append("]\n")
                .append("Broadcast:\t[").append(getBroadcastAddress()).append("]\n")
                 .append("First Address:\t[").append(getLowAddress()).append("]\n")
                 .append("Last Address:\t[").append(getHighAddress()).append("]\n")
                 .append("# Addresses:\t[").append(getAddressCount()).append("]\n");
            return buf.toString();
        }
    }

    /**
     * Return a {@link SubnetInfo} instance that contains subnet-specific statistics
     * @return new instance
     */
    public final SubnetInfo getInfo() { return new SubnetInfo(); }

    /*
     * Convert a dotted decimal format address to a packed integer format
     */
    private static int toInteger(final String address) {
        final Matcher matcher = addressPattern.matcher(address);
        if (matcher.matches()) {
            return matchAddress(matcher);
        }
        throw new IllegalArgumentException(String.format(PARSE_FAIL, address));
    }

    /*
     * Convenience method to extract the components of a dotted decimal address and
     * pack into an integer using a regex match
     */
    private static int matchAddress(final Matcher matcher) {
        int addr = 0;
        for (int i = 1; i <= 4; ++i) {
            final int n = rangeCheck(Integer.parseInt(matcher.group(i)), 0, 255);
            addr |= (n & 0xff) << 8*(4-i);
        }
        return addr;
    }

    /*
     * Convenience function to check integer boundaries.
     * Checks if a value x is in the range [begin,end].
     * Returns x if it is in range, throws an exception otherwise.
     */
    private static int rangeCheck(final int value, final int begin, final int end) {
        if (value >= begin && value <= end) { // (begin,end]
            return value;
        }

        throw new IllegalArgumentException("Value [" + value + "] not in range ["+begin+","+end+"]");
    }

    /*
     * Count the number of 1-bits in a 32-bit integer using a divide-and-conquer strategy
     * see Hacker's Delight section 5.1
     */
    int pop(int x) {
        x = x - (x >>> 1 & 0x55555555);
        x = (x & 0x33333333) + (x >>> 2 & 0x33333333);
        x = x + (x >>> 4) & 0x0F0F0F0F;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        return x & 0x0000003F;
    }

    public SubnetUtils getNext() {
        return new SubnetUtils(getInfo().getNextAddress(), getInfo().getNetmask());
    }

    public SubnetUtils getPrevious() {
        return new SubnetUtils(getInfo().getPreviousAddress(), getInfo().getNetmask());
    }

}
