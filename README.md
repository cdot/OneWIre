# OneWire
This library provides an interface to digital thermometers that support the Dallas Semiconductor 1-wire bus
(https://en.wikipedia.org/wiki/1-Wire), accessed through a USB to Serial adapter.

The code makes use of the patterns used in digitemp_9097 (https://github.com/bcl/digitemp), and
algorithms published by Dallas Semiconductor for interfacing to their devices.

The specific goal was to support DS18B20 digital thermometer devices interfaced using a USB-serial adapter on Android.
To achieve this the port was done first on Linux with a simple driver interface to the JSSC (https://github.com/scream3r/java-simple-serial-connector) library. The jar for the portable bit was then re-used on Android with a driver developed using the UsbSerial (https://github.com/felHR85/UsbSerial) library.

Only thermometer devices directly attached to the main 1-wire bus (i.e. not connected through couplers) are supported.
Other sensors, and couplers, should be fairly easy to add by following the patterns used by digitemp.
