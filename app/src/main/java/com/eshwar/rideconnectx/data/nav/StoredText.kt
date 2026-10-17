package com.eshwar.rideconnectx.data.nav

/** Strips invisible bidi marks and trims, so stored sender names are clean (N54). */
fun cleanStoredText(s: String): String =
    s.filterNot { it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 || it.code == 0x200E || it.code == 0x200F }.trim()
