"""Writes assets/critfall_horseman/sounds.json and the synthesised sounds. Run from the repo root with Python 3,
numpy and ffmpeg (with libvorbis). Deterministic.

Each cue is a list of layers, written as events <cue>_1, <cue>_2, ... that HorsemanSounds plays together. A layer is a
vanilla sound referenced by name (never copied), with its own pitch and volume, or one of the synthesised sounds in
sounds/synth/. Referencing a vanilla event lets the game pick one of its recordings; referencing a file always plays
that recording, which keeps the layers of a laugh in sync.

The laughs layer vanilla voices pitched down to the game's minimum of 0.5, the same recording doubled a few percent
apart, with a synthesised fire burst under each."""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

ASSETS = 'common/src/main/resources/assets/critfall_horseman/'
SOUNDS = ASSETS + 'sounds/'
RATE = 44100


def ev(name, volume=1.0, pitch=1.0):
    """A vanilla sound event; the game picks one of its recordings."""
    return {'name': 'minecraft:' + name, 'type': 'event', 'volume': volume, 'pitch': pitch}


def rec(name, volume=1.0, pitch=1.0):
    """One specific vanilla recording."""
    return {'name': 'minecraft:' + name, 'volume': volume, 'pitch': pitch}


def own(name, volume=1.0, pitch=1.0):
    """A sound synthesised by this script."""
    return {'name': 'critfall_horseman:synth/' + name, 'volume': volume, 'pitch': pitch}


# Each cue is a list of layers; each layer is a list of sounds the game picks one of.
CUES = {
    # laugh_1 is the one used on arrival and at sunrise.
    'laugh_1': [
        [rec('entity/witch/ambient4', 1.0, 0.5)],
        [rec('entity/witch/ambient4', 0.5, 0.53)],
        [rec('mob/evocation_illager/celebrate', 0.75, 0.5)],
        [own('fire_burst', 0.6, 0.9)],
    ],
    'laugh_2': [
        [rec('entity/witch/celebrate', 0.9, 0.5)],
        [rec('entity/witch/ambient4', 0.8, 0.5)],
        [rec('mob/ravager/celebrate1', 0.35, 0.5)],
        [own('fire_burst', 0.5, 0.8)],
    ],
    'laugh_3': [
        [rec('mob/evocation_illager/celebrate', 1.0, 0.5)],
        [rec('mob/evocation_illager/celebrate', 0.5, 0.53)],
        [rec('entity/witch/ambient4', 0.45, 0.55)],
        [own('fire_burst', 0.4, 1.1)],
    ],
    'growl': [
        [ev('entity.hoglin.angry', 1.0, 0.5), ev('entity.zoglin.angry', 1.0, 0.5)],
        [ev('entity.piglin_brute.angry', 0.6, 0.6)],
    ],
    'roar': [
        [ev('entity.ravager.roar', 1.0, 0.7)],
        [ev('entity.warden.roar', 0.4, 0.7)],
        [own('fire_roar', 1.0, 0.9)],
    ],
    'scream': [
        [ev('entity.ghast.death', 1.0, 0.6)],
        [ev('entity.witch.death', 0.6, 0.5)],
        [own('fire_burst', 0.6, 0.6)],
    ],
    'hoofbeats': [
        [ev('entity.horse.gallop', 1.0, 0.55), ev('entity.horse.gallop', 1.0, 0.5)],
        [ev('block.basalt.step', 0.8, 0.5), ev('block.deepslate.step', 0.8, 0.5)],
    ],
    'steed_scream': [
        [ev('entity.horse.death', 1.0, 0.6), ev('entity.horse.angry', 1.0, 0.55)],
        [ev('entity.skeleton_horse.death', 0.8, 0.6)],
        [ev('entity.zombie_horse.death', 0.5, 0.5)],
    ],
    'steed_breath': [
        [ev('entity.horse.breathe', 0.8, 0.6), ev('entity.skeleton_horse.ambient', 0.6, 0.5)],
    ],
    'cackle': [
        [rec('entity/witch/ambient4', 0.9, 0.6), rec('entity/witch/celebrate', 0.8, 0.65)],
        [own('crackle', 0.7, 1.0)],
    ],
    'sword_fire': [
        [ev('item.firecharge.use', 0.7, 0.7)],
        [own('fire_burst', 0.5, 1.3)],
    ],
    'head_whoosh': [
        [own('whoosh', 1.0, 1.0)],
        [own('fire_roar', 0.6, 1.2)],
    ],
    'impact': [
        [ev('entity.iron_golem.attack', 1.0, 0.7), ev('entity.player.attack.knockback', 1.0, 0.6)],
        [ev('entity.horse.land', 0.8, 0.6)],
    ],
    'crumble': [
        [ev('entity.blaze.death', 0.9, 0.5), ev('block.basalt.break', 1.0, 0.6)],
        [own('crackle', 0.8, 0.7)],
    ],
    'fire_roar': [
        [own('fire_roar')],
    ],
    'hiss': [
        [ev('block.fire.extinguish', 1.0, 0.7)],
        [ev('block.lava.extinguish', 0.8, 0.6)],
    ],
    # Stings played with his lines: a low bell and a synthesised boom.
    'sting_arrival': [
        [rec('block/bell/bell_use01', 1.0, 0.5)],
        [rec('block/bell/resonate', 0.6, 0.5)],
        [own('boom', 1.0, 0.9)],
    ],
    'sting_headless': [
        [own('fire_burst', 1.0, 0.8)],
        [rec('mob/ghast/fireball4', 0.7, 0.5)],
        [own('boom', 0.6, 1.0)],
    ],
    'sting_chase': [
        [own('boom', 0.9, 1.1)],
        [rec('mob/guardian/curse', 0.35, 0.5)],
    ],
    'sting_retake': [
        [own('fire_burst', 0.9, 0.7)],
        [rec('block/bell/bell_use02', 0.8, 0.6)],
        [own('boom', 0.6, 0.9)],
    ],
    'sting_steed': [
        [rec('block/bell/bell_use02', 0.9, 0.5)],
        [own('boom', 0.9, 0.8)],
    ],
    'sting_sunrise': [
        [rec('block/bell/bell_use01', 0.9, 0.75)],
        [rec('block/bell/resonate', 0.5, 0.75)],
        [own('whoosh', 0.5, 0.8)],
    ],
    'sting_kill': [
        [rec('block/bell/bell_use01', 1.0, 0.5)],
        [own('boom', 1.0, 0.8)],
    ],
}

SUBTITLES = {
    'laugh': 'subtitles.critfall_horseman.laugh', 'growl': 'subtitles.critfall_horseman.growl',
    'roar': 'subtitles.critfall_horseman.roar', 'scream': 'subtitles.critfall_horseman.scream',
    'hoofbeats': 'subtitles.critfall_horseman.hoofbeats', 'steed_scream': 'subtitles.critfall_horseman.steed_scream',
    'steed_breath': 'subtitles.critfall_horseman.steed_breath', 'cackle': 'subtitles.critfall_horseman.cackle',
    'sword_fire': 'subtitles.critfall_horseman.sword_fire', 'head_whoosh': 'subtitles.critfall_horseman.head_whoosh',
    'impact': 'subtitles.critfall_horseman.impact', 'crumble': 'subtitles.critfall_horseman.crumble',
    'fire_roar': 'subtitles.critfall_horseman.fire_roar', 'sting': 'subtitles.critfall_horseman.sting',
    'hiss': 'subtitles.critfall_horseman.hiss',
}


# --- Making sounds ----------------------------------------------------------------------------------------

def write_wav(path, samples):
    data = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data.tobytes())


def to_ogg(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', src, '-ac', '1', '-ar', str(RATE), '-c:a', 'libvorbis',
                    '-q:a', '5', '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', dst],
                   check=True)


def lowpass(x, cutoff):
    """One-pole low-pass filter; `cutoff` in Hz, either a constant or one value per sample."""
    a = np.exp(-2 * np.pi * np.asarray(cutoff, float) / RATE) * np.ones_like(x)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def crackles(rng, n, count, span, length=60):
    out = np.zeros(n)
    for at in rng.uniform(span[0], span[1], count):
        i = int(at * RATE)
        k = min(length, n - i)
        out[i:i + k] += rng.uniform(-1, 1, k) * np.exp(-np.arange(k) / (length / 5)) * rng.uniform(0.3, 1.0)
    return out


def normal(x, peak=0.9):
    return x / np.max(np.abs(x)) * peak


def fire_roar():
    """Sustained fire: low rumble and hiss, fast attack, slow decay, with crackles."""
    rng = np.random.default_rng(31)
    n = int(RATE * 2.4)
    t = np.arange(n) / RATE
    white = rng.standard_normal(n)
    rumble = lowpass(white, 180) * 6
    hiss = lowpass(white, 2600) - lowpass(white, 600)
    envelope = np.minimum(1, t / 0.18) * np.exp(-np.maximum(0, t - 0.25) * 1.3)
    flutter = 1 + 0.35 * np.sin(2 * np.pi * 7 * t) * rng.uniform(0.6, 1, n)
    out = (rumble * 0.8 + hiss * 0.5) * envelope * flutter + crackles(rng, n, 40, (0.1, 2.2)) * 0.3 * envelope
    return normal(out)


def fire_burst():
    """A short burst of fire: a low thump and a rush of noise."""
    rng = np.random.default_rng(7)
    n = int(RATE * 1.2)
    t = np.arange(n) / RATE
    white = rng.standard_normal(n)
    whump = lowpass(white, 120) * 8 * np.exp(-t * 6)
    rush = (lowpass(white, 3200) - lowpass(white, 500)) * np.minimum(1, t / 0.04) * np.exp(-t * 3.2)
    out = whump + rush * 0.8 + crackles(rng, n, 18, (0.05, 0.9)) * 0.4 * np.exp(-t * 2)
    return normal(out)


def crackle():
    """Burning crackle loop for the head on the ground."""
    rng = np.random.default_rng(19)
    n = int(RATE * 2.0)
    t = np.arange(n) / RATE
    white = rng.standard_normal(n)
    bed = (lowpass(white, 900) - lowpass(white, 150)) * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * t))
    out = bed * 0.5 + crackles(rng, n, 50, (0.0, 1.9), 40)
    fade = np.minimum(1, t / 0.1) * np.minimum(1, (2.0 - t) / 0.3)
    return normal(out * fade, 0.8)


def whoosh():
    """Band-passed noise sweeping up and down, for something flying past."""
    rng = np.random.default_rng(13)
    n = int(RATE * 1.1)
    t = np.arange(n) / RATE
    white = rng.standard_normal(n)
    sweep = 400 + 2400 * np.exp(-((t - 0.45) / 0.22) ** 2)
    band = lowpass(white, sweep) - lowpass(white, sweep * 0.35)
    envelope = np.exp(-((t - 0.45) / 0.3) ** 2)
    return normal(band * envelope + lowpass(white, 150) * 2 * envelope, 0.85)


def boom():
    """Low hit for the stings: two detuned tones with a sharp attack and a long decay."""
    rng = np.random.default_rng(3)
    n = int(RATE * 1.8)
    t = np.arange(n) / RATE
    drop = 1 + 0.25 * np.exp(-t * 8)  # slight downward pitch bend at the attack
    tone = np.sin(2 * np.pi * 49 * drop * t) + 0.8 * np.sin(2 * np.pi * 52 * drop * t)
    tone += 0.3 * np.sin(2 * np.pi * 98 * drop * t) * np.exp(-t * 3)
    hit = lowpass(rng.standard_normal(n), 400) * 4 * np.exp(-t * 25)
    out = np.tanh(1.6 * (tone * np.exp(-t * 2.2) + hit)) * np.minimum(1, t / 0.004)
    return normal(out)


MADE = {'fire_roar': fire_roar, 'fire_burst': fire_burst, 'crackle': crackle, 'whoosh': whoosh, 'boom': boom}


def make():
    for old in os.listdir(SOUNDS + 'synth') if os.path.isdir(SOUNDS + 'synth') else []:
        if os.path.splitext(old)[0] not in MADE:
            os.remove(SOUNDS + 'synth/' + old)
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in MADE.items():
            path = os.path.join(tmp, name + '.wav')
            write_wav(path, fn())
            to_ogg(path, SOUNDS + 'synth/' + name + '.ogg')


def main():
    make()
    events = {}
    for cue, layers in CUES.items():
        family = 'sting' if cue.startswith('sting_') else cue.split('_')[0] if cue.startswith('laugh_') else cue
        for k, sounds in enumerate(layers, 1):
            event = {'sounds': sounds}
            if k == 1 and family in SUBTITLES:
                event['subtitle'] = SUBTITLES[family]
            events['%s_%d' % (cue, k)] = event
    with open(ASSETS + 'sounds.json', 'w') as f:
        f.write(json.dumps(events, indent=2) + '\n')
    print('sounds.json: %d cues, %d events' % (len(CUES), len(events)))


if __name__ == '__main__':
    main()
