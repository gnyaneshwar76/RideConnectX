"""
Turns the generated scene photographs into app-ready backgrounds and heroes.

    python prepare_scenes.py --src "<folder of originals>" --out "<res folder>"

The companion of `prepare_vehicles.py`, and deliberately *not* the same job.
The eight vehicles are cut-outs: background removed, trimmed to the subject,
shadow added. The fifteen images here are scenes — a road, a workshop bench, a
helmet, brushed metal. Removing their background would remove the picture, so
this script only ever crops, resizes and re-encodes.

The originals are never modified. They are the masters; every step here reads
them once and writes elsewhere.

Three steps per image:

  1. **Trim the generator's watermark.** Every Gemini image carries a ✦ sparkle
     in the bottom-right corner. Measured across all 23 originals it sits at the
     *same* absolute offset every time — its outer edge is 95 px from the right
     edge and 95 px from the bottom — so cropping a 96 px band off those two
     sides removes it exactly, on every image, with no inpainting and no
     artefacts. (The vehicles never needed this: `rembg` took the sparkle away
     with the backdrop it sits on.)
  2. **Crop to the ratio the screen expects.** Gemini's output is close to the
     requested ratio but rarely exact — 1.792 where 16:9 was asked for. Cropping
     here rather than letting Compose do it means the framing is decided once,
     visibly, instead of differently on every screen size. The crop is centred
     on the *original* centre, so a centred subject stays centred despite the
     watermark band coming off one side.
  3. **Resize and encode WebP.** The originals are 5-10 MB each at up to
     3584 px. Nothing in the app is displayed wider than a phone screen, so each
     image is sized to what its slot actually shows — see SLOTS below.

`img_promo_keyart` is store artwork, not an app asset. It is written to
`--store-out` instead, so it never reaches the APK.
"""

import argparse
import os
import sys

from PIL import Image

# The sparkle's outer edge, in pixels from the right and bottom edges.
# Measured, not guessed: in a 300x300 corner window the mark occupies
# x[156-203] y[156-203] identically in every original.
WATERMARK_MARGIN = 96

# ratio = width / height, width = the widest the slot is ever drawn.
# A phone is 1080 px across, so a full-bleed slot needs no more than that;
# anything smaller is sized to its own box at 3x density.
SLOTS = {
    #                          ratio,        width, screen
    "img_welcome_hero":        (4 / 5,       1080),  # 02 Welcome, full width
    "img_onboarding_navigation": (1.0,        900),  # 03 Onboarding 1
    "img_onboarding_bluetooth":  (1.0,        900),  # 04 Onboarding 2
    "img_onboarding_intelligence": (1.0,      900),  # 05 Onboarding 3
    "img_permissions_hero":    (16 / 9,      1080),  # 10 Permissions
    "img_pairing_cluster_select": (4 / 3,     900),  # 14 BLE Pairing help card
    "img_navigation_background": (9 / 19.5,  1080),  # 15 Navigation, full bleed
    "img_dashboard_hero_plate": (16 / 9,     1080),  # 13 Dashboard stage
    "img_service_hero":        (16 / 9,      1080),  # 18 Service
    "img_safety_hero":         (16 / 9,      1080),  # 19 Safety
    "img_statistics_hero":     (16 / 9,      1080),  # 16 Statistics
    "img_empty_notifications": (1.0,          600),  # 17 Notifications, small
    "img_profile_cover":       (3.0,         1080),  # 21 Profile banner
    "img_about_header":        (16 / 9,      1080),  # 23 About
    "img_promo_keyart":        (16 / 9,      1920),  # store listing, not the app
}

STORE_ONLY = {"img_promo_keyart"}


def trim_watermark(img, margin=WATERMARK_MARGIN):
    """Drop the corner band the generator's sparkle sits in."""
    return img.crop((0, 0, img.width - margin, img.height - margin))


def crop_to_ratio(img, ratio, centre):
    """
    Crop to `ratio`, keeping `centre` (a point in the *pre-trim* image) centred.

    Passing the original centre matters: the watermark trim has already taken a
    band off the right and bottom, and centring on what is left would drift a
    centred subject towards the corner that was cut.
    """
    w, h = img.size
    if w / h > ratio:
        new_w, new_h = round(h * ratio), h
    else:
        new_w, new_h = w, round(w / ratio)

    left = int(centre[0] - new_w / 2)
    top = int(centre[1] - new_h / 2)
    left = max(0, min(left, w - new_w))          # clamp inside the canvas
    top = max(0, min(top, h - new_h))
    return img.crop((left, top, left + new_w, top + new_h))


def resize_to_width(img, width):
    if img.width <= width:
        return img
    height = round(img.height * width / img.width)
    return img.resize((width, height), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True, help="res/drawable-nodpi")
    ap.add_argument("--store-out", default=None,
                    help="where promotional art goes; skipped if unset")
    ap.add_argument("--quality", type=int, default=82)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    if args.store_out:
        os.makedirs(args.store_out, exist_ok=True)

    total_in = total_out = 0
    written = skipped = 0

    for stem in sorted(SLOTS):
        src = os.path.join(args.src, stem + ".png")
        if not os.path.exists(src):
            print(f"  {stem}: no original, skipped")
            skipped += 1
            continue

        if stem in STORE_ONLY and not args.store_out:
            print(f"  {stem}: store art, --store-out not given, skipped")
            skipped += 1
            continue

        ratio, width = SLOTS[stem]
        total_in += os.path.getsize(src)

        img = Image.open(src)
        centre = (img.width / 2, img.height / 2)     # before anything is cut
        img = img.convert("RGB")                     # these are opaque scenes
        img = trim_watermark(img)
        img = crop_to_ratio(img, ratio, centre)
        img = resize_to_width(img, width)

        out_dir = args.store_out if stem in STORE_ONLY else args.out
        dst = os.path.join(out_dir, stem + ".webp")
        img.save(dst, "WEBP", quality=args.quality, method=6, lossless=False)

        size = os.path.getsize(dst)
        total_out += size
        written += 1
        print(f"  {stem+'.webp':32s} {img.width}x{img.height}  {size//1024:4d} KB"
              f"{'   [store only]' if stem in STORE_ONLY else ''}")

    if not written:
        print(f"\nNothing written. Are the originals in {args.src}?")
        return 1

    print(f"\n{written} images: {total_in//1024//1024} MB -> {total_out//1024} KB"
          f"{f' ({skipped} skipped)' if skipped else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
