#!/usr/bin/env python3
"""Virtual cluster: decode 30-byte frames from a log and draw what a cluster would show.
Independent of the app (no app code). Python 3 stdlib only.
Usage: vcluster.py LOG [--model ACCESS_LCD|SCOOTER_LCD|MOTORCYCLE|ELECTRIC|TFT] [--layout LCD|TFT] [--out DIR] [--bare]
       vcluster.py --selftest
LOG lines: `... TX model=X type=0x31 hex=<60 hex>` (only these are decoded), or a line that is only a
60-hex frame (spaces allowed). Other text such as "Bytes sent ..." is ignored unless --bare. '-' = stdin.
Marks: P proven on a real cluster (Access AS21 only), D documented only, U unknown."""
import sys, re, os, math, html, argparse

MODELS = ['ACCESS_LCD', 'SCOOTER_LCD', 'MOTORCYCLE', 'ELECTRIC', 'TFT']
NAMES = {1: 'junction turn left', 2: 'junction slight left', 3: 'curve left', 4: 'junction turn right',
         5: 'hairpin right', 6: 'curve right', 7: 'U-turn', 8: 'straight', 9: 'destination',
         15: 'arrow down-right', 16: 'arrow back', 17: 'arrow down-left', 18: 'arrow left',
         19: 'slight left', 20: 'roundabout exit sharp left', 21: 'roundabout exit left',
         22: 'roundabout exit slight left', 23: 'roundabout exit straight',
         24: 'roundabout exit slight right', 25: 'roundabout exit right',
         26: 'roundabout exit sharp right', 27: 'ramp/fork left-up', 28: 'ramp/fork right-up',
         29: 'ramp/fork down left', 30: 'ramp/fork down right', 31: 'fork keep left',
         32: 'fork keep right', 33: 'crossroads', 34: 'sharp left', 35: 'curve right',
         36: 'sharp right', 37: 'curve left', 38: 'left with lane bar', 39: 'U-turn',
         40: 'straight', 41: 'slight right', 42: 'right', 43: 'ferry', 44: 'keep left lanes',
         45: 'roundabout', 46: 'blank'}
TYPES = {0x31: 'NAV', 0x32: 'CALL', 0x33: 'HEARTBEAT', 0x34: 'MISSED CALL', 0x35: 'MESSAGE',
         0x36: 'RIDER PROFILE', 0x37: 'TELEMETRY'}
MARK_WORD = {'P': 'PROVEN', 'D': 'DOCUMENTED', 'U': 'UNKNOWN'}


def default_layout(model):
    return 'LCD' if model in ('ACCESS_LCD', 'SCOOTER_LCD') else 'TFT'


def code_mark(c, model):
    if c in (10, 11, 12, 13, 14, 29, 30) or c >= 47:
        return 'U'
    if c in (27, 28, 38) or model != 'ACCESS_LCD':
        return 'D'
    return 'P'


def txt(b):
    out = ''
    for x in b:
        if x in (0x00, 0xFF):
            break
        if 32 <= x < 127:
            out += chr(x)
    return out


def dist(b):
    b = bytes(b)
    if all(x == 0xFF for x in b):
        return None, 'U'
    s = b.decode('latin1')
    m = re.fullmatch(r'(\d{4})M', s)
    if m:
        return '%d m' % int(m.group(1)), 'P'
    m = re.fullmatch(r'(\d\d\.\d)K', s)
    if m:
        return '%g km' % float(m.group(1)), 'U'
    m = re.fullmatch(r'(\d{4})K', s)
    if m:
        return '%d km' % int(m.group(1)), 'U'
    return 'raw ' + repr(s), 'U'


def checksum(fr, model):
    s = sum(fr[1:28]) & 255
    return (255 - s) & 255 if model == 'ACCESS_LCD' else s


def decode(fr, model):
    """Return dict: type, cs_ok, cs_stored, cs_expected, fields[(name,value,mark)], upd{}."""
    A = 'P' if model == 'ACCESS_LCD' else 'D'  # proven only on the owner's Access
    r = {'model': model, 'fields': [], 'upd': {}, 'type': None, 'cs_ok': False}
    F = lambda n, v, m: r['fields'].append((n, v, m))
    if len(fr) != 30 or fr[0] != 0xA5 or fr[29] != 0x7F:
        F('frame', 'bad shape (need 30 bytes, A5 ... 7F), len=%d' % len(fr), 'U')
        return r
    exp = checksum(fr, model)
    r['cs_stored'], r['cs_expected'], r['cs_ok'] = fr[28], exp, fr[28] == exp
    t = fr[1]
    r['type'] = t
    u = r['upd']
    if t == 0x31:
        c = fr[2]
        F('maneuver', '%d = %s' % (c, NAMES.get(c, 'not in table')), code_mark(c, model))
        d, mk = dist(fr[4:9])
        F('next turn distance', d or 'blank', mk if d else 'U')
        s = bytes(fr[9:15]).decode('latin1')
        m = re.fullmatch(r'([ \d]\d)(\d\d)(AM|PM)', s)
        clock = '%s:%s %s' % (m.group(1).strip().zfill(2), m.group(2), m.group(3)) if m else None
        F('clock', clock or 'raw ' + repr(s), A if m else 'U')
        rd, mk2 = dist(fr[18:23])
        F('remaining distance', rd or 'blank', 'P' if rd else 'U')
        g = chr(fr[23])
        F('gps status', {'1': 'GPS ok', '0': 'airplane mode', '4': 'GPS off'}.get(g, 'raw %r' % g), 'D')
        F('nav active', 'yes' if fr[24] == 0x31 else 'raw 0x%02X' % fr[24], A)
        u.update(nav={'code': c, 'dist': d, 'clock': clock, 'rem': rd, 'gps': g})
    elif t == 0x33:
        b = chr(fr[2])
        F('phone battery bars', {'5': 'blank', '4': '3 bars'}.get(b, b + ' bars' if b in '0123' else 'raw %r' % b), A)
        F('charging', {'Y': 'yes (top bar blinks)', 'N': 'no'}.get(chr(fr[3]), 'raw 0x%02X' % fr[3]), A)
        sp = bytes(fr[4:7])
        spd = None if all(x == 0xFF for x in sp) else int(sp.decode('latin1')) if sp.isdigit() else None
        F('speed', '%d km/h' % spd if spd is not None else 'blank', 'D' if spd is None else 'U')
        sg = chr(fr[7])
        F('signal bars', sg if sg in '0123' else 'raw %r' % sg, 'P' if (sg == '3' and model == 'ACCESS_LCD') else 'U')
        c = bytes(fr[8:14])
        clock = None
        if c.isdigit():
            clock = '%s:%s:%s' % (c[0:2].decode(), c[2:4].decode(), c[4:6].decode())
        F('clock', clock or 'blank', A if clock else 'D')
        F('message flag', chr(fr[14]), 'U')
        F('missed-call flag', chr(fr[15]), 'U')
        if model != 'ACCESS_LCD' and fr[21] != 0xFF:
            F('weather code', fr[21], 'D')
            F('temperature', '%d F' % (fr[22] - 115), 'D')
        u.update(hb={'batt': b, 'chg': chr(fr[3]) == 'Y', 'speed': spd, 'sig': sg, 'clock': clock})
    elif t == 0x32:
        n = txt(fr[2:22]) or '(empty)'
        F('caller', n, 'P')
        F('channel', {'N': 'phone', 'W': 'WhatsApp'}.get(chr(fr[22]), 'raw 0x%02X' % fr[22]), 'D')
        F('state', {'1': 'ringing', '2': 'answered'}.get(chr(fr[23]), 'raw 0x%02X' % fr[23]), 'D')
        u.update(call={'name': n, 'ch': chr(fr[22]), 'st': chr(fr[23])})
    elif t == 0x34:
        n = txt(fr[4:24])
        F('missed count', fr[3], 'D')
        F('caller', n or '(none)', 'U')
        F('channel', {'N': 'phone', 'W': 'WhatsApp'}.get(chr(fr[24]), 'raw 0x%02X' % fr[24]), 'D')
        u.update(missed={'n': fr[3], 'name': n})
    elif t == 0x35:
        n = txt(fr[5:25])
        F('new alert', {'Y': 'new', 'N': 'recount/reset'}.get(chr(fr[3]), 'raw 0x%02X' % fr[3]), 'D')
        F('unread count', fr[4], 'D')
        F('sender', n or '(none)', 'P' if n else 'U')
        F('type', {'N': 'SMS', 'W': 'WhatsApp', 'X': 'calendar'}.get(chr(fr[25]), 'raw 0x%02X' % fr[25]), 'D')
        u.update(msg={'n': fr[4], 'name': n, 'ty': chr(fr[25])})
    elif t == 0x36:
        n = txt(fr[2:22])
        f = chr(fr[27])
        line = ('WELCOME %s' % n.upper()) if f == 'R' else ('%s CONNECTED' % n.upper()) if f == 'F' else n.upper()
        F('rider name', n, 'P')
        F('greeting flag', {'R': 'R same cluster', 'F': 'F new cluster'}.get(f, 'raw %r' % f), 'P')
        F('cluster text', line, 'P')
        u.update(profile=line)
    elif t == 0x37:
        d = bytes(fr[2:23])
        if not d.isdigit():
            F('telemetry', 'digits expected in bytes 2-22', 'U')
        else:
            F('odometer', '%d km' % int(d[0:9]), A)
            F('trip A', '%.1f km' % (int(d[9:15]) / 10), A)
            F('trip B', '%.1f km' % (int(d[15:21]) / 10), A)
            u.update(tele={'odo': int(d[0:9]), 'a': int(d[9:15]) / 10, 'b': int(d[15:21]) / 10})
        fb = chr(fr[24])
        if model == 'ELECTRIC':
            F('battery', '%d %%' % (fr[24] - 64), 'D')
            u.setdefault('tele', {})['bat'] = fr[24] - 64
        else:
            F('fuel bar', fb, 'P' if fb == '5' and model == 'ACCESS_LCD' else 'U')
            u.setdefault('tele', {})['fuel'] = fb
    else:
        F('type', 'unknown type 0x%02X' % t, 'U')
    return r


def apply(state, dec):
    for k, v in dec['upd'].items():
        if isinstance(v, dict) and isinstance(state.get(k), dict):
            state[k].update(v)
        else:
            state[k] = dict(v) if isinstance(v, dict) else v


def frame_text(i, fr, model, layout, dec, state):
    L = ['frame %d  model=%s  layout=%s' % (i, model, layout), 'hex  %s' % fr.hex().upper()]
    if dec['type'] is None:
        L.append('NOT A VALID FRAME: ' + dec['fields'][0][1])
        return '\n'.join(L)
    L.append('type 0x%02X %s   checksum %s (stored %02X, expected %02X)' % (
        dec['type'], TYPES.get(dec['type'], '?'), 'OK' if dec['cs_ok'] else 'BAD', dec['cs_stored'], dec['cs_expected']))
    for n, v, m in dec['fields']:
        L.append('  %-20s %-34s [%s]' % (n, v, MARK_WORD[m]))
    L.append('screen:')
    L += ['  | ' + s for s in screen_lines(state)]
    return '\n'.join(L)


def screen_lines(s):
    nav, hb = s.get('nav', {}), s.get('hb', {})
    c = nav.get('code')
    arrow = '[blank]' if c is None or c == 46 else '[%s]' % NAMES.get(c, 'code %d' % c)
    sig = hb.get('sig', '-')
    bt = hb.get('batt', '-')
    out = ['%s  %s  %s' % (s.get('profile', ''), nav.get('clock') or hb.get('clock') or '--:--',
                           'signal %s  battery %s%s' % (sig, {'5': 'blank', '4': '3'}.get(bt, bt), ' CHG' if hb.get('chg') else '')),
           '%s  %s' % (arrow, nav.get('dist') or '---'),
           'to destination: %s   speed: %s' % (nav.get('rem') or '---', ('%d km/h' % hb['speed']) if hb.get('speed') is not None else '---')]
    if 'call' in s:
        out.append('CALL    %s (%s, %s)' % (s['call']['name'], {'N': 'phone', 'W': 'WhatsApp'}.get(s['call']['ch'], '?'),
                                           {'1': 'ringing', '2': 'answered'}.get(s['call']['st'], '?')))
    if 'msg' in s:
        out.append('MESSAGE %s  unread %d' % (s['msg']['name'] or '-', s['msg']['n']))
    if 'missed' in s:
        out.append('MISSED  %d  %s' % (s['missed']['n'], s['missed']['name']))
    if 'tele' in s:
        t = s['tele']
        out.append('ODO %s  A %s  B %s  %s' % (t.get('odo', '-'), t.get('a', '-'), t.get('b', '-'),
                                                 ('batt %s%%' % t['bat']) if 'bat' in t else 'fuel %s' % t.get('fuel', '-')))
    return out


# ---- own-drawn vectors, 100x100 box, y down ----
_L = {1: [([(50, 95), (50, 50), (15, 50)], 1, 9)], 2: [([(50, 95), (50, 55), (25, 30)], 1, 9)],
      3: [([(50, 95), (50, 70), (40, 45), (15, 30)], 1, 9)], 19: [([(75, 85), (25, 25)], 1, 9)],
      34: [([(65, 95), (65, 40), (40, 40), (40, 75)], 1, 9)],
      27: [([(50, 95), (50, 60), (30, 25)], 1, 9), ([(50, 60), (68, 28)], 0, 5)],
      29: [([(50, 10), (50, 45), (30, 80)], 1, 9)],
      31: [([(50, 95), (50, 62), (25, 25)], 1, 9), ([(50, 62), (75, 25)], 0, 5)],
      15: [([(25, 25), (75, 80)], 1, 9)], 18: [([(85, 50), (15, 50)], 1, 9)],
      39: [([(30, 95), (30, 40), (45, 20), (65, 40), (65, 75)], 1, 9)],
      40: [([(50, 95), (50, 15)], 1, 9)], 16: [([(50, 15), (50, 90)], 1, 9)]}
_L[38] = _L[1] + [([(88, 95), (88, 40)], 0, 6)]
_L[44] = _L[31] + [([(12, 95), (12, 70)], 0, 6), ([(88, 95), (88, 70)], 0, 6)]
_L[33] = _L[40] + [([(25, 50), (75, 50)], 0, 6)]
_ALIAS = {7: 39, 8: 40, 37: 3}
_MIR = {4: 1, 6: 3, 35: 3, 41: 19, 36: 34, 5: 34, 28: 27, 32: 31, 30: 29, 42: 18, 17: 15}
_EXIT = {20: (-1, 1), 21: (-1, 0), 22: (-1, -1), 23: (0, -1), 24: (1, -1), 25: (1, 0), 26: (1, 1)}


def _line(pts, head, w, ink):
    p = ' '.join('%.1f,%.1f' % q for q in pts)
    o = '<polyline points="%s" fill="none" stroke="%s" stroke-width="%d" stroke-linecap="round" stroke-linejoin="round"/>' % (p, ink, w)
    if head:
        (x0, y0), (x1, y1) = pts[-2], pts[-1]
        d = math.hypot(x1 - x0, y1 - y0) or 1
        ux, uy = (x1 - x0) / d, (y1 - y0) / d
        bx, by = x1 - ux * 18, y1 - uy * 18
        o += '<polygon points="%.1f,%.1f %.1f,%.1f %.1f,%.1f" fill="%s"/>' % (
            x1 + ux * 4, y1 + uy * 4, bx - uy * 13, by + ux * 13, bx + uy * 13, by - ux * 13, ink)
    return o


def arrow_svg(c, ink):
    """SVG fragment in a 100x100 box, or None when the code has no drawing (caller prints the number)."""
    if c in _EXIT or c == 45:
        o = '<circle cx="50" cy="60" r="18" fill="none" stroke="%s" stroke-width="8"/>' % ink
        o += _line([(50, 100), (50, 78)], 0, 8, ink)
        if c in _EXIT:
            dx, dy = _EXIT[c]
            n = math.hypot(dx, dy)
            dx, dy = dx / n, dy / n
            o += _line([(50 + dx * 18, 60 + dy * 18), (50 + dx * 42, 60 + dy * 42)], 1, 8, ink)
        return o
    if c == 9:
        return ('<circle cx="50" cy="50" r="34" fill="none" stroke="%s" stroke-width="8"/>'
                '<circle cx="50" cy="50" r="12" fill="%s"/>') % (ink, ink)
    if c == 43:
        return ('<polygon points="12,62 88,62 72,84 28,84" fill="%s"/>'
                '<polygon points="50,18 78,54 50,54" fill="%s"/>') % (ink, ink)
    c = _ALIAS.get(c, c)
    mir = c in _MIR
    base = _MIR.get(c, c)
    if base not in _L:
        return None
    o = ''
    for pts, h, w in _L[base]:
        if mir:
            pts = [(100 - x, y) for x, y in pts]
        o += _line(pts, h, w, ink)
    return o


def svg_cluster(state, layout):
    tft = layout == 'TFT'
    bg, ink, dim, acc = ('#0b1220', '#e8f0ff', '#5d6b85', '#4fc3f7') if tft else ('#cfd9b4', '#18230f', '#8d9a76', '#18230f')
    bh = 300 if tft else 260
    k = 1.6 if tft else 1.0
    nav, hb = state.get('nav', {}), state.get('hb', {})
    e = lambda t: html.escape(str(t))
    T = lambda x, y, sz, t, a='start', col=None: '<text x="%d" y="%d" font-size="%d" text-anchor="%s" fill="%s" font-family="monospace" font-weight="bold">%s</text>' % (x, y, sz, a, col or ink, e(t))
    o = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 480 %d">' % (480 * k, bh * k, bh),
         '<rect width="480" height="%d" rx="14" fill="%s"/>' % (bh, bg)]
    o.append(T(12, 24, 14, state.get('profile', '')))
    o.append(T(240, 26, 20, nav.get('clock') or hb.get('clock') or '--:--', 'middle'))
    n = int(hb['sig']) if hb.get('sig', '').isdigit() else 0
    for i in range(3):
        h = 8 + 6 * i
        o.append('<rect x="%d" y="%d" width="8" height="%d" fill="%s"/>' % (370 + i * 11, 28 - h, h, ink if i < n else dim))
    bt = hb.get('batt', '-')
    nb = {'0': 0, '1': 1, '2': 2, '3': 3, '4': 3}.get(bt, 0)
    o.append('<rect x="425" y="10" width="40" height="18" fill="none" stroke="%s" stroke-width="2"/>' % ink)
    if bt != '5':
        for i in range(nb):
            o.append('<rect x="%d" y="13" width="10" height="12" fill="%s"/>' % (428 + i * 12, ink))
    if hb.get('chg'):
        o.append(T(445, 44, 11, 'CHG', 'middle', acc))
    c = nav.get('code')
    o.append('<g transform="translate(14,44) scale(1.3)">')
    o.append('<rect width="100" height="100" fill="none" stroke="%s" stroke-width="1"/>' % dim)
    if c is not None and c != 46:
        a = arrow_svg(c, ink)
        o.append(a if a else T(50, 62, 36, str(c), 'middle'))
    o.append('</g>')
    o.append(T(170, 108, 54, nav.get('dist') or '---'))
    o.append(T(170, 140, 16, 'DEST ' + (nav.get('rem') or '---'), 'start', acc))
    o.append(T(170, 164, 16, 'SPEED ' + (('%d km/h' % hb['speed']) if hb.get('speed') is not None else '---')))
    y = 196
    if 'call' in state:
        cl = state['call']
        o.append(T(12, y, 15, 'CALL ' + cl['name'] + (' (WhatsApp)' if cl['ch'] == 'W' else '') + (' answered' if cl['st'] == '2' else ' ringing'), 'start', acc)); y += 22
    if 'msg' in state:
        o.append(T(12, y, 15, 'MSG %s (%d)' % (state['msg']['name'] or '-', state['msg']['n']))); y += 22
    if 'missed' in state:
        o.append(T(12, y, 15, 'MISSED %d %s' % (state['missed']['n'], state['missed']['name']))); y += 22
    if tft and 'tele' in state:
        t = state['tele']
        o.append(T(12, 285, 14, 'ODO %s km  A %s  B %s  %s' % (t.get('odo', '-'), t.get('a', '-'), t.get('b', '-'),
                                                                ('BATT %s%%' % t['bat']) if 'bat' in t else 'FUEL ' + str(t.get('fuel', '-'))), 'start', dim))
    o.append('</svg>')
    return '\n'.join(o)


FRAME_RE = re.compile(r'A5(?:\s?[0-9A-Fa-f]{2}){29}', re.I)


def parse_log(text, default_model, bare=False):
    out = []
    for ln in text.splitlines():
        if 'TX model=' in ln:  # DIAG TX line: the frame is in hex=, nothing else counts
            h = re.search(r'hex=([0-9A-Fa-f\s]+)', ln)
            m = FRAME_RE.search(h.group(1)) if h else None
        elif bare or FRAME_RE.fullmatch(ln.strip()):  # line is only hex, or --bare
            m = FRAME_RE.search(ln)
        else:
            continue  # e.g. "Bytes sent : A5 31 ..." in the verbose log
        if m:
            mm = re.search(r'model=(\w+)', ln)
            model = mm.group(1).upper() if mm and mm.group(1).upper() in MODELS else default_model
            out.append((model, bytes.fromhex(re.sub(r'\s', '', m.group(0)))))
    return out


def run(text, default_model, layout, outdir, bare=False):
    state, cards, n = {}, [], 0
    os.makedirs(outdir, exist_ok=True)
    for model, fr in parse_log(text, default_model, bare):
        n += 1
        lay = layout or default_layout(model)
        dec = decode(fr, model)
        if dec['type'] is not None:
            apply(state, dec)
        t = frame_text(n, fr, model, lay, dec, state)
        s = svg_cluster(state, lay)
        open(os.path.join(outdir, 'frame-%03d.svg' % n), 'w', encoding='utf-8').write(s)
        open(os.path.join(outdir, 'frame-%03d.txt' % n), 'w', encoding='utf-8').write(t + '\n')
        cards.append('<div style="display:inline-block;vertical-align:top;margin:8px">%s<pre>%s</pre></div>' % (s, html.escape(t)))
    open(os.path.join(outdir, 'index.html'), 'w', encoding='utf-8').write('<!doctype html><meta charset="utf-8"><title>virtual cluster</title>' + '\n'.join(cards))
    return n


# ---- self-test: worked examples from the F1 table ----
def H(s):
    return bytes.fromhex(s.replace(' ', ''))


def selftest():
    fails = []

    def chk(name, cond):
        if not cond:
            fails.append(name)

    def f(d, n):
        return next((v for k, v, m in d['fields'] if k == n), None)

    n31 = H('A5 31 04 FF 30 31 30 30 4D 30 36 31 33 50 4D FF FF FF 30 32 39 39 4D 31 31 FF FF FF D9 7F')
    d = decode(n31, 'ACCESS_LCD')
    chk('31 E1 cs', d['cs_ok'] and d['cs_stored'] == 0xD9)
    chk('31 E1 f', f(d, 'maneuver').startswith('4 = ') and f(d, 'next turn distance') == '100 m' and f(d, 'clock') == '06:13 PM' and f(d, 'remaining distance') == '299 m')
    chk('31 E1 plain-sum model is BAD', not decode(n31, 'MOTORCYCLE')['cs_ok'])
    d = decode(H('A5 31 01 FF 30 32 39 39 4D 30 36 31 33 50 4D FF FF FF FF FF FF FF FF 31 31 FF FF FF EF 7F'), 'ACCESS_LCD')
    chk('31 E2', d['cs_ok'] and f(d, 'remaining distance') == 'blank' and f(d, 'next turn distance') == '299 m')
    d = decode(H('A5 31 0B FF 30 30 34 30 4D 30 39 30 30 41 4D FF FF FF 30 38 2E 35 4B 31 31 FF FF FF EA 7F'), 'ACCESS_LCD')
    chk('31 E3', d['cs_ok'] and f(d, 'remaining distance') == '8.5 km' and f(d, 'clock') == '09:00 AM')
    d = decode(H('A5 32 47 72 61 6E 64 20 4D 6F 74 68 65 72 00 00 00 00 00 00 00 00 4E 31 FF FF FF FF D7 7F'), 'ACCESS_LCD')
    chk('32 E1', d['cs_ok'] and f(d, 'caller') == 'Grand Mother' and f(d, 'state') == 'ringing')
    d = decode(H('A5 32 39 31 20 39 38 37 36 35 00 00 00 00 00 00 00 00 00 00 00 00 57 32 FF FF FF FF AB 7F'), 'ACCESS_LCD')
    chk('32 E2', d['cs_ok'] and f(d, 'caller') == '91 98765' and f(d, 'channel') == 'WhatsApp' and f(d, 'state') == 'answered')
    d = decode(H('A5 33 32 59 FF FF FF 33 31 30 35 31 33 38 4E 4E FF FF FF FF FF FF FF FF FF FF FF FF 4F 7F'), 'ACCESS_LCD')
    chk('33 E1', d['cs_ok'] and f(d, 'clock') == '10:51:38' and f(d, 'speed') == 'blank' and d['upd']['hb']['chg'] and f(d, 'signal bars') == '3')
    d = decode(H('A5 33 35 4E 30 34 32 31 30 39 30 30 30 30 4E 59 FF FF FF FF FF FF FF FF FF FF FF FF BE 7F'), 'ACCESS_LCD')
    chk('33 E2', d['cs_ok'] and f(d, 'speed') == '42 km/h' and f(d, 'phone battery bars') == 'blank' and f(d, 'missed-call flag') == 'Y')
    d = decode(H('A5 34 FF 02 52 61 76 69 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 57 FF FF FF E4 7F'), 'ACCESS_LCD')
    chk('34 E1', d['cs_ok'] and f(d, 'missed count') == 2 and f(d, 'caller') == 'Ravi' and f(d, 'channel') == 'WhatsApp')
    d = decode(H('A5 34 FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4E FF FF FF 81 7F'), 'ACCESS_LCD')
    chk('34 E2', d['cs_ok'] and f(d, 'missed count') == 0)
    d = decode(H('A5 35 FF 59 03 52 61 76 69 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 57 FF FF 88 7F'), 'ACCESS_LCD')
    chk('35 E1', d['cs_ok'] and f(d, 'unread count') == 3 and f(d, 'sender') == 'Ravi' and f(d, 'type') == 'WhatsApp')
    d = decode(H('A5 35 FF 4E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4E FF FF 31 7F'), 'ACCESS_LCD')
    chk('35 E2', d['cs_ok'] and f(d, 'unread count') == 0)
    d = decode(H('A5 36 47 6E 79 61 6E 65 73 68 77 61 72 00 00 00 00 00 00 00 00 00 FF FF FF FF FF 46 01 7F'), 'ACCESS_LCD')
    chk('36 E1', d['cs_ok'] and f(d, 'cluster text') == 'GNYANESHWAR CONNECTED')
    d = decode(H('A5 36 47 6E 79 61 6E 65 73 68 77 61 72 00 00 00 00 00 00 00 00 00 FF FF FF FF FF 52 F5 7F'), 'ACCESS_LCD')
    chk('36 E2', d['cs_ok'] and f(d, 'cluster text') == 'WELCOME GNYANESHWAR')
    d = decode(H('A5 37 30 30 30 30 30 31 36 30 32 30 30 31 39 38 37 30 31 30 37 39 32 01 35 00 00 00 6D 7F'), 'ACCESS_LCD')
    chk('37 E1', d['cs_ok'] and f(d, 'odometer') == '1602 km' and f(d, 'trip A') == '198.7 km' and f(d, 'trip B') == '1079.2 km' and f(d, 'fuel bar') == '5')
    d = decode(H('A5 37 30 30 30 30 30 30 38 39 37 30 30 32 31 38 38 30 30 33 37 33 39 01 35 00 00 00 61 7F'), 'ACCESS_LCD')
    chk('37 E2', d['cs_ok'] and f(d, 'odometer') == '897 km' and f(d, 'trip A') == '218.8 km' and f(d, 'trip B') == '373.9 km')
    bad = bytearray(H('A5 37 30 30 30 30 30 31 36 30 32 30 30 31 39 38 37 30 31 30 37 39 32 01 35 00 00 00 6D 7F'))
    bad[5] = 0x36
    chk('37 E3 bad checksum', not decode(bytes(bad), 'ACCESS_LCD')['cs_ok'])
    chk('log parser', len(parse_log('TX model=TFT type=0x31 hex=' + n31.hex() + '\n' + n31.hex(), 'ACCESS_LCD')) == 2)
    chk('log parser ignores Bytes sent', parse_log('Bytes sent : ' + n31.hex(' ') + '\n', 'ACCESS_LCD') == [])
    chk('log parser --bare reads Bytes sent', len(parse_log('Bytes sent : ' + n31.hex(' '), 'ACCESS_LCD', bare=True)) == 1)
    chk('log parser TX model= picks model', parse_log('TX model=MOTORCYCLE type=0x31 hex=' + n31.hex(), 'ACCESS_LCD')[0][0] == 'MOTORCYCLE')
    for c in range(0, 60):  # every code must render without error
        arrow_svg(c, '#000')
    print('SELFTEST FAIL: ' + ', '.join(fails) if fails else 'SELFTEST OK (all checks passed)')
    return 1 if fails else 0


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('log', nargs='?')
    p.add_argument('--model', default='ACCESS_LCD', choices=MODELS)
    p.add_argument('--layout', choices=['LCD', 'TFT'])
    p.add_argument('--out', default='cluster-out')
    p.add_argument('--bare', action='store_true', help='also decode frames in lines that have other text')
    p.add_argument('--selftest', action='store_true')
    a = p.parse_args()
    if a.selftest:
        sys.exit(selftest())
    if not a.log:
        p.error('give a log file, - for stdin, or --selftest')
    data = sys.stdin.read() if a.log == '-' else open(a.log, encoding='utf-8', errors='replace').read()
    n = run(data, a.model, a.layout, a.out, a.bare)
    print('%d frames -> %s (frame-NNN.svg, frame-NNN.txt, index.html)' % (n, a.out))
