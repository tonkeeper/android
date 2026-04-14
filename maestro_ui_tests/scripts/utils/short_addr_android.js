if (addr.length < 8) {
    output.shortAddrRecieve = addr;
} else {
    output.shortAddrRecieve = addr.substring(0, 4) + "…" + addr.substring(addr.length - 4);
}