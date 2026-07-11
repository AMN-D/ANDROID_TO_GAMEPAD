#!/usr/bin/env python3
"""
TCP Network to Gamepad Mapper (Pure Direct Input Version)
Receives raw gamepad signals from the Android App and injects them directly.
"""
import evdev
from evdev import UInput, ecodes as e
import socket
import sys
import os


def create_gamepad():
    """Create a virtual gamepad device"""
    from evdev import AbsInfo

    caps = {
        e.EV_KEY: [
            e.BTN_SOUTH, e.BTN_EAST, e.BTN_NORTH, e.BTN_WEST,
            e.BTN_TL, e.BTN_TR,
            e.BTN_SELECT, e.BTN_START, e.BTN_MODE,
            e.BTN_THUMBL, e.BTN_THUMBR,
        ],
        e.EV_ABS: [
            (e.ABS_X, AbsInfo(value=0, min=-32768, max=32767, fuzz=0, flat=0, resolution=0)),
            (e.ABS_Y, AbsInfo(value=0, min=-32768, max=32767, fuzz=0, flat=0, resolution=0)),
            (e.ABS_RX, AbsInfo(value=0, min=-32768, max=32767, fuzz=0, flat=0, resolution=0)),
            (e.ABS_RY, AbsInfo(value=0, min=-32768, max=32767, fuzz=0, flat=0, resolution=0)),
            (e.ABS_Z, AbsInfo(value=0, min=0, max=255, fuzz=0, flat=0, resolution=0)),
            (e.ABS_RZ, AbsInfo(value=0, min=0, max=255, fuzz=0, flat=0, resolution=0)),
            (e.ABS_HAT0X, AbsInfo(value=0, min=-1, max=1, fuzz=0, flat=0, resolution=0)),
            (e.ABS_HAT0Y, AbsInfo(value=0, min=-1, max=1, fuzz=0, flat=0, resolution=0)),
        ],
    }

    return UInput(caps, name='Android-USB-Gamepad', bustype=0x03, vendor=0x045e, product=0x028e, version=0x0110)


def reset_gamepad(gamepad):
    """Release all buttons and re-center all axes. Called on disconnect
    so a button/stick held at the moment of disconnect doesn't get stuck."""
    for code in [e.BTN_SOUTH, e.BTN_EAST, e.BTN_NORTH, e.BTN_WEST,
                 e.BTN_TL, e.BTN_TR, e.BTN_SELECT, e.BTN_START]:
        gamepad.write(e.EV_KEY, code, 0)
    for code in [e.ABS_X, e.ABS_Y, e.ABS_RX, e.ABS_RY,
                 e.ABS_HAT0X, e.ABS_HAT0Y, e.ABS_Z, e.ABS_RZ]:
        gamepad.write(e.EV_ABS, code, 0)
    gamepad.syn()


def main():
    if os.geteuid() != 0:
        print("Error: This script must be run as root (use sudo)")
        sys.exit(1)

    gamepad = create_gamepad()
    print("✓ Virtual gamepad created in Linux")
    HOST = '0.0.0.0'  # wireless: accepts connections from any device on the LAN
    PORT = 5005

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, PORT))
        s.listen()

        print(f"\n🎮 Waiting for Android app to connect on port {PORT}...")

        try:
            while True:
                conn, addr = s.accept()
                with conn:
                    print("✓ Connected to Android App!")
                    buffer = ""

                    while True:
                        data = conn.recv(1024)
                        if not data:
                            print("⚠ Android app disconnected. Waiting for new connection...")
                            reset_gamepad(gamepad)
                            break

                        # TCP doesn't preserve message boundaries, so a single recv()
                        # can split a line across two calls (or pack multiple lines
                        # together). Buffer raw bytes and only act once we see '\n'.
                        buffer += data.decode('utf-8')

                        # Write every complete command from this read WITHOUT syncing
                        # in between, then sync ONCE at the end — coalesces same-batch
                        # commands (e.g. a combo button's two presses) into one atomic
                        # frame instead of two. See note below on wireless limits.
                        while '\n' in buffer:
                            msg, buffer = buffer.split('\n', 1)
                            if not msg:
                                continue
                            try:
                                # Expected format: "BTN_SOUTH:1" or "ABS_X:32000"
                                key_str, val_str = msg.split(':')
                                event_code = getattr(e, key_str)
                                event_value = int(val_str)

                                # DIRECT MAPPING LOGIC: No dictionary needed anymore!
                                if key_str.startswith('BTN_'):
                                    gamepad.write(e.EV_KEY, event_code, event_value)
                                elif key_str.startswith('ABS_'):
                                    gamepad.write(e.EV_ABS, event_code, event_value)
                            except Exception:
                                # Silently ignore malformed packets to keep latency at 0ms
                                pass

                        # One sync for everything decoded from this read.
                        gamepad.syn()
        except KeyboardInterrupt:
            print("\n✓ Stopped by user.")
        finally:
            gamepad.close()
            print("✓ Cleaned up virtual gamepad.")


if __name__ == '__main__':
    main()