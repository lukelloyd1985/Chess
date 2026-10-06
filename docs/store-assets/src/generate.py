#!/usr/bin/env python3
"""Generates the HTML sources for the Play Store graphics (run render.js afterwards to screenshot them).

The pages are recreations of the app's real screens (SignInScreen, GameScreen, AnalysisScreen,
SettingsScreen) built from the app's own colours and the same bundled piece artwork
(app/src/main/assets/pieces/) - not captures from a running device.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))

# --- app colours (ui/theme/Theme.kt, settings/AppSettings.kt) -----------------------------------
BG, SURFACE, SURFACE_HI = '#262421', '#312E2B', '#3C3935'
GREEN, MUTED, TEXT = '#81B64C', '#A09D98', '#F1F1F1'
THEMES = {
    'GREEN': ('Green', '#EEEED2', '#769656'), 'BROWN': ('Brown', '#F0D9B5', '#B58863'),
    'BLUE': ('Blue', '#DEE3E6', '#8CA2AD'), 'GREY': ('Grey', '#DADADA', '#8A8A8A'),
    'PURPLE': ('Purple', '#E9E1F2', '#8877B7'), 'CORAL': ('Coral', '#F4E4DD', '#C57F73'),
}
PIECE_COLORS = {
    'STANDARD': ('#FFFFFF', '#1B1B1B'), 'IVORY': ('#F7EBD0', '#2E1D12'), 'GOLD': ('#F4C542', '#1F2E4F'),
}
QUALITY = {'best': '#81B64C', 'excellent': '#96BC4B', 'good': '#A3A3A3',
           'inaccuracy': '#F7C631', 'mistake': '#E58F2A', 'blunder': '#CA3431'}


def load_pieces(name):
    out = {}
    with open(os.path.join(ROOT, 'app/src/main/assets/pieces', name + '.txt')) as f:
        for line in f:
            k, _, d = line.rstrip('\n').partition('=')
            out[k] = d
    return out


def knight_path():
    xml = open(os.path.join(ROOT, 'app/src/main/res/drawable/ic_launcher_foreground.xml')).read()
    return re.search(r'pathData="([^"]+)"', xml).group(1)  # 108 x 108 viewport, centred


def sq_name_to_xy(sq, flipped=False):
    f, r = ord(sq[0]) - 97, int(sq[1]) - 1
    return (7 - f, r) if flipped else (f, 7 - r)


def board_svg(fen, size, theme='GREEN', pieces='classic', colors='STANDARD', last=None, arrow=None,
              labels=True, outline_white=False, stroke=2.5, extra_style=''):
    _, light, dark = THEMES[theme]
    white_fill, black_fill = PIECE_COLORS[colors]
    art = load_pieces(pieces)
    s = size / 8
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
             f'viewBox="0 0 {size} {size}" style="display:block;{extra_style}">']
    for r in range(8):
        for f in range(8):
            is_light = (f + (7 - r)) % 2 == 1
            parts.append(f'<rect x="{f*s}" y="{r*s}" width="{s}" height="{s}" fill="{light if is_light else dark}"/>')
    if last:
        for sq in last:
            x, y = sq_name_to_xy(sq)
            parts.append(f'<rect x="{x*s}" y="{y*s}" width="{s}" height="{s}" fill="#F6F669" fill-opacity="0.5"/>')
    if labels:
        for i in range(8):
            # Rank numbers sit in the a-file squares, file letters in the bottom-rank squares, each in
            # the opposite colour of its square (as the app draws them).
            rank_sq_light = (0 + (7 - i)) % 2 == 1
            file_sq_light = (i + 0) % 2 == 1
            parts.append(f'<text x="5" y="{i*s+s*0.24}" font-size="{s*0.2}" font-weight="700" '
                         f'fill="{dark if rank_sq_light else light}" font-family="sans-serif">{8-i}</text>')
            parts.append(f'<text x="{i*s+s-5}" y="{size-6}" text-anchor="end" font-size="{s*0.2}" font-weight="700" '
                         f'fill="{dark if file_sq_light else light}" font-family="sans-serif">{chr(97+i)}</text>')
    rows = fen.split()[0].split('/')
    for r, row in enumerate(rows):
        f = 0
        for ch in row:
            if ch.isdigit():
                f += int(ch)
                continue
            white = ch.isupper()
            if outline_white and white:
                key, fill, fr = 'w' + ch.upper(), white_fill, 'evenodd'
            else:
                key, fill, fr = 'b' + ch.upper(), (white_fill if white else black_fill), 'nonzero'
            lum = int(fill[1:3], 16) * 0.3 + int(fill[3:5], 16) * 0.59 + int(fill[5:7], 16) * 0.11
            line = '#222222' if lum > 128 else '#DDDDDD'
            sc = s * 0.92 / 100
            parts.append(f'<g transform="translate({f*s + s*0.04},{r*s + s*0.04}) scale({sc})">'
                         f'<path d="{art[key]}" fill="{fill}" fill-rule="{fr}" stroke="{line}" stroke-width="{stroke}" '
                         f'stroke-linejoin="round"/></g>')
            f += 1
    if arrow:
        (x0, y0), (x1, y1) = [tuple(v * s + s / 2 for v in sq_name_to_xy(a)) for a in arrow]
        import math
        ang = math.atan2(y1 - y0, x1 - x0)
        head = s * 0.38
        sx, sy = x1 - math.cos(ang) * head * 0.6, y1 - math.sin(ang) * head * 0.6
        lx, ly = x1 - math.cos(ang - 0.5) * head, y1 - math.sin(ang - 0.5) * head
        rx, ry = x1 - math.cos(ang + 0.5) * head, y1 - math.sin(ang + 0.5) * head
        parts.append(f'<line x1="{x0}" y1="{y0}" x2="{sx}" y2="{sy}" stroke="{GREEN}" stroke-opacity="0.9" '
                     f'stroke-width="{s*0.17}" stroke-linecap="round"/>'
                     f'<polygon points="{x1},{y1} {lx},{ly} {rx},{ry}" fill="{GREEN}" fill-opacity="0.9"/>')
    parts.append('</svg>')
    return ''.join(parts)


def piece_svg(letter, white, size, pieces='classic', colors='STANDARD', outline_white=False, stroke=2.5):
    art = load_pieces(pieces)
    wf, bf = PIECE_COLORS[colors]
    if outline_white and white:
        key, fill, fr = 'w' + letter, wf, 'evenodd'
    else:
        key, fill, fr = 'b' + letter, (wf if white else bf), 'nonzero'
    lum = int(fill[1:3], 16) * 0.3 + int(fill[3:5], 16) * 0.59 + int(fill[5:7], 16) * 0.11
    line = '#222222' if lum > 128 else '#DDDDDD'
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 100 100"><path d="{art[key]}" fill="{fill}" '
            f'fill-rule="{fr}" stroke="{line}" stroke-width="{stroke}" stroke-linejoin="round"/></svg>')


def knight_svg(size, color=GREEN):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 108 108"><path d="{knight_path()}" fill="{color}"/></svg>')


CSS = f'''
*{{box-sizing:border-box;margin:0;padding:0}}
body{{background:{BG};color:{TEXT};font-family:Roboto,"Helvetica Neue",Arial,system-ui,sans-serif;overflow:hidden}}
.screen{{width:1080px;height:1920px;display:flex;flex-direction:column;padding-top:56px}}
.status{{position:absolute;top:0;left:0;right:0;height:56px;display:flex;justify-content:space-between;
  align-items:center;padding:0 44px;font-size:28px;color:{TEXT}}}
.header{{display:flex;align-items:center;height:150px;padding:0 24px;font-size:58px;font-weight:700}}
.header .back{{width:110px;font-size:64px;font-weight:400}}
.muted{{color:{MUTED}}}
.bar{{display:flex;align-items:center;justify-content:space-between;padding:20px 30px}}
.bar .name{{font-size:44px;font-weight:600}}
.bar .sub{{font-size:34px;color:{MUTED};margin-top:4px}}
.clock{{font-size:58px;font-weight:700;padding:8px 34px;border-radius:18px;background:{SURFACE}}}
.clock.active{{background:{SURFACE_HI}}}
.moves{{background:{SURFACE};padding:26px 30px;font-size:38px;display:flex;gap:18px;white-space:nowrap;overflow:hidden}}
.moves .n{{color:{MUTED}}}
.actions{{display:flex;justify-content:space-evenly;margin-top:22px}}
.action{{display:flex;flex-direction:column;align-items:center;font-size:30px;color:{TEXT};padding:12px 26px}}
.action .g{{font-size:62px;line-height:1.1;font-weight:700}}
.card{{background:{SURFACE};border-radius:36px;padding:44px}}
.badge{{display:inline-block;border-radius:50px;padding:6px 26px;color:#000;font-weight:700;font-size:36px}}
'''


def status_bar():
    return '<div class="status"><span>9:41</span><span>▲ ◀ ▮▮▮</span></div>'


def page(body, extra_css=''):
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}{extra_css}</style></head><body>{body}</body></html>'


ITALIAN = 'r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R b KQkq - 0 5'
AFTER_NXE4 = 'r1bqk2r/pppp1ppp/2n5/2b1p3/2B1n3/2NP1N2/PPP2PPP/R1BQK2R w KQkq - 0 6'


def sign_in():
    body = f'''{status_bar()}<div class="screen" style="align-items:center;justify-content:center;padding:0 90px">
  {knight_svg(400)}
  <div style="font-size:150px;font-weight:700;margin-top:10px">Chess</div>
  <div class="muted" style="font-size:46px;text-align:center;line-height:1.35;margin:26px 0 120px">
    Play the computer, challenge friends, and review every game.</div>
  <div style="width:100%;height:150px;border-radius:75px;background:{GREEN};color:#000;display:flex;align-items:center;
    justify-content:center;font-size:48px;font-weight:700">Sign in with Google</div>
  <div style="width:100%;height:140px;border-radius:70px;border:3px solid {MUTED};color:{TEXT};display:flex;
    align-items:center;justify-content:center;font-size:42px;margin-top:34px">Continue as guest (offline play only)</div>
</div>'''
    return page(body)


def game():
    board = board_svg(ITALIAN, 1080, last=['d2', 'd3'])
    mv = ''.join(f'<span class="n">{i+1}.</span><span>{a}</span><span>{b}</span>' for i, (a, b) in
                 enumerate([('e4', 'e5'), ('Nf3', 'Nc6'), ('Bc4', 'Bc5'), ('Nc3', 'Nf6')]))
    mv += '<span class="n">5.</span><span style="font-weight:700">d3</span>'
    body = f'''{status_bar()}<div class="screen">
  <div class="header"><span class="back">←</span>Game</div>
  <div class="bar"><div><div class="name">Clubber Cam (1000)</div></div>
    <div class="clock active">4:12</div></div>
  {board}
  <div class="bar"><div><div class="name">You</div></div>
    <div class="clock">3:58</div></div>
  <div class="moves">{mv}</div>
  <div class="actions">
    <div class="action"><span class="g">⇅</span>Flip</div><div class="action"><span class="g">💡</span>Hint</div>
    <div class="action"><span class="g">↶</span>Undo</div><div class="action"><span class="g">½</span>Draw</div>
    <div class="action"><span class="g">⚑</span>Resign</div></div>
</div>'''
    return page(body)


def analysis():
    board = board_svg(AFTER_NXE4, 900, last=['f6', 'e4'], arrow=('c3', 'e4'), extra_style='margin:0 auto')
    pts = [50, 52, 51, 55, 54, 58, 57, 64, 61, 70, 66, 63, 72, 78, 74, 81, 85, 83, 88]
    w, h = 1000, 120
    poly = ' '.join(f'{i*w/(len(pts)-1):.0f},{h - p/100*h:.0f}' for i, p in enumerate(pts))
    graph = (f'<svg width="{w}" height="{h}" style="background:#403D39;border-radius:20px;margin:0 40px;display:block">'
             f'<polygon points="0,{h} {poly} {w},{h}" fill="#EDEDED"/><line x1="0" y1="{h/2}" x2="{w}" y2="{h/2}" '
             f'stroke="#00000066"/><line x1="{9*w/18}" y1="0" x2="{9*w/18}" y2="{h}" stroke="{GREEN}" stroke-width="5"/></svg>')

    def row(q, label, wn, bn):
        return (f'<div style="display:flex;align-items:center;justify-content:space-between;font-size:38px;margin:7px 0">'
                f'<b style="width:90px">{wn}</b><span style="flex:1;text-align:center"><span class="badge" '
                f'style="background:{QUALITY[q]};font-size:30px">{label}</span></span><b style="width:90px;text-align:right">{bn}</b></div>')
    body = f'''{status_bar()}<div class="screen">
  <div class="header"><span class="back">←</span>Game analysis</div>
  <div style="padding:0 40px 14px;font-size:38px;font-weight:600">You vs Clubber Cam (1000)</div>
  <div style="height:26px;margin:0 40px 14px;border-radius:10px;background:#403D39;overflow:hidden"><div style="width:68%;height:100%;background:#fff"></div></div>
  {board}
  <div style="padding:18px 40px 12px"><div style="display:flex;align-items:center;font-size:42px;font-weight:600">
    <span class="badge" style="background:{QUALITY['mistake']}">?</span>&nbsp; 5... Nxe4 is a mistake</div>
    <div class="muted" style="font-size:34px;margin-top:6px">Best was d5 · Eval +0.92 · depth 18</div></div>
  {graph}
  <div style="display:flex;gap:30px;padding:22px 40px 0">
    <div class="card" style="flex:1;text-align:center;padding:18px"><div class="muted" style="font-size:32px">You</div>
      <div style="font-size:78px;font-weight:700;color:{GREEN}">78.4</div><div class="muted" style="font-size:28px">accuracy</div></div>
    <div class="card" style="flex:1;text-align:center;padding:18px"><div class="muted" style="font-size:32px">Clubber Cam</div>
      <div style="font-size:78px;font-weight:700;color:{GREEN}">91.2</div><div class="muted" style="font-size:28px">accuracy</div></div></div>
  <div style="padding:14px 70px 0">{row('best','Best',9,12)}{row('inaccuracy','Inaccuracy',3,1)}{row('mistake','Mistake',2,0)}{row('blunder','Blunder',1,0)}</div>
</div>'''
    return page(body)


def appearance():
    theme, pieces_name, colors = 'BROWN', 'serif', 'IVORY'
    board = board_svg(ITALIAN, 780, theme=theme, pieces=pieces_name, colors=colors, last=['d2', 'd3'])
    swatch = ''
    for key, (label, light, dark) in THEMES.items():
        sel = f'border:8px solid {GREEN}' if key == theme else f'border:3px solid {SURFACE_HI}'
        swatch += (f'<div style="text-align:center"><div style="width:150px;height:150px;border-radius:24px;{sel};overflow:hidden;'
                   f'display:grid;grid-template-columns:1fr 1fr"><i style="background:{light}"></i><i style="background:{dark}"></i>'
                   f'<i style="background:{dark}"></i><i style="background:{light}"></i></div>'
                   f'<div class="muted" style="font-size:28px;margin-top:6px">{label}</div></div>')
    sets = ''
    for label, name, outline in (('Classic', 'classic', False), ('Serif', 'serif', False), ('Outline', 'classic', True), ('Pixel', 'pixel', False)):
        sel = f'border:8px solid {GREEN}' if name == pieces_name and not outline else f'border:3px solid {SURFACE_HI}'
        st = 1.5 if name == 'pixel' else 2.5
        sets += (f'<div style="border-radius:26px;{sel};overflow:hidden;text-align:center"><div style="display:flex">'
                 f'<div style="width:110px;height:110px;background:{THEMES[theme][2]};padding:8px">{piece_svg("N", True, 94, name, colors, outline, st)}</div>'
                 f'<div style="width:110px;height:110px;background:{THEMES[theme][1]};padding:8px">{piece_svg("N", False, 94, name, colors, outline, st)}</div></div>'
                 f'<div style="font-size:30px;padding:6px">{label}</div></div>')
    chips = ''.join(f'<span style="border:3px solid {GREEN if i==1 else MUTED};background:{"#4C5B3A" if i==1 else "transparent"};'
                    f'border-radius:20px;padding:14px 30px;font-size:34px">{t}</span>'
                    for i, t in enumerate(('White & Black', 'Ivory & Ebony', 'Gold & Navy')))
    body = f'''{status_bar()}<div class="screen">
  <div class="header"><span class="back">←</span>Appearance</div>
  <div style="display:flex;justify-content:center">{board}</div>
  <div style="padding:28px 40px 0"><div style="font-size:38px;font-weight:700;margin-bottom:14px">Board</div>
    <div style="display:flex;gap:22px">{swatch}</div>
    <div style="font-size:38px;font-weight:700;margin:34px 0 14px">Pieces</div><div style="display:flex;gap:22px">{sets}</div>
    <div style="font-size:38px;font-weight:700;margin:34px 0 14px">Piece colours</div><div style="display:flex;gap:18px">{chips}</div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-top:36px;font-size:40px;font-weight:600">
      Show coordinates<span style="width:120px;height:64px;border-radius:32px;background:{GREEN};position:relative;display:inline-block">
      <i style="position:absolute;right:8px;top:8px;width:48px;height:48px;border-radius:24px;background:#000"></i></span></div></div>
</div>'''
    return page(body)


def icon():
    return (f'<!doctype html><html><body style="margin:0;background:{BG};width:512px;height:512px;display:flex;'
            f'align-items:center;justify-content:center">{knight_svg(512)}</body></html>')


def feature():
    board = board_svg(ITALIAN, 370, theme='GREEN', last=['d2', 'd3'], arrow=('f6', 'e4'), labels=False,
                      extra_style='border-radius:18px;box-shadow:0 18px 40px rgba(0,0,0,.5)')
    # The knight glyph only fills ~55% of its 108-unit viewport, so size the svg up and pull it in.
    return f'''<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head>
<body style="width:1024px;height:500px;background:linear-gradient(120deg,#1E1C1A 0%,#312E2B 100%);position:relative">
  <div style="position:absolute;left:30px;top:70px;display:flex;align-items:center">{knight_svg(250)}
    <div style="font-size:112px;font-weight:700;letter-spacing:-2px;margin-left:-30px">Chess</div></div>
  <div style="position:absolute;left:78px;top:276px;font-size:42px;line-height:1.3;color:{TEXT}">Play bots &amp; friends.<br>
    <span style="color:{GREEN};font-weight:700">Analyze every game.</span></div>
  <div style="position:absolute;right:62px;top:62px;transform:rotate(4deg)">{board}</div>
</body></html>'''


FILES = {
    'icon.html': icon, 'feature-graphic.html': feature, 'screenshot-signin.html': sign_in,
    'screenshot-game.html': game, 'screenshot-analysis.html': analysis, 'screenshot-appearance.html': appearance,
}

if __name__ == '__main__':
    for name, fn in FILES.items():
        with open(os.path.join(HERE, name), 'w') as f:
            f.write(fn())
        print('wrote', name)
