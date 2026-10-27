#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成桌面端应用图标 frontend/build/icon.png（512×512，供 electron-builder 使用）。

图形：蓝色圆角方块 + 白色房屋 + 右上角 AI 星点。4 倍超采样后缩放，避免锯齿。
重新生成：python3 gen_icon.py
"""
import pathlib

from PIL import Image, ImageDraw

S = 512          # 输出边长
SS = 4           # 超采样倍数
N = S * SS
OUT = pathlib.Path(__file__).parent / "icon.png"

BLUE_TOP = (43, 124, 240)
BLUE_BOTTOM = (20, 80, 181)
WHITE = (255, 255, 255, 255)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def main():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 竖向渐变底
    grad = Image.new("RGBA", (N, N))
    gd = ImageDraw.Draw(grad)
    for y in range(N):
        gd.line([(0, y), (N, y)], fill=lerp(BLUE_TOP, BLUE_BOTTOM, y / N))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, N - 1, N - 1], radius=int(N * 0.226), fill=255)
    img.paste(grad, (0, 0), mask)

    k = SS  # 尺寸换算：以下坐标按 512 网格给出

    def rect(x0, y0, x1, y1, r, fill):
        d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], radius=r * k, fill=fill)

    def poly(points, fill):
        d.polygon([(x * k, y * k) for x, y in points], fill=fill)

    # 房屋：屋顶三角 + 墙体
    poly([(112, 262), (256, 142), (400, 262)], WHITE)
    rect(146, 250, 366, 396, 16, WHITE)
    # 门：用底色挖空，形成负形
    rect(222, 312, 290, 396, 12, BLUE_BOTTOM + (255,))
    # 窗：两扇小方窗
    rect(172, 286, 212, 322, 8, BLUE_TOP + (255,))
    rect(300, 286, 340, 322, 8, BLUE_TOP + (255,))
    # AI 星点：一大一小四角星
    poly([(388, 116), (399, 148), (431, 159), (399, 170), (388, 202), (377, 170), (345, 159), (377, 148)], WHITE)
    poly([(438, 196), (444, 214), (462, 220), (444, 226), (438, 244), (432, 226), (414, 220), (432, 214)], WHITE)

    img.resize((S, S), Image.LANCZOS).save(OUT)
    print("已生成", OUT, OUT.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
