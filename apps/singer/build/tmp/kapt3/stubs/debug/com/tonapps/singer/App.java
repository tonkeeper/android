package com.tonapps.singer;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import dagger.hilt.android.HiltAndroidApp;

@dagger.hilt.android.HiltAndroidApp
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0007\u0018\u0000 \u00052\u00020\u0001:\u0001\u0005B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0016\u00a8\u0006\u0006"}, d2 = {"Lcom/tonapps/singer/App;", "Landroid/app/Application;", "()V", "onCreate", "", "Companion", "singer_debug"})
public final class App extends android.app.Application {
    public static com.tonapps.singer.App instance;
    @org.jetbrains.annotations.NotNull
    public static final com.tonapps.singer.App.Companion Companion = null;
    
    public App() {
        super();
    }
    
    @java.lang.Override
    public void onCreate() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u001a\u0010\u0003\u001a\u00020\u0004X\u0086.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\b\u00a8\u0006\t"}, d2 = {"Lcom/tonapps/singer/App$Companion;", "", "()V", "instance", "Lcom/tonapps/singer/App;", "getInstance", "()Lcom/tonapps/singer/App;", "setInstance", "(Lcom/tonapps/singer/App;)V", "singer_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.tonapps.singer.App getInstance() {
            return null;
        }
        
        public final void setInstance(@org.jetbrains.annotations.NotNull
        com.tonapps.singer.App p0) {
        }
    }
}