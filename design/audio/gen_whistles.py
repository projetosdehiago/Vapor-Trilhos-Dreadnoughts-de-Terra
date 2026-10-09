#!/usr/bin/env python3
"""Sintetiza os sons de apito do landship (originais, sem amostras de terceiros).

Uso: python3 design/audio/gen_whistles.py
Precisa de numpy e do ffmpeg (para gravar OGG Vorbis). Grava em
src/main/resources/assets/vapor_trilhos/sounds/whistle/<nome>.ogg, sempre em mono: o jogo só
diminui com a distância os sons mono.

Cada som é montado por síntese aditiva (soma de harmônicos), com envelope, ruído filtrado
para o "sopro" e uma reverberação simples (Schroeder) para soar ao ar livre.
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "vapor_trilhos" / "sounds" / "whistle"
SR = 44100
RNG = np.random.default_rng(1902)


# ---------------------------------------------------------------------------------------------
# Blocos básicos
# ---------------------------------------------------------------------------------------------

def timeline(seconds):
    return np.arange(int(seconds * SR)) / SR


def phase(freq):
    """Fase acumulada para uma frequência que muda no tempo (evita estalos nas glissandos)."""
    return 2 * np.pi * np.cumsum(freq) / SR


def envelope(t, attack, release, total):
    env = np.ones_like(t)
    a = t < attack
    env[a] = (t[a] / attack) ** 0.7
    r = t > total - release
    env[r] = np.clip((total - t[r]) / release, 0, 1) ** 1.6
    return env


def one_pole_lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def bandpass(x, center, q):
    """Biquad passa-faixa (receita RBJ)."""
    w0 = 2 * np.pi * center / SR
    alpha = np.sin(w0) / (2 * q)
    b0, b2 = alpha, -alpha
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    b0, b2, a1, a2 = b0 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        out = b0 * v + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, v
        y2, y1 = y1, out
        y[i] = out
    return y


def comb(x, delay, feedback):
    y = np.copy(x)
    for i in range(delay, len(x)):
        y[i] += feedback * y[i - delay]
    return y


def allpass(x, delay, gain):
    y = np.zeros_like(x)
    for i in range(len(x)):
        xd = x[i - delay] if i >= delay else 0.0
        yd = y[i - delay] if i >= delay else 0.0
        y[i] = -gain * x[i] + xd + gain * yd
    return y


def reverb(x, mix, size=1.0, tail=1.2):
    """Reverberação de Schroeder: 4 pentes em paralelo + 2 passa-tudo em série."""
    padded = np.concatenate([x, np.zeros(int(tail * SR))])
    wet = sum(comb(padded, int(d * size), fb) for d, fb in ((1557, 0.80), (1617, 0.79), (1491, 0.78), (1422, 0.77))) / 4
    wet = allpass(allpass(wet, 225, 0.5), 556, 0.5)
    dry = np.concatenate([x, np.zeros(int(tail * SR))])
    return (1 - mix) * dry + mix * wet


def harmonics(freq, weights):
    """Soma de harmônicos: weights[k-1] é o peso do k-ésimo (pode variar no tempo)."""
    ph = phase(freq)
    out = np.zeros_like(freq)
    for k, w in enumerate(weights, start=1):
        out += w * np.sin(k * ph)
    return out


def fade_tail(x, seconds=0.05):
    n = int(seconds * SR)
    x[-n:] *= np.linspace(1, 0, n)
    return x


def normalize(x, peak_db=-1.0):
    return x / np.max(np.abs(x)) * 10 ** (peak_db / 20)


# ---------------------------------------------------------------------------------------------
# Sons
# ---------------------------------------------------------------------------------------------

def steam():
    """Apito de locomotiva: três tubos em acorde (fá# menor), com a "subida" típica do vapor."""
    dur = 2.2
    t = timeline(dur)
    scoop = 1 - 0.07 * np.exp(-t / 0.06)                         # o tom sobe ao abrir a válvula
    droop = 1 - 0.04 * np.clip((t - (dur - 0.3)) / 0.3, 0, 1)    # e cai ao fechar
    trem = 1 + 0.03 * np.sin(2 * np.pi * 5.5 * t)
    out = np.zeros_like(t)
    for base, level in ((369.99, 1.0), (440.0, 0.85), (554.37, 0.7)):
        f = base * scoop * droop
        tone = harmonics(f, [1.0, 0.35, 0.18, 0.08, 0.05, 0.03])
        breath = bandpass(RNG.standard_normal(len(t)), base, 6) * 0.25
        out += level * (tone + breath)
    out *= trem * envelope(t, 0.08, 0.35, dur)
    # chiado de vapor no começo e no fim
    hiss = RNG.standard_normal(len(t)) - one_pole_lowpass(RNG.standard_normal(len(t)), 2500)
    closing = np.clip((t - (dur - 0.45)) / 0.45, 0, 1)
    hiss_env = np.exp(-t / 0.12) + 0.6 * np.sin(np.pi * closing)  # sobe e some ao fechar
    out += 0.5 * hiss * hiss_env
    return reverb(normalize(fade_tail(out, 0.1)), 0.2, tail=0.9)


def foghorn():
    """Buzina de nevoeiro (diafone): tom grave e áspero que termina num "grunhido" descendo."""
    dur = 3.0
    t = timeline(dur)
    f = np.full_like(t, 104.0)
    f *= 1 - 0.09 * np.exp(-t / 0.12)
    grunt = np.clip((t - 2.2) / 0.6, 0, 1)
    f *= 1 - 0.34 * grunt ** 1.5
    weights = [1 / k for k in range(1, 41)]
    tone = harmonics(f, weights)
    tone = one_pole_lowpass(one_pole_lowpass(tone, 900), 1400)
    rasp = one_pole_lowpass(RNG.standard_normal(len(t)), 600) * 0.15
    amp = envelope(t, 0.18, 0.5, dur) * (1 - 0.35 * grunt)
    out = (tone + rasp) * amp
    return reverb(normalize(fade_tail(out, 0.1)), 0.35, size=1.6, tail=1.6)


def bell():
    """Sino de navio: duas batidas ("ding-ding"), com os parciais desafinados de um sino."""
    dur = 4.5
    t = timeline(dur)
    base = 740.0
    partials = ((0.5, 0.35, 2.2), (1.0, 1.0, 1.6), (1.19, 0.5, 1.1), (1.5, 0.35, 0.9),
                (2.0, 0.45, 0.8), (2.51, 0.25, 0.5), (3.0, 0.2, 0.4), (4.07, 0.12, 0.25))
    out = np.zeros_like(t)
    for start, force in ((0.0, 1.0), (0.48, 0.85)):
        s = np.clip(t - start, 0, None)
        on = t >= start
        for ratio, amp, decay in partials:
            f = base * ratio
            out += on * force * amp * np.exp(-s / decay) * np.sin(2 * np.pi * f * s)
        # batida do badalo: ruído curto e agudo
        click = RNG.standard_normal(len(t)) * np.exp(-s / 0.004) * on
        out += 0.3 * force * (click - one_pole_lowpass(click, 3000))
    # batimento lento do parcial principal (dá o "ondular" do sino)
    out *= 1 + 0.08 * np.sin(2 * np.pi * 1.7 * t)
    return reverb(normalize(fade_tail(out, 1.2)), 0.22, tail=1.0)


def war_horn():
    """Corneta de guerra: chamada de duas notas (sol, ré), metal brilhando no ataque."""
    dur = 2.6
    t = timeline(dur)
    f = np.where(t < 0.55, 196.0, 293.66)
    slide = (t >= 0.55) & (t < 0.63)
    f[slide] = 196.0 + (293.66 - 196.0) * ((t[slide] - 0.55) / 0.08)
    vibrato = 1 + 0.006 * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - 0.9) / 0.4, 0, 1)
    f = f * vibrato
    # brilho: o "filtro" abre no ataque de cada nota e assenta
    brightness = 1800 + 1400 * (np.exp(-t / 0.15) + np.exp(-np.clip(t - 0.55, 0, None) / 0.15) * (t >= 0.55))
    weights = [(1 / k) / np.sqrt(1 + (k * f / brightness) ** 4) for k in range(1, 31)]
    tone = harmonics(f, weights)
    breath = one_pole_lowpass(RNG.standard_normal(len(t)), 1800) * 0.06
    amp = envelope(t, 0.07, 0.45, dur) * (1 - 0.25 * ((t > 0.5) & (t < 0.6)))
    out = (tone + breath) * amp
    return reverb(normalize(fade_tail(out, 0.1)), 0.3, size=1.3, tail=1.4)


SOUNDS = {"steam": steam, "foghorn": foghorn, "bell": bell, "war_horn": war_horn}


def write_ogg(samples, path):
    pcm = (np.clip(normalize(samples), -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", tmp.name,
                        "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "4", str(path)], check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        path = OUT / f"{name}.ogg"
        samples = fn()
        write_ogg(samples, path)
        print(f"{path}  {len(samples) / SR:.2f} s")


if __name__ == "__main__":
    main()
