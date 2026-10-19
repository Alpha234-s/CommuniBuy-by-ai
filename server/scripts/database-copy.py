"""Logical backup or restore to an explicitly empty rehearsal schema. Never drops a database."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('operation', choices=['backup', 'restore'])
parser.add_argument('--file', required=True)
parser.add_argument('--mysql-bin-directory', default='')
parser.add_argument('--restore-target', help='Existing empty communibuy_restore_* schema only')
args = parser.parse_args()
env = os.environ.copy()
required = ['DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD', 'DB_NAME']
if any(not env.get(k) for k in required):
    raise SystemExit('Set ' + ', '.join(required) + ' in the process environment')
if not re.fullmatch(r'[A-Za-z0-9_]+', env['DB_NAME']):
    raise SystemExit('Invalid source schema name')
env['MYSQL_PWD'] = env.pop('DB_PASSWORD')
common = ['--protocol=TCP', '-h' + env['DB_HOST'], '-P' + str(int(env['DB_PORT'])), '-u' + env['DB_USER']]


def executable(name):
    if args.mysql_bin_directory:
        return str(Path(args.mysql_bin_directory) / (name + ('.exe' if os.name == 'nt' else '')))
    return name


path = Path(args.file).resolve()
if args.operation == 'backup':
    # Exclusive creation avoids silently overwriting an earlier backup.
    with path.open('xb') as output:
        result = subprocess.run([executable('mysqldump'), *common, '--single-transaction',
                                 '--no-tablespaces', '--set-gtid-purged=OFF', '--hex-blob',
                                 '--triggers', '--skip-comments', env['DB_NAME']],
                                env=env, stdout=output, stderr=subprocess.PIPE)
    if result.returncode:
        raise SystemExit('mysqldump failed; partial file retained; inspect database permissions/connectivity')
else:
    target = args.restore_target or ''
    if not re.fullmatch(r'communibuy_restore_[a-z0-9_]{1,40}', target) or target == env['DB_NAME']:
        raise SystemExit('Restore requires a separate pre-created communibuy_restore_* schema')
    query = 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()'
    probe = subprocess.run([executable('mysql'), *common, '-N', '-B', target, '-e', query],
                           env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if probe.returncode or probe.stdout.strip() != b'0':
        raise SystemExit('Target must exist, be accessible and contain zero tables; no changes made')
    with path.open('rb') as source:
        result = subprocess.run([executable('mysql'), *common, target], env=env,
                                stdin=source, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    if result.returncode:
        raise SystemExit('Restore failed; partial target retained for inspection; do not retry blindly')
print(args.operation.upper() + ' completed; SHA256=' + hashlib.sha256(path.read_bytes()).hexdigest())
