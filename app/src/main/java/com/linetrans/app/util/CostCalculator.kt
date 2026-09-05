package com.linetrans.app.util

import com.linetrans.app.model.ModelConfig

object CostCalculator {
    /** 费用以“每百万 token”的价格配置，乘以当前高峰倍率。 */
    fun costFor(model: ModelConfig, promptTokens: Int, completionTokens: Int): Double {
        val b = model.billing
        val input = promptTokens / 1_000_000.0 * b.inputPrice
        val output = completionTokens / 1_000_000.0 * b.outputPrice
        return (input + output) * b.currentMultiplier
    }
}
