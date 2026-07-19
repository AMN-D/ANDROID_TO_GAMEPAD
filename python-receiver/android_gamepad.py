#!/usr/bin/env python3
import evdev
from evdev import UInput, AbsInfo, ecodes as e
import socket
import sys
import os
import threading
import time

TCP_PORT = 5005
UDP_DISCOVERY_PORT = 5006
BROADCAST_INTERVAL = 1.0
MAGIC = b"AGP_HELLO|5005"

GAMEPAD_CAPS = {
    e.EV_KEY: [e.BTN_SOUTH, e.BTN_EAST, e.BTN_NORTH, e.BTN_WEST, e.BTN_TL, e.BTN_TR, e.BTN_SELECT, e.BTN_START, e.BTN_MODE, e.BTN_THUMBL, e.BTN_THUMBR, e.BTN_C, e.BTN_Z],
    e.EV_ABS: [
        (e.ABS_X, AbsInfo(0, -32768, 32767, 0, 0, 0)), (e.ABS_Y, AbsInfo(0, -32768, 32767, 0, 0, 0)),
        (e.ABS_RX, AbsInfo(0, -32768, 32767, 0, 0, 0)), (e.ABS_RY, AbsInfo(0, -32768, 32767, 0, 0, 0)),
        (e.ABS_Z, AbsInfo(0, 0, 255, 0, 0, 0)), (e.ABS_RZ, AbsInfo(0, 0, 255, 0, 0, 0)),
        (e.ABS_HAT0X, AbsInfo(0, -1, 1, 0, 0, 0)), (e.ABS_HAT0Y, AbsInfo(0, -1, 1, 0, 0, 0))
    ]
}

EV_MAP = {name: getattr(e, name) for name in dir(e) if name.startswith(('BTN_', 'ABS_'))}

def reset_gamepad(gp):
    for c in [e.BTN_SOUTH, e.BTN_EAST, e.BTN_NORTH, e.BTN_WEST, e.BTN_TL, e.BTN_TR, e.BTN_SELECT, e.BTN_START]: gp.write(e.EV_KEY, c, 0)
    for c in [e.ABS_X, e.ABS_Y, e.ABS_RX, e.ABS_RY, e.ABS_HAT0X, e.ABS_HAT0Y, e.ABS_Z, e.ABS_RZ]: gp.write(e.EV_ABS, c, 0)
    gp.syn()

def handle_client(conn, player_id):
    gp = UInput(GAMEPAD_CAPS, name=f'AGP-P{player_id}', vendor=0x045e, product=0x028e)
    buf = ""
    try:
        while True:
            data = conn.recv(1024)
            if not data: break
            buf += data.decode('ascii')
            while '\n' in buf:
                line, buf = buf.split('\n', 1)
                if not line: continue
                try:
                    k, v = line.split(':')
                    code = EV_MAP[k]
                    gp.write(e.EV_KEY if k.startswith('BTN_') else e.EV_ABS, code, int(v))
                except: pass
            gp.syn()
    finally:
        reset_gamepad(gp)
        gp.close()
        conn.close()

def discovery():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    while True:
        try: s.sendto(MAGIC, ('255.255.255.255', UDP_DISCOVERY_PORT))
        except: pass
        time.sleep(BROADCAST_INTERVAL)

def main():
    if os.geteuid() != 0: sys.exit("Run as root")
    threading.Thread(target=discovery, daemon=True).start()
    p_count = 0
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('0.0.0.0', TCP_PORT))
        s.listen()
        while True:
            conn, _ = s.accept()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            p_count += 1
            threading.Thread(target=handle_client, args=(conn, p_count), daemon=True).start()

if __name__ == '__main__': main()
