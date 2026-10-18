package com.amir.filamenttracker.presentation.targets

import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.data.db.TargetRow
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TargetsViewModel @Inject constructor(private val stock: StockRepository) : BaseViewModel() {
    val rows = stock.targetRows.asState(emptyList())

    fun setTracked(row: TargetRow, tracked: Boolean) = launchAction {
        if (tracked) stock.setTarget(row.materialId, row.colorId, 1) else stock.removeTarget(row.materialId, row.colorId)
        null
    }

    fun setMin(row: TargetRow, min: Int) = launchAction { stock.setTarget(row.materialId, row.colorId, min); null }
}
