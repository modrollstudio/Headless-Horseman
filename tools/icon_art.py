"""Draws the mod icon in the Critfall family's pixel style: a 16x16 sprite with a dark outline on a framed panel.
Writes branding/horseman-logo.svg, PNGs at 64, 128, 256 and 512 in branding/, and the 128 one as the mod's logo.png.
Run from the repo root with Python 3; the PNGs are rendered with rsvg-convert, like the other family icons."""
import os
import shutil
import subprocess

# 16x16 sprite: the carved head as it looks in game, with a leaning flame, a smaller tongue and the stem on top.
# One character per pixel, see COLOURS.
SPRITE = (
    '....o...........',
    '...oro..........',
    '...oyro......e..',
    '...oyYro........',
    '...oyYYro.o.....',
    '..oyYYyrooro.so.',
    '.ooyYYyyrooyoSo.',
    '.ohPPpPPPPpPPdo.',
    '.ohPgGpPPpGgPdo.',
    '.ohPgGGPPGGgPdo.',
    '.ohPPpPPPPpPPdo.',
    '.ohPgPpPPpPgPdo.',
    '.ohPgGpGGpGgPdo.',
    '.ohPPgGGpGgPPdo.',
    '..ohPpPPPPpPdo..',
    '...oooooooooo...',
)

COLOURS = {
    'o': '#0d1126',  # outline
    'h': '#e88624',  # pumpkin, lit
    'P': '#ce6614',  # pumpkin
    'p': '#aa4a0c',  # pumpkin, ribs and teeth
    'd': '#843408',  # pumpkin, shadow
    'g': '#ffc848',  # carving glow
    'G': '#ffec96',  # carving glow, hottest
    's': '#3c4a1a',  # stem
    'S': '#64782c',  # stem, lit
    'r': '#e0462f',  # fire
    'y': '#ffb84a',  # fire, hot
    'Y': '#fff4c8',  # fire, white-hot
    'e': '#ff8a3a',  # ember
}

# Panel: outer border, shadow edge, lit edge, fill.
FRAME = ('#06070d', '#0d0c1f', '#2b2a55', '#1b1a38')

BRANDING = 'branding/'


def svg():
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 290 290" shape-rendering="crispEdges">',
           '<rect width="290" height="290" fill="%s"/>' % FRAME[0],
           '<rect x="5" y="5" width="280" height="280" fill="%s"/>' % FRAME[1],
           '<rect x="5" y="5" width="275" height="275" fill="%s"/>' % FRAME[2],
           '<rect x="10" y="10" width="270" height="270" fill="%s"/>' % FRAME[3]]
    for row, line in enumerate(SPRITE):
        col = 0
        while col < 16:
            ch = line[col]
            run = 1
            while col + run < 16 and line[col + run] == ch:
                run += 1
            if ch != '.':
                out.append('<rect x="%d" y="%d" width="%d" height="16" fill="%s"/>'
                           % (17 + col * 16, 17 + row * 16, run * 16, COLOURS[ch]))
            col += run
    out.append('</svg>')
    return '\n'.join(out)


def main():
    os.makedirs(BRANDING, exist_ok=True)
    path = BRANDING + 'horseman-logo.svg'
    with open(path, 'w') as f:
        f.write(svg())
    for size in (64, 128, 256, 512):
        subprocess.run(['rsvg-convert', '-w', str(size), '-h', str(size), '-o',
                        BRANDING + 'horseman-icon-%d.png' % size, path], check=True)
    shutil.copy(BRANDING + 'horseman-icon-128.png', 'common/src/main/resources/logo.png')


if __name__ == '__main__':
    main()
