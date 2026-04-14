const response = http.request(`https://battery.tonkeeper.com/config`, {
    method: "GET",
    headers: {        
        'Content-Type': 'application/json'
    }
});

const r = json(response.body);