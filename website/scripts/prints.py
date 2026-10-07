"""Converte os prints reais do app (PNG) para WebP, para o site carregar menos.

Lê do stdin uma lista JSON [{"src": caminho, "out": caminho}] e escreve cada WebP.
Usado pelo scripts/build.mjs; sem o Pillow, o build copia os PNG como estão.
"""
import json
import sys

from PIL import Image


def main():
    jobs = json.load(sys.stdin)
    for job in jobs:
        with Image.open(job["src"]) as im:
            im = im.convert("RGB")
            im.save(job["out"], "WEBP", quality=82, method=6)
    print(len(jobs))


if __name__ == "__main__":
    main()
