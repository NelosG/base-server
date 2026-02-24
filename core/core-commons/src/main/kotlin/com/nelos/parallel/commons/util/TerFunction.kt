package com.nelos.parallel.commons.util

import java.util.function.Function

/**
 * Functional interface representing a function that accepts three arguments and produces a result.
 *
 * @param T the type of the first argument
 * @param U the type of the second argument
 * @param V the type of the third argument
 * @param R the type of the result
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
fun interface TerFunction<T, U, V, R> {

    /**
     * Applies this function to the given arguments.
     */
    fun apply(t: T, u: U, v: V): R

    /**
     * Returns a composed function that first applies this function and then applies the [after] function.
     */
    fun <Q> andThen(after: Function<in R, out Q>): TerFunction<T, U, V, Q> {
        return TerFunction { t: T, u: U, v: V -> after.apply(apply(t, u, v)) }
    }
}