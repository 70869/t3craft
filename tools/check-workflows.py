#!/usr/bin/env python3
"""Run client checks against disposable protocol 1 and 2 loopback servers."""
import os
from pathlib import Path
import socket
import subprocess
import sys
import time

root = Path(__file__).resolve().parent.parent
wrapper = root / 'mod' / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
for protocol in (1, 2):
    # Reserve adjacent ports before starting; fail loudly if another service owns them.
    port = 25682
    for candidate in range(25682, 25782, 2):
        try:
            with socket.socket() as a, socket.socket() as b:
                a.bind(('127.0.0.1', candidate)); b.bind(('127.0.0.1', candidate + 1))
            port = candidate
            break
        except OSError:
            continue
    else:
        raise RuntimeError('No free adjacent fixture ports')
    fixture = subprocess.Popen([sys.executable, str(root / 'tools/t3-fixture.py'), '--port', str(port)] + (['--protocol2'] if protocol == 2 else []))
    try:
        for _ in range(100):
            if fixture.poll() is not None: raise RuntimeError('Fixture exited before readiness')
            try:
                with socket.create_connection(('127.0.0.1', port), timeout=.2): break
            except OSError: time.sleep(.1)
        else: raise RuntimeError('Fixture did not start')
        tasks = ['connectionCheck', 'featureCheck'] + (['writeCheck', 'workflowCheck'] if protocol == 2 else [])
        subprocess.run([str(wrapper), '-p', str(root / 'mod'), *tasks, f'-PfixturePort={port}'], cwd=root, check=True)
    finally:
        fixture.terminate()
        fixture.wait(timeout=10)
