#!/usr/bin/env python3
import evdev, socket, sys, os, threading, time, random
from evdev import UInput, AbsInfo, ecodes as e

TCP_PORT, UDP_PORT, MAGIC = 5005, 5006, b"AGP_HELLO|5005"
AUTH_PIN = str(random.randint(1000, 9999))

CAPS = {
    e.EV_KEY: [e.BTN_SOUTH, e.BTN_EAST, e.BTN_NORTH, e.BTN_WEST, e.BTN_TL, e.BTN_TR,
               e.BTN_SELECT, e.BTN_START, e.BTN_MODE, e.BTN_THUMBL, e.BTN_THUMBR,
               e.BTN_C, e.BTN_Z],
    e.EV_ABS: [
        (e.ABS_X, AbsInfo(0, -32768, 32767, 0, 0, 0)),
        (e.ABS_Y, AbsInfo(0, -32768, 32767, 0, 0, 0)),
        (e.ABS_RX, AbsInfo(0, -32768, 32767, 0, 0, 0)),
        (e.ABS_RY, AbsInfo(0, -32768, 32767, 0, 0, 0)),
        (e.ABS_Z, AbsInfo(0, 0, 255, 0, 0, 0)),
        (e.ABS_RZ, AbsInfo(0, 0, 255, 0, 0, 0)),
        (e.ABS_HAT0X, AbsInfo(0, -1, 1, 0, 0, 0)),
        (e.ABS_HAT0Y, AbsInfo(0, -1, 1, 0, 0, 0)),
    ]
}

# Pre-filter MAP to include only necessary codes
MAP = {n: getattr(e, n) for n in dir(e) if n.startswith(('BTN_', 'ABS_'))}

MAX_PLAYERS = 4
slots_lock = threading.Lock()
slots_in_use = set()

def claim_slot():
    with slots_lock:
        for pid in range(1, MAX_PLAYERS + 1):
            if pid not in slots_in_use:
                slots_in_use.add(pid)
                return pid
    return None

def release_slot(pid):
    with slots_lock:
        slots_in_use.discard(pid)

def handle(conn, addr):
    pid = claim_slot()
    if pid is None:
        print(f"[!] Rejected {addr}: Slots full")
        conn.close()
        return

    print(f"[P{pid}] Connected: {addr}")
    gp = UInput(CAPS, name=f'AGP-P{pid}', vendor=0x045e, product=0x028e)
    buf = ""
    auth = False

    try:
        while True:
            data = conn.recv(2048)
            if not data: break

            buf += data.decode('ascii', errors='ignore')

            while '\n' in buf:
                line, buf = buf.split('\n', 1)
                if not line: continue

                if not auth:
                    if line.startswith("AUTH:") and line.split(":", 1)[1].strip() == AUTH_PIN:
                        auth = True
                        conn.sendall(b"AUTH_OK\n")
                        print(f"[P{pid}] Authenticated")
                    else:
                        conn.sendall(b"AUTH_FAILED\n")
                        print(f"[P{pid}] Auth failed, dropping")
                        return
                    continue

                try:
                    k, v = line.split(':', 1)
                    code = MAP.get(k)
                    if code is not None:
                        gp.write(e.EV_KEY if k.startswith('BTN_') else e.EV_ABS, code, int(v))
                except (ValueError, KeyError):
                    continue

            if auth: gp.syn()

    except Exception as ex:
        print(f"[P{pid}] Error: {ex}")
    finally:
        # Reset all states on disconnect
        for c in CAPS[e.EV_KEY]: gp.write(e.EV_KEY, c, 0)
        for c, _ in CAPS[e.EV_ABS]: gp.write(e.EV_ABS, c, 0)
        gp.syn()
        gp.close()
        conn.close()
        release_slot(pid)
        print(f"[P{pid}] Disconnected, slot freed")

def discovery():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    while True:
        try:
            s.sendto(MAGIC, ('255.255.255.255', UDP_PORT))
        except: pass
        time.sleep(1)

def main():
    if os.geteuid() != 0:
        sys.exit("Error: Must run as root")

    threading.Thread(target=discovery, daemon=True).start()
    print(f"Server started. PIN: {AUTH_PIN}")

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('0.0.0.0', TCP_PORT))
        s.listen()
        while True:
            conn, addr = s.accept()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            threading.Thread(target=handle, args=(conn, addr), daemon=True).start()

if __name__ == '__main__':
    main()
