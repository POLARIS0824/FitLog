"""
预处理 hasaneyldrm/exercises-dataset 的 exercises.json：
1. 只保留 instructions.zh 和 instruction_steps.zh
2. 删除无用字段（media_id, attribution, created_at, category）
3. 输出到 app/src/main/res/raw/exercises.json
"""

import json
import urllib.request
import os

SOURCE_URL = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json"
OUTPUT_PATH = "app/src/main/res/raw/exercises.json"


def main():
    print("Downloading exercises.json...")
    with urllib.request.urlopen(SOURCE_URL) as response:
        data = json.loads(response.read())

    print(f"Original: {len(data)} exercises")

    processed = []
    for ex in data:
        processed.append({
            "id": ex["id"],
            "name": ex["name"],
            "body_part": ex["body_part"],
            "equipment": ex["equipment"],
            "target": ex["target"],
            "muscle_group": ex["muscle_group"],
            "secondary_muscles": ex["secondary_muscles"],
            "instructions": {"zh": ex["instructions"]["zh"]},
            "instruction_steps": {"zh": ex["instruction_steps"]["zh"]},
            "image": ex["image"],
            "gif_url": ex["gif_url"],
        })

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(processed, f, ensure_ascii=False, separators=(",", ":"))

    size_mb = os.path.getsize(OUTPUT_PATH) / 1024 / 1024
    print(f"Processed: {len(processed)} exercises, {size_mb:.1f}MB -> {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
