package com.tonapps.singer.core.account;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001R\u0018\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/tonapps/singer/core/account/AccountRepository;", "", "authFlow", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "getAuthFlow", "()Lkotlinx/coroutines/flow/MutableStateFlow;", "singer_debug"})
public abstract interface AccountRepository {
    
    @org.jetbrains.annotations.NotNull
    public abstract kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> getAuthFlow();
}