#!/usr/bin/env python3
"""Upscale the plan's 180×180 exercise GIFs into the 720×720 animated WebP demos the app plays.

The exercises-dataset only publishes its Gym visual animations at 180×180, which the demo panel
has to stretch 4× or more. Each frame is upscaled with Real-ESRGAN's realesr-general-x4v3 model
(BSD-3, © 2021 Xintao Wang — the network below is its SRVGGNetCompact, reimplemented for
inference) and the animation is re-encoded as WebP with the original frame timing. Android's
ImageDecoder plays animated WebP natively, so the app needs no extra library.

    pip install torch numpy pillow        # CPU build is enough: ~1 s per frame
    python3 tools/genplan.py app/src/main/assets/plan.json      # writes tools/media_list.txt
    python3 tools/upscale_media.py app/src/main/assets/media

Source GIFs and thumbnails are downloaded from the dataset on first use (GIFs cached in
tools/.media-src/). Existing .webp files are skipped, so a rerun only fills in what's missing.
"""
import io, os, sys, time, urllib.request

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DATASET = 'https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/'
WEIGHTS = 'https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth'
QUALITY = 80


class SRVGGNetCompact(nn.Module):
    def __init__(self, num_feat=64, num_conv=32, upscale=4):
        super().__init__()
        self.upscale = upscale
        self.body = nn.ModuleList([nn.Conv2d(3, num_feat, 3, 1, 1), nn.PReLU(num_feat)])
        for _ in range(num_conv):
            self.body.extend([nn.Conv2d(num_feat, num_feat, 3, 1, 1), nn.PReLU(num_feat)])
        self.body.append(nn.Conv2d(num_feat, 3 * upscale * upscale, 3, 1, 1))
        self.upsampler = nn.PixelShuffle(upscale)

    def forward(self, x):
        out = x
        for layer in self.body:
            out = layer(out)
        return self.upsampler(out) + F.interpolate(x, scale_factor=self.upscale, mode='nearest')


def fetch(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with urllib.request.urlopen(url) as r, open(path + '.part', 'wb') as f:
            f.write(r.read())
        os.replace(path + '.part', path)
    return path


def load_model():
    weights = fetch(WEIGHTS, os.path.join(HERE, '.media-src', 'realesr-general-x4v3.pth'))
    model = SRVGGNetCompact()
    model.load_state_dict(torch.load(weights, map_location='cpu')['params'], strict=True)
    return model.eval()


@torch.no_grad()
def upscale(model, frame):
    x = torch.from_numpy(np.asarray(frame, dtype=np.float32) / 255.).permute(2, 0, 1)[None]
    y = model(x).clamp(0, 1)[0].permute(1, 2, 0).numpy()
    return Image.fromarray((y * 255 + 0.5).astype(np.uint8))


def frames(gif_path):
    """Fully composited RGB frames with their durations (the GIFs update sub-rectangles)."""
    gif = Image.open(gif_path)
    out = []
    for i in range(gif.n_frames):
        gif.seek(i)
        out.append((gif.convert('RGB'), gif.info.get('duration', 100)))
    return out


def main(media_dir):
    names = open(os.path.join(HERE, 'media_list.txt')).read().split()
    model = load_model()
    started = time.time()
    for name in names:
        if name.endswith('.jpg'):
            fetch(DATASET + 'images/' + name, os.path.join(media_dir, name))
            continue
        out = os.path.join(media_dir, name.replace('.gif', '.webp'))
        if os.path.exists(out):
            continue
        src = fetch(DATASET + 'videos/' + name, os.path.join(HERE, '.media-src', name))
        clip = frames(src)
        big = [upscale(model, f) for f, _ in clip]
        big[0].save(out, save_all=True, append_images=big[1:], duration=[d for _, d in clip],
                    loop=0, quality=QUALITY, method=6)
        print(f'{name}: {len(clip)} frames → {os.path.getsize(out) // 1024} KB '
              f'({time.time() - started:.0f} s)', flush=True)


if __name__ == '__main__':
    main(sys.argv[1])
