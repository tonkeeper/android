package uikit.widget.stories

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import uikit.R
import uikit.base.BaseFragment
import uikit.widget.FrescoView
import uikit.widget.RowLayout

open class BaseStoriesScreen: BaseFragment(R.layout.fragment_stories) {

    data class Item(val image: Uri, val title: String, val subtitle: String)

    private lateinit var imageView: FrescoView
    private lateinit var linesView: RowLayout
    private lateinit var closeView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imageView = view.findViewById(R.id.stories_image)
        linesView = view.findViewById(R.id.stories_lines)
        closeView = view.findViewById(R.id.stories_close)
    }
}