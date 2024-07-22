
‰
T
BaselineProfileGeneratorcom.baselineprofilegenerate2ê£Ÿ¥¿√Æı:ë£Ÿ¥Ä„æ
Ü

Ójava.lang.IllegalArgumentException: Baseline Profile collection requires API 33+, or a rooted device running API 28 or higher and rooted adb session (via `adb root`).
at androidx.benchmark.macro.BaselineProfilesKt.buildMacrobenchmarkScope(BaselineProfiles.kt:154)
at androidx.benchmark.macro.BaselineProfilesKt.collect(BaselineProfiles.kt:49)
at androidx.benchmark.macro.junit4.BaselineProfileRule.collect(BaselineProfileRule.kt:136)
at androidx.benchmark.macro.junit4.BaselineProfileRule.collect$default(BaselineProfileRule.kt:126)
at com.baselineprofile.BaselineProfileGenerator.generate(BaselineProfileGenerator.kt:19)
"java.lang.IllegalArgumentExceptionÓjava.lang.IllegalArgumentException: Baseline Profile collection requires API 33+, or a rooted device running API 28 or higher and rooted adb session (via `adb root`).
at androidx.benchmark.macro.BaselineProfilesKt.buildMacrobenchmarkScope(BaselineProfiles.kt:154)
at androidx.benchmark.macro.BaselineProfilesKt.collect(BaselineProfiles.kt:49)
at androidx.benchmark.macro.junit4.BaselineProfileRule.collect(BaselineProfileRule.kt:136)
at androidx.benchmark.macro.junit4.BaselineProfileRule.collect$default(BaselineProfileRule.kt:126)
at com.baselineprofile.BaselineProfileGenerator.generate(BaselineProfileGenerator.kt:19)
"ˇ

logcatandroidÈ
Ê/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/logcat-com.baselineprofile.BaselineProfileGenerator-generate.txt"“

device-infoandroid∑
¥/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/device-info.pb"”

device-info.meminfoandroid∞
≠/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/meminfo"”

device-info.cpuinfoandroid∞
≠/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/cpuinfoû-
f
StartupBenchmarkscom.baselineprofile"startupCompilationBaselineProfiles2ë£Ÿ¥Ä∆˝:ë£Ÿ¥ÄÍÂõ%
∑org.junit.AssumptionViolatedException: got: <false>, expected: is <true>
at org.junit.Assume.assumeThat(Assume.java:106)
at org.junit.Assume.assumeTrue(Assume.java:50)
at androidx.benchmark.macro.junit4.MacrobenchmarkRule$applyInternal$1.evaluate(MacrobenchmarkRule.kt:131)
at androidx.test.rule.GrantPermissionRule$RequestPermissionStatement.evaluate(GrantPermissionRule.java:136)
at org.junit.rules.RunRules.evaluate(RunRules.java:20)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at androidx.test.ext.junit.runners.AndroidJUnit4.run(AndroidJUnit4.java:162)
at org.junit.runners.Suite.runChild(Suite.java:128)
at org.junit.runners.Suite.runChild(Suite.java:27)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
at org.junit.runner.JUnitCore.run(JUnitCore.java:115)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:68)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:59)
at androidx.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java:463)
at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:2205)
%org.junit.AssumptionViolatedException∑org.junit.AssumptionViolatedException: got: <false>, expected: is <true>
at org.junit.Assume.assumeThat(Assume.java:106)
at org.junit.Assume.assumeTrue(Assume.java:50)
at androidx.benchmark.macro.junit4.MacrobenchmarkRule$applyInternal$1.evaluate(MacrobenchmarkRule.kt:131)
at androidx.test.rule.GrantPermissionRule$RequestPermissionStatement.evaluate(GrantPermissionRule.java:136)
at org.junit.rules.RunRules.evaluate(RunRules.java:20)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at androidx.test.ext.junit.runners.AndroidJUnit4.run(AndroidJUnit4.java:162)
at org.junit.runners.Suite.runChild(Suite.java:128)
at org.junit.runners.Suite.runChild(Suite.java:27)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
at org.junit.runner.JUnitCore.run(JUnitCore.java:115)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:68)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:59)
at androidx.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java:463)
at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:2205)
"í

logcatandroid¸
˘/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/logcat-com.baselineprofile.StartupBenchmarks-startupCompilationBaselineProfiles.txt"“

device-infoandroid∑
¥/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/device-info.pb"”

device-info.meminfoandroid∞
≠/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/meminfo"”

device-info.cpuinfoandroid∞
≠/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/cpuinfoÜ-
Z
StartupBenchmarkscom.baselineprofilestartupCompilationNone2ë£Ÿ¥ÄÖ‘:ë£Ÿ¥Ä†¬õ%
∑org.junit.AssumptionViolatedException: got: <false>, expected: is <true>
at org.junit.Assume.assumeThat(Assume.java:106)
at org.junit.Assume.assumeTrue(Assume.java:50)
at androidx.benchmark.macro.junit4.MacrobenchmarkRule$applyInternal$1.evaluate(MacrobenchmarkRule.kt:131)
at androidx.test.rule.GrantPermissionRule$RequestPermissionStatement.evaluate(GrantPermissionRule.java:136)
at org.junit.rules.RunRules.evaluate(RunRules.java:20)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at androidx.test.ext.junit.runners.AndroidJUnit4.run(AndroidJUnit4.java:162)
at org.junit.runners.Suite.runChild(Suite.java:128)
at org.junit.runners.Suite.runChild(Suite.java:27)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
at org.junit.runner.JUnitCore.run(JUnitCore.java:115)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:68)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:59)
at androidx.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java:463)
at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:2205)
%org.junit.AssumptionViolatedException∑org.junit.AssumptionViolatedException: got: <false>, expected: is <true>
at org.junit.Assume.assumeThat(Assume.java:106)
at org.junit.Assume.assumeTrue(Assume.java:50)
at androidx.benchmark.macro.junit4.MacrobenchmarkRule$applyInternal$1.evaluate(MacrobenchmarkRule.kt:131)
at androidx.test.rule.GrantPermissionRule$RequestPermissionStatement.evaluate(GrantPermissionRule.java:136)
at org.junit.rules.RunRules.evaluate(RunRules.java:20)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)
at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at androidx.test.ext.junit.runners.AndroidJUnit4.run(AndroidJUnit4.java:162)
at org.junit.runners.Suite.runChild(Suite.java:128)
at org.junit.runners.Suite.runChild(Suite.java:27)
at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
at org.junit.runner.JUnitCore.run(JUnitCore.java:115)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:68)
at androidx.test.internal.runner.TestExecutor.execute(TestExecutor.java:59)
at androidx.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java:463)
at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:2205)
"Ü

logcatandroid
Ì/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/logcat-com.baselineprofile.StartupBenchmarks-startupCompilationNone.txt"“

device-infoandroid∑
¥/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/device-info.pb"”

device-info.meminfoandroid∞
≠/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/meminfo"”

device-info.cpuinfoandroid∞
≠/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/cpuinfo*∑
c
test-results.logOcom.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver¡
æ/Users/polstianka/StudioProjects/TonkeeperX/baselineprofile/main/build/outputs/androidTest-results/connected/nonMinifiedRelease/API_30_For_release_testing_(AVD) - 11/testlog/test-results.log 2
text/plain