package com.johanpmeert;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static javax.xml.bind.DatatypeConverter.parseHexBinary;
import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class Main {

    public static void main(String[] args) {

        final String sma_multicastIp = "239.12.255.254";
        final int sma_multicastPort = 9522;
        final String myHostIpAddress = "192.168.2.147";  // change here to your PC ip address

        System.out.println("SMA discovery and readout tool for SMA energy meter and SMA home manager 2");
        System.out.print("Opening multicast socket from " + myHostIpAddress + "... ");

        try {
            InetAddress mcastAddr = InetAddress.getByName(sma_multicastIp);
            InetSocketAddress group = new InetSocketAddress(mcastAddr, sma_multicastPort);
            NetworkInterface netIf = NetworkInterface.getByName(myHostIpAddress);
            MulticastSocket mcSocket = new MulticastSocket(sma_multicastPort);
            mcSocket.joinGroup(group, netIf);
            System.out.println("done");

            byte[] txbuf = parseHexBinary("534d4100000402a0ffffffff0000002000000000");  // discovery string to be sent to network, all SMA devices will answer
            System.out.println("Sending out SMA specific discovery code " + printHexBinary(txbuf) + " to multicast address " + sma_multicastIp + "/" + sma_multicastPort);
            DatagramPacket data = new DatagramPacket(txbuf, txbuf.length, mcastAddr, sma_multicastPort);
            mcSocket.send(data);

            byte[] buffer = new byte[1024];
            data = new DatagramPacket(buffer, buffer.length);
            while (true) {
                mcSocket.receive(data);
                // String hexdata = printHexBinary(buffer);
                // hexdata = hexdata.substring(0, data.getLength() * 2);
                // System.out.println("Received from " + data.getAddress() + ": " + hexdata);
                byte[] slice = Arrays.copyOfRange(buffer, 0, data.getLength());
                smaResponseData smaR = parseSmaResponse(slice);
                if (smaR != null) {
                    System.out.print("Device n° " + smaR.serial + " reports: P = ");
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

        String hexString = printHexBinary(hexData);
        int location;
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
            location = hexString.indexOf(internalData.power3fpos.code) / 2;
            byte[] result = Arrays.copyOfRange(hexData, location + 4, location + 8);
            int power3fp = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            location = hexString.indexOf(internalData.power3fneg.code) / 2;
            int power3fn = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            if (power3fp != 0) {
                smar.power3f = BigDecimal.valueOf(power3fp).divide(BigDecimal.TEN);
            } else {
                smar.power3f = BigDecimal.valueOf(-power3fn).divide(BigDecimal.TEN);
            }

            location = hexString.indexOf(internalData.powerL1pos.code) / 2;
            int powerL1p = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            location = hexString.indexOf(internalData.powerL1neg.code) / 2;
            int powerL1n = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            if (powerL1p != 0) {
                smar.powerL1 = BigDecimal.valueOf(powerL1p).divide(BigDecimal.TEN);
            } else {
                smar.powerL1 = BigDecimal.valueOf(-powerL1n).divide(BigDecimal.TEN);
            }

            location = hexString.indexOf(internalData.powerL2pos.code) / 2;
            int powerL2p = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            location = hexString.indexOf(internalData.powerL2neg.code) / 2;
            int powerL2n = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            if (powerL2p != 0) {
                smar.powerL2 = BigDecimal.valueOf(powerL2p).divide(BigDecimal.TEN);
            } else {
                smar.powerL2 = BigDecimal.valueOf(-powerL2n).divide(BigDecimal.TEN);
            }

            location = hexString.indexOf(internalData.powerL3pos.code) / 2;
            int powerL3p = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            location = hexString.indexOf(internalData.powerL3neg.code) / 2;
            int powerL3n = ByteBuffer.wrap(Arrays.copyOfRange(hexData, location + 4, location + 8)).getInt();
            if (powerL3p != 0) {
                smar.powerL3 = BigDecimal.valueOf(powerL3p).divide(BigDecimal.TEN);
            } else {
                smar.powerL3 = BigDecimal.valueOf(-powerL3n).divide(BigDecimal.TEN);
            }

            return smar;
        }
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
        power3fpos("00010400"),
        power3fneg("00020400"),
        powerL1pos("00150400"),
        powerL1neg("00160400"),
        powerL2pos("00290400"),
        powerL2neg("002A0400"),
        powerL3pos("003D0400"),
        powerL3neg("003E0400");

        String code;

        internalData(String code) {
            this.code = code;
        }

    }

}