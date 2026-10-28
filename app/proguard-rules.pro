# Coil discovers decoders/fetchers through its bundled consumer rules.
# Compose and AndroidX also ship their own consumer rules.

# ViewModelProvider instantiates AndroidViewModel through its Application
# constructor. Keep that entry point stable in minified release builds.
-keep class com.yg.liquidink.ui.MainViewModel {
    public <init>(android.app.Application);
}

# Keep source file names for readable crash reports, hide the original line table.
-renamesourcefileattribute SourceFile
