package com.linetrans.app.ui

import com.linetrans.app.model.UnitMode

/** 逐行模式下的单位是“行”，逐句模式下是“句”。 */
internal fun unitLabel(mode: UnitMode): String = if (mode == UnitMode.SENTENCE) "句" else "行"
