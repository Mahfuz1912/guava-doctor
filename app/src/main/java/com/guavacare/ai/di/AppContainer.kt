package com.guavacare.ai.di

import android.content.Context
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.guavacare.ai.ai.inference.HybridInferenceEngine
import com.guavacare.ai.core.utils.ApkInstallerHelper
import com.guavacare.ai.core.utils.LeafImageStorage
import com.guavacare.ai.data.local.PreferencesRepository
import com.guavacare.ai.data.local.database.ScanDatabase
import com.guavacare.ai.data.repository.AdvisoryRepository
import com.guavacare.ai.data.repository.CloudAccountRepository
import com.guavacare.ai.data.repository.ScanRepository
import com.guavacare.ai.data.repository.UpdateRepository
import com.guavacare.ai.domain.usecase.OrchardInsightsCalculator

/**
 * Clean constructor-injected dependency container scoping database, repositories,
 * image storage, preferences, update services, Firebase cloud sync, and HybridInferenceEngine.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val imageStorage: LeafImageStorage = LeafImageStorage(appContext)
    val database: ScanDatabase = ScanDatabase.getInstance(appContext)
    val scanRepository: ScanRepository = ScanRepository(database.scanDao(), imageStorage)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(appContext)
    val advisoryRepository: AdvisoryRepository = AdvisoryRepository(appContext)
    val inferenceEngine: HybridInferenceEngine = HybridInferenceEngine(appContext)
    val insightsCalculator: OrchardInsightsCalculator = OrchardInsightsCalculator()
    val updateRepository: UpdateRepository = UpdateRepository(appContext)
    val apkInstallerHelper: ApkInstallerHelper = ApkInstallerHelper(appContext)

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
    }

    val cloudAccountRepository: CloudAccountRepository = CloudAccountRepository(
        firestoreProvider = { firestore }
    )

    fun isFirebaseInitialized(): Boolean =
        runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)

    fun release() {
        inferenceEngine.close()
    }
}
