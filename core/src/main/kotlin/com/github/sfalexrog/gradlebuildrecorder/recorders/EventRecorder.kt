package com.github.sfalexrog.gradlebuildrecorder.recorders

interface EventRecorder<GradleEventClass, RecordedEventClass> {
    /**
     * Process (record) an event.
     *
     * Implementors are expected to transform and record the event
     * to an internal representation.
     */
    fun processEvent(event: GradleEventClass)

    /**
     * Get all events recorded up to this point.
     */
    fun getRecordedEvents(): List<RecordedEventClass>
}
