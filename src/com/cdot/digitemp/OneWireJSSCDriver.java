package com.cdot.digitemp;

import com.cdot.onewire.OneWireError;
import com.cdot.onewire.OneWireSerialDriver;
import jssc.SerialPort;
import jssc.SerialPortException;

/**
 * 1-wire serial port interface using the JSSC serial port library
 */
class OneWireJSSCDriver extends OneWireSerialDriver {

    SerialPort serialPort;

    OneWireJSSCDriver(String portname, Logger log) {
        super(log);
        try {
            serialPort = new SerialPort(portname);
            serialPort.openPort();
        } catch (SerialPortException se) {
            throw new Error(se);
        }
    }

    private void purgePort() throws SerialPortException {
        serialPort.purgePort(SerialPort.PURGE_RXCLEAR
                | SerialPort.PURGE_TXCLEAR);
    }

    @Override
    public OneWireError reset() {
        try {
            //logger.log("touchReset");
            serialPort.setParams(SerialPort.BAUDRATE_9600,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
            purgePort();

            // Send the 480ms registration pulse
            byte[] buffer = {(byte) 0xF0};
            serialPort.writeBytes(buffer);

            // Look for presence pulse
            buffer = serialPort.readBytes(1);
            int result = buffer[0];

            if (result == 0) /* Data line is a short to ground */ {
                return OneWireError.RESET_FAILED;
            }

            if (result == 0xF0) /* No device responding */ {
                return OneWireError.NO_DEVICES_ON_NET;
            }

            // Here we should check for alarms and errors
            // Set input and output speed to 115.2k
            serialPort.setParams(SerialPort.BAUDRATE_115200,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_2,
                    SerialPort.PARITY_NONE);
            //logger.log("/touchReset got " + hex((byte)result));
            return OneWireError.NO_ERROR_SET;
        } catch (SerialPortException se) {
            //logger.log("touchReset " + se);
            return OneWireError.RESET_FAILED;
        }
    }

    @Override
    public boolean touchBit(boolean sbit) {
        try {
            // Send the bit
            serialPort.writeByte((byte) (sbit ? 0xFF : 0));

            // Get the echo
            byte[] rx = serialPort.readBytes(1);
            //logger.log("/TouchBit: send: " + hex(tx[0]) + ", receive: " + hex(rx[0]));
            return ((rx[0] & 1) != 0);
        } catch (SerialPortException se) {
            throw new Error("touchBit problem " + se);
        }
    }

    /* NOT USED
    public byte[] touchBits(int nbits, byte[] send) {
        byte[] receive = new byte[send.length];
        try {
            purgePort();

            int base = 0;
            int still_to_send = nbits;
            int received = 0;
            int bit_counter = 0;
            byte inch = 0;

            // send and receive blocks of UART_FIFO_SIZE or less
            while (still_to_send > 0) {
                int send_now = still_to_send;
                if (send_now > OneWireSerialDriver.UART_FIFO_SIZE) {
                    send_now = OneWireSerialDriver.UART_FIFO_SIZE;
                }
                still_to_send -= send_now;

                // Construct string of bytes representing bits to be sent
                byte[] buf = new byte[send_now];
                for (int i = 0; i < send_now; i++) {
                    // Bits are taken from the [0] byte first
                    // Bits are taken from each byte lsb first
                    buf[i] = (byte) ((send[(base + i) / 8] & (1 << (i & 0x7))) != 0 ? 0xFF : 0x00);
                }
                base += send_now;

                // write nbits2 bits of buffer to network
                serialPort.writeBytes(buf);
                //logger.log("touchBlock tx:"+hex(buf));

                // read N bytes (bits) paired with above write
                int nretrieved_bits = 0;

                while (nretrieved_bits < send_now) {
                    byte[] read
                            = serialPort.readBytes(send_now - nretrieved_bits);
                    //logger.log("touchBlock rx:"+hex(read));

                    // loop over the buffer and extract the least significant bits
                    // of each byte.
                    for (int i = 0; i < read.length; i++) {
                        boolean isSet = ((read[i] & 0x01) != 0);
                        inch >>= 1;
                        // mask bit 1 of buf, if bit is a '1' set bit 8 of inch,
                        // if bit is a '0' leave bit 8 of inch unset
                        if (isSet) {
                            inch |= 0x80;
                        } else {
                            inch &= 0x7F;
                        }
                        nretrieved_bits++;
                        bit_counter++;

                        if ((bit_counter % 8) == 0) {
                            // we have a full byte
                            //logger.log("touchBlock full byte " + hex(inch));
                            receive[received++] = inch;
                            inch = 0;
                        }
                    }
                }
            }

            if ((bit_counter % 8) != 0) // this is not a full byte
            {
                receive[received++] = inch;
            }
        } catch (SerialPortException se) {
            logger.log("touchBlock " + se);
        }
        return receive;
    }
    */
    
    @Override
    public byte[] touchBlock(byte[] tx) {
        // NOT USED - untested
        if (tx.length > OneWireSerialDriver.UART_FIFO_SIZE) {
            last_error = OneWireError.BLOCK_TOO_BIG;
            return null;
        }

        // send and receive the buffer
        byte[] rx = new byte[tx.length];
        for (int i = 0; i < tx.length; i++) {
            rx[i] = touchByte(tx[i]);
        }
        return rx;
    }

    @Override
    public byte touchByte(byte txbyte) {
        byte rxbyte = 0;
        try {
            purgePort();

            // Construct string of bytes representing bits to be sent
            byte[] buf = new byte[8];
            for (int i = 0; i < 8; i++) {
                // Bits are taken from the [0] byte first
                // Bits are taken from each byte lsb first
                buf[i] = (byte) ((txbyte & (1 << (i & 0x7))) != 0 ? 0xFF : 0x00);
            }

            serialPort.writeBytes(buf);

            byte[] read = serialPort.readBytes(8);

            for (int i = 0; i < 8; i++) {
                rxbyte <<= 1;
                if ((read[7 - i] & 0x01) != 0) {
                    rxbyte |= 1;
                }
            }
        } catch (SerialPortException se) {
            throw new Error("touchByte " + se);
        }
        return rxbyte;
    }

    @Override
    public void msDelay(int len) {
        try {
            Thread.sleep(len, 0);
        } catch (InterruptedException ie) {
        }
    }
}
