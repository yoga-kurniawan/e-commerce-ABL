#!/usr/bin/env python3
"""JWT + ownership E2E with real Keycloak and four real services, isolated DBs.
Requires auth-local up + gateway, Docker, Java and freshly built Maven JARs.
Never modifies Minikube business databases. Temporary processes/container are removed.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--manual', action='store_true', help='Keep isolated backends running for manual curl/Postman tests; Ctrl+C cleans up.')
args = parser.parse_args()

ROOT = Path(__file__).resolve().parents[2]
NAMES = ['product', 'cart', 'order', 'payment']
BASE = {s: f'http://127.0.0.1:{19081+i}' for i,s in enumerate(NAMES)}
processes = []
logs = []
container = None
checks = 0

def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL).strip()

def request(service, method, path, expected, token=None, data=None, key=None):
    global checks
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if key: headers['Idempotency-Key'] = key
    req = Request(BASE[service]+path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
    try: response = urlopen(req, timeout=25)
    except HTTPError as failure: response = failure
    payload = response.read()
    assert response.status == expected, f'{service} {method} {path}: expected {expected}, received {response.status}'
    checks += 1
    return json.loads(payload) if payload else None

def wait_ready(url, process=None):
    end = time.monotonic()+120
    while time.monotonic()<end:
        if process and process.poll() is not None: raise RuntimeError('Service stopped; inspect /tmp/abl-jwt-*-e2e.log')
        try:
            with urlopen(url, timeout=2) as r:
                if r.status==200: return
        except (OSError, URLError): pass
        time.sleep(1)
    raise TimeoutError('Service readiness timed out')

try:
    tokens = {u: command('python3', str(ROOT/'scripts/auth-token.py'), u) for u in ['customer-a','customer-b','api-admin']}
    a,b,admin = (tokens[u] for u in ['customer-a','customer-b','api-admin'])
    password = secrets.token_hex(24)
    container = command('docker','run','-d','--rm','-e','POSTGRES_USER=jwt_test','-e','POSTGRES_PASSWORD='+password,
                        '-p','127.0.0.1::5432','postgres:17.11-bookworm')
    end=time.monotonic()+60
    while subprocess.run(['docker','exec',container,'pg_isready','-U','jwt_test'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode:
        if time.monotonic()>end: raise TimeoutError('PostgreSQL readiness timed out')
        time.sleep(1)
    port=command('docker','port',container,'5432/tcp').rsplit(':',1)[1]
    for i,name in enumerate(NAMES):
        command('docker','exec',container,'psql','-U','jwt_test','-c',f'CREATE DATABASE {name}_db')
        env=dict(os.environ, APP_SECURITY_ENABLED='true',APP_SECURITY_ISSUER='http://localhost:18090/realms/ecommerce',
                 APP_SECURITY_JWK_SET_URI='http://localhost:18090/realms/ecommerce/protocol/openid-connect/certs',
                 APP_SECURITY_AUDIENCE='ecommerce-api',SERVER_PORT=str(19081+i),MANAGEMENT_SERVER_PORT=str(19181+i),
                 SPRING_DATASOURCE_URL=f'jdbc:postgresql://127.0.0.1:{port}/{name}_db',
                 SPRING_DATASOURCE_USERNAME='jwt_test',SPRING_DATASOURCE_PASSWORD=password,
                 SPRING_APPLICATION_JSON=json.dumps({'clients':{n+'-url':BASE[n] for n in NAMES}}))
        log=open(f'/tmp/abl-jwt-{name}-e2e.log','w'); logs.append(log)
        jar=ROOT/f'services/{name}-service/target/{name}-service-0.2.0-SNAPSHOT.jar'
        processes.append(subprocess.Popen(['java','-Xmx384m','-jar',str(jar)],env=env,stdout=log,stderr=subprocess.STDOUT))
    for i,p in enumerate(processes): wait_ready(f'http://127.0.0.1:{19181+i}/actuator/health/readiness',p)
    if args.manual:
        print('MANUAL READY — database uji kosong; deployment Minikube tidak diubah.', flush=True)
        for name in NAMES: print(f'{name}: {BASE[name]}', flush=True)
        print('Login di terminal lain: TOKEN_A=$(python3 scripts/auth-token.py customer-a)', flush=True)
        print('Ctrl+C untuk menghentikan backend dan menghapus database uji.', flush=True)
        while True: time.sleep(1)
    for version in ['v1','v2']:
        prefix='/api/'+version
        request('product','GET',prefix+'/products',401)
        request('product','GET',prefix+'/products',401,'not.a.jwt')
        request('product','POST',prefix+'/products',403,a,{'name':'JWT test','price':100,'stock':10})
        p=request('product','POST',prefix+'/products',201,admin,{'name':'JWT test','price':100,'stock':10})['id']
        p2=request('product','POST',prefix+'/products',201,admin,{'name':'JWT parallel lookup','price':50,'stock':10})['id']
        request('cart','POST',prefix+'/carts',403,a,{'customerId':202})
        c=request('cart','POST',prefix+'/carts',201,a,{'customerId':101})['id']
        request('cart','POST',prefix+f'/carts/{c}/items',201,a,{'productId':p,'quantity':2})
        request('cart','POST',prefix+f'/carts/{c}/items',201,a,{'productId':p2,'quantity':1})
        for method,path,data in [('GET',f'/carts/{c}',None),('POST',f'/carts/{c}/items',{'productId':p,'quantity':1}),
                ('PUT',f'/carts/{c}/items/{p}',{'quantity':3}),('PATCH',f'/carts/{c}/items/{p}',{'quantity':3}),('DELETE',f'/carts/{c}/items/{p}',None)]:
            request('cart',method,prefix+path,403,b,data)
        request('cart','PUT',prefix+f'/carts/{c}/items/{p}',200,a,{'quantity':3})
        request('order','POST',prefix+'/orders',403,b,{'cartId':c,'customerId':202})
        o=request('order','POST',prefix+'/orders',201,a,{'cartId':c,'customerId':101})['id']
        request('order','GET',prefix+f'/orders/{o}',403,b)
        request('order','PATCH',prefix+f'/orders/{o}',403,b,{'shippingAddress':'Not mine'})
        request('payment','POST',prefix+'/payments',403,b,{'orderId':o,'method':'COD'},f'forbidden-{version}')
        pay=request('payment','POST',prefix+'/payments',201,a,{'orderId':o,'method':'COD'},f'owned-{version}')['id']
        request('payment','POST',prefix+'/payments',403,b,{'orderId':o,'method':'COD'},f'owned-{version}')
        request('payment','GET',prefix+f'/payments/{pay}',403,b)
        request('payment','PATCH',prefix+f'/payments/{pay}',403,b,{'transactionReference':'NOT-MINE'})
        request('payment','GET',prefix+f'/payments/{pay}',200,a)
        request('payment','PATCH',prefix+f'/payments/{pay}',200,a,{'transactionReference':'OWNED'})
        if version=='v2':
            request('order','GET',prefix+'/orders?customerId=101',403,b)
            request('payment','GET',prefix+f'/payments?orderId={o}',403,b)
            request('payment','GET',prefix+f'/payments?orderId={o}',200,a)
            request('order','POST',prefix+f'/orders/{o}/cancel',403,b)
            request('order','POST',prefix+f'/orders/{o}/cancel',409,a) # Existing PENDING-payment business rule remains enforced.
            request('cart','DELETE',prefix+f'/carts/{c}/items',403,b)
        request('cart','GET',prefix+f'/carts/{c}',200,a)
    print(f'PASS: {checks} HTTP assertions using real Keycloak JWTs and all four services; isolated databases cleaned up.')
except KeyboardInterrupt:
    print('Menghentikan backend dan membersihkan database uji...', flush=True)
finally:
    for p in processes:
        p.terminate()
    for p in processes:
        try: p.wait(timeout=30)
        except subprocess.TimeoutExpired: p.kill(); p.wait()
    for log in logs: log.close()
    if container:
        subprocess.run(['docker','rm','-f',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
