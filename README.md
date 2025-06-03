# Ribbon_Worm

Ribbon Worm aims to create an encrypted, mesh-like network between machines
on the same LAN. The prototype uses mDNS for peer discovery and then
attempts to establish SOCKS5 connections between discovered hosts.

## Building

Ensure a Java 8 (or newer) JDK is installed. Compile all source files with:

```bash
javac java/*.java
```

## Running

After compilation, run the main entry point:

```bash
java ribbonWorm_Main
```

The application will broadcast discovery packets once per second and attempt
to connect to newly discovered peers through the local SOCKS5 proxy.
