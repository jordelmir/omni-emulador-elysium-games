package com.elysium.console.bridge

/**
 * Libretro Input API Constants.
 * These match the indices defined in libretro.h for the RETRO_DEVICE_ID_JOYPAD_* family.
 */
object RetroButton {
    const val B = 0
    const val Y = 1
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
    const val X = 9
    const val L = 10
    const val R = 11
    const val L2 = 12
    const val R2 = 13
    const val L3 = 14
    const val R3 = 15

    // Helper for debugging/labels
    fun getName(id: Int): String = when(id) {
        A -> "A"
        B -> "B"
        X -> "X"
        Y -> "Y"
        UP -> "UP"
        DOWN -> "DOWN"
        LEFT -> "LEFT"
        RIGHT -> "RIGHT"
        START -> "START"
        SELECT -> "SELECT"
        L -> "L"
        R -> "R"
        else -> "BTN_$id"
    }
}
