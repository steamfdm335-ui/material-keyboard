package com.example.materialkeyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as M

class KeyboardService : InputMethodService() {

    private lateinit var ctx: Context
    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private var ru = false
    private var shift = false
    private var symbols = false

    private val en = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val rus = listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю")
    private val sym = listOf("1234567890", "@#₽_&-+()/", "*\"':;!?")

    override fun onCreateInputView(): View {
        ctx = DynamicColors.wrapContextIfAvailable(
            ContextThemeWrapper(this, R.style.Theme_MaterialKeyboard)
        )
        root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        render()
        return root
    }

    private fun color(attr: Int) = MaterialColors.getColor(ctx, attr, Color.GRAY)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun render() {
        root.removeAllViews()
        root.setBackgroundColor(color(M.attr.colorSurfaceContainer))
        root.setPadding(dp(4), dp(8), dp(4), dp(8))

        val keyBg = color(M.attr.colorSurfaceContainerHigh)
        val keyFg = color(M.attr.colorOnSurface)
        val fnBg = color(M.attr.colorSecondaryContainer)
        val fnFg = color(M.attr.colorOnSecondaryContainer)
        val accent = color(M.attr.colorPrimary)
        val onAccent = color(M.attr.colorOnPrimary)

        val rows = if (symbols) sym else if (ru) rus else en
        rows.forEachIndexed { i, chars ->
            val row = newRow()
            val padded = i == 1 && !ru && !symbols
            if (padded) row.addView(spacer())
            if (i == 2 && !symbols) {
                row.addView(
                    key("⇧", 1.5f, if (shift) accent else fnBg, if (shift) onAccent else fnFg) {
                        shift = !shift; render()
                    }
                )
            }
            chars.forEach { c ->
                val label = if (shift && !symbols) c.toString().uppercase() else c.toString()
                row.addView(key(label, 1f, keyBg, keyFg) {
                    commit(label)
                    if (shift) { shift = false; render() }
                })
            }
            if (padded) row.addView(spacer())
            if (i == 2) row.addView(backspaceKey(fnBg, fnFg))
            root.addView(row)
        }

        val bottom = newRow()
        bottom.addView(key(if (symbols) "ABC" else "?123", 1.5f, fnBg, fnFg) { symbols = !symbols; render() })
        bottom.addView(key(if (ru) "RU" else "EN", 1.2f, fnBg, fnFg) { ru = !ru; render() })
        bottom.addView(key(",", 1f, keyBg, keyFg) { commit(",") })
        bottom.addView(key(if (ru) "Русский" else "English", 4f, keyBg, keyFg) { commit(" ") })
        bottom.addView(key(".", 1f, keyBg, keyFg) { commit(".") })
        bottom.addView(key("↵", 1.5f, accent, onAccent) { enter() })
        root.addView(bottom)
    }

    private fun newRow() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun spacer() = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(52), 0.5f)
    }

    private fun key(label: String, weight: Float, bg: Int, fg: Int, onClick: () -> Unit): TextView {
        val shape = { c: Int -> GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(c) } }
        return TextView(ctx).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(fg)
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(fg, 70)),
                shape(bg), shape(Color.WHITE)
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(52), weight).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
    }

    private fun backspaceKey(bg: Int, fg: Int): View {
        val v = key("⌫", 1.5f, bg, fg) {}
        val repeat = object : Runnable {
            override fun run() { delete(); handler.postDelayed(this, 50) }
        }
        v.setOnClickListener(null)
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    delete()
                    handler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
        return v
    }

    private fun commit(text: String) { currentInputConnection?.commitText(text, 1) }

    private fun delete() { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }

    private fun enter() {
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noAction = opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shift = false
        if (::root.isInitialized) render()
    }
}
