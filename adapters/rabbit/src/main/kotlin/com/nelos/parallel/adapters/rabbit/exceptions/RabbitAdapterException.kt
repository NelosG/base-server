package com.nelos.parallel.adapters.rabbit.exceptions

import com.nelos.parallel.commons.adapter.exceptions.AdapterException

/**
 * Exception for RabbitMQ adapter-related errors.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RabbitAdapterException : AdapterException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}