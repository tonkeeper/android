const now = Math.floor(Date.now() / 1000);
output.timestamp_lt_in_2min = (output.tonTransferTimestamp >= now - 120 && output.tonTransferTimestamp <= now)