"""
下载 exercises-dataset 的所有缩略图到 app/src/main/assets/exercises/。

从预处理后的 exercises.json 中读取 image 字段，
从 GitHub raw URL 下载对应的 JPG 文件。
"""

import json
import urllib.request
import os

BASE_URL = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/"
INPUT_PATH = "app/src/main/res/raw/exercises.json"
OUTPUT_DIR = "app/src/main/assets/exercises"


def main():
    with open(INPUT_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    total = len(data)
    skipped = 0
    downloaded = 0

    for i, ex in enumerate(data):
        image_path = ex["image"]  # e.g. "images/0001-2gPfomN.jpg"
        filename = os.path.basename(image_path)
        url = BASE_URL + image_path
        out_path = os.path.join(OUTPUT_DIR, filename)

        if os.path.exists(out_path):
            skipped += 1
            continue

        try:
            urllib.request.urlretrieve(url, out_path)
            downloaded += 1
        except Exception as e:
            print(f"  FAILED: {filename} - {e}")

        if (i + 1) % 100 == 0:
            print(f"  Progress: {i + 1}/{total} (downloaded: {downloaded}, skipped: {skipped})")

    print(f"Done: {total} total, {downloaded} downloaded, {skipped} skipped -> {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
