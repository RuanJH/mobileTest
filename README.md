package com.example.demo.di

import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryOwner
import javax.inject.Inject
import javax.inject.Provider

/**
 * 自定义的 ViewModelFactory，可以让 Dagger 创建的 ViewModel 拿到系统提供的 SavedStateHandle。
 */
class DaggerSavedStateViewModelFactory @Inject constructor(
    private val providers: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) {
    fun create(owner: SavedStateRegistryOwner): ViewModelProvider.Factory {
        return object : AbstractSavedStateViewModelFactory(owner, null) {
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
            ): T {
                val provider = providers[modelClass]
                    ?: error("No provider found for ${modelClass.name}")

                val viewModel = provider.get()
                if (viewModel is SupportsSavedStateHandle) {
                    viewModel.setSavedStateHandle(handle)
                }
                return viewModel as T
            }
        }
    }
}

/**
 * 所有需要 SavedStateHandle 的 ViewModel 都实现这个接口。
 */
interface SupportsSavedStateHandle {
    fun setSavedStateHandle(handle: SavedStateHandle)
}



class SharedViewModel @Inject constructor() : ViewModel(), SavedStateViewModel {

    private lateinit var handle: SavedStateHandle

    override fun setSavedStateHandle(handle: SavedStateHandle) {
        this.handle = handle
    }

    fun saveUserName(name: String) {
        handle["username"] = name
    }

    fun getUserName(): String {
        return handle["username"] ?: "Guest"
    }
}

@Module
abstract class ViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(SharedViewModel::class)
    abstract fun bindSharedViewModel(vm: SharedViewModel): ViewModel
}


@Composable
fun AppNavHost(
    navController: NavHostController,
    factory: DaggerSavedStateViewModelFactory
) {
    // 获取 Graph 级的 backStackEntry（比如整个 nav_graph）
    val graphEntry = remember(navController) {
        navController.getBackStackEntry("root_graph")
    }

    // 共享的 SharedViewModel（所有子页面共享）
    val sharedViewModel: SharedViewModel = viewModel(
        key = "SharedViewModel",
        factory = factory.create(graphEntry),
        viewModelStoreOwner = graphEntry
    )

    NavHost(navController, startDestination = "home", route = "root_graph") {
        composable("home") {
            HomeScreen(navController, sharedViewModel)
        }
        composable("detail") {
            DetailScreen(navController, sharedViewModel)
        }
    }
}
