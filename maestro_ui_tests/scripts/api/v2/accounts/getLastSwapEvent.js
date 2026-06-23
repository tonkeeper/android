const response = http.request(`https://block.tonapi.io/v2/accounts/${addr}/events?limit=1&subject_only=true`, {
    method: "GET",
    headers: {
        'Authorization': 'Bearer ' + auth_token,
        'Content-Type': 'application/json'
    }
});

const events = json(response.body);
const action = events.events[0].actions[0];
output.jettonLastSwap = action;
output.jettonLastSwapJettonSwap = action.JettonSwap;