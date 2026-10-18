package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus

/** A broken domain rule. The message is Hebrew and is shown to the user as-is. */
open class SpoolRuleException(message: String) : RuntimeException(message)

class IllegalTransitionException(val from: SpoolStatus, val to: SpoolStatus) :
    SpoolRuleException("הפעולה לא אפשרית במצב הנוכחי של הסליל")

class SlotOccupiedException(val slot: Int) :
    SpoolRuleException("חריץ $slot תפוס. יש לפנות אותו קודם")

class UndoNotPossibleException(message: String) : SpoolRuleException(message)
