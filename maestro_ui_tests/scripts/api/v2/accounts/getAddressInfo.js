const response = http.request(`https://block.tonapi.io/v2/accounts/${addr}`, {
    method: "GET",
    headers: {
        'Authorization': 'Bearer ' + auth_token,
        'Content-Type': 'application/json'
    }
});

const accData = json(response.body);

output.balance = accData.balance;
output.status = accData.status;
output.walletAddress = addr;