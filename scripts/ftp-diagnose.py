#!/usr/bin/env python3
"""Diagnose FTP reachability from the current network.

Probes, in order:
  1. DNS resolution of the host
  2. TCP reachability of the FTP-related ports, plus 443 as a control
  3. If port 21 opens: the FTP greeting, AUTH TLS, and whether a PASV data
     connection can actually be opened (the passive-mode failure mode)
  4. If port 21 opens: whether the server accepts an active-mode PORT command

Usage: python3 scripts/ftp-diagnose.py <host> [--port 21]
"""

import argparse
import socket
import ssl
import sys

TIMEOUT = 8.0

# 21 = FTP control, 990 = implicit FTPS, 22 = SFTP (a common fallback),
# 443 = control probe: if this opens and 21 does not, the block is port-specific.
PORTS = [(21, "FTP control"), (990, "implicit FTPS"), (22, "SFTP"), (443, "HTTPS (control probe)")]


def resolve(host):
    try:
        infos = socket.getaddrinfo(host, None, proto=socket.IPPROTO_TCP)
        addrs = sorted({i[4][0] for i in infos})
        print(f"DNS      {host} -> {', '.join(addrs)}")
        return addrs
    except socket.gaierror as e:
        print(f"DNS      {host} -> FAILED: {e}")
        return []


def probe_port(host, port, label):
    sock = socket.socket()
    sock.settimeout(TIMEOUT)
    try:
        sock.connect((host, port))
        print(f"TCP  {port:<5} {label:<24} OPEN")
        return True
    except socket.timeout:
        print(f"TCP  {port:<5} {label:<24} TIMEOUT after {TIMEOUT:.0f}s (silently dropped: firewall/proxy)")
        return False
    except OSError as e:
        print(f"TCP  {port:<5} {label:<24} REFUSED/ERROR: {e}")
        return False
    finally:
        sock.close()


def read_reply(f):
    """Read one (possibly multi-line) FTP reply."""
    line = f.readline().decode("utf-8", "replace").rstrip()
    if len(line) > 3 and line[3] == "-":
        code = line[:3]
        while True:
            nxt = f.readline().decode("utf-8", "replace").rstrip()
            line += " | " + nxt
            if nxt.startswith(code + " "):
                break
    return line


def probe_ftp_dialogue(host, port):
    """Open the control channel and see how far the conversation gets."""
    try:
        sock = socket.create_connection((host, port), timeout=TIMEOUT)
    except OSError as e:
        print(f"FTP      control channel unavailable: {e}")
        return

    with sock, sock.makefile("rb") as f:
        print(f"FTP      greeting: {read_reply(f)}")

        sock.sendall(b"AUTH TLS\r\n")
        auth = read_reply(f)
        print(f"FTP      AUTH TLS: {auth}")
        if auth.startswith("234"):
            try:
                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE
                tls = ctx.wrap_socket(sock, server_hostname=host)
                print(f"TLS      handshake OK ({tls.version()})")
            except OSError as e:
                print(f"TLS      handshake FAILED: {e}")
                return
            conn, cf = tls, tls.makefile("rb")
        else:
            conn, cf = sock, f

        # PASV without logging in: servers reply 530 (not logged in) but that
        # still proves the control channel is usable. If a server does hand back
        # an address, try connecting to it to test the data channel.
        conn.sendall(b"PASV\r\n")
        pasv = read_reply(cf)
        print(f"FTP      PASV: {pasv}")
        if pasv.startswith("227") and "(" in pasv:
            nums = pasv[pasv.index("(") + 1: pasv.index(")")].split(",")
            data_host = ".".join(nums[:4])
            data_port = int(nums[4]) * 256 + int(nums[5])
            advertised = "" if data_host == host else f" (advertised {data_host}, differs from control host)"
            print(f"FTP      passive data target: {data_host}:{data_port}{advertised}")
            probe_port(data_host, data_port, "passive data channel")

        # Active mode: ask the server to connect back to us. The PORT command
        # being accepted only means the server tried; NAT usually kills it.
        conn.sendall(b"PORT 127,0,0,1,4,1\r\n")
        print(f"FTP      PORT (active mode) reply: {read_reply(cf)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("host")
    parser.add_argument("--port", type=int, default=21)
    args = parser.parse_args()

    print(f"--- FTP diagnosis for {args.host}:{args.port} ---")
    if not resolve(args.host):
        return 1

    reachable = {}
    for port, label in PORTS:
        reachable[port] = probe_port(args.host, port, label)

    if reachable.get(args.port):
        probe_ftp_dialogue(args.host, args.port)
    else:
        print(f"FTP      skipped protocol probes: port {args.port} is not reachable")

    print("--- summary ---")
    if not reachable.get(21) and reachable.get(443):
        print("Port 21 is blocked while 443 is open: this network filters FTP outbound.")
        print("Active vs passive mode cannot help, the control channel never opens.")
    elif reachable.get(21):
        print("Port 21 is reachable: any failure is in the data channel or TLS, not the control channel.")
    else:
        print("Nothing reachable, including 443: check general connectivity to this host.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
