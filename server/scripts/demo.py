"""Repeatable HTTP demo against a LOCAL disposable database. No third-party Python packages."""
import argparse,json,secrets,time,urllib.request,urllib.error,uuid
parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8080/api/v1')
parser.add_argument('--wait-seconds',type=int,default=0)
args=parser.parse_args()
if not args.base_url.startswith(('http://127.0.0.1:','http://localhost:')):
    raise SystemExit('Demo creates records; use a local isolated application only')
http=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def call(method,path,body=None,token=None,key=None,expected=200):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    if key: headers['Idempotency-Key']=key
    req=urllib.request.Request(args.base_url+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:
        with http.open(req,timeout=10) as response: status=response.status; value=json.load(response)
    except urllib.error.HTTPError as e: status=e.code; value=json.load(e)
    assert status==expected,(method,path,status,value)
    return value['data']
deadline=time.monotonic()+args.wait_seconds
while True:
    try: call('GET','/auth',expected=401); break
    except (OSError,AssertionError):
        if time.monotonic()>=deadline: raise
        time.sleep(1)
suffix=secrets.token_hex(4)
seller,buyer='seller_'+suffix,'buyer_'+suffix
tokens={}
for user in (seller,buyer):
    password=secrets.token_urlsafe(24)
    call('POST','/auth/register',{'user':user,'password':password})
    tokens[user]=call('POST','/auth',{'user':user,'password':password})['token']
print('PASS prepare accounts -> register -> login')
s,b=tokens[seller],tokens[buyer]
item=call('POST','/item',{'name':'Demo apples','price':1299,'stock':5,'status':0},s)
key=uuid.uuid4().hex
order=call('POST','/order',{'itemId':item['id'],'quantity':2,'address':'Demo receiver, Building 1'},b,key)
retry=call('POST','/order',{'itemId':item['id'],'quantity':2,'address':'Demo receiver, Building 1'},b,key)
assert retry['id']==order['id'] and order['total']==2598
print('PASS seller creates group -> buyer orders -> same-key retry returns original')
order=call('POST',f"/order/{order['id']}/pay",token=b)
assert order['status']==1 and order['payTime'] is not None
call('POST',f"/order/{order['id']}/ship",token=s)
order=call('POST',f"/order/{order['id']}/receive",token=b)
assert order['status']==3 and order['receiptTime'] is not None
print('PASS simulated payment -> seller ships -> buyer receives')
call('GET','/stats',token=b,expected=403)
call('GET','/user/'+seller,token=b,expected=403)
call('POST',f"/item/{item['id']}",{'price':1},b,expected=403)
print('PASS admin capability, other private profile and item management denied (403)')
cancel=call('POST','/order',{'itemId':item['id'],'quantity':1,'address':'Demo address'},b,uuid.uuid4().hex)
assert call('GET',f"/item/{item['id']}",token=b)['stock']==2
for _ in range(2): call('POST',f"/order/{cancel['id']}/cancel",token=b)
assert call('GET',f"/item/{item['id']}",token=b)['stock']==3
print('PASS cancellation restores stock exactly once; final available stock=3')
call('POST','/auth/logout',token=b)
call('GET','/auth',token=b,expected=401)
print('PASS logout revocation (401); demonstration complete')
