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
        for (String portName: portNames){
            System.out.println("Found serial port " + portName);
        }
        final OneWireSerialDriver driver = new OneWireJSSCDriver(portNames[0], new OneWireSerialDriver.Logger() {
            @Override
            public void log(String s) {
                System.out.println(s);
            }
        });
        OneWireSearch scanner = new OneWireSearch(driver);
    
        class MyHandler implements OneWireSearch.Device {
            @Override
            public OneWireError device(long serno) {
                OneWireThermometer owt = new OneWireThermometer(serno, driver);
                owt.update();
                System.out.println(serno + ": " + owt);
                return OneWireError.NO_ERROR_SET;
            }
        }
        scanner.scan(new MyHandler());
    }
}


