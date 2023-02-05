package com.likeminds.feedsx.post.view

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.likeminds.feedsx.R
import com.likeminds.feedsx.branding.model.BrandingData
import com.likeminds.feedsx.databinding.FragmentCreatePostBinding
import com.likeminds.feedsx.media.model.*
import com.likeminds.feedsx.media.util.MediaUtils
import com.likeminds.feedsx.media.view.MediaPickerActivity
import com.likeminds.feedsx.media.view.MediaPickerActivity.Companion.ARG_MEDIA_PICKER_RESULT
import com.likeminds.feedsx.media.view.MediaPickerActivity.Companion.BROWSE_DOCUMENT
import com.likeminds.feedsx.media.view.MediaPickerActivity.Companion.BROWSE_MEDIA
import com.likeminds.feedsx.post.util.CreatePostListener
import com.likeminds.feedsx.post.view.adapter.CreatePostDocumentsAdapter
import com.likeminds.feedsx.post.view.adapter.CreatePostMultipleMediaAdapter
import com.likeminds.feedsx.post.viewmodel.CreatePostViewModel
import com.likeminds.feedsx.posttypes.model.LinkOGTags
import com.likeminds.feedsx.utils.AndroidUtils
import com.likeminds.feedsx.utils.ValueUtils.isValidYoutubeLink
import com.likeminds.feedsx.utils.ViewDataConverter.convertSingleDataUri
import com.likeminds.feedsx.utils.ViewUtils.getUrlIfExist
import com.likeminds.feedsx.utils.ViewUtils.hide
import com.likeminds.feedsx.utils.ViewUtils.show
import com.likeminds.feedsx.utils.customview.BaseFragment
import com.likeminds.feedsx.utils.databinding.ImageBindingUtil
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreatePostFragment :
    BaseFragment<FragmentCreatePostBinding>(),
    CreatePostListener {

    private val viewModel: CreatePostViewModel by viewModels()

    private var selectedMediaUris: ArrayList<SingleUriData> = arrayListOf()

    private var multiMediaAdapter: CreatePostMultipleMediaAdapter? = null
    private var documentsAdapter: CreatePostDocumentsAdapter? = null

    override fun getViewBinding(): FragmentCreatePostBinding {
        return FragmentCreatePostBinding.inflate(layoutInflater)
    }

    override fun setUpViews() {
        super.setUpViews()
        initAddAttachmentsView()
        initPostContentTextListener()
    }

    // triggers gallery launcher for (IMAGE)/(VIDEO)/(IMAGE & VIDEO)
    private fun initiateMediaPicker(list: List<String>) {
        val extras = MediaPickerExtras.Builder()
            .mediaTypes(list)
            .allowMultipleSelect(true)
            .build()

        val intent = MediaPickerActivity.getIntent(requireContext(), extras)
        galleryLauncher.launch(intent)
    }

    // initializes click listeners on add attachment layouts
    private fun initAddAttachmentsView() {
        binding.layoutAttachFiles.setOnClickListener {
            val extra = MediaPickerExtras.Builder()
                .mediaTypes(listOf(PDF))
                .allowMultipleSelect(true)
                .build()
            val intent = MediaPickerActivity.getIntent(requireContext(), extra)
            documentLauncher.launch(intent)
        }

        binding.layoutAddImage.setOnClickListener {
            initiateMediaPicker(listOf(IMAGE))
        }

        binding.layoutAddVideo.setOnClickListener {
            initiateMediaPicker(listOf(VIDEO))
        }
    }

    // process the result obtained from media picker
    private fun checkMediaPickedResult(result: MediaPickerResult?) {
        if (result != null) {
            when (result.mediaPickerResultType) {
                MEDIA_RESULT_BROWSE -> {
                    if (MediaType.isPDF(result.mediaTypes)) {
                        val intent = AndroidUtils.getExternalDocumentPickerIntent(
                            allowMultipleSelect = result.allowMultipleSelect
                        )
                        startActivityForResult(intent, BROWSE_DOCUMENT)
                    } else {
                        val intent = AndroidUtils.getExternalPickerIntent(
                            result.mediaTypes,
                            result.allowMultipleSelect,
                            result.browseClassName
                        )
                        if (intent != null)
                            startActivityForResult(intent, BROWSE_MEDIA)
                    }
                }
                MEDIA_RESULT_PICKED -> {
                    onMediaPicked(result)
                }
            }
        }
    }

    private fun onMediaPickedFromGallery(data: Intent?) {
        val uris = MediaUtils.getExternalIntentPickerUris(data)
        viewModel.fetchUriDetails(requireContext(), uris) {
            val mediaUris = MediaUtils.convertMediaViewDataToSingleUriData(
                requireContext(), it
            )
            selectedMediaUris.addAll(mediaUris)
            if (mediaUris.isNotEmpty()) {
                showPostMedia()
            }
        }
    }

    private fun onPdfPicked(data: Intent?) {
        val uris = MediaUtils.getExternalIntentPickerUris(data)
        viewModel.fetchUriDetails(requireContext(), uris) {
            val mediaUris = MediaUtils.convertMediaViewDataToSingleUriData(
                requireContext(), it
            )
            selectedMediaUris.addAll(mediaUris)
            if (mediaUris.isNotEmpty()) {
                showAttachedDocuments()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        when (requestCode) {
            BROWSE_MEDIA -> {
                onMediaPickedFromGallery(data)
            }
            BROWSE_DOCUMENT -> {
                onPdfPicked(data)
            }
        }
    }

    // converts the picked media to SingleUriData and adds to the selected media
    private fun onMediaPicked(result: MediaPickerResult) {
        val data =
            MediaUtils.convertMediaViewDataToSingleUriData(requireContext(), result.medias)
        selectedMediaUris.addAll(data)
        showPostMedia()
    }

    // handles the logic to show the type of post
    private fun showPostMedia() {
        when {
            selectedMediaUris.size >= 1 && MediaType.isPDF(selectedMediaUris.first().fileType) -> {
                showAttachedDocuments()
            }
            selectedMediaUris.size == 1 && MediaType.isImage(selectedMediaUris.first().fileType) -> {
                showAttachedImage()
            }
            selectedMediaUris.size == 1 && MediaType.isVideo(selectedMediaUris.first().fileType) -> {
                showAttachedVideo()
            }
            selectedMediaUris.size >= 1 -> {
                showMultiMediaAttachments()
            }
            else -> {
                val text = binding.etPostContent.text?.trim()
                if (selectedMediaUris.size == 0 && text != null) {
                    showLinkPreview(text.toString())
                }
                handlePostButton(!text.isNullOrEmpty())
                handleAddAttachmentLayouts(true)
            }
        }
    }

    // adds text watcher on post content edit text
    private fun initPostContentTextListener() {
        binding.etPostContent.doAfterTextChanged {
            val text = it?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                if (selectedMediaUris.isEmpty()) handlePostButton(false)
                else handlePostButton(true)
            } else {
                showPostMedia()
                handlePostButton(true)
            }
        }
    }

    // handles click action on Post button
    private fun handlePostButton(clickable: Boolean) {
        val createPostActivity = requireActivity() as CreatePostActivity
        if (clickable) {
            createPostActivity.binding.tvPostDone.isClickable = true
            createPostActivity.binding.tvPostDone.setTextColor(BrandingData.getButtonsColor())
        } else {
            createPostActivity.binding.tvPostDone.isClickable = false
            createPostActivity.binding.tvPostDone.setTextColor(Color.parseColor("#666666"))
        }
    }

    // handles visibility of add attachment layouts
    private fun handleAddAttachmentLayouts(show: Boolean) {
        binding.groupAddAttachments.isVisible = show
    }

    // shows link preview for link post type
    private fun showLinkPreview(text: String?) {
        if (text.isNullOrEmpty()) {
            binding.linkPreview.root.hide()
            return
        }

        val link = text.getUrlIfExist()

        if (!link.isNullOrEmpty()) {

            //TODO: handle internal links
//            if (Route.isInternalLink(link)) {
//                sharedData = sharedData.isInternalLink(true)
//                viewModel.fetchPreview(link)
//                setInitialDataInInternalLinkView(link)
//            } else {
//                ProgressHelper.showProgress(binding.inputBox.viewLink.progressBar)
//                viewModel.decodeUrl(link)
//            }
            //TODO: testing data

            val linkData = LinkOGTags.Builder()
                .title("Youtube video")
                .image("https://i.ytimg.com/vi/EbyAoYaUcVo/hq720.jpg?sqp=-oaymwEcCNAFEJQDSFXyq4qpAw4IARUAAIhCGAFwAcABBg==&rs=AOn4CLDiI5bXtT71sC4IAnHiDAh52LxbFA")
                .url("https://www.youtube.com/watch?v=sAuQjwEl-Bo")
                .description("This is a youtube video")
                .build()

            initLinkView(
                linkData
            )
        } else {
            binding.linkPreview.root.hide()
        }
    }

    // renders data in the link view
    private fun initLinkView(data: LinkOGTags) {
        binding.linkPreview.apply {
            this.root.show()
            val isYoutubeLink = data.url?.isValidYoutubeLink() == true
            tvLinkTitle.text = if (data.title?.isNotBlank() == true) {
                data.title
            } else {
                binding.root.context.getString(R.string.link)
            }
            tvLinkDescription.isVisible = !data.description.isNullOrEmpty()
            tvLinkDescription.text = data.description

            if (isYoutubeLink) {
                ivLink.hide()
                ivPlay.isVisible = !data.image.isNullOrEmpty()
                ivYoutubeLink.isVisible = !data.image.isNullOrEmpty()
                ivYoutubeLogo.isVisible = !data.image.isNullOrEmpty()
            } else {
                ivPlay.hide()
                ivYoutubeLink.hide()
                ivYoutubeLogo.hide()
                ivLink.isVisible = !data.image.isNullOrEmpty()
            }

            ImageBindingUtil.loadImage(
                if (isYoutubeLink) ivYoutubeLink else ivLink,
                data.image,
                placeholder = R.drawable.ic_link_primary_40dp,
                cornerRadius = 8,
                isBlur = isYoutubeLink
            )

            tvLinkUrl.text = data.url
            ivCross.setOnClickListener {
                this.root.hide()
            }
        }
    }

    // shows attached video in single video post type
    private fun showAttachedVideo() {
        handleAddAttachmentLayouts(false)
        handlePostButton(true)
        binding.apply {
            singleVideoAttachment.root.show()
            singleImageAttachment.root.hide()
            linkPreview.root.hide()
            documentsAttachment.root.hide()
            multipleMediaAttachment.root.hide()
            singleVideoAttachment.btnAddMore.setOnClickListener {
                initiateMediaPicker(listOf(IMAGE, VIDEO))
            }
            singleVideoAttachment.layoutSingleVideoPost.ivCross.setOnClickListener {
                selectedMediaUris.clear()
                singleVideoAttachment.root.hide()
                handleAddAttachmentLayouts(true)
                val text = etPostContent.text?.trim()
                handlePostButton(!text.isNullOrEmpty())
            }

            //TODO: Use exo player
            singleVideoAttachment.layoutSingleVideoPost.vvSingleVideoPost.setVideoURI(
                selectedMediaUris.first().uri
            )
        }
    }

    // shows attached image in single image post type
    private fun showAttachedImage() {
        handleAddAttachmentLayouts(false)
        handlePostButton(true)
        binding.apply {
            singleImageAttachment.root.show()
            singleVideoAttachment.root.hide()
            linkPreview.root.hide()
            documentsAttachment.root.hide()
            multipleMediaAttachment.root.hide()
            singleImageAttachment.btnAddMore.setOnClickListener {
                initiateMediaPicker(listOf(IMAGE, VIDEO))
            }
            singleImageAttachment.layoutSingleImagePost.ivCross.setOnClickListener {
                selectedMediaUris.clear()
                singleImageAttachment.root.hide()
                handleAddAttachmentLayouts(true)
                val text = etPostContent.text?.trim()
                handlePostButton(!text.isNullOrEmpty())
            }

            ImageBindingUtil.loadImage(
                singleImageAttachment.layoutSingleImagePost.ivSingleImagePost,
                selectedMediaUris.first().uri,
                placeholder = R.drawable.image_placeholder
            )
        }
    }

    // shows view pager with multiple media
    private fun showMultiMediaAttachments() {
        handleAddAttachmentLayouts(false)
        handlePostButton(true)
        binding.apply {
            singleImageAttachment.root.hide()
            singleVideoAttachment.root.hide()
            linkPreview.root.hide()
            documentsAttachment.root.hide()
            multipleMediaAttachment.root.show()
            multipleMediaAttachment.btnAddMore.setOnClickListener {
                initiateMediaPicker(listOf(IMAGE, VIDEO))
            }

            val attachments = selectedMediaUris.map {
                convertSingleDataUri(it)
            }

            if (multiMediaAdapter == null) {
                multipleMediaAttachment.viewpagerMultipleMedia.isSaveEnabled = false
                multiMediaAdapter = CreatePostMultipleMediaAdapter(this@CreatePostFragment)
                multipleMediaAttachment.viewpagerMultipleMedia.adapter = multiMediaAdapter
                multipleMediaAttachment.dotsIndicator.setViewPager2(multipleMediaAttachment.viewpagerMultipleMedia)
            }
            multiMediaAdapter!!.replace(attachments)
        }
    }

    // shows document recycler view with attached files
    private fun showAttachedDocuments() {
        handleAddAttachmentLayouts(false)
        handlePostButton(true)
        binding.apply {
            singleVideoAttachment.root.hide()
            singleImageAttachment.root.hide()
            linkPreview.root.hide()
            documentsAttachment.root.show()
            multipleMediaAttachment.root.hide()
            documentsAttachment.btnAddMore.setOnClickListener {
                initiateMediaPicker(listOf(PDF))
            }

            val attachments = selectedMediaUris.map {
                convertSingleDataUri(it)
            }

            if (documentsAdapter == null) {
                documentsAdapter = CreatePostDocumentsAdapter(this@CreatePostFragment)
                documentsAttachment.rvDocuments.apply {
                    adapter = documentsAdapter
                    layoutManager = LinearLayoutManager(context)
                }
            }
            documentsAdapter!!.replace(attachments)
        }
    }

    // triggered when a document/media from view pager is removed
    override fun onMediaRemoved(position: Int, mediaType: String) {
        selectedMediaUris.removeAt(position)
        if (mediaType == PDF) documentsAdapter?.removeIndex(position)
        else multiMediaAdapter?.removeIndex(position)
        showPostMedia()
    }

    // launcher to handle gallery (IMAGE/VIDEO) intent
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data =
                    result.data?.extras?.getParcelable<MediaPickerResult>(ARG_MEDIA_PICKER_RESULT)
                checkMediaPickedResult(data)
            }
        }

    // launcher to handle document (PDF) intent
    private val documentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data =
                    result.data?.extras?.getParcelable<MediaPickerResult>(ARG_MEDIA_PICKER_RESULT)
                checkMediaPickedResult(data)
            }
        }
}