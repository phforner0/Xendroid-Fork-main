#!/usr/bin/env python3
"""Tests of the reference community server: python3 tools/community-server/test_server.py"""
import json
import os
import sqlite3
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import server  # noqa: E402

TITLE = '4D5307E6'


def upload(**changes):
    body = {
        'titleId': TITLE, 'name': 'Steady 30', 'note': 'Holds 30 FPS where 60 stutters',
        'result': 'PLAYABLE', 'settings': {'GPU|framerate_limit': '30', 'Console|widescreen': 'true'},
        'device': {'manufacturer': 'samsung', 'model': 'SM-S911B', 'soc': 'SM8550', 'gpu': 'Adreno (TM) 740',
                   'driver': 'Qualcomm 0.762', 'androidSdk': 34},
        'appVersionCode': 120, 'appBuild': '1.2.0',
    }
    body.update(changes)
    return body


class ServerTest(unittest.TestCase):
    # Generous by default: the protocol tests send many requests from one address.
    limits = {'upload': (1000, 3600), 'vote': (1000, 3600), 'delete': (1000, 3600)}

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.db = os.path.join(self.dir.name, 'community.sqlite3')
        self.server = server.make_server(self.db, port=0, limiter=server.RateLimiter(self.limits),
                                         admin_token='a' * 24, quiet=True)
        self.base = 'http://127.0.0.1:%d' % self.server.server_address[1]
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={'poll_interval': 0.05}, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.dir.cleanup()

    def call(self, method, path, body=None, headers=None, raw=None, content_type='application/json'):
        data = raw if raw is not None else (None if body is None else json.dumps(body).encode('utf-8'))
        request = urllib.request.Request(self.base + path, data=data, method=method)
        if data is not None:
            request.add_header('Content-Type', content_type)
        for key, value in (headers or {}).items():
            request.add_header(key, value)
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                text = response.read().decode('utf-8')
                return response.status, (json.loads(text) if text else None)
        except urllib.error.HTTPError as error:
            text = error.read().decode('utf-8')
            return error.code, (json.loads(text) if text else None)

    def share(self, **changes):
        status, body = self.call('POST', '/v1/configs', upload(**changes))
        self.assertEqual(201, status, body)
        return body

    def listing(self, title=TITLE):
        status, body = self.call('GET', '/v1/titles/%s/configs' % title)
        self.assertEqual(200, status)
        return body


class ProtocolTest(ServerTest):
    def test_health_names_the_protocol(self):
        self.assertEqual((200, {'format': 'xendroid-community-configs', 'version': 1}), self.call('GET', '/v1/health'))

    def test_a_shared_config_is_listed_without_its_token(self):
        receipt = self.share()
        self.assertRegex(receipt['id'], r'^[a-z0-9]{8,32}$')
        self.assertRegex(receipt['deleteToken'], r'^[A-Za-z0-9_-]{16,128}$')
        body = self.listing(TITLE.lower())
        self.assertEqual(('xendroid-community-configs', 1, TITLE), (body['format'], body['version'], body['titleId']))
        config = body['configs'][0]
        self.assertEqual(receipt['id'], config['id'])
        self.assertEqual({'GPU|framerate_limit': '30', 'Console|widescreen': 'true'}, config['settings'])
        self.assertEqual('SM8550', config['device']['soc'])
        self.assertRegex(config['createdAt'], r'^\d{4}-\d{2}-\d{2}$')
        self.assertEqual((0, 0), (config['votesUp'], config['votesDown']))
        self.assertNotIn('deleteToken', json.dumps(body))
        # Only a hash of the token is kept.
        stored = sqlite3.connect(self.db).execute('SELECT delete_token_sha256 FROM configs').fetchone()[0]
        self.assertNotEqual(receipt['deleteToken'], stored)
        self.assertEqual(64, len(stored))

    def test_another_game_lists_nothing(self):
        self.share()
        self.assertEqual([], self.listing('415607E6')['configs'])
        self.assertEqual(400, self.call('GET', '/v1/titles/00000000/configs')[0])
        self.assertEqual(404, self.call('GET', '/v1/titles/XYZ/configs')[0])

    def test_rules_of_a_config(self):
        refused = {
            'disallowed key': upload(settings={'Vulkan|vulkan_lib_path': '/sdcard/x.so'}),
            'unknown key': upload(settings={'GPU|no_such_thing': '1'}),
            'value with a space': upload(settings={'GPU|framerate_limit': '30 fps'}),
            'no settings': upload(settings={}),
            'too many settings': upload(settings={k: '1' for k in sorted(server.load_contract()['allowedKeys'])[:33]}),
            'title': upload(titleId='4D5307E'),
            'zero title': upload(titleId='00000000'),
            'blank name': upload(name='  '),
            'long name': upload(name='x' * 61),
            'long note': upload(note='x' * 501),
            'control character': upload(note='bell\x07'),
            'result': upload(result='GREAT'),
            'device field': upload(device={'model': 'x' * 65}),
            'unknown device field': upload(device={'imei': '123'}),
            'sdk': upload(device={'androidSdk': 120}),
            'version as text': upload(appVersionCode='120'),
            'version as bool': upload(appVersionCode=True),
        }
        for why, body in refused.items():
            status, answer = self.call('POST', '/v1/configs', body)
            self.assertEqual(400, status, why)
            self.assertTrue(answer['error'], why)
        extra = upload()
        extra['clientId'] = 'abc'
        self.assertEqual(400, self.call('POST', '/v1/configs', extra)[0])
        self.assertEqual(400, self.call('POST', '/v1/configs', raw=b'{"titleId":"4D5307E6","titleId":"4D5307E6"}')[0])
        self.assertEqual(400, self.call('POST', '/v1/configs', raw=b'not json')[0])
        self.assertEqual(415, self.call('POST', '/v1/configs', upload(), content_type='text/plain')[0])
        self.assertEqual(413, self.call('POST', '/v1/configs', upload(note='x' * 20000))[0])
        self.assertEqual([], self.listing()['configs'])

    def test_text_is_trimmed_and_a_title_uppercased(self):
        self.share(titleId=TITLE.lower(), name='  Steady 30  ', device={'model': ' SM-S911B '})
        config = self.listing()['configs'][0]
        self.assertEqual(('Steady 30', 'SM-S911B', ''), (config['name'], config['device']['model'], config['device']['gpu']))

    def test_one_vote_per_voter_and_taking_it_back(self):
        config = self.share()['id']
        a, b, c = '0' * 32, '1' * 32, 'f' * 32

        def vote(voter, value):
            status, body = self.call('POST', '/v1/configs/%s/vote' % config, {'voter': voter, 'vote': value})
            self.assertEqual(200, status, body)
            return body['votesUp'], body['votesDown']

        self.assertEqual((1, 0), vote(a, 1))
        self.assertEqual((1, 0), vote(a, 1))
        self.assertEqual((2, 0), vote(b, 1))
        self.assertEqual((2, 1), vote(c, -1))
        self.assertEqual((1, 2), vote(a, -1))
        self.assertEqual((1, 1), vote(a, 0))
        listed = self.listing()['configs'][0]
        self.assertEqual((1, 1), (listed['votesUp'], listed['votesDown']))
        self.assertEqual(404, self.call('POST', '/v1/configs/0123456789abcdef/vote', {'voter': a, 'vote': 1})[0])
        for body in ({'voter': 'xyz', 'vote': 1}, {'voter': a, 'vote': 2}, {'voter': a, 'vote': True}, {'voter': a}):
            self.assertEqual(400, self.call('POST', '/v1/configs/%s/vote' % config, body)[0], body)

    def test_best_voted_first(self):
        first = self.share(name='First')['id']
        second = self.share(name='Second')['id']
        self.call('POST', '/v1/configs/%s/vote' % second, {'voter': '2' * 32, 'vote': 1})
        self.call('POST', '/v1/configs/%s/vote' % first, {'voter': '2' * 32, 'vote': -1})
        self.assertEqual([second, first], [c['id'] for c in self.listing()['configs']])

    def test_only_the_token_or_a_moderator_deletes(self):
        receipt = self.share()
        path = '/v1/configs/%s' % receipt['id']
        self.call('POST', path + '/vote', {'voter': '3' * 32, 'vote': 1})
        self.assertEqual(403, self.call('DELETE', path)[0])
        self.assertEqual(403, self.call('DELETE', path, headers={'X-Delete-Token': 'x' * 32})[0])
        self.assertEqual(403, self.call('DELETE', path, headers={'X-Admin-Token': 'b' * 24})[0])
        self.assertEqual((204, None), self.call('DELETE', path, headers={'X-Delete-Token': receipt['deleteToken']}))
        self.assertEqual([], self.listing()['configs'])
        self.assertEqual(0, sqlite3.connect(self.db).execute('SELECT COUNT(*) FROM votes').fetchone()[0])
        self.assertEqual(404, self.call('DELETE', path, headers={'X-Delete-Token': receipt['deleteToken']})[0])
        other = self.share()['id']
        self.assertEqual(204, self.call('DELETE', '/v1/configs/%s' % other, headers={'X-Admin-Token': 'a' * 24})[0])

    def test_a_game_keeps_at_most_so_many_configs(self):
        old = server.MAX_PER_TITLE
        server.MAX_PER_TITLE = 2
        try:
            self.share()
            self.share()
            self.assertEqual(409, self.call('POST', '/v1/configs', upload())[0])
            self.share(titleId='415607E6')
        finally:
            server.MAX_PER_TITLE = old

    def test_unknown_paths(self):
        self.assertEqual(404, self.call('GET', '/v1/configs')[0])
        self.assertEqual(404, self.call('POST', '/v1/titles/%s/configs' % TITLE, upload())[0])
        self.assertEqual(404, self.call('DELETE', '/v1/configs/../x', headers={'X-Delete-Token': 'x' * 32})[0])


class RateLimitTest(ServerTest):
    limits = {'upload': (2, 3600), 'vote': (1, 3600), 'delete': (1, 3600)}

    def test_shares_and_votes_per_address_are_limited(self):
        config = self.share()['id']
        self.share()
        self.assertEqual(429, self.call('POST', '/v1/configs', upload())[0])
        self.assertEqual(200, self.call('POST', '/v1/configs/%s/vote' % config, {'voter': '4' * 32, 'vote': 1})[0])
        self.assertEqual(429, self.call('POST', '/v1/configs/%s/vote' % config, {'voter': '5' * 32, 'vote': 1})[0])

    def test_the_window_slides(self):
        now = [0.0]
        limiter = server.RateLimiter({'upload': (1, 10)}, clock=lambda: now[0])
        self.assertTrue(limiter.allow('upload', 'a'))
        self.assertFalse(limiter.allow('upload', 'a'))
        self.assertTrue(limiter.allow('upload', 'b'))
        now[0] = 10.5
        self.assertTrue(limiter.allow('upload', 'a'))


class ContractTest(unittest.TestCase):
    def test_contract(self):
        contract = server.load_contract()
        self.assertEqual(('xendroid-community-configs', 1), (contract['format'], contract['version']))
        self.assertIn('GPU|framerate_limit', contract['allowedKeys'])
        self.assertNotIn('Vulkan|vulkan_lib_path', contract['allowedKeys'])
        self.assertEqual({'NOTHING', 'BOOTS', 'INTRO', 'IN_GAME', 'PLAYABLE'}, contract['results'])


if __name__ == '__main__':
    unittest.main()
