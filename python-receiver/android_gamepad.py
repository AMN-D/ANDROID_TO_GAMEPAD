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
MAP = {n: getattr(e, n) for n in dir(e) if n.startswith(('BTN_', 'ABS_'))}

# Tracks which player slots are currently taken, so reconnecting players
# reuse the lowest free slot instead of always counting up.
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
        print(f"[!] Connection from {addr} rejected: all {MAX_PLAYERS} player slots full")
        conn.close()
        return

    print(f"[P{pid}] Connecting from {addr}...")

    gp = UInput(CAPS, name=f'AGP-P{pid}', vendor=0x045e, product=0x028e)
    buf = ""
    auth = False

    try:
        while True:
            data = conn.recv(1024)
            if not data:
                print(f"[P{pid}] Disconnected (connection closed by client)")
                break

            try:
                buf += data.decode('ascii')
            except UnicodeDecodeError:
                print(f"[P{pid}] Error: received non-ASCII data, ignoring chunk")
                continue

            while '\n' in buf:
                line, buf = buf.split('\n', 1)
                if not line:
                    continue

                if not auth:
                    if line.startswith("AUTH:") and line.split(":", 1)[1].strip() == AUTH_PIN:
                        auth = True
                        conn.sendall(b"AUTH_OK\n")
                        print(f"[P{pid}] Authenticated.")
                    else:
                        conn.sendall(b"AUTH_FAILED\n")
                        print(f"[P{pid}] Auth failed (bad PIN), dropping connection.")
                        return
                    continue

                try:
                    k, v = line.split(':')
                    code = MAP.get(k)
                    if code is None:
                        print(f"[P{pid}] Warning: unknown input code '{k}', ignoring")
                        continue
                    gp.write(e.EV_KEY if k.startswith('BTN_') else e.EV_ABS, code, int(v))
                except ValueError as ex:
                    print(f"[P{pid}] Warning: malformed input line '{line}' ({ex})")
                except Exception as ex:
                    print(f"[P{pid}] Unexpected error handling input '{line}': {ex}")

            if auth:
                gp.syn()

    except ConnectionResetError:
        print(f"[P{pid}] Disconnected (connection reset)")
    except Exception as ex:
        print(f"[P{pid}] Error: {ex}")
    finally:
        for c in CAPS[e.EV_KEY]:
            gp.write(e.EV_KEY, c, 0)
        for c, _ in CAPS[e.EV_ABS]:
            gp.write(e.EV_ABS, c, 0)
        gp.syn()
        gp.close()
        conn.close()
        release_slot(pid)
        print(f"[P{pid}] Slot freed, ready for reconnect.")


def discovery():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    while True:
        try:
            s.sendto(MAGIC, ('255.255.255.255', UDP_PORT))
        except Exception as ex:
            print(f"[discovery] Error broadcasting: {ex}")
        time.sleep(1)


def main():
    if os.geteuid() != 0:
        sys.exit("Run as root")

    threading.Thread(target=discovery, daemon=True).start()
    print(f"PIN: {AUTH_PIN}")

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('0.0.0.0', TCP_PORT))
        s.listen()
        print(f"Listening on TCP port {TCP_PORT}...")

        while True:
            conn, addr = s.accept()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            threading.Thread(target=handle, args=(conn, addr), daemon=True).start()


if __name__ == '__main__':
    main()