package com.risyan.quickshutdownphone.screen_content_guard.extensions
fun gradeFuzzyOccurrence(
    safeCount: Int,
    nsfwCount: Int,
    blankCount: Int,
    resetCounters: () -> Unit,
    totalCountMultiplier: Int = 1
): Boolean {

    val total = safeCount + nsfwCount + blankCount
    if (total < 12 * totalCountMultiplier.coerceAtLeast(1)) return false

    val safe = safeCount / total.toFloat()
    val nsfw = nsfwCount / total.toFloat()
    val blank = blankCount / total.toFloat()

    // 1️⃣ Absolute NSFW presence
    val nsfwRisk = nsfw                       // 0..1

    // 2️⃣ NSFW >= SAFE parity trigger (critical)
    val parityRisk =
        if (nsfwCount >= safeCount && nsfwCount > 0) 0.35f else 0f

    // 3️⃣ Blank-to-safe dominance (incognito behavior)
    val blankSafeRatio =
        if (safeCount == 0) 1f
        else blankCount.toFloat() / safeCount.toFloat()

    val blankEvasionRisk =
        blankSafeRatio.coerceIn(0f, 1f)

    // 4️⃣ Absolute blank pressure (nonlinear)
    val blankPressure = blank * blank

    // 5️⃣ Trigger if blank is close to safe (70%+) - incognito indicator
    val blankCloseToSafe =
        if (blankCount >= (safeCount * 0.7f) && blankCount >= 4) 0.5f else 0f

    // 6️⃣ Final fuzzy score
    var score =
        nsfwRisk * 0.65f +
                blankEvasionRisk * 0.65f +
                blankPressure * 0.5f +
                blankCloseToSafe +
                parityRisk -
                safe * 0.06f

    score = score.coerceIn(0f, 1f)

    // smoothstep
    score = score * score * (3f - 2f * score)

    // Debug logging
//    println("gradeFuzzyOccurrence: safe=$safeCount, nsfw=$nsfwCount, blank=$blankCount")
//    println("  -> blankEvasionRisk=$blankEvasionRisk, blankPressure=$blankPressure, blankCloseToSafe=$blankCloseToSafe")
//    println("  -> raw score before smoothstep=${(nsfwRisk * 0.65f + blankEvasionRisk * 0.6f + blankPressure * 0.45f + blankCloseToSafe + parityRisk - safe * 0.08f)}")
//    println("  -> final score=$score, threshold=0.6, result=${score >= 0.6f}")

    resetCounters()

    return score >= 0.6f
}
