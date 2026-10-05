#!/usr/bin/env python3
"""Reference server for XenDroid community configs, protocol v1 (docs/community-configs.md).

Python standard library only (http.server + sqlite3). It speaks plain HTTP on 127.0.0.1 and is
meant to sit behind a reverse proxy that terminates TLS: the app only talks HTTPS and never
follows a redirect. What it keeps per config is what the app sends (docs/community-configs.md,
"What is sent") plus the votes and a SHA-256 of the delete token; it logs no client address.

    python3 tools/community-server/server.py --db /var/lib/xendroid/community.sqlite3

An app build talks to it when built with -PxendroidCommunityUrl=https://<host>[/<prefix>].
Moderation: with XENDROID_COMMUNITY_ADMIN_TOKEN set (16+ characters), a DELETE carrying it in
X-Admin-Token removes any config.
"""
import argparse
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import sys
import threading
import time
from collections import deque
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))

TITLE = re.compile(r'^[0-9A-F]{8}$')
CONFIG_ID = re.compile(r'^[a-z0-9]{8,32}$')
VOTER = re.compile(r'^[0-9a-f]{32}$')
TOKEN = re.compile(r'^[A-Za-z0-9_-]{16,128}$')
VALUE = re.compile(r'^[A-Za-z0-9_.:+-]{0,64}$')
# Text fields may hold tabs and line breaks; no other control characters.
CONTROL = re.compile(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]')

UPLOAD_KEYS = {'titleId', 'name', 'note', 'result', 'settings', 'device', 'appVersionCode', 'appBuild'}
DEVICE_TEXT = ('manufacturer', 'model', 'soc', 'gpu', 'driver')
MAX_PER_TITLE = 300
INT32 = 2 ** 31 - 1

# Per client address and hour: shares, votes, deletes.
DEFAULT_LIMITS = {'upload': (10, 3600), 'vote': (120, 3600), 'delete': (30, 3600)}

PATH_LIST = re.compile(r'^/v1/titles/([0-9A-Fa-f]{8})/configs$')
PATH_VOTE = re.compile(r'^/v1/configs/([a-z0-9]{8,32})/vote$')
PATH_CONFIG = re.compile(r'^/v1/configs/([a-z0-9]{8,32})$')


def load_contract(path=os.path.join(HERE, 'contract.json')):
    with open(path, encoding='utf-8') as f:
        contract = json.load(f)
    contract['allowedKeys'] = frozenset(contract['allowedKeys'])
    contract['results'] = frozenset(contract['results'])
    return contract


class RateLimiter:
    """At most n events per window for each (kind, client); events older than the window are forgotten."""

    def __init__(self, limits=None, clock=time.monotonic):
        self.limits = dict(DEFAULT_LIMITS if limits is None else limits)
        self.clock = clock
        self.events = {}
        self.lock = threading.Lock()

    def allow(self, kind, who):
        limit, window = self.limits[kind]
        now = self.clock()
        with self.lock:
            seen = self.events.setdefault((kind, who), deque())
            while seen and seen[0] <= now - window:
                seen.popleft()
            if len(seen) >= limit:
                return False
            seen.append(now)
            return True


def _hash_token(token):
    return hashlib.sha256(token.encode('ascii')).hexdigest()


class Store:
    """SQLite, one connection behind a lock (the reference server is small, not fast)."""

    def __init__(self, path):
        self.db = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self.lock = threading.Lock()
        with self.lock:
            self.db.executescript('''
                PRAGMA journal_mode = WAL;
                CREATE TABLE IF NOT EXISTS configs (
                    id TEXT PRIMARY KEY,
                    title_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    note TEXT NOT NULL,
                    result TEXT NOT NULL,
                    settings TEXT NOT NULL,
                    device TEXT NOT NULL,
                    app_version_code INTEGER NOT NULL,
                    app_build TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    delete_token_sha256 TEXT NOT NULL,
                    votes_up INTEGER NOT NULL DEFAULT 0,
                    votes_down INTEGER NOT NULL DEFAULT 0
                );
                CREATE INDEX IF NOT EXISTS configs_by_title ON configs (title_id);
                CREATE TABLE IF NOT EXISTS votes (
                    config_id TEXT NOT NULL,
                    voter TEXT NOT NULL,
                    vote INTEGER NOT NULL,
                    PRIMARY KEY (config_id, voter)
                );
            ''')

    def listing(self, title_id, limit):
        with self.lock:
            rows = self.db.execute(
                'SELECT id, title_id, name, note, result, settings, device, app_version_code, app_build,'
                ' created_at, votes_up, votes_down FROM configs WHERE title_id = ?'
                ' ORDER BY votes_up - votes_down DESC, created_at DESC, id ASC LIMIT ?',
                (title_id, limit)).fetchall()
        return [{
            'id': r[0], 'titleId': r[1], 'name': r[2], 'note': r[3], 'result': r[4],
            'settings': json.loads(r[5]), 'device': json.loads(r[6]), 'appVersionCode': r[7],
            'appBuild': r[8], 'createdAt': r[9], 'votesUp': r[10], 'votesDown': r[11],
        } for r in rows]

    def add(self, upload, today):
        """Returns (id, delete token), or None when the game already has the most configs kept."""
        config_id = secrets.token_hex(8)
        token = secrets.token_urlsafe(24)
        with self.lock:
            self.db.execute('BEGIN IMMEDIATE')
            try:
                count = self.db.execute('SELECT COUNT(*) FROM configs WHERE title_id = ?',
                                        (upload['titleId'],)).fetchone()[0]
                if count >= MAX_PER_TITLE:
                    self.db.execute('ROLLBACK')
                    return None
                self.db.execute(
                    'INSERT INTO configs (id, title_id, name, note, result, settings, device, app_version_code,'
                    ' app_build, created_at, delete_token_sha256) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
                    (config_id, upload['titleId'], upload['name'], upload['note'], upload['result'],
                     json.dumps(upload['settings'], sort_keys=True), json.dumps(upload['device'], sort_keys=True),
                     upload['appVersionCode'], upload['appBuild'], today, _hash_token(token)))
                self.db.execute('COMMIT')
            except BaseException:
                self.db.execute('ROLLBACK')
                raise
        return config_id, token

    def vote(self, config_id, voter, vote):
        """Returns (up, down) after the vote, or None for an unknown config."""
        with self.lock:
            self.db.execute('BEGIN IMMEDIATE')
            try:
                if self.db.execute('SELECT 1 FROM configs WHERE id = ?', (config_id,)).fetchone() is None:
                    self.db.execute('ROLLBACK')
                    return None
                if vote == 0:
                    self.db.execute('DELETE FROM votes WHERE config_id = ? AND voter = ?', (config_id, voter))
                else:
                    self.db.execute('INSERT INTO votes (config_id, voter, vote) VALUES (?, ?, ?)'
                                    ' ON CONFLICT (config_id, voter) DO UPDATE SET vote = excluded.vote',
                                    (config_id, voter, vote))
                up, down = self.db.execute(
                    'SELECT COALESCE(SUM(vote = 1), 0), COALESCE(SUM(vote = -1), 0) FROM votes WHERE config_id = ?',
                    (config_id,)).fetchone()
                self.db.execute('UPDATE configs SET votes_up = ?, votes_down = ? WHERE id = ?', (up, down, config_id))
                self.db.execute('COMMIT')
            except BaseException:
                self.db.execute('ROLLBACK')
                raise
        return up, down

    def delete(self, config_id, token=None, admin=False):
        """'deleted', 'forbidden' (wrong token) or 'missing'."""
        with self.lock:
            row = self.db.execute('SELECT delete_token_sha256 FROM configs WHERE id = ?', (config_id,)).fetchone()
            if row is None:
                return 'missing'
            if not admin and (token is None or not hmac.compare_digest(row[0], _hash_token(token))):
                return 'forbidden'
            self.db.execute('BEGIN IMMEDIATE')
            self.db.execute('DELETE FROM votes WHERE config_id = ?', (config_id,))
            self.db.execute('DELETE FROM configs WHERE id = ?', (config_id,))
            self.db.execute('COMMIT')
        return 'deleted'


def _text(value, limit, required):
    """The stripped text, or None when it is not a string of the allowed length and characters."""
    if not isinstance(value, str) or CONTROL.search(value):
        return None
    value = value.strip()
    if len(value) > limit or (required and not value):
        return None
    return value


def _int(value, low, high):
    return isinstance(value, int) and not isinstance(value, bool) and low <= value <= high


def validate_upload(body, contract):
    """(upload, None) with the stored form, or (None, why) for the 400 answer."""
    limits = contract['limits']
    if not isinstance(body, dict) or set(body) != UPLOAD_KEYS:
        return None, 'a config has exactly these fields: ' + ', '.join(sorted(UPLOAD_KEYS))
    title = body['titleId']
    if not isinstance(title, str) or not TITLE.match(title.upper()) or title.upper() == '00000000':
        return None, 'titleId must be 8 hexadecimal digits'
    name = _text(body['name'], limits['name'], True)
    if name is None:
        return None, 'name must be 1-%d characters' % limits['name']
    note = _text(body['note'], limits['note'], True)
    if note is None:
        return None, 'note must be 1-%d characters' % limits['note']
    if body['result'] not in contract['results']:
        return None, 'result must be one of ' + ', '.join(sorted(contract['results']))
    settings = body['settings']
    if not isinstance(settings, dict) or not 1 <= len(settings) <= limits['settings']:
        return None, 'settings must have 1-%d entries' % limits['settings']
    for key, value in settings.items():
        if key not in contract['allowedKeys']:
            return None, '%s is not a setting configs may change' % key[:80]
        if not isinstance(value, str) or not VALUE.match(value):
            return None, '%s has a value configs may not carry' % key
    device = body['device']
    if not isinstance(device, dict) or not set(device) <= set(DEVICE_TEXT) | {'androidSdk'}:
        return None, 'device has only ' + ', '.join(DEVICE_TEXT + ('androidSdk',))
    clean_device = {}
    for field in DEVICE_TEXT:
        text = _text(device.get(field, ''), limits['field'], False)
        if text is None:
            return None, 'device.%s must be up to %d characters' % (field, limits['field'])
        clean_device[field] = text
    sdk = device.get('androidSdk', 0)
    if not _int(sdk, 0, 99):
        return None, 'device.androidSdk must be 0-99'
    clean_device['androidSdk'] = sdk
    if not _int(body['appVersionCode'], 0, INT32):
        return None, 'appVersionCode must be a whole number from 0'
    build = _text(body['appBuild'], limits['field'], False)
    if build is None:
        return None, 'appBuild must be up to %d characters' % limits['field']
    return {
        'titleId': title.upper(), 'name': name, 'note': note, 'result': body['result'],
        'settings': dict(settings), 'device': clean_device, 'appVersionCode': body['appVersionCode'],
        'appBuild': build,
    }, None


def _no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        raise ValueError('a field appears twice')
    return dict(pairs)


class Handler(BaseHTTPRequestHandler):
    server_version = 'XenDroidCommunity/1'
    protocol_version = 'HTTP/1.1'

    def log_message(self, format, *args):
        # No client address in the log: method, path and status only.
        if not self.app['quiet']:
            sys.stderr.write('%s %s\n' % (self.log_date_time_string(), format % args))

    @property
    def app(self):
        return self.server.app

    def client(self):
        if self.app['trust_forwarded']:
            forwarded = self.headers.get('X-Forwarded-For', '')
            if forwarded:
                return forwarded.split(',')[-1].strip()
        return self.client_address[0]

    def answer(self, status, body=None):
        data = b'' if body is None else json.dumps(body, separators=(',', ':')).encode('utf-8')
        self.send_response(status)
        if body is not None:
            self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        if data:
            self.wfile.write(data)

    def refuse(self, status, why):
        self.answer(status, {'error': why})

    def read_json(self):
        """The request's JSON object, or None after answering why not."""
        limit = self.app['contract']['limits']['body']
        if not self.headers.get('Content-Type', '').split(';')[0].strip().lower() == 'application/json':
            self.refuse(415, 'send application/json')
            return None
        try:
            length = int(self.headers.get('Content-Length', ''))
        except ValueError:
            self.refuse(411, 'Content-Length is required')
            return None
        if length < 0 or length > limit:
            self.close_connection = True
            self.refuse(413, 'the request is larger than %d bytes' % limit)
            return None
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode('utf-8'), object_pairs_hook=_no_duplicates)
        except (UnicodeDecodeError, ValueError):
            self.refuse(400, 'the request is not JSON')
            return None

    def do_GET(self):
        path = self.path.split('?', 1)[0]
        contract = self.app['contract']
        if path == '/v1/health':
            return self.answer(200, {'format': contract['format'], 'version': contract['version']})
        match = PATH_LIST.match(path)
        if not match:
            return self.refuse(404, 'no such resource')
        title = match.group(1).upper()
        if title == '00000000':
            return self.refuse(400, 'titleId must be 8 hexadecimal digits')
        configs = self.app['store'].listing(title, contract['limits']['listed'])
        self.answer(200, {'format': contract['format'], 'version': contract['version'], 'titleId': title,
                          'configs': configs})

    def do_POST(self):
        path = self.path.split('?', 1)[0]
        if path == '/v1/configs':
            return self.share()
        match = PATH_VOTE.match(path)
        if match:
            return self.vote(match.group(1))
        self.refuse(404, 'no such resource')

    def share(self):
        if not self.app['limiter'].allow('upload', self.client()):
            return self.refuse(429, 'too many shares from this address; try again later')
        body = self.read_json()
        if body is None:
            return
        upload, why = validate_upload(body, self.app['contract'])
        if upload is None:
            return self.refuse(400, why)
        today = datetime.now(timezone.utc).strftime('%Y-%m-%d')
        added = self.app['store'].add(upload, today)
        if added is None:
            return self.refuse(409, 'this game has the most configs the server keeps')
        self.answer(201, {'id': added[0], 'deleteToken': added[1]})

    def vote(self, config_id):
        if not self.app['limiter'].allow('vote', self.client()):
            return self.refuse(429, 'too many votes from this address; try again later')
        body = self.read_json()
        if body is None:
            return
        if not isinstance(body, dict) or set(body) != {'voter', 'vote'}:
            return self.refuse(400, 'a vote has exactly the fields voter and vote')
        voter, vote = body['voter'], body['vote']
        if not isinstance(voter, str) or not VOTER.match(voter) or not _int(vote, -1, 1):
            return self.refuse(400, 'voter must be 32 hexadecimal digits and vote -1, 0 or 1')
        votes = self.app['store'].vote(config_id, voter, vote)
        if votes is None:
            return self.refuse(404, 'no such config')
        self.answer(200, {'votesUp': votes[0], 'votesDown': votes[1]})

    def do_DELETE(self):
        match = PATH_CONFIG.match(self.path.split('?', 1)[0])
        if not match:
            return self.refuse(404, 'no such resource')
        if not self.app['limiter'].allow('delete', self.client()):
            return self.refuse(429, 'too many deletes from this address; try again later')
        admin_token = self.app['admin_token']
        given_admin = self.headers.get('X-Admin-Token')
        admin = bool(admin_token and given_admin and hmac.compare_digest(admin_token, given_admin))
        token = self.headers.get('X-Delete-Token')
        if not admin and (token is None or not TOKEN.match(token)):
            return self.refuse(403, 'X-Delete-Token is required')
        outcome = self.app['store'].delete(match.group(1), token, admin)
        if outcome == 'missing':
            return self.refuse(404, 'no such config')
        if outcome == 'forbidden':
            return self.refuse(403, 'the delete token does not match')
        self.answer(204)


def make_server(db_path, host='127.0.0.1', port=8780, contract=None, limiter=None, admin_token=None,
                trust_forwarded=False, quiet=False):
    server = ThreadingHTTPServer((host, port), Handler)
    server.daemon_threads = True
    server.app = {
        'store': Store(db_path),
        'contract': contract or load_contract(),
        'limiter': limiter or RateLimiter(),
        'admin_token': admin_token if admin_token and len(admin_token) >= 16 else None,
        'trust_forwarded': trust_forwarded,
        'quiet': quiet,
    }
    return server


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('--db', default='community.sqlite3', help='SQLite file (created if missing)')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8780, help='0 picks a free port')
    parser.add_argument('--contract', default=os.path.join(HERE, 'contract.json'))
    parser.add_argument('--trust-forwarded', action='store_true',
                        help='rate-limit by X-Forwarded-For (only behind a proxy that sets it)')
    args = parser.parse_args(argv)
    server = make_server(args.db, args.host, args.port, load_contract(args.contract),
                         admin_token=os.environ.get('XENDROID_COMMUNITY_ADMIN_TOKEN'),
                         trust_forwarded=args.trust_forwarded)
    host, port = server.server_address[:2]
    print('listening on http://%s:%d' % (host, port), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
