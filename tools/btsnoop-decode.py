#!/usr/bin/env python3
"""Decode a btsnoop_hci capture (pure Python, no tshark) and print the ATT/SMP/HCI
traffic of ONE BLE connection: the device whose advertised (or GAP) name starts with
'S' and is exactly 12 characters. Everything else is dropped. MAC addresses are masked.

  python -I tools/btsnoop-decode.py btsnoop_hci.log [--out out.txt] [--name PREFIX]
                                    [--all] [--show-mac]

Not decoded: SMP key material (never printed), ATT signed writes, prepare/execute writes,
extended-advertising names split over several reports, LE Audio / classic traffic.
"""
import argparse
import struct
import sys
import uuid

ATT_ERR = {1: "Invalid Handle", 2: "Read Not Permitted", 3: "Write Not Permitted",
           4: "Invalid PDU", 5: "Insufficient Authentication", 6: "Request Not Supported",
           7: "Invalid Offset", 8: "Insufficient Authorization", 9: "Prepare Queue Full",
           0xA: "Attribute Not Found", 0xB: "Attribute Not Long", 0xC: "Insufficient Encryption Key Size",
           0xD: "Invalid Attribute Value Length", 0xE: "Unlikely Error", 0xF: "Insufficient Encryption",
           0x10: "Unsupported Group Type", 0x11: "Insufficient Resources"}
ATT_OPS = {0x02: "MTU_REQ", 0x03: "MTU_RSP", 0x04: "FIND_INFO_REQ", 0x05: "FIND_INFO_RSP",
           0x06: "FIND_BY_TYPE_REQ", 0x07: "FIND_BY_TYPE_RSP", 0x08: "READ_BY_TYPE_REQ",
           0x09: "READ_BY_TYPE_RSP", 0x0A: "READ_REQ", 0x0B: "READ_RSP", 0x0C: "READ_BLOB_REQ",
           0x0D: "READ_BLOB_RSP", 0x10: "READ_BY_GROUP_REQ", 0x11: "READ_BY_GROUP_RSP",
           0x12: "WRITE_REQ", 0x13: "WRITE_RSP", 0x16: "PREPARE_WRITE_REQ", 0x18: "EXEC_WRITE_REQ",
           0x1B: "NOTIFICATION", 0x1D: "INDICATION", 0x1E: "CONFIRMATION", 0x52: "WRITE_CMD"}
SMP_OPS = {1: "Pairing Request", 2: "Pairing Response", 3: "Pairing Confirm", 4: "Pairing Random",
           5: "Pairing Failed", 6: "Encryption Information", 7: "Central Identification",
           8: "Identity Information", 9: "Identity Address Information", 0xA: "Signing Information",
           0xB: "Security Request", 0xC: "Public Key", 0xD: "DHKey Check", 0xE: "Keypress Notification"}
DISC = {0x06: "link key missing", 0x08: "connection timeout", 0x13: "remote user terminated",
        0x14: "remote low resources", 0x15: "remote power off", 0x16: "local host terminated",
        0x22: "LL response timeout", 0x28: "instant passed", 0x3D: "MIC failure",
        0x3E: "failed to be established"}
PROPS = [(1, "BROADCAST"), (2, "READ"), (4, "WRITE_NO_RSP"), (8, "WRITE"), (0x10, "NOTIFY"),
         (0x20, "INDICATE"), (0x40, "SIGNED"), (0x80, "EXT")]
FRAME_TYPES = {0x31: "nav", 0x32: "0x32", 0x33: "heartbeat", 0x34: "0x34", 0x35: "0x35",
               0x36: "rider name", 0x37: "telemetry"}
BTSNOOP_EPOCH = 0x00dcddb30f2f8000  # microseconds from year 0 to 1970


def u16(b, i):
    return b[i] | b[i + 1] << 8


def uu(b):
    return "%04X" % u16(b, 0) if len(b) == 2 else str(uuid.UUID(bytes_le=bytes(b))).upper()


def hx(b):
    return bytes(b).hex()


def asc(b):
    return "".join(chr(c) if 32 <= c < 127 else "." for c in b)


def addr_s(b):
    return ":".join("%02X" % x for x in reversed(b))


def read_records(path):
    data = open(path, "rb").read()
    if data[:8] != b"btsnoop\0":
        raise SystemExit("not a btsnoop file")
    dl = struct.unpack(">I", data[12:16])[0]
    pos, trunc = 16, False
    while pos + 24 <= len(data):
        ol, il, fl, dr, ts = struct.unpack(">IIIIq", data[pos:pos + 24])
        pos += 24
        body = data[pos:pos + il]
        pos += il
        if len(body) < il:
            trunc = True
            break
        yield dl, (ts - BTSNOOP_EPOCH) / 1e6, fl, body
    if pos < len(data):
        trunc = True
    if trunc:
        print("note: capture is truncated, last record dropped", file=sys.stderr)


def to_hci(dl, fl, body):
    """-> (type 1 cmd/2 acl/4 event, sent_by_host, payload) or None. Types as in H4."""
    if dl == 1002:  # H4: type byte first
        return (body[0], not fl & 1, body[1:]) if body else None
    if dl == 1001:  # unencapsulated: flags bit1 = command/event
        t = (1 if not fl & 1 else 4) if fl & 2 else 2
        return t, not fl & 1, body
    if dl == 2001:  # monitor: opcode in low 16 bits of flags
        m = {2: (1, True), 3: (4, False), 4: (2, True), 5: (2, False)}.get(fl & 0xFFFF)
        return (m[0], m[1], body) if m else None
    raise SystemExit("unsupported datalink %d" % dl)


class Sess:
    def __init__(self, handle, addr):
        self.handle, self.addr, self.rows = handle, addr, []
        self.hl = {}          # attribute handle -> label (uuid)
        self.gap_name = None
        self.last_type = self.last_group = self.read_h = None
        self.writes = []      # (handle, label, bytes)

    def add(self, ts, d, text):
        self.rows.append((ts, d, text))


def lab(s, h):
    return " (%s)" % s.hl[h] if h in s.hl else ""


def props_s(p):
    return "|".join(n for b, n in PROPS if p & b) or "none"


def auth_s(a):
    return "bond=%d mitm=%d sc=%d" % (a & 3 != 0, a >> 2 & 1, a >> 3 & 1)


def att(s, ts, sent, p):
    d = "->" if sent else "<-"
    op = p[0]
    R = lambda t: s.add(ts, d, t)
    name = ATT_OPS.get(op, "ATT_0x%02X" % op)
    if op == 1 and len(p) >= 5:
        R("ATT_ERROR req=%s handle=0x%04X code=0x%02X %s" % (
            ATT_OPS.get(p[1], "0x%02X" % p[1]), u16(p, 2), p[4], ATT_ERR.get(p[4], "?")))
    elif op in (2, 3):
        R("%s mtu=%d" % (name, u16(p, 1)))
    elif op in (4, 6, 8, 0x10) and len(p) >= 5:
        extra = ""
        if op in (8, 0x10):
            u = uu(p[5:])
            extra = " type=" + u
            if op == 8:
                s.last_type = u
            else:
                s.last_group = u
        R("%s 0x%04X-0x%04X%s" % (name, u16(p, 1), u16(p, 3), extra))
    elif op == 5:  # descriptors
        n = 2 if p[1] == 1 else 16
        i = 2
        while i + 2 + n <= len(p):
            h, u = u16(p, i), uu(p[i + 2:i + 2 + n])
            s.hl[h] = u
            R("DESCRIPTOR handle=0x%04X uuid=%s" % (h, u))
            i += 2 + n
    elif op == 9 and len(p) > 2:
        L, i = p[1], 2
        while L >= 2 and i + L <= len(p):
            h, v = u16(p, i), p[i + 2:i + L]
            i += L
            if s.last_type == "2803" and len(v) >= 5:
                vh, u = u16(v, 1), uu(v[3:])
                s.hl[vh] = u
                R("CHARACTERISTIC uuid=%s props=%s decl=0x%04X value_handle=0x%04X" % (
                    u, props_s(v[0]), h, vh))
            else:
                if s.last_type == "2A00":
                    s.gap_name = bytes(v).decode("utf-8", "replace")
                R("READ_BY_TYPE_RSP handle=0x%04X%s hex=%s ascii=%s" % (h, lab(s, h), hx(v), asc(v)))
    elif op == 0x11 and len(p) > 2:
        L, i = p[1], 2
        while L >= 6 and i + L <= len(p):
            u = uu(p[i + 4:i + L])
            kind = "SERVICE" if s.last_group in (None, "2800") else "GROUP " + s.last_group
            s.hl[u16(p, i)] = "svc " + u
            R("%s uuid=%s handles=0x%04X-0x%04X" % (kind, u, u16(p, i), u16(p, i + 2)))
            i += L
    elif op == 0x0A:
        s.read_h = u16(p, 1)
        R("READ_REQ handle=0x%04X%s" % (s.read_h, lab(s, s.read_h)))
    elif op in (0x0B, 0x0D):
        v = p[1:]
        if s.read_h is not None and s.hl.get(s.read_h) == "2A00":
            s.gap_name = bytes(v).decode("utf-8", "replace")
        R("%s hex=%s ascii=%s" % (name, hx(v), asc(v)))
    elif op in (0x12, 0x52, 0x1B, 0x1D) and len(p) >= 3:
        h, v = u16(p, 1), p[3:]
        R("%s handle=0x%04X%s len=%d hex=%s ascii=%s" % (name, h, lab(s, h), len(v), hx(v), asc(v)))
        if op in (0x12, 0x52):
            s.writes.append((h, s.hl.get(h, "?"), bytes(v)))
    else:
        R("%s len=%d" % (name, len(p)))


def smp(s, ts, sent, p):
    op = p[0]
    t = SMP_OPS.get(op, "SMP_0x%02X" % op)
    if op in (1, 2) and len(p) >= 7:
        t += " io_cap=%d oob=%d %s max_key=%d" % (p[1], p[2], auth_s(p[3]), p[4])
    elif op == 0xB and len(p) >= 2:
        t += " " + auth_s(p[1])
    elif op == 5 and len(p) >= 2:
        t += " reason=0x%02X" % p[1]
    s.add(ts, "->" if sent else "<-", "SMP " + t)  # key material is never printed


def adv_name(d):
    i = 0
    while i < len(d) and d[i]:
        if d[i] > 0 and i + 1 < len(d) and d[i + 1] in (8, 9):
            return bytes(d[i + 2:i + 1 + d[i]]).decode("utf-8", "replace")
        i += 1 + d[i]
    return None


def process(path):
    sessions, active, adv, pend, bufs, t0 = [], {}, {}, {}, {}, None

    def get(h, ts):
        if h not in active:
            active[h] = Sess(h, None)
            sessions.append(active[h])
            active[h].add(ts, "--", "(connection start not in capture)")
        return active[h]

    for dl, ts, fl, body in read_records(path):
        r = to_hci(dl, fl, body)
        if not r:
            continue
        t, sent, p = r
        if t == 4 and len(p) >= 2:
            code, q = p[0], p[2:]
            if code == 5 and len(q) >= 4:
                s = active.pop(u16(q, 1), None)
                if s:
                    s.add(ts, "--", "DISCONNECT status=0x%02X reason 0x%02X %s" % (
                        q[0], q[3], DISC.get(q[3], "")))
            elif code == 8 and len(q) >= 4:
                s = get(u16(q, 1), ts)
                s.add(ts, "--", "ENCRYPTION CHANGE status=0x%02X enabled=%d" % (q[0], q[3]))
            elif code == 0x30 and len(q) >= 3:
                get(u16(q, 1), ts).add(ts, "--", "ENCRYPTION KEY REFRESH status=0x%02X" % q[0])
            elif code == 0x3E and q:
                sub, m = q[0], q[1:]
                if sub in (1, 0x0A, 0x29) and len(m) >= 11 and m[0] == 0:
                    s = Sess(u16(m, 1), bytes(m[5:11]))
                    active[s.handle] = s
                    sessions.append(s)
                    s.add(ts, "--", "LE CONNECTION COMPLETE handle=0x%04X role=%s%s" % (
                        s.handle, "central" if m[3] == 0 else "peripheral",
                        " (enhanced)" if sub != 1 else ""))
                    s.rows[:0] = pend.pop(s.addr, [])
                elif sub == 2 and m:
                    i = 1
                    for _ in range(m[0]):
                        if i + 9 > len(m):
                            break
                        a, L = bytes(m[i + 2:i + 8]), m[i + 8]
                        n = adv_name(m[i + 9:i + 9 + L])
                        if n:
                            adv[a] = n
                        i += 9 + L + 1
                elif sub == 0x0D and m:
                    i = 1
                    for _ in range(m[0]):
                        if i + 24 > len(m):
                            break
                        a, L = bytes(m[i + 3:i + 9]), m[i + 23]
                        n = adv_name(m[i + 24:i + 24 + L])
                        if n:
                            adv[a] = n
                        i += 24 + L
                elif sub == 5 and len(m) >= 12:  # rand/ediv not printed
                    get(u16(m, 0), ts).add(ts, "--", "LE LONG TERM KEY REQUEST")
        elif t == 1 and len(p) >= 3:
            oc, q = u16(p, 0), p[3:]
            if oc == 0x200D and len(q) >= 12:
                a = bytes(q[6:12])
                pend.setdefault(a, []).append((ts, "--", "CMD LE CREATE CONNECTION"))
            elif oc in (0x0411, 0x0413, 0x2019) and len(q) >= 2:
                nm = {0x0411: "AUTHENTICATION REQUESTED", 0x0413: "SET CONNECTION ENCRYPTION",
                      0x2019: "LE START ENCRYPTION"}[oc]
                if u16(q, 0) in active:
                    active[u16(q, 0)].add(ts, "--", "CMD " + nm)
        elif t == 2 and len(p) >= 4:
            hv = u16(p, 0)
            h, pb = hv & 0xFFF, hv >> 12 & 3
            data = p[4:4 + u16(p, 2)]
            key = (h, sent)
            if pb in (0, 2) and len(data) >= 4:
                bufs[key] = [u16(data, 0), u16(data, 2), bytearray(data[4:])]
            elif pb == 1 and key in bufs:
                bufs[key][2].extend(data)
            else:
                continue
            L, cid, pl = bufs[key]
            if len(pl) >= L and pl:
                del bufs[key]
                if cid in (4, 6):
                    (att if cid == 4 else smp)(get(h, ts), ts, sent, pl[:L])
    return sessions, adv


def frame_report(s):
    out = []
    ws = [w for w in s.writes if w[1] != "2902"][:15]
    for n, (h, u, v) in enumerate(ws, 1):
        if len(v) != 30:
            out.append("#%d handle=0x%04X uuid=%s len=%d (not a 30-byte frame) hex=%s" % (
                n, h, u, len(v), hx(v)))
            continue
        plain = sum(v[1:28]) & 0xFF
        comp = ~sum(v[1:28]) & 0xFF
        rule = "plain-sum" if v[28] == plain else "complement" if v[28] == comp else "NEITHER"
        out.append("#%d handle=0x%04X uuid=%s" % (n, h, u))
        out.append("    b0 header=%02X | b1 type=0x%02X (%s) | b2-27=%s | b28 checksum=%02X | b29=%02X" % (
            v[0], v[1], FRAME_TYPES.get(v[1], "unknown"), hx(v[2:28]), v[28], v[29]))
        out.append("    plain-sum(b1..27)=%02X complement=%02X -> byte28 matches: %s" % (plain, comp, rule))
    return out or ["(no writes except CCCD)"]


def render(sessions, adv, args):
    mask = (lambda a: addr_s(a)) if args.show_mac else (lambda a: "XX:XX:XX:XX:XX:XX")
    text, summary = [], []
    for s in sessions:
        names = [n for n in (adv.get(s.addr), s.gap_name) if n]
        if not args.all:
            ok = (lambda n: n.startswith(args.name)) if args.name else (
                lambda n: n.startswith("S") and len(n) == 12)
            if not any(ok(n) for n in names):
                continue
        t0 = s.rows[0][0] if s.rows else 0
        head = "=== CONNECTION handle=0x%04X peer=%s name=%s ===" % (
            s.handle, mask(s.addr) if s.addr else "unknown", names[0] if names else "unknown")
        text += [head]
        text += ["%9.3fs %s %s" % (ts - t0, d, x) for ts, d, x in s.rows]
        text += ["", "--- FIRST BYTES (first 15 writes, CCCD writes skipped) ---"] + frame_report(s) + [""]
        summary.append("%s: %d rows, %d writes" % (head.strip("= "), len(s.rows), len(s.writes)))
    return text, summary


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("file")
    ap.add_argument("--out")
    ap.add_argument("--name", help="advertised-name prefix instead of 'S' + 12 chars")
    ap.add_argument("--all", action="store_true", help="every connection")
    ap.add_argument("--show-mac", action="store_true")
    a = ap.parse_args(argv)
    sessions, adv = process(a.file)
    text, summary = render(sessions, adv, a)
    if not summary:
        print("no matching connection (%d connections in file)" % len(sessions))
        return 1
    if a.out:
        open(a.out, "w", encoding="utf-8").write("\n".join(text) + "\n")
        print("\n".join(summary + ["written: " + a.out]))
    else:
        print("\n".join(text))
    return 0


if __name__ == "__main__":
    sys.exit(main())
