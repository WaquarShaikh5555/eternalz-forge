"""Contrast regression checks read the actual Compose light palette, not copied colors."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


def luminance(hex_color):
    channels = [int(hex_color[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    channels = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in channels]
    return sum(v * weight for v, weight in zip(channels, (0.2126, 0.7152, 0.0722)))


class DesignContrastTest(unittest.TestCase):
    def test_light_theme_text_contrast(self):
        theme = (ROOT / 'app/src/main/java/dev/eternalz/forge/ui/Theme.kt').read_text()
        light = theme.split('private val Light = lightColorScheme(', 1)[1].split('\n)', 1)[0]
        colors = dict(re.findall(r'(\w+)\s*=\s*Color\(0xFF([A-Fa-f0-9]{6})\)', light))
        colors.update({key: 'FFFFFF' for key in re.findall(r'(\w+)\s*=\s*Color.White', light)})
        for foreground, background in [('onBackground', 'background'), ('onSurfaceVariant', 'surface'),
                                       ('onPrimary', 'primary'), ('onPrimaryContainer', 'primaryContainer')]:
            with self.subTest(foreground=foreground, background=background):
                low, high = sorted([luminance(colors[foreground]), luminance(colors[background])])
                self.assertGreaterEqual((high + 0.05) / (low + 0.05), 4.5)
