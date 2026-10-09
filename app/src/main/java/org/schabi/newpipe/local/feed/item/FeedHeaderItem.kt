package org.schabi.newpipe.local.feed.item

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedScreenHeaderBinding

class FeedHeaderItem(
    private val selectedCategory: String,
    private val onCategorySelected: (String) -> Unit
) : BindableItem<FeedScreenHeaderBinding>() {

    private val categories = listOf(
        R.string.feed_category_all,
        R.string.feed_category_gaming,
        R.string.feed_category_technology,
        R.string.feed_category_podcasts,
        R.string.feed_category_music,
        R.string.feed_category_live,
        R.string.feed_category_privacy
    )

    override fun getId(): Long = HEADER_ID

    override fun getLayout(): Int = R.layout.feed_screen_header

    override fun initializeViewBinding(view: View) = FeedScreenHeaderBinding.bind(view)

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount

    override fun bind(viewBinding: FeedScreenHeaderBinding, position: Int) {
        val context = viewBinding.root.context
        val density = context.resources.displayMetrics.density
        viewBinding.feedCategoryChips.removeAllViews()

        categories.forEach { categoryRes ->
            val category = context.getString(categoryRes)
            val selected = category == selectedCategory
            val chip = TextView(context).apply {
                text = category
                textSize = 13f
                setTextColor(if (selected) Color.WHITE else TEXT_COLOR)
                gravity = android.view.Gravity.CENTER
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                background = GradientDrawable().apply {
                    cornerRadius = 20 * density
                    setColor(if (selected) TEXT_COLOR else CHIP_COLOR)
                    if (!selected) {
                        setStroke((1 * density).toInt(), BORDER_COLOR)
                    }
                }
                contentDescription = category
                isClickable = true
                isFocusable = true
                setOnClickListener { onCategorySelected(category) }
            }
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (36 * density).toInt()
            ).apply {
                marginEnd = (8 * density).toInt()
            }
            viewBinding.feedCategoryChips.addView(chip, layoutParams)
        }
    }

    companion object {
        private const val HEADER_ID = Long.MIN_VALUE + 1
        private const val TEXT_COLOR = 0xFF0F0F0F.toInt()
        private const val CHIP_COLOR = 0xFFF2F2F2.toInt()
        private const val BORDER_COLOR = 0xFFE5E5E5.toInt()
    }
}
