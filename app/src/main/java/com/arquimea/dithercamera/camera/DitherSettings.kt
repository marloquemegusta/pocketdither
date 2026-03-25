package com.arquimea.dithercamera.camera

data class DitherSettings(
    val pixelSize: Int = 6,
    val pattern: DitherPattern = DitherPattern.BAYER_4X4,
    val contrast: Float = 1.0f,
    val colorLevels: Int = 4,
    val colorProfile: ColorProfile = ColorProfile.FULL_COLOR,
    val detail: Float = 1.0f,
)

data class DitherPreset(
    val label: String,
    val settings: DitherSettings,
)

enum class PaletteMode {
    FULL_COLOR,
    LUMA_PALETTE,
    NEAREST_COLOR_PALETTE,
}

enum class ColorProfile(
    val label: String,
    val mode: PaletteMode,
    val palette: IntArray = intArrayOf(),
) {
    FULL_COLOR(
        label = "Color",
        mode = PaletteMode.FULL_COLOR,
    ),
    GAME_BOY_DMG(
        label = "Game Boy DMG",
        mode = PaletteMode.LUMA_PALETTE,
        palette = intArrayOf(
            0xFF0F380F.toInt(),
            0xFF306230.toInt(),
            0xFF8BAC0F.toInt(),
            0xFF9BBC0F.toInt(),
        ),
    ),
    GAME_BOY_POCKET(
        label = "Game Boy Pocket",
        mode = PaletteMode.LUMA_PALETTE,
        palette = intArrayOf(
            0xFF1A1A1B.toInt(),
            0xFF55555A.toInt(),
            0xFFA7A7AD.toInt(),
            0xFFE8E8EC.toInt(),
        ),
    ),
    MACINTOSH_1BIT(
        label = "Macintosh",
        mode = PaletteMode.LUMA_PALETTE,
        palette = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
        ),
    ),
    VIRTUAL_BOY(
        label = "Virtual Boy",
        mode = PaletteMode.LUMA_PALETTE,
        palette = intArrayOf(
            0xFF1A0000.toInt(),
            0xFF5C0000.toInt(),
            0xFF9F0000.toInt(),
            0xFFFF2A2A.toInt(),
        ),
    ),
    CGA_4(
        label = "IBM CGA",
        mode = PaletteMode.NEAREST_COLOR_PALETTE,
        palette = intArrayOf(
            0xFF000000.toInt(),
            0xFF55FFFF.toInt(),
            0xFFFF55FF.toInt(),
            0xFFFFFFFF.toInt(),
        ),
    ),
    COMMODORE_64(
        label = "C64",
        mode = PaletteMode.NEAREST_COLOR_PALETTE,
        palette = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF813338.toInt(),
            0xFF75CEC8.toInt(),
            0xFF8E3C97.toInt(),
            0xFF56AC4D.toInt(),
            0xFF2E2C9B.toInt(),
            0xFFEDF171.toInt(),
            0xFF8E5029.toInt(),
            0xFF553800.toInt(),
            0xFFC46C71.toInt(),
            0xFF4A4A4A.toInt(),
            0xFF7B7B7B.toInt(),
            0xFFA9FF9F.toInt(),
            0xFF706DEB.toInt(),
            0xFFB2B2B2.toInt(),
        ),
    );
}

enum class DitherPattern(
    val label: String,
    val matrix: Array<IntArray>,
) {
    BAYER_2X2(
        label = "Bayer 2x2",
        matrix = arrayOf(
            intArrayOf(0, 2),
            intArrayOf(3, 1),
        ),
    ),
    BAYER_4X4(
        label = "Bayer 4x4",
        matrix = arrayOf(
            intArrayOf(0, 8, 2, 10),
            intArrayOf(12, 4, 14, 6),
            intArrayOf(3, 11, 1, 9),
            intArrayOf(15, 7, 13, 5),
        ),
    ),
    BAYER_8X8(
        label = "Bayer 8x8",
        matrix = arrayOf(
            intArrayOf(0, 48, 12, 60, 3, 51, 15, 63),
            intArrayOf(32, 16, 44, 28, 35, 19, 47, 31),
            intArrayOf(8, 56, 4, 52, 11, 59, 7, 55),
            intArrayOf(40, 24, 36, 20, 43, 27, 39, 23),
            intArrayOf(2, 50, 14, 62, 1, 49, 13, 61),
            intArrayOf(34, 18, 46, 30, 33, 17, 45, 29),
            intArrayOf(10, 58, 6, 54, 9, 57, 5, 53),
            intArrayOf(42, 26, 38, 22, 41, 25, 37, 21),
        ),
    );

    val size: Int
        get() = matrix.size
}

object DitherPresets {
    val presets = listOf(
        DitherPreset(
            label = "Game Boy DMG",
            settings = DitherSettings(
                pixelSize = 8,
                pattern = DitherPattern.BAYER_4X4,
                contrast = 1.15f,
                colorProfile = ColorProfile.GAME_BOY_DMG,
                detail = 0.58f,
            ),
        ),
        DitherPreset(
            label = "Game Boy Pocket",
            settings = DitherSettings(
                pixelSize = 8,
                pattern = DitherPattern.BAYER_4X4,
                contrast = 1.0f,
                colorProfile = ColorProfile.GAME_BOY_POCKET,
                detail = 0.62f,
            ),
        ),
        DitherPreset(
            label = "Macintosh",
            settings = DitherSettings(
                pixelSize = 6,
                pattern = DitherPattern.BAYER_2X2,
                contrast = 1.35f,
                colorProfile = ColorProfile.MACINTOSH_1BIT,
                colorLevels = 2,
                detail = 0.48f,
            ),
        ),
        DitherPreset(
            label = "Virtual Boy",
            settings = DitherSettings(
                pixelSize = 7,
                pattern = DitherPattern.BAYER_4X4,
                contrast = 1.2f,
                colorProfile = ColorProfile.VIRTUAL_BOY,
                detail = 0.55f,
            ),
        ),
        DitherPreset(
            label = "IBM CGA",
            settings = DitherSettings(
                pixelSize = 6,
                pattern = DitherPattern.BAYER_2X2,
                contrast = 1.0f,
                colorProfile = ColorProfile.CGA_4,
                detail = 0.65f,
            ),
        ),
        DitherPreset(
            label = "Commodore 64",
            settings = DitherSettings(
                pixelSize = 6,
                pattern = DitherPattern.BAYER_2X2,
                contrast = 1.0f,
                colorProfile = ColorProfile.COMMODORE_64,
                detail = 0.72f,
            ),
        ),
    )
}
