package com.cdot.digitemp;

import com.cdot.onewire.OneWireError;
import com.cdot.onewire.OneWireSearch;
import com.cdot.onewire.OneWireSerialDriver;
import com.cdot.onewire.OneWireThermometer;
import jssc.SerialPortList;

/**
 * Digital thermometer example main. Scans the 1-wire net to find thermometers
 * and samples them. Does not check what the device it found is - assumes
 * they are all thermometers.
 */
public class DigiTemp {
    public static void main(String[] args) {
        String[] portNames = SerialPortList.getPortNames();
        System.out.println("Scanning serial ports");
        for (String portName: portNames){
            System.out.println("Found serial port " + portName);

            // Construct a driver for this port 
            final OneWireSerialDriver driver = new OneWireJSSCDriver(portName, new OneWireSerialDriver.Logger() {
                @Override
                public void log(String s) {
                    System.out.println(s);
                }
            });
            
            // Scan the 1-wire bus for supported devices
            OneWireSearch scanner = new OneWireSearch(driver);

            scanner.scan(new OneWireSearch.Device() {
                @Override
                public OneWireError device(long serno) {
                    System.out.println(String.format("Found 1-wire device %X", serno));
                    if (!OneWireThermometer.supportsDevice(serno)) {
                        System.out.println("\t- not supported");
                        return OneWireError.NO_ERROR_SET;
                    }
                    OneWireThermometer owt = new OneWireThermometer(serno, driver);
                    owt.update();
                    System.out.println(owt);
                    return OneWireError.NO_ERROR_SET;
                }
            });
        }
    }
}


