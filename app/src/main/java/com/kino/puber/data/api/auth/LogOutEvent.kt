package com.kino.puber.data.api.auth

import com.kino.puber.core.coroutine.SubscriptionBus

object LogOutEvent

class LogOutBus : SubscriptionBus<LogOutEvent>()