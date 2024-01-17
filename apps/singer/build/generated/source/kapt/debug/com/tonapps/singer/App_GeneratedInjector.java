package com.tonapps.singer;

import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.internal.GeneratedEntryPoint;

@OriginatingElement(
    topLevelClass = App.class
)
@GeneratedEntryPoint
@InstallIn(SingletonComponent.class)
public interface App_GeneratedInjector {
  void injectApp(App app);
}
