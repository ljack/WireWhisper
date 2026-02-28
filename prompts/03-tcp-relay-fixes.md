# Prompt 3: TCP Relay Bug Fixes

**Session**: 3 (Feb 28, 2026 ~11:00 PM)
**Commits**: 554504a, 82473f8, d72337a

## Context

Chrome and other apps were hanging or failing to load pages. VPN was capturing traffic but TCP connections weren't completing properly.

## Bugs Fixed

### 1. TCP Checksum (Critical)

Packets written back to the TUN interface had no TCP checksum. Unlike UDP where checksum=0 is valid in IPv4, TCP checksum is mandatory. Added `computeTcpChecksum()` in TunProcessor that computes the checksum over the pseudo-header (src/dst addresses + protocol + length) plus the TCP segment.

### 2. SYN Sequence Number Extraction

The TCP proxy wasn't reading the client's initial sequence number from the raw SYN packet. Instead it was using 0. Fixed by extracting `clientSeqNum` from `packet.getInt(tcpOffset + 4)` and setting `theirSeqNum = clientSeq + 1`.

### 3. SYN-ACK Sequence Advance

After sending a SYN-ACK, `ourSeqNum` wasn't incremented. SYN consumes 1 byte of sequence space, so `ourSeqNum++` is needed after sending SYN-ACK.

### 4. Async TCP Writes

Data writes to remote servers were blocking the TUN read loop. Added a per-session `Channel<ByteArray>` write queue with a dedicated writer coroutine to avoid stalling packet processing.

### 5. IPv6 Header Length Bug

Operator precedence issue: `(value and 0xFF + 1)` was evaluating as `(value and 0x100)` due to `+` binding tighter than `and`. Fixed to `((value and 0xFF) + 1)`.

## Also Added

- Comprehensive unit test suite (~120 tests across 13 files) covering packet parsers, DNS/TLS parsing, flow tracking, and data models
