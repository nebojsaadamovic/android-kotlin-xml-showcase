package com.example.zdravlje.ui.home

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.app.democall.composesample.data.call.JoinCallResponse
import com.app.democall.composesample.data.user.UserResponse
import com.example.zdravlje.data.model.request.PinRequest
import com.example.zdravlje.data.model.request.profile.KeepLiveRequest
import com.example.zdravlje.data.repository.DashboardRepository
import com.example.zdravlje.data.repository.DataStoreRepository
import com.example.zdravlje.data.repository.ProfileRepository
import com.example.zdravlje.data.sharedPref.PreferenceHelper
import com.example.zdravlje.logout
import com.example.zdravlje.services.CloudMessagingService
import com.example.zdravlje.services.CloudMessagingService.Companion.EXTRA_PUSH_IS_CALL_ACCEPTED
import com.example.zdravlje.services.CloudMessagingService.Companion.EXTRA_PUSH_PAYLOAD
import com.example.zdravlje.services.CloudMessagingService.Companion.EXTRA_PUSH_TYPE
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_ACTIVITY_REM
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_CREATE_CHAT
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_MEDICINE_REM
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_MODIFIED_CHAT
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_NEW_CALL
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_NEW_CONTACT_REQUEST
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_NEW_CONTACT_REQUEST_DECLINED
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_NEW_MESSAGE
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_PRESCRIPTION
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_RECORD_ACCESS
import com.example.zdravlje.services.CloudMessagingService.Companion.TYPE_VIDEO_CONSULTATION_SCHEDULED
import com.example.zdravlje.ui.base.BaseActivity
import com.example.zdravlje.ui.calendar.CalendarFragment
import com.example.zdravlje.ui.calendar.apointment.BottomSheetDialogConfirmFragment
import com.example.zdravlje.ui.calendar.nextOnSchedule.BottomSheetConfirmDeleteReminderFragment
import com.example.zdravlje.ui.chat.ChatInboxFragment
import com.example.zdravlje.ui.chat.singleAndGroupMessages.ChatMessagesActivity
import com.example.zdravlje.ui.fitparameters.FitParametersFragment
import com.example.zdravlje.ui.fitparameters.WayOfLifeFragment
import com.example.zdravlje.ui.healthcare.HealthcareFragment
import com.example.zdravlje.ui.healthcare.medicalcarton.MedicalCartonFragment
import com.example.zdravlje.ui.healthcare.prescribedlistmedicaments.PrescribedListMedicamentsFragment
import com.example.zdravlje.ui.healthcare.prescribedlistmedicaments.medicamentoptions.MedicamentDetailsFragment
import com.example.zdravlje.ui.healthcare.prescribedlistmedicaments.medicamentoptions.RecipeDetailsFragment
import com.example.zdravlje.ui.login.LoginActivity
import com.example.zdravlje.ui.login.LoginViewModel
import com.example.zdravlje.ui.notification.NotificationActivity
import com.example.zdravlje.ui.profile.ProfileViewModel
import com.example.zdravlje.ui.profile.ProfileViewModelFactory
import com.example.zdravlje.utilities.observeOnce
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.nites.ezdravljeapp.R
import com.nites.ezdravljeapp.databinding.ActivityHomeBinding
import java.util.concurrent.Executor

class HomeActivity : BaseActivity(),
    BottomSheetConfirmDeleteReminderFragment.OnCheckedListener {

    lateinit var binding: ActivityHomeBinding
    private var lastClickTime: Long = 0

    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var dataStoreRepository: DataStoreRepository

    lateinit var fab: FloatingActionButton

    val onNewIntentLD = MutableLiveData<Intent?>()


    // --- Biometric ---
    private lateinit var biometricExecutor: Executor

    // “Gate” zastavice
    private var authInProgress = false
    private var forceAuth = false
    private var intentPermissionDialogShown = false

    companion object {
        private var didAuthThisSession = false
        const val EXTRA_CALL_CHAT_ID = "EXTRA_CALL_CHAT_ID"
        const val EXTRA_OPEN_FRAGMENT = "OPEN_FRAGMENT"
        const val EXTRA_OPEN_FRAGMENT_CHAT_INBOX = "chat_inbox"
        const val EXTRA_OPEN_FRAGMENT_CALENDAR = "calendar"
        const val EXTRA_REFRESH_HOME_FRAGMENT = "REFRESH_HOME_FRAGMENT"

        fun start(
            activity: FragmentActivity,
            clearRun: Boolean = false,
            openChatInbox: Boolean = false,
            openCalendar: Boolean = false,
            refreshDashboardData: Boolean = false,
            callChatId: String? = null,
            extraPushType: String? = null,
            extraPushPayload: String? = null,
            extraPushCallAccepted: Boolean? = null,
        ) {

            if (activity is HomeActivity) {
                (activity as HomeActivity).setCurrentFragment(HomeFragment())
                return
            }

            activity.startActivity(Intent(activity, HomeActivity::class.java).apply {
                if (clearRun)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (openChatInbox)
                    putExtra(EXTRA_OPEN_FRAGMENT, EXTRA_OPEN_FRAGMENT_CHAT_INBOX)
                if (openCalendar)
                    putExtra(EXTRA_OPEN_FRAGMENT, EXTRA_OPEN_FRAGMENT_CALENDAR)
                if (refreshDashboardData)
                    putExtra(EXTRA_REFRESH_HOME_FRAGMENT, true)
                if (!callChatId.isNullOrEmpty())
                    putExtra(EXTRA_CALL_CHAT_ID, callChatId)

                if (!extraPushType.isNullOrEmpty())
                    putExtra(EXTRA_PUSH_TYPE, extraPushType)
                if (!extraPushPayload.isNullOrEmpty())
                    putExtra(EXTRA_PUSH_PAYLOAD, extraPushPayload)
                if (extraPushCallAccepted != null)
                    putExtra(EXTRA_PUSH_IS_CALL_ACCEPTED, extraPushCallAccepted)
            })
            activity.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setViewModels()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fab = binding.fab
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, windowInsets ->
            val insetsKeyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val insetsSystem = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
            // Apply the insets as padding to the view. Here, set all the dimensions
            // as appropriate to your layout. You can also update the view's margin if
            // more appropriate.
            view.updateLayoutParams {
                if (this is CoordinatorLayout.LayoutParams) this.bottomMargin =
                    if (insetsKeyboard.bottom > 0) insetsKeyboard.bottom else insetsSystem.bottom
            }
            windowInsets
        }

        setupBiometrics()
        maybeAuthenticateOrInit()
        onNewIntentLD.value = intent
        Log.i("sssssadad", "usao ovdje")
        handlePadding()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.getBooleanExtra("REFRESH_HOME_FRAGMENT", false)) {
            dashboardViewModel.dateParem.value = null
            val menuItem2 = binding.bottomNavigationView.menu.getItem(2)
            menuItem2.isChecked = true
            setCurrentFragment(HomeFragment())
            intent.removeExtra("REFRESH_HOME_FRAGMENT")
        }

        onNewIntentLD.value = intent
        handlePushIntent(intent)
    }


    private fun handlePadding() {
        val appBar = binding.homeFragmentContainerView
        val bottomNav = binding.bottomNavigationView

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = bars.top)
            bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }

        appBar.setBackgroundColor(getColor(R.color.white_bg))
    }

    private fun handlePushIntent(intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_PUSH_TYPE)
        if (!TextUtils.isEmpty(type)) {
            val payloadStr = intent?.getStringExtra(EXTRA_PUSH_PAYLOAD)
            chatId = CloudMessagingService.extractChatId(payloadStr) ?: ""
            when (type) {
                TYPE_NEW_MESSAGE, TYPE_CREATE_CHAT, TYPE_MODIFIED_CHAT -> {

                    // Just lead to inbox tab if PIN not set
                    if (!isPinSet()) {
                        showChatFragment()
                    } else
                    // Try to get chat history from response
                        getMainViewModel().chatListResponseLiveData.observeOnce(this) { response ->
                            if (response == null) return@observeOnce
                            response.data.forEach { chatItem ->
                                if (chatItem.id == chatId) {
                                    startActivity(
                                        Intent(
                                            this,
                                            ChatMessagesActivity::class.java
                                        ).apply {
                                            putExtra(
                                                ChatMessagesActivity.EXTRA_CHAT_ITEM,
                                                Gson().toJson(chatItem)
                                            )
                                            putExtra(ChatMessagesActivity.EXTRA_CHAT_ID, chatId)
                                        })
                                }
                            }
                        }
                }

                TYPE_PRESCRIPTION -> {
                    setCurrentFragment(HealthcareFragment())
                }

                TYPE_RECORD_ACCESS -> {
                    setCurrentFragment(HealthcareFragment())
                }

                TYPE_MEDICINE_REM -> {
                    setCurrentFragment(CalendarFragment())
                }

                TYPE_ACTIVITY_REM -> {
                    setCurrentFragment(CalendarFragment())
                }

                TYPE_NEW_CALL -> {

                    // Check if call accepted with notification action
                    if (intent.extras?.containsKey(EXTRA_PUSH_IS_CALL_ACCEPTED) == true) {
                        val isCallAccepted =
                            intent.getBooleanExtra(EXTRA_PUSH_IS_CALL_ACCEPTED, false)
                        if (isCallAccepted) onAcceptCall(
                            chatId,
                            CloudMessagingService.extractIsVideo(payloadStr)
                        )
                        else onDeclineCall(chatId)
                    }
                }

                TYPE_NEW_CONTACT_REQUEST_DECLINED,
                TYPE_NEW_CONTACT_REQUEST,
                TYPE_VIDEO_CONSULTATION_SCHEDULED -> {
                    startActivity(Intent(this, NotificationActivity::class.java))
                }
            }

            intent?.removeExtra(EXTRA_PUSH_TYPE)
            intent?.removeExtra(EXTRA_PUSH_PAYLOAD)
            intent?.removeExtra(EXTRA_PUSH_IS_CALL_ACCEPTED)
        }

        // Check chat from deeplink
        val deeplinkCallChatId = intent?.getStringExtra(EXTRA_CALL_CHAT_ID)
        if (!TextUtils.isEmpty(deeplinkCallChatId)) {

            // Show network pin not set
            if (!isPinSet()) {
                showChatFragment()
                return
            }
            binding.progressBar.visibility = View.VISIBLE
            chatId = deeplinkCallChatId!!
            LoginViewModel.get(this).anonymousLoginToChat(chatId = chatId, pin = getStoredPin())
                .observe(this) {
                    if (it == null) return@observe

                    // Set token to refresh AVC connection
                    if (getMainViewModel().userResponseLiveData.value == null) {
                        PreferenceHelper.saveToken(this, it)
                    }

                    // Listen for AVC startup
                    getMainViewModel().userResponseLiveData.observe(
                        this,
                        object : Observer<UserResponse?> {
                            override fun onChanged(value: UserResponse?) {
                                if (value == null) return
                                getMainViewModel().userResponseLiveData.removeObserver(this)
                                onAcceptCall(
                                    chatId,
                                    false
                                )
                            }
                        })

                }
        }
    }

    override fun startCallActivity(
        joinCallResponse: JoinCallResponse,
        chatId: String,
        isVideo: Boolean,
        notificationIdCall: Int?,
        anonymousName: String?
    ) {
        super.startCallActivity(
            joinCallResponse,
            chatId,
            isVideo,
            notificationIdCall,
            anonymousName
        )
        this.runOnUiThread { binding.progressBar.visibility = View.GONE }
    }

    fun showChatFragment() {
        defautlHomeBtn()
        binding.bottomNavigationView.menu[1].isChecked = true
        setCurrentFragment(ChatInboxFragment())
    }

    override fun onResume() {
        super.onResume()
        if (isPinSet()) {
            profileViewModel.keepALive(
                KeepLiveRequest(
                    2,
                    getStoredFcmToken(),
                    "Android",
                    getStoredPin()

                )
            )
        }

        // Ako smo u “gate” modu i još nismo autentifikovani u ovoj sesiji -> reprompt
        if (forceAuth && !didAuthThisSession) {
            binding.biometricsWrapper.visibility = View.VISIBLE
            repromptBiometrics(300)
        }
        handleOpenFragment(intent)
    }

    override fun onPause() {
        super.onPause()
        if (isPinSet()) {
            profileViewModel.keepALive(
                KeepLiveRequest(
                    2,
                    getStoredFcmToken(),
                    "Android",
                    getStoredPin()

                )
            )

        }
    }

    private fun handleOpenFragment(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_OPEN_FRAGMENT)) {
            "HOME" -> {
                finish()
                setCurrentFragment(HomeFragment())
            }

            EXTRA_OPEN_FRAGMENT_CHAT_INBOX -> {
                val fragment = ChatInboxFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.home_fragment_container_view, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    // ----------------  BIOMETRIJA  ----------------

    private fun setupBiometrics() {
        biometricExecutor = ContextCompat.getMainExecutor(this)
        // Prompt kreiramo svaki put iznova u repromptBiometrics()
    }

    private fun buildPromptInfo(): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometrics_title))
            .setSubtitle(getString(R.string.biometrics_subtitle))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .setNegativeButtonText(getString(R.string.cancel))
                }
            }
            .build()

    private fun buildBiometricPrompt(): BiometricPrompt =
        BiometricPrompt(
            this,
            biometricExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    didAuthThisSession = true
                    initAfterAuth()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // korisnik je odustao / greška / lockout
                    authInProgress = false
                    if (!forceAuth) return

                    binding.biometricsWrapper.visibility = View.VISIBLE

                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            repromptBiometrics(1500)
                        }

                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            // “Neću” / back — i dalje blokiramo app, pa reprompt
                            repromptBiometrics(400)
                        }

                        else -> {
                            // sve ostalo — pokušaj ponovo
                            repromptBiometrics(400)
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    // pokušan pogrešan biometrijski input — prompt ostaje otvoren
                }
            }
        )

    private fun repromptBiometrics(delayMs: Long = 400) {
        if (authInProgress) return
        authInProgress = true

        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) {
                buildBiometricPrompt().authenticate(buildPromptInfo())
            } else {
                authInProgress = false
            }
        }, delayMs)
    }

    private fun maybeAuthenticateOrInit() {
        if (didAuthThisSession) {
            // već smo prošli auth u ovoj sesiji
            initAfterAuth()
            return
        }

        val can = BiometricManager.from(this)
            .canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

        when (can) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // strogi gate: tražimo auth, bez izlaza dok ne uspe
                forceAuth = true
                binding.biometricsWrapper.visibility = View.VISIBLE
                repromptBiometrics(300)
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                // Nema biometrije ni fallback-a → izloguj i idi na login
                //  goToLoginAndFinish()
                //    binding.biometricsWrapper.visibility = View.GONE

                binding.root.postDelayed({
                    binding.biometricsWrapper.visibility = View.GONE
                }, 500)
            }

            else -> {
                // Ostali statusi – probaj ipak prompt
                forceAuth = true
                binding.biometricsWrapper.visibility = View.VISIBLE
                repromptBiometrics(300)
            }
        }
    }

    // ----------------  INIT NAKON AUTH  ----------------

    private fun initAfterAuth() {
        forceAuth = false
        binding.biometricsWrapper.visibility = View.GONE
        setViewModels()
        setupUi()
        subscribeToObservers()
    }

    private fun setViewModels() {
        val dashboardRepository = DashboardRepository()
        dataStoreRepository = DataStoreRepository(this)
        val dashboardViewModelFactory =
            DashboardViewModelFactory(dashboardRepository, dataStoreRepository)

        dashboardViewModel = ViewModelProvider(
            this,
            dashboardViewModelFactory
        ).get(DashboardViewModel::class.java)

        val profileRepository = ProfileRepository()
        val profileViewModelFactory =
            ProfileViewModelFactory(profileRepository, dataStoreRepository)
        profileViewModel =
            ViewModelProvider(this, profileViewModelFactory)
                .get(ProfileViewModel::class.java)
    }

    private fun setupUi() {
        binding.biometricsWrapper.visibility = View.GONE

        dashboardViewModel.checkTerms()
        checkNotificationPermission()

        // Open calendar
        if (intent.getStringExtra(EXTRA_OPEN_FRAGMENT).equals(EXTRA_OPEN_FRAGMENT_CALENDAR)) {
            setCurrentFragment(CalendarFragment())
        }

        val menuItem = binding.bottomNavigationView.menu.getItem(2)
        menuItem.isChecked = true

        binding.bottomNavigationView.setOnItemSelectedListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 100) {
                return@setOnItemSelectedListener false
            }
            lastClickTime = currentTime

            when (it.itemId) {
                R.id.menu_item_web -> {
                    defautlHomeBtn()
                    setCurrentFragment(ChatInboxFragment())
                }

                R.id.menu_item_health -> {
                    defautlHomeBtn()
                    setCurrentFragment(HealthcareFragment())
                }

                R.id.menu_item_notification -> {
                    defautlHomeBtn()
                    setCurrentFragment(HomeFragment())
                }

                R.id.menu_item_life_way -> {
                    defautlHomeBtn()
                    setCurrentFragment(WayOfLifeFragment())
                }

                R.id.menu_item_calendar -> {
                    defautlHomeBtn()
                    setCurrentFragment(CalendarFragment())
                }
            }
            true
        }

        fab.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        fab.setOnClickListener {
            fab.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            dashboardViewModel.dateParem.value = null
            val menuItem2 = binding.bottomNavigationView.menu.getItem(2)
            menuItem2.isChecked = true
            setCurrentFragment(HomeFragment())
        }
    }

    private fun checkNotificationPermission() {

        // Check Show on lock screen permission on MIUI os
        if (!isShowOnLockScreenPermissionEnable() && !PreferenceHelper.isPermissionAdded(this)) {
            if (Build.MANUFACTURER.equals("Xiaomi", true)) {
                BottomSheetDialogConfirmFragment().apply {
                    arguments = bundleOf(
                        "header_title" to this@HomeActivity.getString(R.string.activity_home_full_screen_intent_permission_header),
                        "content" to this@HomeActivity.getString(R.string.activity_home_full_screen_intent_permission_message),
                        "title" to "NotificationSettingsFragment"
                    )
                    intentPermissionDialogShown = true
                }.setDeleteListener(object :
                    BottomSheetDialogConfirmFragment.OnDeleteListener {
                    override fun onDelete(
                        value: String?,
                        reasonsValue: Int?,
                        usingAppValue: Int?
                    ) {
                        if (value != "No") {
                            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                            intent.setClassName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.permissions.PermissionsEditorActivity"
                            )
                            intent.putExtra("extra_pkgname", packageName)
                            startActivity(intent)
                        }
                    }
                }).show(supportFragmentManager, "BottomSheetDialogConfirmFragment")
            }
        }
    }

    private fun isShowOnLockScreenPermissionEnable(): Boolean {
        return try {
            val manager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )

            val result = method.invoke(
                manager,
                10020,
                applicationInfo.uid,   // sigurnije od Binder.getCallingUid()
                packageName
            ) as Int

            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            true   // da ne blokiraš usera ako ne radi
        }
    }


    fun setCurrentFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.home_fragment_container_view, fragment)
            addToBackStack(null)
            commit()
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        val v = supportFragmentManager.findFragmentById(R.id.home_fragment_container_view)
        if (v?.javaClass?.simpleName == "HomeFragment") {
            moveTaskToBack(true)
            return
        }

        val currentFragment =
            supportFragmentManager.findFragmentById(R.id.home_fragment_container_view)

        if (currentFragment is PrescribedListMedicamentsFragment
            || currentFragment is MedicalCartonFragment
        ) {
            setCurrentFragment(HealthcareFragment()); return
        } else if (currentFragment is RecipeDetailsFragment
            || currentFragment is MedicamentDetailsFragment
        ) {
            setCurrentFragment(PrescribedListMedicamentsFragment()); return
        } else if (currentFragment !is ChatInboxFragment &&
            currentFragment !is HealthcareFragment &&
            currentFragment !is FitParametersFragment &&
            currentFragment !is CalendarFragment
        ) {
            super.onBackPressed(); return
        }

        val selectedItem = binding.bottomNavigationView.selectedItemId
        when (selectedItem) {
            R.id.menu_item_web, R.id.menu_item_health, R.id.menu_item_notification,
            R.id.menu_item_life_way, R.id.menu_item_calendar -> setCurrentFragment(HomeFragment())

            else -> super.onBackPressed()
        }
    }

    private fun defautlHomeBtn() {
        fab.setImageResource(R.drawable.ic_home_white)
        fab.clearColorFilter()
        fab.imageTintList = null
    }

    private fun subscribeToObservers() {
        dashboardViewModel.getCheckTermsLiveData.observe(this) { response ->
            if (response.isSuccessful) {
                val data = response.body()
                val intValue = data.toString().toDoubleOrNull()?.toInt() ?: 0
                if (intValue == 0) {
                    val bottomSheet = BottomSheetConfirmDeleteReminderFragment()
                    bottomSheet.setCheckedListener(this)
                    bottomSheet.setTextSource("HomeActivity-check_terms")
                    bottomSheet.show(supportFragmentManager, bottomSheet.tag)
                }
            }
        }

        dashboardViewModel.getConfirmTermsLiveData.observe(this) { response ->
            if (response.isSuccessful) {
                Toast.makeText(
                    this,
                    getString(R.string.activity_home_accept_terms),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        onNewIntentLD.observe(this) { onNewIntentItem ->
            if (didAuthThisSession)
                handlePushIntent(onNewIntentItem)
        }
    }

    override fun onChecked(isChecked: Int) {
        if (isChecked == 1) {
            dashboardViewModel.confirmTerms(PinRequest(pin = getStoredPin()))
        } else {
            logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}
