"""Local, isolated SIMULATOR demonstration; never a real payment platform sandbox."""
import argparse
import json
import secrets
import time
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8080/api/v1')
parser.add_argument('--wait-seconds', type=int, default=0)
args = parser.parse_args()
if not args.base_url.startswith(('http://127.0.0.1:', 'http://localhost:')):
    raise SystemExit('Demonstration writes data; use a local isolated environment')
http = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, body=None, token=None, key=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    request = urllib.request.Request(args.base_url + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with http.open(request, timeout=15) as response:
            status, result = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, result = error.code, json.load(error)
    assert status == expected, (method, path, status, result)
    return result['data']


deadline = time.monotonic() + args.wait_seconds
while True:
    try:
        call('GET', '/auth', expected=401)
        break
    except (OSError, AssertionError):
        if time.monotonic() >= deadline:
            raise
        time.sleep(1)

suffix = secrets.token_hex(4)
tokens = {}
for role in ('seller_a', 'seller_b', 'buyer'):
    user, password = role + '_' + suffix, secrets.token_urlsafe(24)
    call('POST', '/auth/register', {'user': user, 'password': password})
    tokens[role] = call('POST', '/auth', {'user': user, 'password': password})['token']
seller_a, seller_b, buyer = (tokens[x] for x in ('seller_a', 'seller_b', 'buyer'))
print('PASS prepare ordinary accounts: each may buy and sell; credentials omitted')
products = []
for name, price, seller in [('Apples', 299, seller_a), ('Eggs', 199, seller_a), ('Bread', 499, seller_b)]:
    item = call('POST', '/item', {'name': name, 'price': price, 'stock': 5, 'status': 0}, seller)
    products.append(item)
    cart = call('PUT', '/cart/' + str(item['id']), {'quantity': 1, 'selected': True}, buyer)
request = {'address': 'Isolated demonstration receiver', 'cartVersion': cart['version'], 'expectedTotal': 997}
key = uuid.uuid4().hex
checkout = call('POST', '/checkout', request, buyer, key)
assert len(checkout['orders']) == 2 and checkout['total'] == 997
assert call('POST', '/checkout', request, buyer, key)['id'] == checkout['id']
assert call('GET', '/cart', token=buyer)['lines'] == []
print('PASS cart -> atomic split into two seller orders -> persistent idempotent retry')
orders = checkout['orders']
for detailed in orders:
    order = detailed['order']
    payment = call('POST', '/payments', {'orderId': order['id']}, buyer)
    assert payment['channel'] == 'SIMULATOR'
    assert call('POST', f"/payments/{payment['id']}/simulate", token=buyer)['status'] == 'SUCCEEDED'
    seller = seller_a if len(detailed['lines']) == 2 else seller_b
    call('POST', f"/order/{order['id']}/ship", token=seller)
    completed = call('POST', f"/order/{order['id']}/receive", token=buyer)
    assert completed['status'] == 3
    for line in detailed['lines']:
        call('POST', f"/order/{order['id']}/reviews", {'itemId': line['itemId'], 'rating': 5, 'comment': 'Confirmed in simulator demonstration'}, buyer)
sale_a = call('POST', '/settlements', {}, seller_a, uuid.uuid4().hex)
sale_b = call('POST', '/settlements', {}, seller_b, uuid.uuid4().hex)
assert sale_a['netAmount'] == 498 and sale_b['netAmount'] == 499
print('PASS independent simulator payment -> shipping -> receipt -> review -> internal settlement')
order_a = next(x for x in orders if len(x['lines']) == 2)['order']
before = call('GET', f"/item/{products[0]['id']}", token=buyer)['stock']
refund = call('POST', f"/order/{order_a['id']}/refunds", {'reason': 'Demonstrate full after-sale refund'}, buyer)
refund = call('POST', f"/refunds/{refund['id']}/approve", token=seller_a)
assert refund['status'] == 'SUCCEEDED' and refund['amount'] == 498
assert call('GET', f"/item/{products[0]['id']}", token=buyer)['stock'] == before
adjustment = call('POST', '/settlements', {}, seller_a, uuid.uuid4().hex)
assert adjustment['netAmount'] == -498 and sale_a['netAmount'] + adjustment['netAmount'] == 0
print('PASS refund application -> approval -> simulated refund -> negative accounting adjustment; shipped stock unchanged')
call('GET', '/stats', token=buyer, expected=403)
call('GET', '/user/seller_a_' + suffix, token=buyer, expected=403)
call('GET', f"/order/{order_a['id']}", token=seller_b, expected=403)
pending = call('POST', '/order', {'itemId': products[2]['id'], 'quantity': 1, 'address': 'Demo receiver'}, buyer, uuid.uuid4().hex)
for _ in range(2):
    call('POST', f"/order/{pending['id']}/cancel", token=buyer)
assert call('GET', f"/item/{products[2]['id']}", token=buyer)['stock'] == 4
call('POST', '/auth/logout', token=buyer)
call('GET', '/auth', token=buyer, expected=401)
print('PASS permission refusals -> once-only cancellation restock -> logout revocation')
