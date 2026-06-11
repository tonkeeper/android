function formatAmount(amount, decimals, precision) {    
    if (precision === undefined) precision = 4;
        
    if (amount === undefined || amount === null) return '0';
    if (decimals === undefined || decimals === null) return '0';
        
    var amountNum = Number(amount);
    var decimalsNum = Number(decimals);
    var multiplier = Math.pow(10, precision);
    
    var divisor = Math.pow(10, decimalsNum);
    var rawValue = amountNum / divisor;
        
    var truncated = Math.floor(rawValue * multiplier) / multiplier;
     
    var result = String(truncated);
    result = result.replace(/\.?0+$/, '');
    
    return result;
}

function formatTon(amount, precision) {
    if (precision === undefined) precision = 2;
    return formatAmount(amount, 9, precision);
}

function formatJetton(amount, decimals, precision) {
    if (precision === undefined) precision = 4;
    return formatAmount(amount, decimals, precision);
}

output.formatter = {
    formatJetton: formatJetton,
    formatTon: formatTon
}