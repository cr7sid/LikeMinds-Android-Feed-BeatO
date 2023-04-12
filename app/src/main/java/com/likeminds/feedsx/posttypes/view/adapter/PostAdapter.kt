package com.likeminds.feedsx.posttypes.view.adapter

import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.likeminds.feedsx.posttypes.model.PostViewData
import com.likeminds.feedsx.posttypes.view.adapter.databinder.*
import com.likeminds.feedsx.utils.ValueUtils.getItemInList
import com.likeminds.feedsx.utils.customview.BaseRecyclerAdapter
import com.likeminds.feedsx.utils.customview.ViewDataBinder
import com.likeminds.feedsx.utils.model.BaseViewType

class PostAdapter constructor(
    val listener: PostAdapterListener
) : BaseRecyclerAdapter<BaseViewType>() {

    init {
        initViewDataBinders()
    }

    override fun getSupportedViewDataBinder(): MutableList<ViewDataBinder<*, *>> {
        val viewDataBinders = ArrayList<ViewDataBinder<*, *>>(6)

        val itemPostTextOnlyBinder = ItemPostTextOnlyViewDataBinder(listener)
        viewDataBinders.add(itemPostTextOnlyBinder)

        val itemPostSingleImageViewDataBinder = ItemPostSingleImageViewDataBinder(listener)
        viewDataBinders.add(itemPostSingleImageViewDataBinder)

        val itemPostSingleVideoViewDataBinder = ItemPostSingleVideoViewDataBinder(listener)
        viewDataBinders.add(itemPostSingleVideoViewDataBinder)

        val itemPostLinkViewDataBinder = ItemPostLinkViewDataBinder(listener)
        viewDataBinders.add(itemPostLinkViewDataBinder)

        val itemPostDocumentsViewDataBinder = ItemPostDocumentsViewDataBinder(listener)
        viewDataBinders.add(itemPostDocumentsViewDataBinder)

        val itemPostMultipleMediaViewDataBinder = ItemPostMultipleMediaViewDataBinder(listener)
        viewDataBinders.add(itemPostMultipleMediaViewDataBinder)

        return viewDataBinders
    }

    operator fun get(position: Int): BaseViewType? {
        return items().getItemInList(position)
    }
}

interface PostAdapterListener {
    fun updatePostSeenFullContent(position: Int, alreadySeenFullContent: Boolean)
    fun savePost(position: Int)
    fun likePost(position: Int)
    fun sharePost() {}
    fun comment(postId: String)
    fun onPostMenuItemClicked(
        postId: String,
        creatorId: String,
        title: String
    )

    fun onMultipleDocumentsExpanded(postData: PostViewData, position: Int)
    fun showLikesScreen(postId: String)
    fun postDetail(postData: PostViewData) {}
    fun updateFromLikedSaved(position: Int)
    fun sendMediaItemToExoPlayer(
        position: Int,
        playerView: StyledPlayerView,
        item: MediaItem
    ) {
    }

    fun playPauseOnVideo(position: Int) {}
}