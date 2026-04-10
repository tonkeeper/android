const response = http.request(`https://keeper.tonapi.io/v2/accounts/${_dns}/dns/expiring?period=366`, {
    method: "GET",
    headers: {
        'Authorization': 'Bearer ' + auth_token,
        'Content-Type': 'application/json'
    }
});

const r = json(response.body);

const timestamp = r.items[0].expiring_at;

const date = new Date(timestamp * 1000);
const day = date.getUTCDate();
const month = date.toLocaleString('en', { month: 'short', timeZone: 'UTC' });
const year = date.getUTCFullYear();

const d = new Date();
d.setFullYear(d.getFullYear() + 1);
const formatted = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
}).format(d);

output.dns = [];

output.dns.push({
  expired: {
    renewFormatted: formatted,
    unix: timestamp,
    day: day,
    month: month,
    year: year
  }
})