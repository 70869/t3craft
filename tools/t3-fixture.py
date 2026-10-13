#!/usr/bin/env python3
"""Adapted from maxwellyoung/t3craft under licenses/t3craft-client-MIT.txt.
Two loopback-only T3 fixtures. No credentials, agent calls, or real repo writes.

Used by `featureCheck` and the `desk-review` Minecraft self-test.
"""
import argparse
import base64
import hashlib
import json
import socket
import socketserver
import struct
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs

PATCH = '''diff --git a/src/greeting.ts b/src/greeting.ts
index 1111111..2222222 100644
--- a/src/greeting.ts
+++ b/src/greeting.ts
@@ -1 +1,2 @@
-export const greeting = "hello";
+export const greeting = "hello, Minecraft";
+export const reviewed = true;
@@ -20,2 +21,2 @@
 unchanged context
-old footer
+new footer
diff --git a/obsolete.txt b/obsolete.txt
deleted file mode 100644
--- a/obsolete.txt
+++ /dev/null
@@ -1 +0,0 @@
-old text
'''


def thread(ident, waiting=False, question=False, older=False):
    now = '2026-10-04T00:00:00Z' if older else datetime.now(timezone.utc).isoformat()
    payload = {'requestId': 'shared-request', 'requestKind': 'Bash', 'detail': 'npm test -- --runInBand'}
    kind = 'approval.requested'
    if question:
        kind = 'user-input.requested'
        payload = {'requestId': 'shared-request', 'questions': [{'id': 'format', 'question': 'Which export format should we use?', 'options': [{'label': 'JSON'}, {'label': 'CSV'}], 'allowCustomAnswer': True}]}
    return {'id': ident, 'projectId': 'fixture-project', 'title': 'Review greeting changes' if not waiting else 'Export format' if question else 'Run greeting tests',
            'updatedAt': now, 'archivedAt': None, 'latestTurn': {'turnId': ident + '-turn', 'state': 'completed', 'requestedAt': now},
            'modelSelection': {'instanceId': 'fixture', 'model': 'fixture'}, 'runtimeMode': 'approval-required', 'interactionMode': 'default',
            'hasPendingApprovals': waiting and not question, 'hasPendingUserInput': waiting and question,
            'messages': [{'id': ident + '-reply', 'role': 'assistant', 'text': 'Updated the greeting and removed the obsolete file. Ready for your review.', 'streaming': False}, {'id': ident + '-newer-reply', 'role': 'assistant', 'text': 'A later unrelated reply.', 'streaming': False}],
            'activities': [{'kind': kind, 'createdAt': now, 'payload': payload}] if waiting else [{'kind': 'command.completed', 'summary': 'Fixture command completed (exit 1)', 'createdAt': now, 'payload': {'exitCode': 1}}],
            'checkpoints': [{'checkpointTurnCount': 1, 'status': 'ready', 'assistantMessageId': ident + '-reply', 'files': [{'path': 'src/greeting.ts'}, {'path': 'obsolete.txt'}]}]}


def shell_thread(value):
    pending = {'id': 'shared-request', 'kind': 'user_input' if value['hasPendingUserInput'] else 'command', 'createdAt': value['updatedAt']} if value['hasPendingApprovals'] or value['hasPendingUserInput'] else None
    status = value.get('studioState', 'completed')
    return {**value, 'status': status, 'activeRunId': value['id'] + '-turn' if status == 'running' else None, 'latestRunId': value['id'] + '-turn', 'latestRunRequestedAt': value['updatedAt'], 'latestRunStartedAt': value['updatedAt'], 'pendingRuntimeRequest': pending}


def projection(value):
    ident = value['id']; run_id = ident + '-turn'; node_id = ident + '-node'
    requests = []; items = []
    if value['hasPendingApprovals'] or value['hasPendingUserInput']:
        question = value['activities'][0]['kind'] == 'user-input.requested'
        resolved = any(a['kind'].endswith('.resolved') for a in value['activities'])
        requests = [{'id': 'shared-request', 'nodeId': node_id, 'kind': 'user_input' if question else 'command', 'status': 'resolved' if resolved else 'pending', 'responseCapability': {'type': 'live', 'providerSessionId': 'fixture-session'}, 'createdAt': value['updatedAt']}]
        payload = value['activities'][0]['payload']
        items = [{'type': 'user_input_request' if question else 'approval_request', 'requestId': 'shared-request', 'nodeId': node_id, 'questions': payload.get('questions', []), 'prompt': payload.get('detail'), 'requestKind': 'command'}]
    items += [{'id': ident + '-command', 'type': 'command_execution', 'status': 'completed', 'input': 'npm test', 'exitCode': 1, 'startedAt': value['updatedAt'], 'ordinal': 2}, {'id': ident + '-unknown', 'type': 'future_tool', 'title': 'Future backend tool', 'status': 'running', 'startedAt': value['updatedAt'], 'ordinal': 3}]
    if value.get('workItem'):
        items.append({**value['workItem'], 'id': ident + '-active-work', 'runId': run_id, 'status': 'running', 'startedAt': value['updatedAt'], 'ordinal': 4})
    messages = [{**m, 'runId': run_id if i == 0 else 'unrelated-run', 'createdAt': value['updatedAt']} for i, m in enumerate(value['messages'])]
    return {'thread': shell_thread(value), 'runs': [{'id': run_id, 'rootNodeId': node_id, 'ordinal': 1, 'status': value.get('studioState', 'completed'), 'requestedAt': value['updatedAt'], 'startedAt': value['updatedAt']}] + value.get('queuedRuns', []), 'subagents': value.get('subagents', []), 'messages': messages, 'runtimeRequests': requests, 'turnItems': items,
            'checkpoints': [{'appRunOrdinal': 1, 'runId': run_id, 'capturedAt': value['updatedAt'], 'status': 'ready', 'files': value['checkpoints'][0]['files']}] if value['checkpoints'] else []}


class Fixture(ThreadingHTTPServer):
    daemon_threads = True
    def server_bind(self):
        # This fixture is loopback-only. HTTPServer's reverse DNS lookup can stall
        # on hosted macOS runners before either server starts accepting requests.
        socketserver.TCPServer.server_bind(self)
        self.server_name = 'localhost'
        self.server_port = self.server_address[1]

    def handle_error(self, request, client_address):
        if isinstance(sys.exc_info()[1], (ConnectionResetError, BrokenPipeError)): return
        super().handle_error(request, client_address)
    def __init__(self, port, machine, protocol2=False):
        super().__init__(('127.0.0.1', port), Handler)
        self.machine = machine
        self.protocol2 = protocol2
        self.lock = threading.RLock()
        self.dispatches = []
        self.connections = []
        self.subscribers = {}
        self.offline = False
        self.auth_failed = False
        self.protocol_version = 2 if self.protocol2 else 1
        self.attachment_uploads = self.protocol2
        self.file_attachment_bytes = 50 * 1024 * 1024 if self.protocol2 else 0
        self.reset()

    def reset(self):
        self.threads = {f'{self.machine}-done-{i}': thread(f'{self.machine}-done-{i}') for i in range(12)}
        self.threads[f'{self.machine}-waiting'] = thread(f'{self.machine}-waiting', True, self.machine == 'B', True)
        self.threads[f'{self.machine}-done-11']['projectId'] = 'fixture-alt'
        self.dispatches.clear()
        self.commands = set()
        self.fail_next = self.lose_ack = False
        self.rpcs = []
        self.schedules = {}
        self.uploads = {}
        self.terminals = {}
        self.terminal_subscribers = {}
        self.terminal_failure = False
        self.pairing_scopes = []
        self.offline = False
        self.auth_failed = False
        self.protocol_version = 2 if self.protocol2 else 1
        self.attachment_uploads = self.protocol2
        self.file_attachment_bytes = 50 * 1024 * 1024 if self.protocol2 else 0

    def shell(self):
        return {'schemaVersion': 2 if self.protocol2 else 1, 'projects': [{'id': 'fixture-project', 'title': 'Greeting fixture', 'workspaceRoot': '/fixture'}, {'id': 'fixture-alt', 'title': 'Second project', 'workspaceRoot': '/fixture-alt'}], 'threads': [shell_thread(t) if self.protocol2 else t for t in self.threads.values() if not t.get('archivedAt')]}


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *_): pass

    def reply(self, value, code=200):
        body = json.dumps(value).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        with self.server.lock:
            if self.path == '/_qa/shelves':
                now = datetime.now(timezone.utc).isoformat()
                settled = self.server.threads[self.server.machine + '-done-0']
                settled.update(settledOverride='settled', settledAt=now)
                working = self.server.threads[self.server.machine + '-done-1']
                working.update(studioState='running', hasPendingApprovals=False, hasPendingUserInput=False, workItem={'type': 'file_change', 'fileName': 'fixture.txt'})
                self.broadcast_snapshot(); return self.reply({'ok': True})
            if self.path.startswith('/_qa/leisure?'):
                ident = parse_qs(self.path.split('?', 1)[1]).get('thread', [self.server.machine + '-done-0'])[0]
                if ident not in self.server.threads: return self.reply({'message': 'Unknown fixture thread'}, 400)
                value = thread(ident); value.update(studioState='completed', checkpoints=[], activities=[], updatedAt=datetime.now(timezone.utc).isoformat())
                self.server.threads[ident] = value; self.broadcast_snapshot(); return self.reply({'ok': True})
            if self.path == '/_qa/terminal-failure':
                self.server.terminal_failure = True; return self.reply({'ok': True})
            if self.path == '/_qa/terminal-recover':
                self.server.terminal_failure = False; return self.reply({'ok': True})
            if self.path == '/_qa/workflow':
                value = self.server.threads[self.server.machine + '-done-0']; value['studioState'] = 'running'; value['queuedRuns'] = []
                for i in range(2):
                    ident = f'queued-{i}'; value['queuedRuns'].append({'id': ident, 'ordinal': i + 2, 'status': 'queued', 'requestedAt': value['updatedAt'], 'startedAt': None, 'queuePosition': i, 'userMessageId': ident + '-message'})
                    value['messages'].append({'id': ident + '-message', 'role': 'user', 'text': 'Queued fixture ' + str(i), 'streaming': False})
                self.broadcast_snapshot(); return self.reply({'ok': True})
            if self.path in ('/_qa/fail-next', '/_qa/lose-ack'):
                if self.path.endswith('fail-next'): self.server.fail_next = True
                else: self.server.lose_ack = True
                return self.reply({'ok': True})
            if self.path == '/_qa/rpcs': return self.reply(self.server.rpcs)
            if self.path == '/_qa/pairing-scopes': return self.reply(self.server.pairing_scopes)
            if self.path.startswith('/_qa/npc?'):
                query = parse_qs(self.path.split('?', 1)[1]); ident = query.get('thread', [self.server.machine + '-done-0'])[0]
                activity = query.get('activity', ['running'])[0]
                work = {'reading': {'type': 'file_search'}, 'editing': {'type': 'file_change', 'fileName': 'fixture.txt'},
                        'running': {'type': 'command_execution', 'input': 'npm run build'}, 'testing': {'type': 'command_execution', 'input': 'npm test'},
                        'reviewing': {'type': 'command_execution', 'input': 'git diff --stat'}, 'thinking': {'type': 'reasoning'}}
                if ident not in self.server.threads or activity not in work: return self.reply({'message': 'Invalid NPC fixture'}, 400)
                value = self.server.threads[ident]; value.update(studioState='running', hasPendingApprovals=False, hasPendingUserInput=False, workItem=work[activity])
                value['activities'] = []
                value['subagents'] = [{'id': 'npc-child-task', 'title': 'Fixture delegated task', 'status': 'running', 'progress': 'Reading fixture sources', 'origin': 'app_owned', 'childThreadId': 'npc-child-chat'}]
                self.broadcast_snapshot(); return self.reply({'ok': True, 'activity': activity})
            if self.path.startswith('/_qa/question?'):
                query = parse_qs(self.path.split('?', 1)[1]); mode = query.get('mode', ['simple'])[0]
                ident = query.get('thread', [self.server.machine + '-done-0'])[0]
                if ident not in self.server.threads or mode not in ('simple', 'multi', 'long', 'free', 'withdraw'):
                    return self.reply({'message': 'Invalid question scenario'}, 400)
                value = thread(ident, waiting=True, question=True); value['studioState'] = 'running'
                questions = value['activities'][0]['payload']['questions']
                questions[0].update(header='Export format', options=[
                    {'label': 'JSON (Recommended)', 'value': 'json', 'description': 'Matches the existing storage format and keeps integrations simple.'},
                    {'label': 'Markdown', 'value': 'markdown', 'description': 'Easy to read, edit and share with your team.'}])
                if mode == 'multi':
                    questions[0]['multiSelect'] = True
                    questions.append({'id': 'audience', 'header': 'Audience', 'question': 'Who will use these exports?', 'options': [], 'allowCustomAnswer': True})
                if mode == 'free': questions[0]['options'] = []
                if mode == 'long':
                    questions[0]['question'] = ('Which export format should we use for the workspace reports? Please consider how people will read, edit and share the files. ' * 4).strip()
                    questions[0]['options'] = [{'label': f'Format {i + 1}', 'value': str(i), 'description': 'A detailed explanation of this choice, including its compatibility and how it affects the export workflow.'} for i in range(12)]
                if mode == 'withdraw': value['activities'].append({'kind': 'user-input.resolved', 'payload': {'requestId': 'shared-request'}})
                self.server.threads[ident] = value; self.broadcast_snapshot()
                return self.reply({'ok': True, 'thread': ident, 'mode': mode})
            if self.path.startswith('/_qa/studio?'):
                query = parse_qs(self.path.split('?', 1)[1]); state = query.get('state', ['completed'])[0]
                ident = query.get('thread', [self.server.machine + '-done-0'])[0]
                if state not in ('running', 'waiting', 'completed', 'failed') or ident not in self.server.threads:
                    return self.reply({'message': 'Invalid fixture transition'}, 400)
                value = thread(ident, waiting=state == 'waiting')
                value['studioState'] = 'running' if state == 'waiting' else state
                value['latestTurn']['state'] = value['studioState']
                if state == 'failed': value['lastError'] = 'Fixture run failed'
                self.server.threads[ident] = value; self.broadcast_snapshot()
                return self.reply({'ok': True, 'thread': ident, 'state': state})
            if self.path == '/_qa/reply':
                target=self.server.threads[self.server.machine+'-done-0']
                target['messages'].append({'id':'studio-new-reply','role':'assistant','text':'The T3 studio reply is ready.','streaming':False})
                self.broadcast_snapshot(); return self.reply({'ok':True})
            if self.path == '/.well-known/t3/environment':
                capabilities = {'attachmentUploads': self.server.attachment_uploads}
                if self.server.file_attachment_bytes > 0: capabilities['fileAttachments'] = {'maxUploadBytes': self.server.file_attachment_bytes}
                return self.reply({'label': 'Fixture ' + self.server.machine, 'orchestrationProtocolVersion': self.server.protocol_version, 'capabilities': capabilities})
            if self.path == '/_qa/no-file-attachments': self.server.file_attachment_bytes = 0; return self.reply({'ok': True})
            if self.path == '/_qa/small-file-limit': self.server.file_attachment_bytes = 3; return self.reply({'ok': True})
            if self.path == '/_qa/enable-file-attachments': self.server.file_attachment_bytes = 50 * 1024 * 1024; return self.reply({'ok': True})
            if self.path == '/_qa/invalid-protocol-metadata': self.server.protocol_version = 'v2'; return self.reply({'ok': True})
            if self.path == '/_qa/restore-protocol-metadata': self.server.protocol_version = 2 if self.server.protocol2 else 1; return self.reply({'ok': True})
            if self.path in ('/_qa/protocol3', '/_qa/auth-failed'):
                if self.path == '/_qa/protocol3': self.server.protocol_version = 3
                else: self.server.auth_failed = True
                for connection in list(self.server.connections):
                    try: connection.shutdown(socket.SHUT_RDWR)
                    except OSError: pass
                return self.reply({'ok': True})
            if self.path == '/_qa/dispatches': return self.reply(self.server.dispatches)
            if self.path == '/_qa/reset': self.server.reset(); self.broadcast_snapshot(); return self.reply({'ok': True})
            if self.path == '/_qa/empty': self.server.threads.clear(); self.broadcast_snapshot(); return self.reply({'ok': True})
            if self.path == '/_qa/collision':
                self.server.threads['A-done-0'] = thread('A-done-0')
                self.broadcast_snapshot()
                return self.reply({'ok': True})
            if self.path == '/_qa/churn':
                self.server.threads[f'{self.server.machine}-done-0']['updatedAt'] = '2020-01-01T00:00:00Z'
                for i in range(6):
                    ident = f'{self.server.machine}-new-{i}'
                    self.server.threads[ident] = thread(ident)
                self.broadcast_snapshot()
                return self.reply({'ok': True})
            if self.path == '/_qa/archive-pin':
                target = self.server.threads[f'{self.server.machine}-done-0']
                target['archivedAt'] = datetime.now(timezone.utc).isoformat()
                self.broadcast_snapshot()
                return self.reply({'ok': True})
            if self.path == '/_qa/offline':
                self.server.offline = True
                for connection in list(self.server.connections):
                    try: connection.shutdown(socket.SHUT_RDWR)
                    except OSError: pass
                return self.reply({'ok': True})
            if self.path == '/_qa/resolve':
                target = self.server.threads[f'{self.server.machine}-waiting']
                target['activities'].append({'kind': 'user-input.resolved' if self.server.machine == 'B' else 'approval.resolved', 'payload': {'requestId': 'shared-request'}})
                # Deliberately retain stale shell flags: response code must revalidate activities.
                return self.reply({'ok': True})
            if self.server.offline: return self.reply({'message': 'Fixture offline'}, 503)
            if self.server.auth_failed: return self.reply({'message': 'Device access denied'}, 401)
            if self.path.startswith('/api/orchestration/') and self.headers.get('x-t3-orchestration-protocol') != str(self.server.protocol_version): return self.reply({'message': 'Wrong protocol header'}, 400)
            if self.path.startswith('/ws?'):
                if self.server.protocol2 and 'orchestrationProtocol=2' not in self.path: return self.reply({'message': 'Missing websocket protocol'}, 400)
            elif self.path == '/api/orchestration/shell': return self.reply(self.server.shell())
            elif self.path.startswith('/api/orchestration/threads/'):
                if self.server.protocol2 != (self.path.endswith('/bounded') or '/history?' in self.path): return self.reply({'message': 'Wrong thread endpoint'}, 404)
                ident = self.path.split('/threads/')[1].split('?')[0].split('/')[0]
                value = self.server.threads[ident]
                return self.reply({'projection': projection(value), 'hasMoreHistory': False} if self.server.protocol2 else {'thread': value})
            else: return self.reply({'message': 'Unknown route'}, 404)
        self.websocket()

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        if self.path == '/oauth/token' and parse_qs(body.decode()).get('subject_token', [''])[0] == 'delayed-fixture': time.sleep(3)
        with self.server.lock:
            if self.server.offline: return self.reply({'message': 'Fixture offline'}, 503)
            if self.path == '/oauth/token':
                token = parse_qs(body.decode()).get('subject_token', [''])[0]
                if token == 'expired-fixture': return self.reply({'message': 'Do not expose this credential: ' + token}, 401)
                if token not in ('fixture-pairing', 'delayed-fixture'): return self.reply({'message': 'Invalid token: ' + token}, 400)
                self.server.pairing_scopes = parse_qs(body.decode()).get('scope', [''])[0].split()
                self.server.auth_failed = False
                return self.reply({'access_token': 'fixture-only', 'expires_in': 3600})
            if self.server.auth_failed: return self.reply({'message': 'Device access denied'}, 401)
            if self.path == '/api/auth/websocket-ticket': return self.reply({'ticket': 'fixture-only'})
            if self.path == '/api/orchestration/dispatch':
                command = json.loads(body)
                self.dispatch(command)
                return self.reply({'sequence': len(self.server.dispatches)})
        self.reply({'message': 'Unknown route'}, 404)

    def broadcast_snapshot(self):
        for handler, ident in list(self.server.subscribers.items()):
            try: handler.frame({'_tag': 'Chunk', 'requestId': ident, 'values': [{'kind': 'snapshot', 'snapshot': self.server.shell()}]})
            except OSError: pass

    def dispatch(self, command):
        ident = command['commandId']
        if ident in self.server.commands: return
        if self.server.fail_next:
            self.server.fail_next = False
            raise ValueError('Fixture rejected command before acceptance')
        allowed = ('runtime-request.respond', 'thread.create', 'message.dispatch', 'thread.model-selection.set', 'thread.metadata.update', 'thread.interaction-mode.set', 'thread.runtime-mode.set', 'thread.archive', 'thread.unarchive', 'thread.fork', 'queued-run.edit', 'queued-run.cancel', 'queued-run.reorder', 'queued-message.promote-to-steer', 'queue.resume', 'run.interrupt', 'delegated_task.request', 'delegated_task.completion-delivery.dispose', 'thread.pull-request.link', 'thread.pull-request.watch')
        if self.server.protocol2 and (command['type'] not in allowed or 'createdAt' in command): raise ValueError('Invalid protocol 2 command')
        if command['type'].startswith('delegated_task.') and 'threadId' in command: raise ValueError('Delegation must use parent identity')
        self.server.commands.add(ident)
        self.server.dispatches.append(command)
        kind = command['type']
        if kind == 'thread.create':
            value = thread(command['threadId']); value.update({k: v for k, v in command.items() if k in ('title', 'projectId', 'modelSelection', 'runtimeMode', 'interactionMode', 'branch', 'worktreePath')}); value['messages'] = []; self.server.threads[value['id']] = value
        elif kind == 'thread.fork':
            value = json.loads(json.dumps(self.server.threads[command['sourceThreadId']])); value['id'] = command['targetThreadId']; self.server.threads[value['id']] = value
        elif kind in ('thread.archive', 'thread.unarchive'): self.server.threads[command['threadId']]['archivedAt'] = datetime.now(timezone.utc).isoformat() if kind.endswith('.archive') else None
        elif kind == 'message.dispatch':
            target = self.server.threads[command['threadId']]; target['messages'].append({'id': command['messageId'], 'role': 'user', 'text': command['text'], 'attachments': command['attachments'], 'streaming': False})
        elif kind == 'thread.metadata.update': self.server.threads[command['threadId']].update({k: v for k, v in command.items() if k in ('title', 'branch', 'worktreePath')})
        elif kind == 'thread.model-selection.set': self.server.threads[command['threadId']]['modelSelection'] = command['modelSelection']
        elif kind in ('thread.runtime-mode.set', 'thread.interaction-mode.set'):
            field = 'runtimeMode' if 'runtime-mode' in kind else 'interactionMode'; self.server.threads[command['threadId']][field] = command[field]
        elif kind.startswith('queued-run.') or kind == 'queued-message.promote-to-steer':
            target = self.server.threads[command['threadId']]; runs = target.get('queuedRuns', []); ident = command.get('runId', command.get('queuedRunId')); chosen = next(r for r in runs if r['id'] == ident)
            if kind == 'queued-run.edit': next(m for m in target['messages'] if m['id'] == chosen['userMessageId'])['text'] = command['text']
            elif kind in ('queued-run.cancel', 'queued-message.promote-to-steer'): runs.remove(chosen)
            else:
                runs.remove(chosen); before = command['beforeRunId']; at = next((i for i, r in enumerate(runs) if r['id'] == before), len(runs)); runs.insert(at, chosen)
            for i, run in enumerate(runs): run['queuePosition'] = i
        elif kind == 'delegated_task.request':
            parent = self.server.threads[command['parentThreadId']]; child = thread('child-' + ident); child['studioState'] = 'running'; self.server.threads[child['id']] = child
            parent.setdefault('subagents', []).append({'id': ident, 'origin': 'app_owned', 'childThreadId': child['id'], 'parentThreadId': parent['id'], 'parentNodeId': command['parentNodeId'], 'title': command['task'], 'status': 'running', 'progress': 'Fixture child running', 'result': None})
        elif kind == 'run.interrupt': self.server.threads[command['threadId']]['studioState'] = 'completed'
        elif kind == 'delegated_task.completion-delivery.dispose':
            parent = self.server.threads[command['parentThreadId']]; next(t for t in parent.get('subagents', []) if t['id'] == command['taskId'])['status'] = 'cancelled'
        if kind != 'runtime-request.respond': self.broadcast_snapshot(); return
        target = self.server.threads[command['threadId']]
        target['activities'].append({'kind': 'approval.resolved' if command.get('decision') else 'user-input.resolved', 'payload': {'requestId': command['requestId']}})
        target['hasPendingApprovals'] = target['hasPendingUserInput'] = False
        for handler, ident in list(self.server.subscribers.items()):
            try: handler.frame({'_tag': 'Chunk', 'requestId': ident, 'values': [{'kind': 'thread.updated' if self.server.protocol2 else 'thread-upserted', 'thread': shell_thread(target) if self.server.protocol2 else target}]})
            except OSError: pass

    def rpc(self, tag, p):
        self.server.rpcs.append({'method': tag, 'payload': p})
        if tag == 'server.getConfig': return {'providers': [{'instanceId': 'fixture', 'driver': 'codex', 'displayName': 'Fixture provider', 'enabled': True, 'status': 'ready', 'models': [{'slug': 'fixture', 'name': 'Fixture model', 'capabilities': {'optionDescriptors': [{'id': 'reasoning', 'label': 'Reasoning', 'type': 'select', 'options': [{'id': 'high', 'label': 'High'}, {'id': 'low', 'label': 'Low'}]}, {'id': 'fast', 'label': 'Fast', 'type': 'boolean'}]}}]}]}
        if tag in ('orchestration.getFullThreadDiff', 'orchestration.getTurnDiff'): return {'threadId': p['threadId'], 'fromTurnCount': p.get('fromTurnCount', 0), 'toTurnCount': p['toTurnCount'], 'diff': PATCH}
        if tag == 'orchestration.getThreadProjection': return {'projection': projection(self.server.threads[p['threadId']])}
        if tag == 'orchestration.getArchivedShellSnapshot': return {'threads': [shell_thread(t) for t in self.server.threads.values() if t.get('archivedAt')]}
        if tag == 'orchestration.launchThread':
            ident = p['threadId']
            if ident not in self.server.threads:
                value = thread(ident); value.update({k: p[k] for k in ('title', 'projectId', 'modelSelection', 'runtimeMode', 'interactionMode')}); strategy = p['workspaceStrategy']; value['branch'] = strategy.get('branch'); value['worktreePath'] = strategy.get('worktreePath', '/fixture-worktree' if strategy['type'] == 'worktree' else None); self.server.threads[ident] = value; self.broadcast_snapshot()
            return {'threadId': ident, 'workspacePreparation': {'status': 'ready'}}
        if tag == 'attachments.createUploadUrl':
            ident = 'upload-' + str(len(self.server.uploads)); self.server.uploads[ident] = p; return {'attachmentId': ident, 'relativeUrl': '/api/attachments/upload/' + ident, 'expiresAt': 9999999999999}
        if tag == 'vcs.listRefs': return {'refs': [{'name': 'main', 'worktreePath': '/fixture'}]}
        if tag == 'vcs.createWorktree': return {'worktree': {'path': p.get('path') or '/fixture-worktree', 'refName': p['newRefName']}}
        if tag == 'vcs.refreshStatus': return {'refName': 'main', 'workingTree': {'files': []}, 'pr': None}
        if tag == 'git.runStackedAction': return {p['action'].replace('create_', ''): {'status': 'created' if p['action'] == 'create_pr' else 'success', 'url': 'https://github.com/fixture/repo/pull/1' if p['action'] == 'create_pr' else ''}}
        if tag == 'scheduledTasks.list': return {'tasks': list(self.server.schedules.values())}
        if tag == 'scheduledTasks.upsert':
            ident = p.get('id', p['commandId']); value = {**p, 'id': ident, 'nextRunAt': '2026-10-06T09:00:00Z', 'lastRunStatus': 'idle'}; self.server.schedules[ident] = value; return {'task': value}
        if tag.startswith('scheduledTasks.'):
            task = self.server.schedules[p['id']]
            if tag.endswith('setEnabled'): task['enabled'] = p['enabled']
            elif tag.endswith('delete'): self.server.schedules.pop(p['id'])
            elif tag.endswith('runNow'): task['lastRunStatus'] = 'running'
            else: raise ValueError('Unknown scheduler method')
            return {}
        if tag == 'pullRequests.detail': return {'title': 'Fixture PR', 'body': 'Isolated PR', 'state': 'open'}
        if tag == 'pullRequests.checks': return {'state': 'passing', 'checks': [{'name': 'Fixture CI', 'status': 'completed'}]}
        if tag in ('pullRequests.update', 'pullRequests.submitReview', 'pullRequests.runAction'): return {}
        if tag in ('terminal.open', 'terminal.restart'):
            key = p['threadId'] + '|' + p['terminalId']
            if tag == 'terminal.open' and key in self.server.terminals: return self.server.terminals[key]
            value = {'threadId': p['threadId'], 'terminalId': p['terminalId'], 'cwd': p['cwd'], 'status': 'running', 'history': 'Fixture shell ready\r\n$ '}
            self.server.terminals[key] = value
            self.terminal_event(key, {'type': 'restarted' if tag == 'terminal.restart' else 'started', 'snapshot': value})
            return value
        if tag == 'terminal.write' and getattr(self.server, 'terminal_failure', False): raise ValueError('Fixture terminal delivery failed; input should remain')
        if tag == 'terminal.write':
            key = p['threadId'] + '|' + p['terminalId']; value = self.server.terminals[key]
            if value['status'] != 'running': raise ValueError('Terminal is not running')
            data = p['data']
            if data == '\x03': output = '^C\r\n$ '
            elif data == 'exit\r':
                value['status'] = 'exited'; self.terminal_event(key, {'type': 'exited', 'exitCode': 0}); return None
            else: output = data.replace('\r', '\r\n') + ('fixture-output\r\n$ ' if data.endswith('\r') else '')
            value['history'] += output; self.terminal_event(key, {'type': 'output', 'data': output}); return None
        if tag == 'terminal.resize': return None
        if tag == 'terminal.close':
            key = p['threadId'] + '|' + p['terminalId']; self.server.terminals.pop(key, None)
            self.terminal_event(key, {'type': 'closed'}); return None
        raise ValueError('Unexpected RPC: ' + tag)

    def do_PUT(self):
        ident = self.path.rsplit('/', 1)[-1]
        data = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        if not self.path.startswith('/api/attachments/upload/') or ident not in self.server.uploads: return self.reply({'message': 'Unknown upload'}, 404)
        if len(data) != self.server.uploads[ident]['sizeBytes']: return self.reply({'message': 'Wrong upload size'}, 400)
        self.reply({'ok': True})

    def terminal_event(self, key, event):
        thread_id, terminal_id = key.split('|', 1)
        value = dict(event, threadId=thread_id, terminalId=terminal_id)
        for handler, (ident, subscribed) in list(self.server.terminal_subscribers.items()):
            if subscribed == key:
                try: handler.frame({'_tag': 'Chunk', 'requestId': ident, 'values': [value]})
                except OSError: self.server.terminal_subscribers.pop(handler, None)

    def frame(self, obj):
        data = json.dumps(obj).encode()
        length = len(data)
        header = bytes([0x81, length]) if length < 126 else bytes([0x81, 126]) + struct.pack('!H', length) if length < 65536 else bytes([0x81, 127]) + struct.pack('!Q', length)
        with self.frame_lock:
            self.wfile.write(header + data)
            self.wfile.flush()

    def websocket(self):
        self.frame_lock = threading.Lock()
        key = self.headers['Sec-WebSocket-Key']
        accept = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
        self.send_response(101)
        self.send_header('Upgrade', 'websocket')
        self.send_header('Connection', 'Upgrade')
        self.send_header('Sec-WebSocket-Accept', accept)
        self.end_headers()
        self.server.connections.append(self.connection)
        self.close_connection = True
        try:
            while True:
                header = self.rfile.read(2)
                if len(header) != 2 or header[0] & 15 == 8: break
                length = header[1] & 127
                if length == 126: length = struct.unpack('!H', self.rfile.read(2))[0]
                elif length == 127: length = struct.unpack('!Q', self.rfile.read(8))[0]
                mask = self.rfile.read(4) if header[1] & 128 else None
                data = self.rfile.read(length)
                if mask: data = bytes(v ^ mask[i % 4] for i, v in enumerate(data))
                request = json.loads(data)
                if request.get('_tag') == 'Ping': self.frame({'_tag': 'Pong'}); continue
                if request.get('_tag') == 'Interrupt':
                    current = self.server.terminal_subscribers.get(self)
                    if current and current[0] == request['requestId']: self.server.terminal_subscribers.pop(self, None)
                    continue
                if request.get('_tag') != 'Request': continue
                ident, tag = request['id'], request['tag']
                if tag == 'orchestration.subscribeShell':
                    self.server.subscribers[self] = ident
                    self.frame({'_tag': 'Chunk', 'requestId': ident, 'values': [{'kind': 'snapshot', 'snapshot': self.server.shell()}, {'kind': 'synchronized'}]})
                    if self.server.protocol2:
                        metadata = {'kind': 'snapshot', 'snapshot': {'projects': self.server.shell()['projects'], 'threads': [], 'archivedThreads': []}, 'resolvedRepositoryIdentityRoots': ['/fixture']}
                        self.frame({'_tag': 'Chunk', 'requestId': ident, 'values': [metadata]})
                elif tag == 'orchestration.subscribeThread': pass
                elif tag == 'orchestration.dispatchCommand':
                    command = request['payload']
                    try:
                        with self.server.lock: self.dispatch(command)
                    except ValueError as e:
                        self.frame({'_tag': 'Exit', 'requestId': ident, 'exit': {'_tag': 'Failure', 'cause': str(e)}}); continue
                    if self.server.lose_ack:
                        self.server.lose_ack = False; self.connection.shutdown(socket.SHUT_RDWR); break
                    self.frame({'_tag': 'Exit', 'requestId': ident, 'exit': {'_tag': 'Success', 'value': {'sequence': len(self.server.dispatches)}}})
                elif tag == 'terminal.attach':
                    key = request['payload']['threadId'] + '|' + request['payload']['terminalId']
                    self.server.terminal_subscribers[self] = (ident, key)
                    value = self.server.terminals[key]
                    self.frame({'_tag': 'Chunk', 'requestId': ident, 'values': [{'type': 'snapshot', 'snapshot': value}]})
                else:
                    try:
                        with self.server.lock: value = self.rpc(tag, request['payload'])
                    except (ValueError, KeyError) as e:
                        self.frame({'_tag': 'Exit', 'requestId': ident, 'exit': {'_tag': 'Failure', 'cause': str(e)}}); continue
                    self.frame({'_tag': 'Exit', 'requestId': ident, 'exit': {'_tag': 'Success', 'value': value}})
        except (OSError, ValueError): pass
        finally:
            self.server.subscribers.pop(self, None)
            self.server.terminal_subscribers.pop(self, None)
            if self.connection in self.server.connections: self.server.connections.remove(self.connection)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=25680)
    parser.add_argument('--config', type=Path)
    parser.add_argument('--protocol2', action='store_true')
    args = parser.parse_args()
    servers = [Fixture(args.port, 'A', args.protocol2), Fixture(args.port + 1, 'B', args.protocol2)]
    if args.config:
        args.config.parent.mkdir(parents=True, exist_ok=True)
        args.config.write_text(json.dumps({'environments': [{'label': 'Fixture ' + s.machine, 'baseUrl': f'http://127.0.0.1:{s.server_port}', 'accessToken': 'fixture-only'} for s in servers], 'threadId': 'A-done-0', 'books': False}))
        args.config.chmod(0o600)
    for server in servers: threading.Thread(target=server.serve_forever, daemon=True).start()
    print('Loopback T3 fixtures ready:', args.port, args.port + 1, flush=True)
    threading.Event().wait()
