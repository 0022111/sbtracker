package com.sbtracker

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.sbtracker.data.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    @Inject lateinit var prefsRepo: UserPreferencesRepository

    private lateinit var pager: ViewPager2
    private lateinit var llDots: LinearLayout
    private lateinit var btnNext: FrameLayout
    private lateinit var tvNextLabel: TextView
    private lateinit var tvSkip: TextView

    private val pages = listOf(
        OnboardingPage(
            emoji = "🌿",
            title = "Welcome to SBTracker",
            body = "Track every session, monitor your device, and gain insights about your usage habits — all from one place.",
            permissionLabel = null
        ),
        OnboardingPage(
            emoji = "📡",
            title = "Bluetooth Access",
            body = "SBTracker uses Bluetooth to connect to your device and read live temperature, battery, and session data.\n\nNo data is sent externally.",
            permissionLabel = "Grant Bluetooth"
        ),
        OnboardingPage(
            emoji = "🔔",
            title = "Notifications",
            body = "Get notified when your device reaches target temperature, when battery is at 80%, or when a session ends.\n\nYou can change this anytime in Settings.",
            permissionLabel = if (Build.VERSION.SDK_INT >= 33) "Allow Notifications" else null
        )
    )

    private val requestBle = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
    private val requestNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        pager = findViewById(R.id.onboarding_pager)
        llDots = findViewById(R.id.ll_dots)
        btnNext = findViewById(R.id.btn_onboarding_next)
        tvNextLabel = findViewById(R.id.tv_onboarding_next_label)
        tvSkip = findViewById(R.id.tv_onboarding_skip)

        pager.adapter = OnboardingPagerAdapter()
        pager.isUserInputEnabled = true

        buildDots()

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                tvNextLabel.text = if (position == pages.lastIndex) "Get Started" else "Next"
                tvSkip.visibility = if (position == pages.lastIndex) View.INVISIBLE else View.VISIBLE
            }
        })

        btnNext.setOnClickListener {
            val current = pager.currentItem
            if (current < pages.lastIndex) {
                pager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }

        tvSkip.setOnClickListener { completeOnboarding() }
    }

    private fun buildDots() {
        llDots.removeAllViews()
        for (i in pages.indices) {
            val dot = View(this).apply {
                val size = if (i == 0) 10.dp else 8.dp
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = 8.dp
                    it.marginStart = if (i == 0) 0 else 0
                }
                background = ContextCompat.getDrawable(
                    this@OnboardingActivity,
                    if (i == 0) R.drawable.bg_badge_green else R.drawable.bg_chip
                )
            }
            llDots.addView(dot)
        }
    }

    private fun updateDots(active: Int) {
        for (i in 0 until llDots.childCount) {
            val dot = llDots.getChildAt(i)
            val size = if (i == active) 10.dp else 8.dp
            val params = dot.layoutParams as LinearLayout.LayoutParams
            params.width = size
            params.height = size
            dot.layoutParams = params
            dot.background = ContextCompat.getDrawable(
                this,
                if (i == active) R.drawable.bg_badge_green else R.drawable.bg_chip
            )
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun completeOnboarding() {
        lifecycleScope.launch {
            prefsRepo.setOnboardingComplete()
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }

    fun requestBlePermissions() {
        val perms = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        requestBle.launch(perms)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    data class OnboardingPage(
        val emoji: String,
        val title: String,
        val body: String,
        val permissionLabel: String?
    )

    inner class OnboardingPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = pages.size
        override fun createFragment(position: Int): Fragment =
            OnboardingPageFragment.newInstance(
                emoji = pages[position].emoji,
                title = pages[position].title,
                body = pages[position].body,
                permissionLabel = pages[position].permissionLabel
            )
    }
}

class OnboardingPageFragment : Fragment() {

    companion object {
        private const val ARG_EMOJI = "emoji"
        private const val ARG_TITLE = "title"
        private const val ARG_BODY = "body"
        private const val ARG_PERM = "perm"

        fun newInstance(emoji: String, title: String, body: String, permissionLabel: String?): OnboardingPageFragment =
            OnboardingPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EMOJI, emoji)
                    putString(ARG_TITLE, title)
                    putString(ARG_BODY, body)
                    permissionLabel?.let { putString(ARG_PERM, it) }
                }
            }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_onboarding_page, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tv_onboarding_emoji).text = arguments?.getString(ARG_EMOJI) ?: ""
        view.findViewById<TextView>(R.id.tv_onboarding_title).text = arguments?.getString(ARG_TITLE) ?: ""
        view.findViewById<TextView>(R.id.tv_onboarding_body).text = arguments?.getString(ARG_BODY) ?: ""

        val permLabel = arguments?.getString(ARG_PERM)
        val btnPerm = view.findViewById<FrameLayout>(R.id.btn_grant_permission)
        val tvPermLabel = view.findViewById<TextView>(R.id.tv_grant_permission_label)
        if (permLabel != null) {
            btnPerm.visibility = View.VISIBLE
            tvPermLabel.text = permLabel
            btnPerm.setOnClickListener {
                val activity = requireActivity() as? OnboardingActivity ?: return@setOnClickListener
                val args = arguments?.getString(ARG_PERM)
                if (args == "Grant Bluetooth") activity.requestBlePermissions()
                else activity.requestNotificationPermission()
            }
        } else {
            btnPerm.visibility = View.GONE
        }
    }
}
