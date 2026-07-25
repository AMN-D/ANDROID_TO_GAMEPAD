#!/usr/bin/env python3
import evdev, socket, sys, os, threading, time, random
from evdev import UInput, AbsInfo, ecodes as e

TCP_PORT, UDP_PORT, MAGIC = 5005, 5006, b"AGP_HELLO|5005"
AUTH_PIN = str(random.randint(1000, 9999))

CAPS = {
    e.EV_KEY: [e.BTN_SOUTH, e.BTN_EAST, e.BTN_NORTH, e.BTN_WEST, e.BTN_TL, e.BTN_TR, e.BTN_SELECT, e.BTN_START, e.BTN_MODE, e.BTN_THUMBL, e.BTN_THUMBR, e.BTN_C, e.BTN_Z],
    e.EV_ABS: [(e.ABS_X, AbsInfo(0, -32768, 32767, 0, 0, 0)), (e.ABS_Y, AbsInfo(0, -32768, 32767, 0, 0, 0)), (e.ABS_RX, AbsInfo(0, -32768, 32767, 0, 0, 0)), (e.ABS_RY, AbsInfo(0, -32768, 32767, 0, 0, 0)), (e.ABS_Z, AbsInfo(0, 0, 255, 0, 0, 0)), (e.ABS_RZ, AbsInfo(0, 0, 255, 0, 0, 0)), (e.ABS_HAT0X, AbsInfo(0, -1, 1, 0, 0, 0)), (e.ABS_HAT0Y, AbsInfo(0, -1, 1, 0, 0, 0))]
}
MAP = {n: getattr(e, n) for n in dir(e) if n.startswith(('BTN_', 'ABS_'))}

def handle(conn, pid):
    gp, buf, auth = UInput(CAPS, name=f'AGP-P{pid}', vendor=0x045e, product=0x028e), "", False
    try:
        while True:
            data = conn.recv(1024)
            if not data: break
            buf += data.decode('ascii')
            while '\n' in buf:
                line, buf = buf.split('\n', 1)
                if not line: continue
                if not auth:
                    if line.startswith("AUTH:") and line.split(":")[1].strip() == AUTH_PIN:
                        auth = True
                        conn.sendall(b"AUTH_OK\n")
                        print(f"[P{pid}] Authenticated.")
                    else:
                        conn.sendall(b"AUTH_FAILED\n")
                        print(f"[P{pid}] Auth failed.")
                        return
                    continue
                try:
                    k, v = line.split(':')
                    gp.write(e.EV_KEY if k.startswith('BTN_') else e.EV_ABS, MAP[k], int(v))
                except: pass
            if auth: gp.syn()
    finally:
        for c in CAPS[e.EV_KEY]: gp.write(e.EV_KEY, c, 0)
        for c, _ in CAPS[e.EV_ABS]: gp.write(e.EV_ABS, c, 0)
        gp.syn(); gp.close(); conn.close()

def discovery():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    while True:
        try: s.sendto(MAGIC, ('255.255.255.255', UDP_PORT))
        except: pass
        time.sleep(1)

def main():
    if os.geteuid() != 0: sys.exit("Run as root")
    threading.Thread(target=discovery, daemon=True).start()
    print(f"PIN: {AUTH_PIN}")
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('0.0.0.0', TCP_PORT)); s.listen()
        p_count = 0
        while True:
            conn, _ = s.accept()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            p_count += 1
            threading.Thread(target=handle, args=(conn, p_count), daemon=True).start()

if __name__ == '__main__': main()
