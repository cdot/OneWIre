package com.cdot.onewire;

/**
 * A single addressable device on a 1-wire bus
 */
class OneWireDevice {

    /**
     * Devices currently unsupported, but code can be ported from digitemp
     * 0x1F DS2409 MicroLAN coupler
     * 0x12 DS2406 dual addressable switch
     * 0x1C DS2422 temperature/datalogger with 8K memory
     * 0x1D DS2423 4K RAM with counter
     * 0x26 DS2438 smart battery monitor
     * 0x29 DS2408 8 channel addressable switch
     * 0x3A DS2413 dual channel addressable switch
     */
    public OneWireError last_error;
    public long serialNumber;
    protected OneWireSerialDriver driver;

    private static final byte MATCH_ROM = (byte) 0x55;

    protected OneWireDevice(long sn, OneWireSerialDriver d) {
        serialNumber = sn;
        driver = d;
    }
    
    /**
     * Subclasses are expected to override this for the devices they support
     * @return if this device supports the given device getFamily
     */
    public static boolean supportsDevice(byte family) {
        return false;
    }
    
    /**
     * @return if this device supports the device with the given serial number
     */
    public static boolean supportsDevice(long serno) {
        return supportsDevice((byte)((serno >> 56) & 0xFF));
    }
    
    /**
     * @return the byte from the serial number that indicates the device getFamily
     */
    public byte getFamily() {
        return (byte) ((serialNumber >> 56) & 0xFF); // SMELL: MSB or LSB?
    }

    /**
     * Reset the 1-Wire and send a MATCH Serial Number command followed by the
     * current SerialNum code. After this function is complete the 1-Wire device
     * is ready to accept slave-specific commands.
     *
     * @return true : reset indicates present and device is ready for commands.
     * false: reset does not indicate presence or echoes 'writes' are not
     * correct.
     */
    public OneWireError access() {
        // reset the 1-wire
        OneWireError e = driver.reset();
        if (e != OneWireError.NO_ERROR_SET) {
            return e;
        }

        if (driver.touchByte(MATCH_ROM) != MATCH_ROM) {
            return OneWireError.WRITE_VERIFY_FAILED;
        }

        // Send the serial number MSB first
        for (int i = 0; i < 8; i++) {
            byte b = (byte) ((serialNumber >> ((7 - i) * 8)) & 0xFF);
            if (driver.touchByte(b) != b) {
                return OneWireError.WRITE_VERIFY_FAILED;
            }
        }

        return OneWireError.NO_ERROR_SET;
    }
}
