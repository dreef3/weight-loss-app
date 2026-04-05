package com.dreef3.weightlossapp.app.sync

fun interface DriveSyncTrigger {
    fun requestSync(reason: String)
}

object NoOpDriveSyncTrigger : DriveSyncTrigger {
    override fun requestSync(reason: String) = Unit
}
