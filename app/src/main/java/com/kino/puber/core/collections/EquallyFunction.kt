package com.kino.puber.core.collections

fun interface EquallyFunction<T> {
    fun isItemTheSame(oldItem: T, newItem: T): Boolean
}