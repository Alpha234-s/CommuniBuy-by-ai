"""Repeatable local read workload. Targets are declared before execution; no credentials are emitted."""
import argparse
import concurrent.futures
import ctypes
import hashlib
import json
import math
import os
import platform
import secrets
import statistics
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8080/api/v1')
parser.add_argument('--output', required=True)
parser.add_argument('--items', type=int, default=200)
parser.add_argument('--requests', type=int, default=400)
parser.add_argument('--concurrency', type=int, default=8)
parser.add_argument('--jar')
args = parser.parse_args()
if not args.base_url.startswith(('http://127.0.0.1:', 'http://localhost:')):
    raise SystemExit('Use a local isolated environment only')
if min(args.items, args.requests, args.concurrency) < 1 or args.items > 250:
    raise SystemExit('Positive sizes required; at most 250 seed items per run')
targets = {'p95_ms_max': 250, 'throughput_rps_min': 30, 'error_rate_max': 0}
print('Predeclared targets:', json.dumps(targets))
local = threading.local()


def request(method, path, body=None, token=None):
    if not hasattr(local, 'http'):
        local.http = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(args.base_url + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    with local.http.open(req, timeout=15) as response:
        return json.load(response), response.headers.get('X-Trace-Id')


user, password = 'bench_' + secrets.token_hex(6), secrets.token_urlsafe(24)
request('POST', '/auth/register', {'user': user, 'password': password})
result, _ = request('POST', '/auth', {'user': user, 'password': password})
token = result['data']['token']
for index in range(args.items):
    request('POST', '/item', {'name': f'Benchmark item {index}', 'description': 'Local load-test fixture', 'price': 199, 'stock': 1000}, token)
path = '/item?' + urllib.parse.urlencode({'owner': user, 'keyword': 'Benchmark', 'status': 0, 'page': 0, 'size': 20})
for _ in range(20):
    request('GET', path, token=token)


def measure(_):
    started = time.perf_counter()
    try:
        data, trace = request('GET', path, token=token)
        success = data['success'] and data['data']['count'] == args.items and len(data['data']['content']) == min(args.items, 20)
        return (time.perf_counter() - started) * 1000, success, trace
    except (OSError, ValueError, KeyError):
        return (time.perf_counter() - started) * 1000, False, None


started = time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
    results = list(pool.map(measure, range(args.requests)))
duration = time.perf_counter() - started
latencies = sorted(x[0] for x in results if x[1])
failures = sum(not x[1] for x in results)


def percentile(p):
    return latencies[max(0, math.ceil(len(latencies) * p) - 1)] if latencies else None


memory = None
if os.name == 'nt':
    class MemoryStatus(ctypes.Structure):
        _fields_ = [('length', ctypes.c_ulong), ('load', ctypes.c_ulong)] + [(name, ctypes.c_ulonglong) for name in ('total', 'available', 'page_total', 'page_available', 'virtual_total', 'virtual_available', 'extended')]
    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
        memory = status.total
elif Path('/proc/meminfo').exists():
    memory = int(Path('/proc/meminfo').read_text().splitlines()[0].split()[1]) * 1024
report = {
    'timestamp_utc': datetime.now(timezone.utc).isoformat(),
    'hardware': {'os': platform.platform(), 'cpu': platform.processor() or 'unavailable', 'logical_cpus': os.cpu_count(), 'memory_bytes': memory},
    'workload': {'seed_items_for_owner': args.items, 'concurrency': args.concurrency, 'requests': args.requests, 'page_size': 20, 'warmup_requests': 20, 'query': 'owner AND keyword AND status, page=0, id DESC'},
    'targets': targets, 'duration_seconds': duration,
    'results': {'throughput_rps': args.requests / duration, 'p50_ms': percentile(.5), 'p95_ms': percentile(.95), 'p99_ms': percentile(.99), 'mean_ms': statistics.mean(latencies) if latencies else None, 'failures': failures, 'error_rate': failures / args.requests},
    'trace_id_sample': next((x[2] for x in results if x[2]), None),
    'limits': 'One local read workload; normal host background load; not production capacity certification; write concurrency is verified separately by MySQL invariant tests.'
}
if args.jar:
    report['jar_sha256'] = hashlib.sha256(Path(args.jar).read_bytes()).hexdigest()
report['passed'] = failures == 0 and report['results']['p95_ms'] is not None and report['results']['p95_ms'] <= targets['p95_ms_max'] and report['results']['throughput_rps'] >= targets['throughput_rps_min']
output = Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
print(json.dumps({'passed': report['passed'], 'results': report['results']}, ensure_ascii=True))
raise SystemExit(0 if report['passed'] else 1)
