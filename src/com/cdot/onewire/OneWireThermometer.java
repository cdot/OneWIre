package com.cdot.onewire;

/**
 * Support for 1-wire temperature sensors.
 * Code is translated from https://github.com/bcl/digitemp and should support:
 * DS18B20, DS1822, DS28EA00, DS1923, DS1820
 * DS1820 temperature sensor
 * DS1822 Econo temperature sensor
 * DS18B20 temperature sensor
 * DS28EA00 thermometer with sequence detect and PIO
 * DS1923 iButton Hygrochron temperature/humidity logger
 * BUT ONLY DS18B20 IS TESTED!
 */
public class OneWireThermometer extends OneWireDevice {

    // Supported device families
    public static final byte DS1820 = 0x10;
    public static final byte DS1822 = 0x22;
    public static final byte DS18B20 = 0x28;
    public static final byte DS28EA00 = 0x42;
    public static final byte DS1923 = 0x41;

    // Commands. See https://datasheets.maximintegrated.com/en/ds/DS18B20.pdf
    private static final byte CONVERT_T = (byte) 0x44;
    private static final byte WRITE_SCRATCHPAD = (byte) 0x4E;
    private static final byte READ_SCRATCHPAD = (byte) 0xBE;
    private static final byte COPY_SCRATCHPAD = (byte) 0x48;

    // 9 bytes of the scratchpad
    private static final int SP_TEMPERATURE = 0;
    private static final int SP_SIGN = 1;
    private static final int SP_TH = 2;
    private static final int SP_TL = 3;
    private static final int SP_CONFIG = 4;
    private static final int SP_RESERVED = 5;
    private static final int SP_COUNT_REMAIN = 6; // DS1820
    private static final int SP_COUNT_PER_C = 7; // DS1820
    private static final int SP_CRC = 8;

    // temperature detected at last update (in C)
    public double temperature;

    // bits of resolution detected at last update
    public int resolution;
    public int TH_alarm, TL_alarm;

    public OneWireThermometer(long serno, OneWireSerialDriver d) {
        super(serno, d);
        temperature = -273.5; // 0K
    }

    public static boolean supportsDevice(byte fam) {
        switch (fam) {
            case DS18B20: case DS1822: case DS28EA00: case DS1923: case DS1820:
            return true;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return String.format("%X: %g (%d) %d<T>%d",
                serialNumber, temperature, resolution, TL_alarm, TH_alarm);
    }

    /**
     * Poll the sensor to update the temperature
     *
     * @return a OneWireError indicating status
     */
    public OneWireError update() {
        int attempt = 0; // max tries
        int ds1820_try = 0;

        while (attempt++ < 3) {

            // Initiate temperature conversion
            OneWireError e = access();
            if (e != OneWireError.NO_ERROR_SET) {
                return e;
            }

            byte repl = driver.touchByte(CONVERT_T);
            if (repl != CONVERT_T) {
                return OneWireError.WRITE_VERIFY_FAILED;
            }

            // Sleep to allow conversion to complete. Max conversion time
            // for the DS18B20 is 750ms, so 1s is ample.
            driver.msDelay(1000);

            // Initiate scratchpad read
            e = access();
            if (e != OneWireError.NO_ERROR_SET) {
                return e;
            }

            if (driver.touchByte(READ_SCRATCHPAD) != READ_SCRATCHPAD) {
                return OneWireError.READ_SCRATCHPAD_FAILED;
            }

            CRC8 crc = new CRC8();
            byte[] scratchpad = new byte[9];
            for (int i = 0; i < 9; i++) {
                byte b = driver.touchByte((byte) 0xFF);
                crc.add(b);
                scratchpad[i] = b;
            }

            TL_alarm = scratchpad[SP_TL];
            TH_alarm = scratchpad[SP_TH];
            switch (scratchpad[SP_CONFIG] & 0x60) {
                case 0x00:
                    resolution = 9;
                    break;
                case 0x20:
                    resolution = 10;
                    break;
                case 0x40:
                    resolution = 11;
                    break;
                case 0x60:
                    resolution = 12;
                    break;
            }

            // If the CRC8 is valid then calculate the temperature 
            if (crc.get() == 0x00) {
                // DS1822 and DS18B20 use a different calculation
                switch (this.getFamily()) {
                    case DS18B20:
                    case DS1822:
                    case DS28EA00:
                    case DS1923:
                        int temp2 = ((int) scratchpad[SP_SIGN] << 8)
                                | ((int) scratchpad[SP_TEMPERATURE] & 0xFF);
                        temperature = temp2 / 16.0;
                        break;
                    case DS1820:
                        // Check for DS1820 glitch condition
                        // COUNT_PER_C - COUNT_REMAIN == 1
                        if (attempt == 0) {
                            if ((scratchpad[SP_COUNT_PER_C] - scratchpad[SP_COUNT_REMAIN]) == 1) {
                                // DS1820 error, try again
                                ds1820_try = 1;
                                continue; // try again
                            }
                        }

                        // Check for DS18S20 Error condition
                        // LSB = 0xAA
                        // MSB = 0x00
                        // COUNT_REMAIN = 0x0C
                        // COUNT_PER_C = 0x10
                        if (ds1820_try == 0) {
                            if ((scratchpad[SP_TL] == 0xAA)
                                    && (scratchpad[SP_TH] == 0x00)
                                    && (scratchpad[SP_COUNT_REMAIN] == 0x0C)
                                    && (scratchpad[SP_COUNT_PER_C] == 0x10)) {
                                ds1820_try = 1;
                                continue; // try again
                            }
                        }

                        //  Calculated using formula from DS1820 datasheet
                        //                   count_per_C - count_remain
                        //   (temp - 0.25) * --------------------------
                        //                       count_per_C
                        //
                        //   If Sign is not 0x00 then it is a negative (Centigrade) number, and
                        //   the temperature must be subtracted from 0x100 and multiplied by -1 */
                        if (scratchpad[SP_SIGN] == 0) {
                            temperature = (double) (scratchpad[SP_TEMPERATURE] >> 1);
                        } else {
                            temperature = -1 * (int) (0x100 - scratchpad[SP_TEMPERATURE]) >> 1;
                        }
                        /* Negative temp calculation */
                        temperature -= 0.25;
                        int hi_precision = (int) scratchpad[SP_COUNT_PER_C] - (int) scratchpad[SP_COUNT_REMAIN];
                        hi_precision = hi_precision / (int) scratchpad[SP_COUNT_PER_C];
                        temperature += hi_precision;
                }

                return OneWireError.NO_ERROR_SET;
            }
            // Try again
        }
        return OneWireError.READ_STATUS_NOT_COMPLETE;
    }
}
