/*
 * Copyright (c) 2018 ThanksMister LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thanksmister.iot.mqtt.alarmpanel.ui.fragments

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.content.LocalBroadcastManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.CheckBox
import android.widget.LinearLayout
import com.thanksmister.iot.mqtt.alarmpanel.BaseFragment
import com.thanksmister.iot.mqtt.alarmpanel.R
import com.thanksmister.iot.mqtt.alarmpanel.network.AlarmPanelService
import com.thanksmister.iot.mqtt.alarmpanel.persistence.Configuration
import com.thanksmister.iot.mqtt.alarmpanel.utils.DialogUtils
import kotlinx.android.synthetic.main.bottom_sheet.*
import kotlinx.android.synthetic.main.dialog_progress.*
import kotlinx.android.synthetic.main.fragment_platform.*
import timber.log.Timber
import javax.inject.Inject

class PlatformFragment : BaseFragment(){

    @Inject lateinit var configuration: Configuration
    @Inject lateinit var dialogUtils: DialogUtils
    private var listener: OnPlatformFragmentListener? = null
    private var currentUrl:String? = null
    private var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>? = null
    private var displayProgress = true
    private var zoomLevel = 1.0f

    interface OnPlatformFragmentListener {
        fun navigateAlarmPanel()
        fun setPagingEnabled(value: Boolean)
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is OnPlatformFragmentListener) {
            listener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnPlatformFragmentListener")
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        if (configuration.platformBar) {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED;
        } else {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN;
        }
        if(configuration.hasPlatformChange()) {
            configuration.setHasPlatformChange(false)
            loadWebPage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(dialogUtils)
        currentUrl = configuration.webUrl
        displayProgress = configuration.appShowActivity
        zoomLevel = configuration.testZoomLevel
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Timber.d("onActivityCreated")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
        button_alarm.setOnClickListener {
            if(listener != null) {
                listener!!.navigateAlarmPanel()
            }
        }
        button_refresh.setOnClickListener {
            loadWebPage()
        }
        button_hide.setOnClickListener { v ->
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN;
        }
        loadWebPage()
    }

    private fun loadWebPage() {
        Timber.d("loadWebPage url ${configuration.webUrl}")
        if (configuration.hasPlatformModule() && !TextUtils.isEmpty(configuration.webUrl) && webView != null) {
            configureWebSettings(configuration.browserUserAgent)
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (!displayProgress) return
                    if (newProgress == 100) {
                        prgressDialog.visibility = View.GONE
                        pageLoadComplete(view.url)
                        return
                    }
                    prgressDialog.visibility = View.VISIBLE
                    progressDialogMessage.text = getString(R.string.progress_loading, newProgress.toString())
                }

                override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    dialogUtils.showAlertDialog(view.context, message)
                    return true
                }
            }
            webView.webViewClient = object : WebViewClient() {
                //If you will not use this method url links are open in new browser not in webview
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    webView.loadUrl(configuration.webUrl)
                    return true
                }
            }
            if (zoomLevel.toDouble() != 1.0) {
                webView!!.setInitialScale((zoomLevel * 100).toInt())
            }
            webView.loadUrl(configuration.webUrl)
        } else if (webView != null) {
            webView.loadUrl(WEBSITE)
        }
    }

    private fun pageLoadComplete(url: String) {
        Timber.d("pageLoadComplete currentUrl $url")
        val intent = Intent(AlarmPanelService.BROADCAST_EVENT_URL_CHANGE)
        intent.putExtra(AlarmPanelService.BROADCAST_EVENT_URL_CHANGE, url)
        if(activity != null) {
            val bm = LocalBroadcastManager.getInstance(activity!!)
            bm.sendBroadcast(intent)
        }
    }

    private fun configureWebSettings(userAgent: String) {
        val webSettings = webView!!.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.setAppCacheEnabled(true)
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        if(!TextUtils.isEmpty(userAgent)) {
            webSettings.userAgentString = userAgent
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        Timber.d(webSettings.userAgentString)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_platform, container, false)
    }

    companion object {
        fun newInstance(): PlatformFragment {
            return PlatformFragment()
        }
        const val WEBSITE: String = "http://thanksmister.com/android-mqtt-alarm-panel/"
    }
}