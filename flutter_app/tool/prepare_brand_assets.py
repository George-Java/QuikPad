"""Regenerate Android and Flutter assets from the repository's logo.png.

Run from the repository root: python flutter_app/tool/prepare_brand_assets.py
Requires Pillow. The original logo is never modified.
"""
from pathlib import Path
import base64
import re
import shutil
from PIL import Image

root = Path(__file__).resolve().parents[2]
app = root / 'flutter_app'
res = app / 'android/app/src/main/res'
logo = Image.open(root / 'logo.png').convert('RGBA')
background = (23, 25, 36, 255)

def canvas_icon(size, fraction, opaque):
    canvas = Image.new('RGBA', (size, size), background if opaque else (0, 0, 0, 0))
    mark = logo.copy()
    mark.thumbnail((round(size * fraction), round(size * fraction)), Image.Resampling.LANCZOS)
    canvas.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    return canvas

for density, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    directory = res / f'mipmap-{density}'
    directory.mkdir(parents=True, exist_ok=True)
    canvas_icon(round(48 * scale), .68, True).save(directory / 'ic_launcher.png')
    # Android adaptive foreground canvas is 108dp, with a 66dp safe region.
    canvas_icon(round(108 * scale), .58, False).save(directory / 'ic_launcher_foreground.png')

assets = app / 'assets'
shutil.copyfile(root / 'logo.png', assets / 'logo.png')
canvas_icon(192, .65, False).save(res / 'drawable-nodpi/launch_image.png')

# The upstream maps are decoded losslessly into PNG for Flutter asset loading.
source = (root / 'liquid-glass-react/src/utils.ts').read_text(encoding='utf-8')
maps = assets / 'glass'
maps.mkdir(exist_ok=True)
for name, filename in [('displacementMap', 'standard'), ('polarDisplacementMap', 'polar'), ('prominentDisplacementMap', 'prominent')]:
    match = re.search(r'export const ' + name + r'\s*=\s*"data:image/(?:png|jpeg);base64,([^"]+)"', source)
    if not match:
        raise ValueError(f'Missing upstream map: {name}')
    target = maps / f'{filename}.png'
    from io import BytesIO
    with Image.open(BytesIO(base64.b64decode(match.group(1)))) as image:
        image.save(target)
        print(f'{filename}: {image.size}')
shutil.copyfile(root / 'liquid-glass-react/LICENSE', assets / 'liquid-glass-react-LICENSE.txt')
print('QuikPad icons and upstream displacement maps generated.')
