package com.likeminds.feedsx.report.view

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.likeminds.feedsx.FeedSXApplication.Companion.LOG_TAG
import com.likeminds.feedsx.LMAnalytics
import com.likeminds.feedsx.R
import com.likeminds.feedsx.databinding.FragmentReportBinding
import com.likeminds.feedsx.posttypes.model.PostViewData
import com.likeminds.feedsx.report.model.*
import com.likeminds.feedsx.report.view.adapter.ReportAdapter
import com.likeminds.feedsx.report.view.adapter.ReportAdapter.ReportAdapterListener
import com.likeminds.feedsx.report.viewmodel.ReportViewModel
import com.likeminds.feedsx.utils.ViewUtils
import com.likeminds.feedsx.utils.customview.BaseFragment
import com.likeminds.feedsx.utils.emptyExtrasException
import com.likeminds.feedsx.utils.model.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReportFragment : BaseFragment<FragmentReportBinding>(),
    ReportAdapterListener {

    private val viewModel: ReportViewModel by viewModels()
    private lateinit var reason: String

    override fun getViewBinding(): FragmentReportBinding {
        return FragmentReportBinding.inflate(layoutInflater)
    }

    companion object {
        const val TAG = "ReportFragment"
        const val REPORT_RESULT = "REPORT_RESULT"
    }

    private lateinit var extras: ReportExtras
    private lateinit var mAdapter: ReportAdapter
    private var tagSelected: ReportTagViewData? = null

    override fun receiveExtras() {
        super.receiveExtras()
        extras = requireActivity().intent?.getBundleExtra("bundle")
            ?.getParcelable(ReportActivity.ARG_REPORTS)
            ?: throw emptyExtrasException(TAG)
    }

    override fun reportTagSelected(reportTagViewData: ReportTagViewData) {
        super.reportTagSelected(reportTagViewData)
        //check if [Others] is selected, edit text for reason should be visible
        binding.etOthers.isVisible = reportTagViewData.name.contains("Others", true)

        //replace list in adapter and only highlight selected tag
        mAdapter.replace(
            mAdapter.items()
                .map {
                    (it as ReportTagViewData).toBuilder()
                        .isSelected(it.id == reportTagViewData.id)
                        .build()
                })
    }

    override fun setUpViews() {
        super.setUpViews()
        initRecyclerView()
        initViewAsType()
        initListeners()
        getReportTags()
    }

    override fun observeData() {
        super.observeData()

        viewModel.listOfTagViewData.observe(viewLifecycleOwner) { tags ->
            mAdapter.replace(tags)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            ViewUtils.showErrorMessageToast(requireContext(), error)
            requireActivity().setResult(Activity.RESULT_CANCELED)
            requireActivity().finish()
        }

        viewModel.postReportResponse.observe(viewLifecycleOwner) { success ->
            if (success) {
                Log.d(LOG_TAG, "report send successfully")

                //send analytics events
                sendReportEvent()

                val intent = Intent().apply {
                    putExtra(
                        REPORT_RESULT,
                        ReportType.getEntityType(this@ReportFragment.extras.entityType)
                    )
                }
                //set result, from where the result is coming.
                requireActivity().setResult(Activity.RESULT_OK, intent)
                requireActivity().finish()
            }
        }
    }

    //send report event depending upon which type of the report is created
    private fun sendReportEvent() {
        when (extras.entityType) {
            REPORT_TYPE_POST -> {
                viewModel.sendPostReportedEvent(
                    extras.entityId,
                    extras.entityCreatorId,
                    getPostType(extras.post),
                    reason
                )
            }
            REPORT_TYPE_COMMENT -> {
                viewModel.sendCommentReportedEvent(
                    extras.post.id,
                    extras.entityCreatorId,
                    extras.entityId,
                    reason
                )
            }
            REPORT_TYPE_REPLY -> {
                viewModel.sendReplyReportedEvent(
                    extras.post.id,
                    extras.entityCreatorId,
                    extras.parentCommentId,
                    extras.entityId,
                    reason
                )
            }
        }
    }

    private fun getPostType(post: PostViewData): String {
        return when (post.viewType) {
            ITEM_POST_TEXT_ONLY -> {
                LMAnalytics.Keys.POST_TYPE_TEXT
            }
            ITEM_POST_SINGLE_IMAGE -> {
                LMAnalytics.Keys.POST_TYPE_IMAGE
            }
            ITEM_POST_SINGLE_VIDEO -> {
                LMAnalytics.Keys.POST_TYPE_VIDEO
            }
            ITEM_POST_DOCUMENTS -> {
                LMAnalytics.Keys.POST_TYPE_DOCUMENT
            }
            ITEM_POST_MULTIPLE_MEDIA -> {
                LMAnalytics.Keys.POST_TYPE_IMAGE_VIDEO
            }
            ITEM_POST_LINK -> {
                LMAnalytics.Keys.POST_TYPE_LINK
            }
            else -> {
                LMAnalytics.Keys.POST_TYPE_TEXT
            }
        }
    }

    //setup recycler view
    private fun initRecyclerView() {
        mAdapter = ReportAdapter(this)
        val flexboxLayoutManager = FlexboxLayoutManager(requireContext())
        flexboxLayoutManager.flexDirection = FlexDirection.ROW
        flexboxLayoutManager.justifyContent = JustifyContent.FLEX_START
        binding.rvReport.layoutManager = flexboxLayoutManager
        binding.rvReport.adapter = mAdapter
    }

    //set headers and sub header as per report type
    private fun initViewAsType() {
        when (extras.entityType) {
            REPORT_TYPE_POST -> {
                binding.tvReportSubHeader.text = getString(R.string.report_sub_header, "post")
            }
            REPORT_TYPE_COMMENT -> {
                binding.tvReportSubHeader.text = getString(R.string.report_sub_header, "comment")
            }
            REPORT_TYPE_REPLY -> {
                binding.tvReportSubHeader.text = getString(R.string.report_sub_header, "reply")
            }
        }
    }

    private fun initListeners() {
        binding.ivCross.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnPostReport.setOnClickListener {
            //get selected tag
            tagSelected = mAdapter.items()
                .map { it as ReportTagViewData }
                .find { it.isSelected }

            //get reason for [edittext]
            reason = binding.etOthers.text?.trim().toString()
            val isOthersSelected = tagSelected?.name?.contains("Others", true)

            //if no tag is selected
            if (tagSelected == null) {
                ViewUtils.showShortSnack(
                    binding.root,
                    "Please select at least on report tag."
                )
                return@setOnClickListener
            }

            //if [Others] is selected but reason is empty
            if (isOthersSelected == true && reason.isEmpty()) {
                ViewUtils.showShortSnack(
                    binding.root,
                    "Please enter a reason."
                )
                return@setOnClickListener
            }

            //call post api
            viewModel.postReport(
                extras.entityId,
                extras.entityCreatorId,
                extras.entityType,
                tagSelected?.id,
                reason
            )
        }
    }

    //get tags
    private fun getReportTags() {
        viewModel.getReportTags()
    }
}