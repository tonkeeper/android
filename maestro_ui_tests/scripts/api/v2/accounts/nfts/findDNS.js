const response = http.request(`https://keeper.tonapi.io/v2/accounts/${addr}/nfts?limit=1000&offset=0&indirect_ownership=true`, {
    method: "GET",
    headers: {
        'Authorization': 'Bearer ' + auth_token,
        'Content-Type': 'application/json'
    }
});

const r = json(response.body);
