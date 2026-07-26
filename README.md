## Requirements
* **Linux PC:** `python-evdev` installed.
* **Network:** Both devices must be on the same Wi-Fi network.

## Tested On

| PC | Phone |
| :--- | :--- |
| Arch Linux | Samsung Galaxy A50 (SM-A505FN) |

## How to Run

1. **Install the Client:** Install the provided `.apk` on your Android phone.
2. **Setup the Server:** Download the Python receiver script to your Linux PC.
3. **Connect to Wi-Fi:** Connect both devices to the same network.
   > **Pro-Tip:** For maximum performance and lowest latency, connect your PC directly to your phone's mobile hotspot. Be sure to enable the **5GHz band** in your phone's hotspot settings!
4. **Launch:** Run the Python receiver script on your PC with root privileges (e.g., `sudo python3 server.py`).
5. **Pair & Play:** Enter the PIN on your phone, and you are done! You can now use your phone as a gamepad.

---

## Backstory
Due to some circumstances, I don't have a proper desk setup to comfortably use a mouse, so I built this app to still be able to play my games. It takes a little getting used to, but it gets the job done!
