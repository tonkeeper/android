package com.tonapps.singer.screen.root;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import com.tonapps.singer.R;
import com.tonapps.singer.screen.IntroFragment;
import dagger.hilt.android.AndroidEntryPoint;
import uikit.base.BaseActivity;
import uikit.base.BaseFragment;
import uikit.navigation.Navigation;

@dagger.hilt.android.AndroidEntryPoint
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 -2\u00020\u00012\u00020\u00022\u00020\u0003:\u0001-B\u0005\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u001a\u0010\u0013\u001a\u00020\u00102\u0006\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0017H\u0016J\u0012\u0010\u0018\u001a\u00020\u00102\b\u0010\u0019\u001a\u0004\u0018\u00010\u001aH\u0014J\b\u0010\u001b\u001a\u00020\u0015H\u0016J\u0018\u0010\u001c\u001a\u00020\u00102\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020\u0015H\u0016J\u0010\u0010 \u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020!H\u0016J\u0018\u0010\"\u001a\u00020\u00102\u0006\u0010#\u001a\u00020\u001e2\u0006\u0010$\u001a\u00020\u001aH\u0016JH\u0010%\u001a\u00020\u00102\u0006\u0010#\u001a\u00020\u001e26\u0010&\u001a2\u0012\u0013\u0012\u00110\u001e\u00a2\u0006\f\b(\u0012\b\b)\u0012\u0004\b\b(#\u0012\u0013\u0012\u00110\u001a\u00a2\u0006\f\b(\u0012\b\b)\u0012\u0004\b\b(*\u0012\u0004\u0012\u00020\u00100\'H\u0016J\u0010\u0010+\u001a\u00020\u00102\u0006\u0010,\u001a\u00020\u001eH\u0016R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u001b\u0010\t\u001a\u00020\n8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\r\u0010\u000e\u001a\u0004\b\u000b\u0010\f\u00a8\u0006."}, d2 = {"Lcom/tonapps/singer/screen/root/RootActivity;", "Luikit/base/BaseActivity;", "Luikit/navigation/Navigation;", "Landroid/view/ViewTreeObserver$OnPreDrawListener;", "()V", "contentView", "Landroid/view/View;", "toastView", "Landroidx/appcompat/widget/AppCompatTextView;", "viewModel", "Lcom/tonapps/singer/screen/root/RootViewModel;", "getViewModel", "()Lcom/tonapps/singer/screen/root/RootViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "add", "", "fragment", "Luikit/base/BaseFragment;", "initRoot", "skipPasscode", "", "intent", "Landroid/content/Intent;", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onPreDraw", "openURL", "url", "", "external", "remove", "Landroidx/fragment/app/Fragment;", "setFragmentResult", "requestKey", "result", "setFragmentResultListener", "listener", "Lkotlin/Function2;", "Lkotlin/ParameterName;", "name", "bundle", "toast", "message", "Companion", "singer_debug"})
public final class RootActivity extends uikit.base.BaseActivity implements uikit.navigation.Navigation, android.view.ViewTreeObserver.OnPreDrawListener {
    private static final int hostFragmentId = 0;
    @org.jetbrains.annotations.NotNull
    private final kotlin.Lazy viewModel$delegate = null;
    private android.view.View contentView;
    private androidx.appcompat.widget.AppCompatTextView toastView;
    @org.jetbrains.annotations.NotNull
    public static final com.tonapps.singer.screen.root.RootActivity.Companion Companion = null;
    
    public RootActivity() {
        super();
    }
    
    private final com.tonapps.singer.screen.root.RootViewModel getViewModel() {
        return null;
    }
    
    @java.lang.Override
    protected void onCreate(@org.jetbrains.annotations.Nullable
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override
    public boolean onPreDraw() {
        return false;
    }
    
    @java.lang.Override
    public void setFragmentResult(@org.jetbrains.annotations.NotNull
    java.lang.String requestKey, @org.jetbrains.annotations.NotNull
    android.os.Bundle result) {
    }
    
    @java.lang.Override
    public void setFragmentResultListener(@org.jetbrains.annotations.NotNull
    java.lang.String requestKey, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function2<? super java.lang.String, ? super android.os.Bundle, kotlin.Unit> listener) {
    }
    
    @java.lang.Override
    public void initRoot(boolean skipPasscode, @org.jetbrains.annotations.Nullable
    android.content.Intent intent) {
    }
    
    @java.lang.Override
    public void add(@org.jetbrains.annotations.NotNull
    uikit.base.BaseFragment fragment) {
    }
    
    @java.lang.Override
    public void remove(@org.jetbrains.annotations.NotNull
    androidx.fragment.app.Fragment fragment) {
    }
    
    @java.lang.Override
    public void openURL(@org.jetbrains.annotations.NotNull
    java.lang.String url, boolean external) {
    }
    
    @java.lang.Override
    public void toast(@org.jetbrains.annotations.NotNull
    java.lang.String message) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/tonapps/singer/screen/root/RootActivity$Companion;", "", "()V", "hostFragmentId", "", "getHostFragmentId", "()I", "singer_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final int getHostFragmentId() {
            return 0;
        }
    }
}