package com.platinum.vip.hasiba

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.iconics.view.IconicsImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: View
    private lateinit var adapter: HistoryAdapter
    private lateinit var etSearch: EditText
    private var fullList: List<HistoryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val headerCard = findViewById<View>(R.id.headerCard)
        headerCard.setOnApplyWindowInsetsListener { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + view.paddingTop,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        rv = findViewById(R.id.rvHistory)
        tvEmpty = findViewById(R.id.tvEmpty)
        val btnBack = findViewById<IconicsImageView>(R.id.btnBack)
        val btnClear = findViewById<IconicsImageView>(R.id.btnClear)
        val btnHelp = findViewById<IconicsImageView>(R.id.btnHelp)
        etSearch = findViewById(R.id.etSearch)

        btnBack.setOnClickListener { finish() }

        btnClear.setOnClickListener {
            if (HistoryManager.getAll(this).isNotEmpty()) {
                MaterialDialog(this).show {
                    cornerRadius(20f)
                    title(text = "مسح السجل")
                    message(text = "هل أنت متأكد من حذف السجل بالكامل؟")
                    positiveButton(text = "حذف") {
                        HistoryManager.clear(this@HistoryActivity)
                        updateUI()
                    }
                    negativeButton(text = "إلغاء")
                }
            }
        }

        btnHelp.setOnClickListener {
            MaterialDialog(this).show {
                cornerRadius(20f)
                title(text = "كيفية استخدام السجل")
                message(text = "• ضغطة قصيرة على أي عملية: نسخ النتيجة\n• ضغطة مطوّلة: استعادة العملية إلى الحاسبة\n• سحب لليمين أو اليسار: حذف العملية\n• البحث المباشر: اكتب رقم أو عملية للفلترة")
                positiveButton(text = "فهمت")
            }
        }

        setupSearch()

        rv.layoutManager = LinearLayoutManager(this)
        fullList = HistoryManager.getAll(this)
        adapter = HistoryAdapter(fullList.toMutableList())
        rv.adapter = adapter

        setupSwipeToDelete()
        updateUI()
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    adapter.updateData(fullList.toMutableList())
                } else {
                    val filtered = fullList.filter { item ->
                        item.expression.contains(query, ignoreCase = true) ||
                        item.result.contains(query, ignoreCase = true)
                    }
                    adapter.updateData(filtered.toMutableList())
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            val background = ColorDrawable(Color.parseColor("#F44336"))

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.items[position]

                MaterialDialog(this@HistoryActivity).show {
                    cornerRadius(20f)
                    title(text = "تأكيد الحذف") // تم إزالة الإيموجي
                    message(text = "هل أنت متأكد من حذف هذه العملية؟")
                    positiveButton(text = "حذف") {
                        HistoryManager.remove(this@HistoryActivity, item)
                        adapter.removeItem(position)
                        if (adapter.items.isEmpty()) {
                            updateUI()
                        }
                    }
                    negativeButton(text = "إلغاء") {
                        adapter.notifyItemChanged(position)
                    }
                    cancelOnTouchOutside(false)
                    setOnCancelListener {
                        adapter.notifyItemChanged(position)
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val deleteIcon = ContextCompat.getDrawable(this@HistoryActivity, android.R.drawable.ic_menu_delete)

                val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                val iconBottom = iconTop + deleteIcon.intrinsicHeight

                if (dX > 0) {
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + deleteIcon.intrinsicWidth
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                } else if (dX < 0) {
                    val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                } else {
                    background.setBounds(0, 0, 0, 0)
                }

                background.draw(c)
                if (dX != 0f) {
                    deleteIcon.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(rv)
    }

    private fun updateUI() {
        fullList = HistoryManager.getAll(this)
        if (fullList.isEmpty()) {
            rv.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                val filtered = fullList.filter { item ->
                    item.expression.contains(query, ignoreCase = true) ||
                    item.result.contains(query, ignoreCase = true)
                }
                adapter.updateData(filtered.toMutableList())
            } else {
                adapter.updateData(fullList.toMutableList())
            }
        }
    }

    inner class HistoryAdapter(var items: MutableList<HistoryItem>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("EEEE، yyyy/MM/dd - hh:mm a", Locale("ar"))

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvExpr: TextView = v.findViewById(R.id.tvHistoryExpr)
            val tvRes: TextView = v.findViewById(R.id.tvHistoryRes)
            val tvDate: TextView = v.findViewById(R.id.tvHistoryDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvExpr.text = item.expression
            holder.tvRes.text = "= ${item.result}"
            holder.tvDate.text = dateFormat.format(Date(item.timestamp))

            holder.itemView.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Result", item.result)
                clipboard.setPrimaryClip(clip)
                // تم إزالة توست النسخ
            }

            holder.itemView.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val resultIntent = Intent().apply {
                    putExtra("selected_expression", item.expression)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                // تم إزالة توست النقل
                finish()
                true
            }
        }

        override fun getItemCount() = items.size

        fun removeItem(position: Int) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }

        fun updateData(newItems: List<HistoryItem>) {
            this.items = newItems.toMutableList()
            notifyDataSetChanged()
        }
    }
}