package com.tonapps.singer.screen.root;

import androidx.lifecycle.ViewModel;
import com.tonapps.singer.core.account.AccountRepository;
import com.tonapps.singer.core.account.AccountRepositoryImpl;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\n"}, d2 = {"Lcom/tonapps/singer/screen/root/RootViewModel;", "Landroidx/lifecycle/ViewModel;", "accountRepository", "Lcom/tonapps/singer/core/account/AccountRepository;", "(Lcom/tonapps/singer/core/account/AccountRepository;)V", "authFlow", "Lkotlinx/coroutines/flow/StateFlow;", "", "getAuthFlow", "()Lkotlinx/coroutines/flow/StateFlow;", "singer_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel
public final class RootViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull
    private final com.tonapps.singer.core.account.AccountRepository accountRepository = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> authFlow = null;
    
    @javax.inject.Inject
    public RootViewModel(@org.jetbrains.annotations.NotNull
    com.tonapps.singer.core.account.AccountRepository accountRepository) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> getAuthFlow() {
        return null;
    }
}