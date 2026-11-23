# Tileable grass ground texture: mottled base + many short blade strokes. Run: python3 gen_grass.py
import numpy as np, os
from PIL import Image, ImageDraw, ImageFilter
N = 512; r = np.random.RandomState(4)
def tnoise(n, cells):
    g = r.rand(cells, cells); g = np.pad(g, ((0, 1), (0, 1)), mode='wrap')
    x = np.linspace(0, cells, n, endpoint=False); i = x.astype(int); f = x - i; f = f * f * (3 - 2 * f)
    a = g[i][:, i]; b = g[i][:, i + 1]; c = g[i + 1][:, i]; d = g[i + 1][:, i + 1]
    fx = f[None, :]; fy = f[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
base = tnoise(N, 6) * 0.5 + tnoise(N, 14) * 0.3 + tnoise(N, 40) * 0.2
im = Image.new('L', (N * 3, N * 3), 0); d = ImageDraw.Draw(im)
for k in range(5200):
    x, y = r.rand() * N, r.rand() * N; a = r.normal(-1.57, 0.6); l = 5 + r.rand() * 11; v = int(90 + r.rand() * 165)
    for ox in (0, N, 2 * N):
        for oy in (0, N, 2 * N):
            d.line((x + ox, y + oy, x + ox + np.cos(a) * l, y + oy + np.sin(a) * l), fill=v, width=2)
bl = np.asarray(im.crop((N, N, 2 * N, 2 * N)).filter(ImageFilter.GaussianBlur(0.6))) / 255.0
v = 0.42 + base * 0.36 + bl * 0.34
dark = np.array([0.16, 0.36, 0.12]); mid = np.array([0.30, 0.56, 0.20]); lite = np.array([0.50, 0.74, 0.30])
t = np.clip((v - 0.45) / 0.6, 0, 1)[..., None]
col = np.where(t < 0.5, dark + (mid - dark) * (t * 2), mid + (lite - mid) * (t * 2 - 1))
col *= (0.94 + 0.12 * tnoise(N, 3))[..., None]
out = Image.fromarray(np.clip(col * 255, 0, 255).astype(np.uint8), 'RGB').resize((256, 256), Image.LANCZOS)
out.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'assets', 'wb', 'tex', 't_grass.png'))
a = np.asarray(out) / 255.; print(a.mean((0, 1)).round(2), a.std((0, 1)).round(3))
