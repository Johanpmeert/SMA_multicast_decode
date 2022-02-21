package com.johanpmeert;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Main {

    public static void main(String[] args) {

        final String sma_multicastIp = "239.12.255.254";
        final int sma_multicastPort = 9522;

        String myHostIpAddress = null;
        try {
            myHostIpAddress = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.out.println("Host ip address could not be found");
        }
        System.out.println("SMA discovery and readout tool for SMA energy meter and SMA home manager 2");
        System.out.print("Opening multicast socket from " + myHostIpAddress + "... ");

        try {
            InetAddress mcastAddr = InetAddress.getByName(sma_multicastIp);
            InetSocketAddress group = new InetSocketAddress(mcastAddr, sma_multicastPort);
            NetworkInterface netIf = NetworkInterface.getByName(myHostIpAddress);
            MulticastSocket mcSocket = new MulticastSocket(sma_multicastPort);
            mcSocket.joinGroup(group, netIf);
            System.out.println("done");

            byte[] txbuf = hexStringToByteArray("534d4100000402a0ffffffff0000002000000000");  // discovery string to be sent to network, all SMA devices will answer
            System.out.println("Sending out SMA specific discovery code " + byteArrayToHexString(txbuf) + " to multicast address " + sma_multicastIp + "/" + sma_multicastPort);
            DatagramPacket data = new DatagramPacket(txbuf, txbuf.length, mcastAddr, sma_multicastPort);
            mcSocket.send(data);

            byte[] buffer = new byte[1024];
            data = new DatagramPacket(buffer, buffer.length);
            while (true) {
                mcSocket.receive(data);
                byte[] slice = Arrays.copyOfRange(buffer, 0, data.getLength());
                smaResponseData smaR = parseSmaResponse(slice);
                if (smaR != null) {
                    System.out.print("Device serial n° " + smaR.serial + " reports: P = ");
                    System.out.print(smaR.power3f + " W");
                    System.out.print(" (" + smaR.powerL1 + "," + smaR.powerL2 + "," + smaR.powerL3 + ")");
                    System.out.println();
                }
            }
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    public static smaResponseData parseSmaResponse(byte[] hexData) {

        // Extracting the correct values from the measurement byte[]
        // Up to now (2020) it is 600 or 608 bytes long: 600 for SMA energy meter and 608 for the SMA home manager 2
        // To be as future proof as possible, we do not use the data byte offsets given by SMA (since they already changed once with a firmware update)
        // For the measurement we search the hexData for a specific marker and then extract from 4 to 8 bytes further the data
        // These markers are stored in the enum internalData

        String hexString = byteArrayToHexString(hexData);
        if (hexString.length() < 1200) return null;
        else {
            smaResponseData smar = new smaResponseData();

            // the serial number is extracted direct from byte 20 to 24, then converted to a unsigned long
            // int is too short and would give negative serial numbers for some devices
            int serial = ByteBuffer.wrap(Arrays.copyOfRange(hexData, 20, 24)).getInt();
            smar.serial = Integer.toUnsignedLong(serial);

            // Power is read relative to a marker position, but for every value there are 2 markers
            // one for the positive power, one for the negative
            // at least one of them is always zero
            // also, the power is stored in 0.1W numbers, so we need to divide by 10 to get the value in Watts
            // to make searching the marker position easier to program, we convert the byte[] to a hexString and look for the markers in this String
            // the extraction is done from the byte[] with the search result from above (divided by 2)
            // for correctness, the result is stored in a BigDecimal
            int power3fp = getValueFromMarker(hexData, internalData.power3fpos);
            int power3fn = getValueFromMarker(hexData, internalData.power3fneg);
            if (power3fp != 0) {
                smar.power3f = BigDecimal.valueOf(power3fp).divide(BigDecimal.TEN);
            } else {
                smar.power3f = BigDecimal.valueOf(-power3fn).divide(BigDecimal.TEN);
            }

            int powerL1p = getValueFromMarker(hexData, internalData.powerL1pos);
            int powerL1n = getValueFromMarker(hexData, internalData.powerL1neg);
            if (powerL1p != 0) {
                smar.powerL1 = BigDecimal.valueOf(powerL1p).divide(BigDecimal.TEN);
            } else {
                smar.powerL1 = BigDecimal.valueOf(-powerL1n).divide(BigDecimal.TEN);
            }

            int powerL2p = getValueFromMarker(hexData, internalData.powerL2pos);
            int powerL2n = getValueFromMarker(hexData, internalData.powerL2neg);
            if (powerL2p != 0) {
                smar.powerL2 = BigDecimal.valueOf(powerL2p).divide(BigDecimal.TEN);
            } else {
                smar.powerL2 = BigDecimal.valueOf(-powerL2n).divide(BigDecimal.TEN);
            }

            int powerL3p = getValueFromMarker(hexData, internalData.powerL3pos);
            int powerL3n = getValueFromMarker(hexData, internalData.powerL3neg);
            if (powerL3p != 0) {
                smar.powerL3 = BigDecimal.valueOf(powerL3p).divide(BigDecimal.TEN);
            } else {
                smar.powerL3 = BigDecimal.valueOf(-powerL3n).divide(BigDecimal.TEN);
            }

            return smar;
        }
    }

    public static int getValueFromMarker(byte[] hexData, internalData marker) {
        String hexDataString = byteArrayToHexString(hexData);
        int markerLocation = hexDataString.indexOf(marker.code) / 2;
        if (markerLocation == -1) return 0;
        return ByteBuffer.wrap(Arrays.copyOfRange(hexData, markerLocation + marker.offset, markerLocation + marker.offset + marker.length)).getInt();
    }

    public static class smaResponseData {
        long serial;
        BigDecimal power3f;
        BigDecimal powerL1;
        BigDecimal powerL2;
        BigDecimal powerL3;
    }

    enum internalData {
        // These are the 4 byte markers in hex
        power3fpos("00010400", 4, 4),
        power3fneg("00020400", 4, 4),
        powerL1pos("00150400", 4, 4),
        powerL1neg("00160400", 4, 4),
        powerL2pos("00290400", 4, 4),
        powerL2neg("002A0400", 4, 4),
        powerL3pos("003D0400", 4, 4),
        powerL3neg("003E0400", 4, 4);

        String code;
        int offset;
        int length;


        internalData(String code, int offset, int length) {
            this.code = code;
            this.offset = offset;
            this.length = length;
        }

    }

    public static byte[] hexStringToByteArray(String hex) {
        hex = hex.length() % 2 != 0 ? "0" + hex : hex;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(hex.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    public static String byteArrayToHexString(byte[] bytes) {
        final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}