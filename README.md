# Aircurse

## Desktop bridge (v0.5)

The Android app is a client; desktop cursor control requires this Python bridge on a Windows, macOS, or Linux computer.

1. Keep the computer and Android phone on the same trusted private Wi-Fi network.
2. Install Python 3 and run `python -m pip install pyautogui`.
3. Create a private token of at least 16 characters. Set it as the environment variable `AIRCURSOR_TOKEN` before starting the server.
4. Run `python desktop_server/aircursor_server.py` from the repository folder.
5. Find the computer's local Wi-Fi IPv4 address. Enter that address, port `8765`, and the same token in AirCursor.
6. Tap **Test connection**, then try the touchpad and click controls.

**Safety:** Use only on a trusted private network. Do not port-forward this server or expose it to the public internet. The bridge uses plain HTTP on the local network, so the token is a basic pairing check, not encryption. Stop the server when finished. Desktop input permissions may need to be granted by the operating system.
