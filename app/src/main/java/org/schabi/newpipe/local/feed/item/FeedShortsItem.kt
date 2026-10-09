package org.schabi.newpipe.local.feed.item

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.FeedShortsShelfBinding
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PicassoHelper

class FeedShortsItem(
    private val streams: List<StreamEntity>,
    private val onStreamSelected: (StreamEntity) -> Unit
) : BindableItem<FeedShortsShelfBinding>() {

    override fun getId(): Long = SHORTS_ID

    override fun getLayout(): Int = R.layout.feed_shorts_shelf

    override fun initializeViewBinding(view: View) = FeedShortsShelfBinding.bind(view)

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount

    override fun bind(viewBinding: FeedShortsShelfBinding, position: Int) {
        val context = viewBinding.root.context
        val density = context.resources.displayMetrics.density
        val itemWidth = (112 * density).toInt()
        val thumbnailHeight = (148 * density).toInt()
        viewBinding.feedShortsItems.removeAllViews()

        streams.forEach { stream ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                contentDescription = stream.title
                setOnClickListener { onStreamSelected(stream) }
            }
            val thumbnailFrame = FrameLayout(context)
            val thumbnail = ShapeableImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(12 * density)
                    .build()
            }
            PicassoHelper.loadScaledDownThumbnail(context, stream.thumbnailUrl).into(thumbnail)
            thumbnailFrame.addView(
                thumbnail,
                FrameLayout.LayoutParams(itemWidth, thumbnailHeight)
            )

            val duration = TextView(context).apply {
                text = Localization.getDurationString(stream.duration)
                setTextColor(Color.WHITE)
                textSize = 10f
                setPadding(
                    (5 * density).toInt(),
                    (2 * density).toInt(),
                    (5 * density).toInt(),
                    (2 * density).toInt()
                )
                background = GradientDrawable().apply {
                    cornerRadius = 3 * density
                    setColor(0xCC000000.toInt())
                }
            }
            thumbnailFrame.addView(
                duration,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    marginEnd = (5 * density).toInt()
                    bottomMargin = (5 * density).toInt()
                }
            )
            card.addView(thumbnailFrame)

            val title = TextView(context).apply {
                text = stream.title
                setTextColor(0xFF0F0F0F.toInt())
                textSize = 12f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, (6 * density).toInt(), 0, 0)
            }
            card.addView(
                title,
                LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            viewBinding.feedShortsItems.addView(
                card,
                LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (10 * density).toInt()
                }
            )
        }
    }

    companion object {
        private const val SHORTS_ID = Long.MIN_VALUE + 2
    }
}
