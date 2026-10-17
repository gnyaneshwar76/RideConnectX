"""
Turns the generated vehicle photographs into app-ready cut-outs.

    python prepare_vehicles.py --src "<folder of originals>" --out "<res folder>"

The originals are never modified — everything is read once and written
elsewhere. They are the masters; if any step here goes wrong, re-run it.

Four steps per image:

  1. **Cut out the background.** `rembg` (U²-Net) isolates the vehicle and
     writes real alpha. This also removes the generator's watermark for free:
     the mark sits on the studio backdrop, so it leaves with it.
  2. **Trim to the subject.** The generated frames carry a lot of empty
     backdrop. Cropping to the alpha bounding box means the vehicle fills its
     card instead of floating in the middle of it.
  3. **Add a contact shadow.** A cut-out with no shadow looks pasted on. A
     soft ellipse under the wheels is what makes it sit on the surface — this
     is the step that makes a flat cut-out read as three-dimensional.
  4. **Resize and encode WebP.** The originals are ~6 MB each at 2752px, which
     is roughly sixteen times the whole APK. At display size in WebP they come
     out around 60 KB with no visible loss on a phone.
"""

import argparse
import io
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

try:
    from rembg import remove, new_session
except ImportError:
    print("ERROR: pip install rembg onnxruntime")
    sys.exit(1)


def cut_out(path, session):
    """Background removed, returned as RGBA."""
    with open(path, "rb") as fh:
        data = remove(fh.read(), session=session)
    return Image.open(io.BytesIO(data)).convert("RGBA")


def trim(img, padding=0.02):
    """
    Crop to the visible subject, keeping a small margin.

    Without this the vehicle sits in the middle of a large transparent frame
    and appears far smaller on its card than it should.
    """
    box = img.getbbox()
    if not box:
        return img
    pad_x = int(img.width * padding)
    pad_y = int(img.height * padding)
    left = max(0, box[0] - pad_x)
    top = max(0, box[1] - pad_y)
    right = min(img.width, box[2] + pad_x)
    bottom = min(img.height, box[3] + pad_y)
    return img.crop((left, top, right, bottom))


def add_contact_shadow(img, opacity=110, blur_ratio=0.035):
    """
    A soft ellipse beneath the wheels.

    This is what sells the depth. A cut-out dropped on a card looks like a
    sticker; the same cut-out with a shadow anchored under it looks like an
    object standing on the surface. The ellipse is sized from the subject
    rather than fixed, so it works for a short scooter and a long tourer alike.
    """
    w, h = img.size
    extra = int(h * 0.06)                     # room for the shadow to spread
    canvas = Image.new("RGBA", (w, h + extra), (0, 0, 0, 0))

    shadow = Image.new("RGBA", (w, h + extra), (0, 0, 0, 0))
    draw = ImageDraw.Draw(shadow)

    # Sized and placed against the bottom of the subject, slightly inset so it
    # reads as contact rather than a halo.
    ell_w = int(w * 0.82)
    ell_h = int(h * 0.055)
    cx = w // 2
    cy = h - int(h * 0.005)
    draw.ellipse(
        [cx - ell_w // 2, cy - ell_h // 2, cx + ell_w // 2, cy + ell_h // 2],
        fill=(0, 0, 0, opacity),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(4, int(h * blur_ratio))))

    canvas.alpha_composite(shadow)
    canvas.alpha_composite(img, (0, 0))
    return canvas


def resize_to_width(img, width):
    if img.width <= width:
        return img
    height = round(img.height * width / img.width)
    return img.resize((width, height), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--width", type=int, default=900)
    ap.add_argument("--quality", type=int, default=90)
    ap.add_argument("--prefix", default="img_vehicle_")
    ap.add_argument("--no-shadow", action="store_true")
    # u2net keeps enclosed background — the gap under a scooter's handlebar came
    # back as a grey patch. isnet-general-use handles holes far better.
    ap.add_argument("--model", default="isnet-general-use")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    session = new_session(args.model)

    names = sorted(
        n for n in os.listdir(args.src)
        if n.lower().endswith((".png", ".jpg", ".jpeg")) and n.startswith(args.prefix)
    )
    if not names:
        print(f"No files matching {args.prefix}* in {args.src}")
        return

    total_in = total_out = 0
    for name in names:
        src = os.path.join(args.src, name)
        total_in += os.path.getsize(src)

        img = cut_out(src, session)
        img = trim(img)
        if not args.no_shadow:
            img = add_contact_shadow(img)
        img = resize_to_width(img, args.width)

        # Android resource names: lowercase, digits and underscores only.
        stem = os.path.splitext(name)[0].lower().replace("-", "_")
        dst = os.path.join(args.out, f"{stem}.webp")
        img.save(dst, "WEBP", quality=args.quality, method=6, lossless=False)
        total_out += os.path.getsize(dst)

        print(f"  {name}  ->  {stem}.webp  "
              f"({img.width}x{img.height}, {os.path.getsize(dst)//1024} KB)")

    print(f"\n{len(names)} images: {total_in//1024//1024} MB -> {total_out//1024} KB")


if __name__ == "__main__":
    main()
