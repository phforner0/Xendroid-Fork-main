#!/usr/bin/env python3
"""Gera os recursos de marca do site a partir dos arquivos do próprio app.

Fontes (não editar aqui; atualize-as no app e rode este script de novo):
  app/src/main/res/drawable-nodpi/xd_logo_mark.webp   marca (chip com o X+)
  app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.webp  cor de fundo do ícone do app
  docs/assets/xendroid-plus-logo.png / -dark.png       logo completa (texto escuro / claro)
  app/src/main/res/font/*.ttf                          Barlow, Barlow Semi Condensed, JetBrains Mono

Saída: website/src/assets/brand/. Precisa de Pillow (pip install pillow).
Uso: python3 website/scripts/brand-assets.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "website" / "src" / "assets" / "brand"
MARK = ROOT / "app/src/main/res/drawable-nodpi/xd_logo_mark.webp"
LOGO_LIGHT_BG = ROOT / "docs/assets/xendroid-plus-logo.png"  # texto escuro
LOGO_DARK_BG = ROOT / "docs/assets/xendroid-plus-logo-dark.png"  # texto claro
FONTS = ROOT / "app/src/main/res/font"

BG = (15, 18, 20)  # --bg do app (XdTheme.kt, Touch.bg 0xFF0F1214)
FG = (237, 241, 243)  # fg 0xFFEDF1F3
FG2 = (178, 189, 196)  # fg2 0xFFB2BDC4
ACC = (116, 216, 99)  # acc 0xFF74D863


def trimmed(im: Image.Image) -> Image.Image:
    box = im.getbbox()
    return im.crop(box) if box else im


def fit(im: Image.Image, size: int) -> Image.Image:
    """Reduz para caber num quadrado `size` mantendo a proporção, centralizado com transparência."""
    im = im.copy()
    im.thumbnail((size, size), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(im, ((size - im.width) // 2, (size - im.height) // 2), im)
    return canvas


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    mark = trimmed(Image.open(MARK).convert("RGBA"))

    # marca para o cabeçalho (1x e 2x) e para os documentos
    for px in (64, 128, 256):
        fit(mark, px).save(OUT / f"mark-{px}.png", optimize=True)
        fit(mark, px).save(OUT / f"mark-{px}.webp", quality=92, method=6)

    # ícones: favicon e ícones de app (o ícone do Android tem o fundo escuro arredondado)
    fit(mark, 32).save(OUT / "favicon-32.png", optimize=True)
    ico_sizes = [(16, 16), (32, 32), (48, 48)]
    fit(mark, 48).save(OUT / "favicon.ico", sizes=ico_sizes)
    # ícones de app no tamanho pedido: a marca sobre o fundo do ícone adaptativo, com margem
    # de segurança (o conteúdo fica nos 70% centrais, como num ícone "maskable")
    bg_color = Image.open(ROOT / "app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.webp").convert("RGB").getpixel((0, 0))
    for px, name in ((180, "apple-touch-icon.png"), (192, "icon-192.png"), (512, "icon-512.png")):
        icon = Image.new("RGBA", (px, px), bg_color + (255,))
        m = fit(mark, round(px * 0.7))
        icon.paste(m, ((px - m.width) // 2, (px - m.height) // 2), m)
        icon.convert("RGB").save(OUT / name, optimize=True)

    # logo completa para a página (texto claro no tema escuro, escuro no claro)
    for src, name in ((LOGO_DARK_BG, "logo-on-dark"), (LOGO_LIGHT_BG, "logo-on-light")):
        im = trimmed(Image.open(src).convert("RGBA"))
        w = 440
        h = round(im.height * w / im.width)
        im.resize((w, h), Image.LANCZOS).save(OUT / f"{name}.webp", quality=92, method=6)
        im.resize((w * 2, h * 2) if im.width >= w * 2 else (im.width, im.height), Image.LANCZOS).save(OUT / f"{name}@2x.webp", quality=90, method=6)

    # imagem de compartilhamento (Open Graph) 1200x630
    og = Image.new("RGB", (1200, 630), BG)
    d = ImageDraw.Draw(og)
    # fileira de "pinos" do chip como régua discreta no rodapé
    for x in range(64, 1136, 28):
        d.rounded_rectangle((x, 586, x + 16, 594), radius=3, fill=(37, 43, 48))
    m = fit(mark, 360)
    og.paste(m, (60, 120), m)
    display = ImageFont.truetype(str(FONTS / "barlow_semi_condensed_bold.ttf"), 104)
    body = ImageFont.truetype(str(FONTS / "barlow_medium.ttf"), 40)
    mono = ImageFont.truetype(str(FONTS / "jetbrains_mono.ttf"), 26)
    x0 = 470
    d.text((x0, 150), "Xendroid", font=display, fill=FG)
    plus_x = x0 + d.textlength("Xendroid", font=display) + 4
    d.text((plus_x, 150), "+", font=display, fill=ACC)
    d.text((x0, 290), "Emulação de Xbox 360 no Android,", font=body, fill=FG2)
    d.text((x0, 340), "ajustada para Snapdragon e Adreno.", font=body, fill=FG2)
    d.text((x0, 430), "arm64-v8a · Android 10+ · Vulkan", font=mono, fill=(126, 138, 146))
    og.save(OUT / "og-image.png", optimize=True)

    for f in sorted(OUT.iterdir()):
        print(f"{f.relative_to(ROOT)}  {f.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
