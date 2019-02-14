package com.cdot.onewire;

/**
 * Scans a one-wire net addressable using a given driver,
 * and identifies the addresses of the devices attached to the net.
 * The scan is done according to the algorithm described in
 * https://www.maximintegrated.com/en/app-notes/index.mvp/id/187
 */
public class OneWireSearch {

    private final OneWireSerialDriver driver;

    private static final byte ALARM_SEARCH_COMMAND = (byte) 0xEC;
    private static final byte NORMAL_SEARCH_COMMAND = (byte) 0xF0;

    public interface Device {

        /**
         * @param serialNum serial number of the device found
         * @return something other than OwError.NO_ERROR_SET to abort the scan
         */
        public OneWireError device(long serialNum);
    }

    /**
     * Last error seen by the adapter
     */
    public OneWireError last_error;

    public OneWireSearch(OneWireSerialDriver comms) {
        last_error = OneWireError.NO_ERROR_SET;
        driver = comms;
    }

    /**
     * Finds devices on the 1-Wire Net.
     *
     * @param callback listener called for each device found
     * @param alarm_only if set the find alarm command 0xEC is sent instead of
     * the normal search command 0xF0. This will find only those devices in an
     * alarm state.
     * @param family family byte code to restrict the search
     * @return an error code, if something went wrong
     */
    public OneWireError scan(Device callback, boolean alarm_only, byte family) {

        if (driver.reset() != OneWireError.NO_ERROR_SET) {
            return OneWireError.NO_DEVICES_ON_NET;
        }

        int LastDiscrepancy = 0;
        int LastFamilyDiscrepancy = 0;
        byte LastSearchCommand = (alarm_only ? ALARM_SEARCH_COMMAND : NORMAL_SEARCH_COMMAND);

        byte[] serial_bytes = new byte[8];
        for (int i = 1; i < 8; i++) {
            serial_bytes[i] = 0;
        }

        if (family != 0) {
            serial_bytes[0] = family;
            LastDiscrepancy = 64;
        }
        
        while (true) {
            CRC8 crc = new CRC8();
            int bit_number = 1;
            int last_zero = 0;

            if (driver.touchByte(LastSearchCommand) != LastSearchCommand)
                return OneWireError.WRITE_VERIFY_FAILED;

            int serial_byte_number = 0;
            byte serial_byte_mask = 1;
            boolean direction;

            while (serial_byte_number < 8) {
                // Initiate the response from devices. All participating
                // devices simultaneously send the LSB from their ROM, which
                // results in a logical AND.
                boolean bit1 = driver.touchBit(true);
                // Now initiate the second bit. This time devices send the
                // complement of their LSB.
                boolean bit0 = driver.touchBit(true);
                //System.out.println(bit1 + "~" + bit0);

                // 00 There are both 0s and 1s in the current bit position
                //    of the participating ROM numbers. This is a discrepancy.
                // 01 There are only 0s in the LSB of the participating ROM
                //    numbers.
                // 10 There are only 1s in the LSB of the participating ROM
                //    numbers.
                // 11 No devices participating in search
                // Check for no devices
                if (bit1 && bit0) {
                    //System.out.println("No devices");
                    break;
                }

                // Send a bit back to the slaves. Only those slaves sharing
                // that bit will continue participating.
                if (!(bit0 || bit1)) {
                    // There are both 0s and 1s in the current bit of
                    // participating slaves

                    // If this discrepancy is before the Last Discrepancy
                    // on a previous next() then pick the same as last time
                    if (bit_number < LastDiscrepancy) {
                        direction = ((serial_bytes[serial_byte_number] & serial_byte_mask) != 0);
                    } else // if equal to last pick 1, if not then pick 0
                    {
                        direction = (bit_number == LastDiscrepancy);
                    }

                    // if 0 was picked then record its position in LastZero
                    if (!direction) {
                        last_zero = bit_number;
                    }

                    // check for Last discrepancy in family
                    if (last_zero < 9) {
                        LastFamilyDiscrepancy = last_zero;
                    }
                } else {
                    // only 0s => bit_test = 01
                    // only 1s => bit_test = 10
                    direction = !bit0;
                }

                // Record this bit in the serial number buffer
                if (direction) {
                    serial_bytes[serial_byte_number] |= serial_byte_mask;
                } else {
                    serial_bytes[serial_byte_number] &= ~serial_byte_mask;
                }

                // Write direction. Slaves that don't have this bit go
                // into a wait state.
                if (driver.touchBit(direction) != direction)
                    return OneWireError.WRITE_VERIFY_FAILED;

                serial_byte_mask <<= 1;
                bit_number++;

                // if the mask is 0 then move to new byte
                if (serial_byte_mask == 0) {
                    // accumulate the CRC8
                    crc.add(serial_bytes[serial_byte_number]);
                    //System.out.println("Completed byte " +
                    //     serial_byte_number + " = " + Driver.hex(serial_bytes[serial_byte_number])
                    //      + " CRC8 " + Driver.hex(lastcrc)); 
                    serial_byte_number++;
                    serial_byte_mask = 1;
                }
                // loop through bytes 0-7
            }

            // Build the serial number from the buffer
            long serialNum = 0;
            for (int i = 0; i < 8; i++) {
                serialNum = (serialNum << 8) | ((long) serial_bytes[i] & 0xFF);
            }

            // if the search was successful then
            if (bit_number != 65 || crc.get() != 0 || (serialNum & 0xFF) == 0)
                return OneWireError.SEARCH_ERROR;
                
            // search successful, 64 bit ID received
            LastDiscrepancy = last_zero;
            OneWireError e = callback.device(serialNum);
            if (e != OneWireError.NO_ERROR_SET)
                return e;
            if (LastDiscrepancy == 0) {
                return OneWireError.NO_ERROR_SET;
            }
        }
    }
    public OneWireError scan(Device callback) {
        return scan(callback, false, (byte)0);
    }
    public OneWireError scan(Device callback, boolean alarm_only) {
        return scan(callback, alarm_only, (byte)0);
    }
    public OneWireError scan(Device callback, byte family) {
        return scan(callback, false, family);
    }
}
