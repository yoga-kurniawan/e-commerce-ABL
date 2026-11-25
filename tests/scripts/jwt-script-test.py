#!/usr/bin/env python3
"""Command-double checks: JWT guards must fail before any cluster mutation."""
import base64,json,os,subprocess,sys,tempfile,time,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def token(role,cid='101',exp=None):
 c={'exp':int(time.time())+3600 if exp is None else exp,'realm_access':{'roles':[role]},'customer_id':cid}
 return 'test.'+base64.urlsafe_b64encode(json.dumps(c).encode()).decode().rstrip('=')+'.fixture'
STUB=r'''import os,sys,time,json
from pathlib import Path
args=sys.argv[1:]; tool=Path(sys.argv[0]).name
with open(os.environ['CALLS'],'a') as f:f.write(tool+' '+(' '.join(a for a in args if a in ['scale','apply','delete','exec','restart','port-forward'] or a.startswith('--replicas=')))+'\n')
if tool=='curl':
 url=args[-1]; auth=any(x.startswith('Authorization: Bearer ') for x in args)
 method=args[args.index('-X')+1] if '-X' in args else 'GET'
 status='200';body='[]'
 if '/products' in url and not auth:status='401' if not os.getenv('OLD_IMAGE') else '200'
 if '/actuator/health' in url and ':19090/' not in url:status='404'
 if '/orders' in url and method=='POST':status='503'
 if '/carts' in url and method=='POST':status='201';body='{"id":91}'
 if os.getenv('AUTH_DENIED') and auth:status='401'
 if '-o' in args and args[args.index('-o')+1]!='/dev/null':Path(args[args.index('-o')+1]).write_text(body)
 print(status,end='');sys.exit()
if 'get' in args:
 if 'peerauthentication' in args:print('STRICT')
 else:print('3')
elif 'apply' in args:sys.stdin.read()
elif 'wait' in args and os.getenv('POD_FAIL'):sys.exit(1)
elif 'port-forward' in args:time.sleep(60)
elif 'exec' in args:
 t=sys.stdin.read().strip(); method=args[-3];url=args[-2]
 if any(a.startswith('mtls-probe-') for a in args):print('\n000',end='');sys.exit(7)
 if method=='GET' and '/products' in url:
  print(('[]\n200' if t else '{"code":"UNAUTHORIZED"}\n401'),end='')
 else:
  print(('{"code":"FORBIDDEN"}\n403' if os.getenv('APP_DENIAL') else 'RBAC: access denied\n403'),end='')
elif 'rollout' in args:time.sleep(.15)
'''
class Scripts(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.folder=Path(self.tmp.name)
  self.env=dict(os.environ,ADMIN_TOKEN=token('ADMIN'),CUSTOMER_TOKEN=token('CUSTOMER'),CALLS=str(self.folder/'calls'),PATH=str(self.folder)+os.pathsep+os.environ['PATH'])
  self.env.pop('CUSTOMER_ID',None)
  for tool in ['curl','minikube']:
   p=self.folder/tool;p.write_text('#!'+sys.executable+'\n'+STUB);p.chmod(0o755)
 def run_script(self,path,**overrides):
  result=subprocess.run(['bash',str(ROOT/path)],env=dict(self.env,**overrides),capture_output=True,text=True,timeout=15)
  return result
 def assert_ok(self,r):self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def assert_no_mutation(self):
  calls=(self.folder/'calls').read_text() if (self.folder/'calls').exists() else ''
  self.assertNotIn('scale',calls);self.assertNotIn('apply',calls);self.assertNotIn('restart',calls)
 def test_fault_suites_send_jwt_and_restore_original_replicas(self):
  for name in ['downstream-failover','zero-downtime-rollout']:
   with self.subTest(name=name):
    self.assert_ok(self.run_script(f'tests/resilience/{name}.sh'))
    self.assertIn('--replicas=3',(self.folder/'calls').read_text())
 def test_invalid_and_old_images_stop_before_fault_injection(self):
  for variable in ['AUTH_DENIED','OLD_IMAGE']:
   for name in ['downstream-failover','zero-downtime-rollout']:
    with self.subTest(variable=variable,name=name):
     r=self.run_script(f'tests/resilience/{name}.sh',**{variable:'1'});self.assertNotEqual(r.returncode,0);self.assert_no_mutation()
 def test_mesh_checks_distinguish_backend_and_envoy(self):
  self.assert_ok(self.run_script('tests/security/istio-mesh-security.sh'))
 def test_backend_403_does_not_count_as_mesh_rbac_success(self):
  r=self.run_script('tests/security/istio-mesh-security.sh',APP_DENIAL='1')
  self.assertNotEqual(r.returncode,0);self.assertIn('not backend JWT denial',r.stderr)
 def test_pod_start_failure_is_not_security_success(self):
  r=self.run_script('tests/security/istio-mesh-security.sh',POD_FAIL='1')
  self.assertNotEqual(r.returncode,0);self.assertNotIn('Non-mesh traffic is blocked',r.stdout)
 def test_bad_identity_role_and_expiry_fail_without_http_or_cluster_mutation(self):
  for change in [{'CUSTOMER_ID':'202'},{'CUSTOMER_TOKEN':token('ADMIN')},{'CUSTOMER_TOKEN':token('CUSTOMER',exp=int(time.time())-1)}]:
   with self.subTest(change=list(change)):
    r=self.run_script('tests/resilience/downstream-failover.sh',**change);self.assertNotEqual(r.returncode,0);self.assert_no_mutation()
 def test_helper_refreshes_local_tokens_but_rejects_expiring_external_tokens(self):
  fake=self.folder/'scripts';fake.mkdir();(fake/'auth-token.py').write_text('print('+repr(token('CUSTOMER'))+')\n')
  command=f'''source tests/lib/jwt.sh
jwt_init customer
JWT_CUSTOMER_EXP=1
if jwt_refresh customer; then exit 9; fi
JWT_CUSTOMER_AUTO=true
JWT_REPO_ROOT={self.folder}
jwt_refresh customer
[[ "$JWT_CUSTOMER_EXP" -gt $(date +%s) ]]'''
  r=subprocess.run(['bash','-euc',command],cwd=ROOT,env=self.env,capture_output=True,text=True)
  self.assert_ok(r)
if __name__=='__main__':unittest.main()
