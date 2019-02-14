package com.cdot.onewire;

public abstract class OneWireSerialDriver {

    /* The UART_FIFO_SIZE defines the number of bytes that are written before
     * reading a reply. Any positive value should work and 160 is probably low
     * enough to avoid losing bytes in even most extreme situations on all
     * modern UARTs. The value affects readout performance asymptotically.
     * Note: Each bit sent to the 1-wire net requires 1 byte to be sent to 
     *       the uart.
     */
    public static final int UART_FIFO_SIZE = 160;

    // mode bit flags
    public enum MODE {
        NORMAL,
        OVERDRIVE,
        STRONG5,
        PROGRAM,
        BREAK
    };

    public OneWireError last_error;

    public interface Logger {
        public void log(String s);
    }

    protected final Logger logger;
    
    /**
     * Constructor
     * @param log may be null if debug logging is not required 
     */
    protected OneWireSerialDriver(Logger log) {
        if (log == null)
            logger = new Logger() {
                @Override
                public void log(String s) {}
            };
        else
            logger = log;
    }
    /**
     * Reset all of the devices on the 1-Wire Net
     * @return true if presence pulse(s) was detected and devices(s) reset,
     * otherwise the reset has failed and it returns false.
     */
    public abstract OneWireError reset();
    
    /**
     * Send a single bit and return the resulting bit read from the bus.
     * @param bit the bit value to send
     * @return true if the returned bit is set
     */
    public abstract boolean touchBit(boolean bit);
    
    /**
     * Send a specified number of bits from a bit string. The [0] byte is sent
     * first, and bits are sent from each byte LSB first
     * @param nbits number of bits to send
     * @param send bit string encapsulated in a sequence of bytes
     * @return a bit string with one bit of response for each bit sent
     */
    // NOT USED public abstract byte[] touchBits(int nbits, byte[] bits);

    /**
     * Send the individual bytes in the block. Writes the bytes individually
     * and reads the responses for each byte
     * @param tx block of bytes to transmit
     * @return one byte for each byte transmitted
     */
    public abstract byte[] touchBlock(byte[] tx);

    /**
     * Send the bits in a single byte and return the response.
     * @param sendbyte byte to send
     * @return response
     */
    public abstract byte touchByte(byte sendbyte);

    /* Delay for at least 'len' ms */
    public abstract void msDelay(int len);

}
