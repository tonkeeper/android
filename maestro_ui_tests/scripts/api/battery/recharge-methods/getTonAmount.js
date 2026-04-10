const response = http.request(`https://battery.tonkeeper.com/recharge-methods?include_recharge_only=false`, {
    method: "GET",
    headers: {        
        'Content-Type': 'application/json'
    }
});

const r = json(response.body);
const m = meth.methods;