const response = http.request(`https://block.tonapi.io/v2/accounts/${addr}/events?limit=1&subject_only=true`, {
    method: "GET",
    headers: {
        'Authorization': 'Bearer ' + auth_token,
        'Content-Type': 'application/json'
    }
});

const events = json(response.body);
console.log(`${addr}`)
output.tonTransfer = events.events[0].TonTransfer;
output.tonTransferTimestamp = events.events[0].timestamp;