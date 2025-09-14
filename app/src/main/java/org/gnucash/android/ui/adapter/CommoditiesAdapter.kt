package org.gnucash.android.ui.adapter

import android.content.Context
import android.widget.ArrayAdapter
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.lang.VoidCallback
import org.gnucash.android.model.Commodity

class CommoditiesAdapter @JvmOverloads constructor(
    context: Context,
    private val adapter: CommoditiesDbAdapter = CommoditiesDbAdapter.getInstance()!!,
    private val scope: CoroutineScope
) : ArrayAdapter<CommoditiesAdapter.Label>(context, android.R.layout.simple_spinner_item) {

    private var loadJob: Job? = null

    constructor(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ) : this(
        context = context,
        scope = lifecycleOwner.lifecycleScope
    )

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    fun getCommodity(position: Int): Commodity? {
        return getItem(position)?.commodity
    }

    fun getPosition(mnemonic: String): Int {
        for (i in 0 until count) {
            val commodity = getCommodity(i)!!
            if (commodity.currencyCode == mnemonic) {
                return i
            }
        }
        return -1
    }

    fun getPosition(commodity: Commodity): Int {
        for (i in 0 until count) {
            if (commodity == getCommodity(i)) {
                return i
            }
        }
        return -1
    }

    fun load(callback: VoidCallback? = null) {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            val records = loadData(adapter)
            val labels = records.map { Label(it) }
            scope.launch(Dispatchers.Main) {
                clear()
                addAll(labels)
                callback?.invoke()
            }
        }
    }

    private fun loadData(adapter: CommoditiesDbAdapter): List<Commodity> {
        val where = DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC + " <> ?" +
                " AND " + DatabaseSchema.CommodityEntry.COLUMN_NAMESPACE + " <> ?";
        val whereArgs = arrayOf(Commodity.TEMPLATE, Commodity.TEMPLATE)
        return adapter.getAllRecords(where, whereArgs)
    }

    data class Label(val commodity: Commodity) {
        override fun toString(): String = commodity.formatListItem()
    }
}