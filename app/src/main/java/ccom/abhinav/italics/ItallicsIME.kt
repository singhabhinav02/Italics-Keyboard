package ccom.abhinav.italics   // <- keep EXACTLY what your current files use

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout

/**
 * Phase 1: letters / symbols / emoji pages, style switcher (italic, bold-italic,
 * bold, mono), convert-selected-text button, key vibration, hold-to-repeat backspace.
 */
class ItalicsIME : InputMethodService() {

    private enum class Style(val label: String, val upper: Int, val lower: Int) {
        NORMAL("Normal", 0, 0),
        ITALIC("Italic", 0x1D608, 0x1D622),
        BOLD_ITALIC("Bold italic", 0x1D63C, 0x1D656),
        BOLD("Bold", 0x1D5D4, 0x1D5EE),
        MONO("Mono", 0x1D670, 0x1D68A)
    }

    private enum class Layer { LETTERS, SYMBOLS, EMOJI }

    private var style = Style.ITALIC
    private var shiftOn = false

    // Change these to false/true to turn feedback off/on (a settings screen comes in Phase 2)
    private var vibrationOn = true
    private var soundOn = false

    private val letterKeys = mutableListOf<Pair<Button, Char>>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var keyArea: LinearLayout
    private lateinit var styleButton: Button

    // ---------- Lifecycle ----------
    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(0xFF202124.toInt())
        }

        // Top toolbar: style button, convert selection, keyboard picker
        styleButton = makeButton("", 2f)
        styleButton.setOnClickListener { feedback(it); cycleStyle() }
        val convert = key("Convert selection", 2.5f) { convertSelection() }
        val globe = key("🌐", 1f) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        root.addView(row(styleButton, convert, globe))

        keyArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keyArea)

        showLayer(Layer.LETTERS)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shiftOn = false
        if (::keyArea.isInitialized) showLayer(Layer.LETTERS)
    }

    // ---------- Text styling ----------
    private fun styled(c: Char): String {
        if (style == Style.NORMAL) return c.toString()
        val cp = when (c) {
            in 'A'..'Z' -> style.upper + (c - 'A')
            in 'a'..'z' -> style.lower + (c - 'a')
            else -> return c.toString()
        }
        return String(Character.toChars(cp))
    }

    private fun styleText(s: String): String =
        buildString { s.forEach { append(styled(it)) } }

    /** Turns any styled letters back into plain A-Z / a-z. */
    private fun toPlain(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            var out = cp
            for (st in Style.values()) {
                if (st == Style.NORMAL) continue
                if (cp in st.upper..(st.upper + 25)) out = 'A'.code + (cp - st.upper)
                else if (cp in st.lower..(st.lower + 25)) out = 'a'.code + (cp - st.lower)
            }
            sb.appendCodePoint(out)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun convertSelection() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel.isNullOrEmpty()) return
        ic.commitText(styleText(toPlain(sel.toString())), 1)
    }

    private fun cycleStyle() {
        val all = Style.values()
        style = all[(style.ordinal + 1) % all.size]
        refreshLabels()
    }

    // ---------- Typing actions ----------
    private fun typeChar(c: Char) {
        val ch = if (shiftOn && c.isLetter()) c.uppercaseChar() else c
        currentInputConnection?.commitText(styled(ch), 1)
        if (shiftOn) { shiftOn = false; refreshLabels() }
    }

    private fun typeRaw(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun pressEnter() {
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action == null || action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.commitText("\n", 1)
        } else {
            sendDefaultEditorAction(true)
        }
    }

    private fun feedback(v: View) {
        if (vibrationOn) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundOn) {
            (getSystemService(AUDIO_SERVICE) as AudioManager)
                .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    // ---------- UI helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun makeButton(label: String, weight: Float): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
    }

    private fun key(label: String, weight: Float = 1f, onClick: () -> Unit): Button =
        makeButton(label, weight).also { b ->
            b.setOnClickListener { feedback(b); onClick() }
        }

    private fun row(vararg keys: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        keys.forEach { addView(it) }
    }

    /** Backspace that keeps deleting while you hold it. */
    private fun backspaceKey(weight: Float = 1.5f): Button {
        val b = makeButton("⌫", weight)
        val repeat = object : Runnable {
            override fun run() {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                handler.postDelayed(this, 50)
            }
        }
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    feedback(v)
                    v.isPressed = true
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    handler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
        return b
    }

    private fun refreshLabels() {
        letterKeys.forEach { (btn, c) ->
            val shown = if (shiftOn) c.uppercaseChar() else c
            btn.text = styled(shown)
        }
        if (::styleButton.isInitialized) styleButton.text = "Style: ${style.label}"
    }

    // ---------- Pages ----------
    private fun showLayer(l: Layer) {
        letterKeys.clear()
        keyArea.removeAllViews()
        when (l) {
            Layer.LETTERS -> buildLetters()
            Layer.SYMBOLS -> buildSymbols()
            Layer.EMOJI -> buildEmoji()
        }
        refreshLabels()
    }

    private fun letterKeysFor(chars: String): List<Button> = chars.map { c ->
        key(c.toString()) { typeChar(c) }.also { letterKeys.add(it to c) }
    }

    private fun buildLetters() {
        keyArea.addView(row(*letterKeysFor("1234567890").toTypedArray()))
        keyArea.addView(row(*letterKeysFor("qwertyuiop").toTypedArray()))
        keyArea.addView(row(*letterKeysFor("asdfghjkl").toTypedArray()))

        val shift = key("⇧", 1.5f) { shiftOn = !shiftOn; refreshLabels() }
        keyArea.addView(row(shift, *letterKeysFor("zxcvbnm").toTypedArray(), backspaceKey()))

        keyArea.addView(row(
            key("?123", 1.5f) { showLayer(Layer.SYMBOLS) },
            key("😊", 1f) { showLayer(Layer.EMOJI) },
            key(",") { typeChar(',') },
            key("space", 4f) { typeRaw(" ") },
            key(".") { typeChar('.') },
            key("⏎", 1.5f) { pressEnter() }
        ))
    }

    private fun rawKeys(chars: String): List<Button> =
        chars.map { c -> key(c.toString()) { typeRaw(c.toString()) } }

    private fun buildSymbols() {
        keyArea.addView(row(*rawKeys("1234567890").toTypedArray()))
        keyArea.addView(row(*rawKeys("@#\$%&-+()/").toTypedArray()))
        keyArea.addView(row(*rawKeys("*\"':;!?_=").toTypedArray(), backspaceKey()))
        keyArea.addView(row(
            key("ABC", 1.5f) { showLayer(Layer.LETTERS) },
            key(",") { typeRaw(",") },
            key("space", 4f) { typeRaw(" ") },
            key(".") { typeRaw(".") },
            key("⏎", 1.5f) { pressEnter() }
        ))
    }

    private fun buildEmoji() {
        val rows = listOf(
            listOf("😀", "😂", "😍", "🥰", "😎", "😭", "😡", "👍"),
            listOf("🙏", "❤️", "🔥", "✨", "🎉", "💯", "😅", "🤔"),
            listOf("👋", "😊", "😢", "😴", "🤝", "💪", "🌟", "✅")
        )
        rows.forEach { r ->
            keyArea.addView(row(*r.map { e -> key(e) { typeRaw(e) } }.toTypedArray()))
        }
        keyArea.addView(row(
            key("ABC", 1.5f) { showLayer(Layer.LETTERS) },
            key("space", 4f) { typeRaw(" ") },
            backspaceKey(),
            key("⏎", 1.5f) { pressEnter() }
        ))
    }
}