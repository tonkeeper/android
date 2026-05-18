const response = http.request(`https://keeper.tonapi.io/v2/accounts/${_dns}.ton/publickey`, {
    method: "GET",
    headers: {
        'Authorization': 'Bearer ' + auth_token,
        'Content-Type': 'application/json'
    }
});

const r = json(response.body);

output.pubkey = r.public_key;