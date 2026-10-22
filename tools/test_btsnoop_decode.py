"""Self-test for btsnoop-decode.py. Run: python -I tools/test_btsnoop_decode.py
Builds a synthetic capture (no real data) in a temp dir and checks the decoder output."""
import importlib.util
import os
import struct
import tempfile

here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("dec", os.path.join(here, "btsnoop-decode.py"))
dec = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dec)

A = bytes([1, 2, 3, 4, 5, 0xA6])    # wire order (little endian) of the target device
B = bytes([9, 8, 7, 6, 5, 0xB6])    # unrelated device
HA, HB = 0x0040, 0x0041
le16 = lambda v: struct.pack("<H", v)
recs = []


def rec(h4, sent):
    flags = 0 if sent else 1
    recs.append(struct.pack(">IIIIq", len(h4), len(h4), flags, 0,
                            dec.BTSNOOP_EPOCH + 1_000_000 * (len(recs) + 1)) + h4)


def meta(sub, body):
    rec(bytes([4, 0x3E, 1 + len(body), sub]) + body, False)


def adv(addr, name):
    d = bytes([len(name) + 1, 9]) + name
    meta(2, bytes([1, 0, 1]) + addr + bytes([len(d)]) + d + b"\xc8")


def acl(h, cid, pl, sent=True, split=None):
    l2 = le16(len(pl)) + le16(cid) + pl
    parts = [l2] if split is None else [l2[:split], l2[split:]]
    for i, part in enumerate(parts):
        rec(b"\x02" + le16(h | ((2 if i == 0 else 1) << 12)) + le16(len(part)) + part, sent)


def att(h, pl, sent=True, **k):
    acl(h, 4, pl, sent, **k)


def frame(t, fill):
    f = bytearray([0x5A, t] + [fill] * 26 + [0, 0])
    f[28] = sum(f[1:28]) & 0xFF
    return bytes(f)


def conn(h, addr):
    meta(1, b"\x00" + le16(h) + b"\x00\x01" + addr + b"\x18\x00\x00\x00\x48\x00\x00")


adv(B, b"OtherThing")
adv(A, b"SBM010000001")
rec(b"\x01" + le16(0x200D) + b"\x19" + b"\x60\x00\x30\x00\x00\x01" + A + b"\x00" * 13, True)
conn(HA, A)
conn(HB, B)
att(HB, b"\x12\x20\x00\xde\xad\xbe\xef")                              # unrelated write
att(HA, b"\x02" + le16(247))
att(HA, b"\x03" + le16(247), False)
att(HA, b"\x10\x01\x00\xff\xff\x00\x28")
att(HA, b"\x11\x06\x10\x00\x20\x00\xfb\xfe", False)
att(HA, b"\x08\x10\x00\x20\x00\x03\x28")
att(HA, b"\x09\x07" + le16(0x11) + b"\x04" + le16(0x12) + le16(1)
    + le16(0x13) + b"\x10" + le16(0x14) + le16(3) + le16(0x16) + b"\x0a" + le16(0x17) + le16(4), False)
att(HA, b"\x04\x15\x00\x16\x00")
att(HA, b"\x05\x01" + le16(0x15) + le16(0x2902), False)
att(HA, b"\x12" + le16(0x15) + b"\x01\x00")
att(HA, b"\x13", False)
frames = [frame(0x31, 3), frame(0x33, 7), frame(0x36, 9)]
att(HA, b"\x52" + le16(0x12) + frames[0])
att(HA, b"\x52" + le16(0x12) + frames[1], split=20)                  # fragmented
att(HA, b"\x52" + le16(0x12) + frames[2])
att(HA, b"\x1b" + le16(0x14) + frame(0x37, 5), False)
att(HA, b"\x0a\x99\x00")
att(HA, b"\x01\x0a\x99\x00\x0a", False)
acl(HA, 6, b"\x0b\x09", False)
acl(HA, 6, b"\x06" + b"\xee" * 16)                                   # key material, must not print
meta(5, le16(HA) + b"\x00" * 8 + b"\x00\x00")
rec(bytes([4, 0x08, 4, 0]) + le16(HA) + b"\x01", False)
att(HB, b"\x1b\x20\x00\xca\xfe", False)
rec(bytes([4, 0x05, 4, 0]) + le16(HB) + b"\x08", False)
rec(bytes([4, 0x05, 4, 0]) + le16(HA) + b"\x13", False)

d = tempfile.mkdtemp()
src = os.path.join(d, "x.log")
open(src, "wb").write(b"btsnoop\0" + struct.pack(">II", 1, 1002) + b"".join(recs))


def run(*args):
    out = os.path.join(d, "o.txt")
    assert dec.main([src, "--out", out, *args]) == 0
    return open(out, encoding="utf-8").read()


t = run()
for want in ["name=SBM010000001", "SERVICE uuid=FEFB", "CHARACTERISTIC uuid=0001 props=WRITE_NO_RSP",
             "uuid=0003 props=NOTIFY", "uuid=0004 props=READ|WRITE", "DESCRIPTOR handle=0x0015 uuid=2902",
             "MTU_REQ mtu=247", "MTU_RSP mtu=247", "WRITE_CMD handle=0x0012 (0001) len=30",
             "NOTIFICATION handle=0x0014 (0003)", "ATT_ERROR req=READ_REQ handle=0x0099 code=0x0A Attribute Not Found",
             "SMP Security Request", "SMP Encryption Information", "LONG TERM KEY REQUEST",
             "ENCRYPTION CHANGE status=0x00 enabled=1", "reason 0x13 remote user terminated",
             "CMD LE CREATE CONNECTION", "LE CONNECTION COMPLETE"]:
    assert want in t, want
assert t.count("WRITE_CMD") == 3 and t.count("matches: plain-sum") == 3, t
assert "b1 type=0x33 (heartbeat)" in t
assert "XX:XX:XX:XX:XX:XX" in t
for bad in ["deadbeef", "cafe", "OtherThing", "eeeeeeee", "EEEEEEEE"]:
    assert bad not in t, bad
assert "A6:05:04:03:02:01" not in t
assert "A6:05:04:03:02:01" in run("--show-mac")
assert "deadbeef" not in t and "deadbeef" in run("--all")
assert "deadbeef" in run("--name", "Oth") and "SBM01" not in run("--name", "Oth")
# checksum rule: complement is recognised
c = bytearray(frames[0])
c[28] = ~sum(c[1:28]) & 0xFF
s = dec.Sess(1, None)
s.writes.append((0x12, "0001", bytes(c)))
assert "matches: complement" in "\n".join(dec.frame_report(s))
print("ok")
