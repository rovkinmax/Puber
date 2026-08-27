package com.kino.puber

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
import coil3.request.crossfade
import coil3.memory.MemoryCache
import coil3.util.DebugLogger
import com.kino.puber.core.error.DefaultErrorHandler
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.LinkingDebugTree
import com.kino.puber.core.system.AndroidResourceProvider
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.data.di.apiModule
import com.kino.puber.data.di.repositoryModule
import com.kino.puber.domain.di.interactorModule
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import timber.log.Timber

private val resourceModule = module {
    single<ResourceProvider> { AndroidResourceProvider(get()) }
}

private val handlersModule = module {
    singleOf(::DefaultErrorHandler) { bind<ErrorHandler>() }
}

open class PuberApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        if (!shouldInitializeRuntime()) return

        configureRuntime()
        initDi()
        initLogger()
    }

    /**
     * Allows an instrumentation-only control process to avoid initializing
     * the measured application runtime while preparing its sandbox.
     */
    protected open fun shouldInitializeRuntime(): Boolean = true

    /**
     * Allows an instrumentation-only application subclass to configure a
     * deterministic endpoint before any Koin definition is created.
     */
    protected open fun configureRuntime() = Unit

    /**
     * Allows an instrumentation-only application subclass to replace
     * production dependencies without changing the production module graph.
     */
    protected open fun runtimeModules(): List<Module> = emptyList()

    private fun initDi() {
        val koinApplication = startKoin {
            androidContext(this@PuberApp)
            modules(
                listOf(
                    resourceModule,
                    handlersModule,
                    apiModule,
                    repositoryModule,
                    interactorModule,
                ) + runtimeModules(),
            )
        }
        koinApplication.koin.get<ApiDomainInteractor>().initialize()
    }

    private fun initLogger() {
        if (BuildConfig.DEBUG) {
            Timber.plant(LinkingDebugTree())
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .crossfade(false)
            .networkCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath(normalize = true))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .components {
                add(HttpsEnforcingInterceptor())
            }
            .build()
    }
}

private class HttpsEnforcingInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is String && data.startsWith("http://")) {
            val newRequest = chain.request.newBuilder()
                .data(data.replaceFirst("http://", "https://"))
                .build()
            return chain.withRequest(newRequest).proceed()
        }
        return chain.proceed()
    }
}
