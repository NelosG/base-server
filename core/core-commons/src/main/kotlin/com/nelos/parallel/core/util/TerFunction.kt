package com.nelos.parallel.core.util

import java.util.function.Function

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
fun interface TerFunction<T, U, V, R> {

    fun apply(t: T, u: U, v: V): R

    fun <Q> andThen(after: Function<in R, out Q>): TerFunction<T, U, V, Q> {
        return TerFunction { t: T, u: U, v: V -> after.apply(apply(t, u, v)) }
    }
}