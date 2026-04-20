package com.autoclicker

import java.io.Serializable

data class ClickPoint(
    val id: Long = System.currentTimeMillis(),
    var label: String = "点",
    var x: Int = 0,
    var y: Int = 0,
    var delayMs: Long = 500L
) : Serializable
