package com.amir.filamenttracker.data.db

import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolEvent

fun SpoolEntity.toDomain() = Spool(id = id, status = status, amsSlot = amsSlot)

fun SpoolEventEntity.toDomain() = SpoolEvent(
    id = id, spoolId = spoolId, fromStatus = fromStatus, toStatus = toStatus,
    fromSlot = fromSlot, toSlot = toSlot, at = at,
)
