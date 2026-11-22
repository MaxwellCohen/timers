const easProfile = process.env.EAS_BUILD_PROFILE;

const androidBuildProps = {
  enableMinifyInReleaseBuilds: true,
  enableShrinkResourcesInReleaseBuilds: true,
};

// Internal APK profile: drop x86/x86_64 emulator libs.
if (easProfile && easProfile !== 'development') {
  androidBuildProps.buildArchs = ['arm64-v8a', 'armeabi-v7a'];
  androidBuildProps.enableBundleCompression = true;
}

module.exports = {
  expo: {
    name: 'Timers',
    slug: 'timers',
    version: '1.0.0',
    orientation: 'portrait',
    icon: './assets/images/icon.png',
    scheme: 'timers',
    userInterfaceStyle: 'dark',
    backgroundColor: '#0E1012',
    platforms: ['android'],
    android: {
      adaptiveIcon: {
        backgroundColor: '#181B1F',
        foregroundImage: './assets/images/android-icon-foreground.png',
        monochromeImage: './assets/images/android-icon-monochrome.png',
      },
      predictiveBackGestureEnabled: false,
      package: 'com.maxwellcohen.timers',
    },
    plugins: [
      [
        'expo-splash-screen',
        {
          image: './assets/images/splash-icon.png',
          imageWidth: 160,
          resizeMode: 'contain',
          backgroundColor: '#0E1012',
        },
      ],
      ['expo-build-properties', { android: androidBuildProps }],
      'expo-font',
    ],
    experiments: {
      reactCompiler: true,
    },
    extra: {
      eas: {
        projectId: 'dcb3a715-7950-4966-a2d9-a4f3ce7d306e',
      },
    },
  },
};
