const tons = output.balance / 1_000_000_000;
const truncated = Math.trunc(tons * 1000) / 1000;
output.screenTonValue = truncated.toString().replace(/\.?0+$/, '');